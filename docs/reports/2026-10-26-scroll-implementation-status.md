# Kaydırma performansı: uygulama ve kanıt durumu

26 Ekim 2026. Dal: `perf/local-scroll-smoothness-2026-10-26`. Başlangıç kaynak commit'i: `e395981af203e1b44adcf9b69ef05dfa4c7d71f7`. PR #36 bağımlılığı korunmaktadır.

**Akıcılık kabulü henüz verilmedi.** Aşağıdaki sonuçlar kaynak davranışı, yerel testler ve paket bütünlüğünü kanıtlar. Fiziksel Galaxy S20 FE üzerinde 60/120 Hz karşılaştırması yapılmadı; ADB listesi boş ve Windows'ta Samsung/Android USB aygıtı görülmedi.

## Uygulanan kaynak adayları

- Harita geometrisi ekran ziyaretleri arasında, izdüşüm yolları ise geometri/viewport/yoğunluk değişene kadar yeniden kullanılır. Seçim ve trafik boyayı değiştirir. Delikler, antimeridyen kopyaları ve dokunma geometrisi korunur. Kenar elemesi keskin çizgi birleşimleri ve kenar yumuşatma payını içerir.
- Yerelleştirilmiş uygulama kaynakları tam yapılandırma ve güncel dil anahtarıyla yeniden kullanılır. Pencereye özgü bağlamlar önbelleğe alınmaz; harici dil değişimi, ViewModel/worker ve font ölçeği davranışları korunur.
- Aynı favicon adresinin eşzamanlı tüketicileri tek isteği ve çözülmüş bitmap'i paylaşır. Bir tüketicinin iptali diğerini kesmez; son tüketici ayrılınca HTTP işi iptal edilir. URL üretimi Main dışında yürür. 4 MiB bitmap sınırı, 192 px kalite sınırı, High filtreleme, 3x/2x/1x sırası, HTTPS/host/yönlendirme/MIME/boyut sınırları ve zaman bütçesi korunur.
- Analiz kartlarına kalıcı anahtarlar ve içerik türleri; günlük satırlarına içerik türü eklendi. Grafik maksimumu hatırlanır ve çizim sırasında geçici komşu-nokta çiftleri üretilmez. Sayı biçimi ve cihaz menüsü güncel dile bağlandı.
- R8 karşılaştırması `-PscrollMinify=true` ile açılır; kaynak küçültme kapalıdır. Moshi API ve eski kayıt modelleri için açık sınıf/alan/constructor kuralları vardır. Varsayılan inceleme yapılandırmasının R8 tercihi, çalışma zamanı ve fiziksel kapılar geçilmeden kalıcı olarak değiştirilmedi.

Görünüm, animasyon süreleri, API adresleri/alanları, paket kimliği, 1.000 günlük sınırı, cursor/generation korumaları ve hukuki DRAFT durumu değiştirilmedi. SSE yayınlama ve büyük kartları sanallaştırma, önce iz kanıtı gerektiren koşullu işler olarak durmaktadır.

## Gerçek doğrulama sonuçları

| Kapı | Sonuç | Sınır |
|---|---|---|
| Hedefli JVM regresyonları | 26/26 geçti; öncesinde altı davranış hatası gözlendi | Favicon ortak istek/iptal, harita ve dil kaynakları |
| Tam JVM paketi | 287 test; 0 hata, 0 atlama | `final-unit-lint.log`, 13 dk 4 sn; son miter payı için ayrıca odaklı kontrol |
| Son harita sınır kontrolü | 6/6 geçti; 0 atlama | `map-final-check.log`, 9 dk 20 sn; keskin birleşim/kenar payı sonrası |
| Python denetimleri | 43/43 geçti | Atlanmış test, eksik/tekrarlı iz, boş/NaN frame dizisi, gömülmüş R8 test kodu dahil |
| Android Lint | 0 hata, 163 mevcut uyarı | Yeni Lint baseline veya kontrol gevşetmesi eklenmedi |
| R8 uygulama APK'ları | Benchmark ve release üretildi | Derleme başarısı; cihazda çalıştırılmış sayılmaz |
| R8 release test APK'sı | Derleme geçti, 5 dk 16 sn | 18 zorunlu native testin çalışması CI/cihaz kapısıdır |
| Release DEX + mapping | 2 DEX, 5.890 tür referansı; gerçek test APK'sı ve bozuk DEX reddedildi | Sınıf başlıkları ile gömülmüş metotların asıl sahipleri incelenir |
| Bağımsız inceleme | İki P2 ölçüm bulgusu düzeltildi; yedi kanıt testi bağımsız olarak geçti | Fiziksel performans onayı değildir |

R8 açıldığında yalnız isteğe bağlı JVM TLS sağlayıcılarına ait dokuz eksik sınıf için dar kurallar gerekti. Bunlar [Square'ın resmî tüketici kurallarının](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/resources/META-INF/proguard/okhttp3.pro) daha dar bir alt kümesidir. Test APK'sındaki iki ek kural, Android'de yürütülmeyen derleyici anotasyonlarına özgüdür; üretim kurallarına eklenmemiştir. Android TLS sağlayıcısı ve sertifika doğrulaması korunur.

Windows test ortamındaki Conscrypt dil hatası, yalnız test JVM dilini İngilizce yaparak giderildi. Java soket yolu yalnız süreç ortamında kısaltıldı. Windows dili ve güvenlik yazılımı ayarları değiştirilmedi. İptal testindeki MockWebServer kapanış yarışı, üretim zaman aşımını değiştirmeden bekleyen soketle giderildi.

## Yerel ölçüm altyapısı

13 senaryo dokuz hedefi, 200/1.000 günlük satırını ve derin harita/seri grafiği/500 cihaz kartını kapsar. Giriş, gezinme, sayfalama, veri bekleme ve hedefi bulma ölçüm bloğunun dışındadır. Analiz özeti ile seri yüklemesi birlikte beklenir. İzin/engel ve Gizlilik listeleri sentetik verilerle doldurulur.

`scripts/run_local_scroll.ps1`, yalnız `.benchmark` paketini kabul eder; üretim kurulumunu kaldırmaz veya temizlemez. `scripts/verify_scroll_capture.py`, 13 başlamış/başarılı senaryoyu, her senaryoda 10 dolu frame dizisini ve 130 farklı Perfetto dosyasını zorunlu kılar. Hash'ler saklanır. Başarılı yakalama sonucunda bile `performanceAccepted=false` kalır: izlerin gerçek yenileme aralığı, hareket süresi, termal durum ve eşlenmiş dağılımlar açısından incelenmesi gerekir.

`scripts/prepare_scroll_baseline.py`, başlangıcı Git arşivinden çıkarır ve yalnız aynı ölçüm altyapısı ile iki Analiz test işaretini ekler. `provenance.json`, diğer bütün `app/src/main` dosyalarının başlangıçla aynı olduğunu doğrular. İlk 10 senaryolu arşiv güncel karşılaştırmada kullanılmamalıdır.

Güncel 13 senaryolu başlangıç derlemesi 6 dk 34 sn içinde geçti:

| APK | SHA-256 |
|---|---|
| Başlangıç uygulaması | `0fac6612f1acb1b3c4e0b2a73fd9d67b5ce211a9a01ed7737ebdefc018f806e6` |
| Başlangıç ölçüm APK'sı | `4fa194130693c90d35905883db2199c44970925b3c0a84fb172c8c1fc8c10416` |

Bu dosyalar yerel `.superpowers/paired-baseline-v2` arşivindedir. Aday paket en son kaynak commit'i ve aynı harness hash'leriyle yeniden kaydedilmelidir. APK boyutunun azalması frame süresi, bellek veya akıcılık kabulü olarak kullanılmaz.

## Açık kalan plan kapıları

1. Tanılama varyantında JankStats/etkin pencere kadansı ve etiketler; ayrı sıcak/soğuk/tekrarlı favicon yükleri; 5/s ve 50/s akış gecikmesi; 20 gezinme döngüsünde bellek ölçümü henüz tamamlanmadı.
2. Galaxy S20 FE'de gerçek 60 ve 120 Hz, eşlenmiş başlangıç/adayı, termal ve frame dağılımları ölçülmedi. Sadece panel modu veya başarılı test özeti kabul kanıtı değildir.
3. Mevcut SDK adayları karşılaştırıldıktan sonraki ayrı Compose/compileSdk 37 geçişi ve görsel karşılaştırmaları yapılmadı. Mevcut Kotlin/AGP/Gradle sürümleri değiştirilmedi.
4. Uygulamaya ait Baseline Profile üretimi, paket içinde doğrulama ve None/Partial karşılaştırması yapılmadı.
5. Küçültülmüş gerçek native uyumluluk testleri, normal PR/CI sonucunun teyidi ve fiziksel kapıdan sonraki APK teslimi açıktır. Bu rapor planın tamamlandığı veya uygulamanın yayıma hazır olduğu anlamına gelmez.

BrowserStack kullanılmadı. Sonraki adımlar onaylanmış ana plan içinde yürütülür; bu ilk uygulama dilimi diğer fazların yerine geçmez.
