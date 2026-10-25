# Arayüz Akıcılığı ve Performans Regresyonu Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task after the user's review. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Önceki sürümdeki akıcı gezinme deneyimini, mevcut işlevleri ve mimari sınırları koruyarak geri kazanmak; nedeni ölçümle belirlemek ve sonucu aynı cihazdaki karşılaştırmayla kanıtlamak.

**Architecture:** Önce kaynak değişikliği, APK derleme türü ve cihaz koşulları birbirinden ayrılır. Mevcut izole `.benchmark` varyantı ve gerçek Home/Logs/Analytics ekranları kullanılarak önce/sonra ölçülür; yalnız ölçümde belirlenen maliyetler küçük ve geri alınabilir değişikliklerle giderilir.

**Tech Stack:** Kotlin, Jetpack Compose, coroutines/StateFlow, OkHttp, AndroidX Macrobenchmark 1.5.0, Perfetto, mevcut JVM/Compose/Android runtime testleri ve GitHub Actions.

**Spec:** Kullanıcının 25 Ekim 2026 talebi ve `docs/superpowers/plans/2026-10-21-nextdns-completion-without-speed-test.md` P07 hedefleri; ölçüm yöntemi `docs/performance/README.md`.

**Durum:** Kullanıcı tanı planını uygun buldu. Bu turda APK eşleşmesi, BrowserStack kapasitesi ve kaynak taraması doğrulandı; ürün kodu değiştirilmedi. Kök neden için gerçek cihaz performans ölçümü hâlâ gerekli.

## Kapsam ve korunacak davranış

- Kullanıcının belirttiği sürüm CI436; sorun genel arayüz gezinmesinde.
- API adresleri, Android üretim paket kimliği, hesap/profil izolasyonu ve mevcut mimari korunur.
- İkonları, haritayı, IP analizini veya başka bir özelliği kaldırarak performans sonucu üretilemez. İkon kalitesi ve favicon güvenlik sınırları korunur.
- Canlı günlüklerin sırası, kimliği, filtresi, imleci ve mevcut 1.000 kayıt tutma sınırı korunur. Bu sınır performans amacıyla azaltılmaz; olay düşürülmez.
- Eşitlemenin doğruluğu, görünürlük/lifecycle davranışı, hata bildirimi ve sunucudan değişiklik doğrulaması korunur.
- Beş dil, sistem dilini izleme, çalışma sırasında dil değişikliği ve retained ViewModel/worker doğruluğu korunur.
- Uygulama verisi silinmez; mevcut kurulum kaldırılmaz. İzole benchmark paketi sentetik verilerle çalıştırılır. Yeni APK tesliminde imza uyumluluğu ayrıca doğrulanır.
- Bağımlılık/Compose sürümü yükseltmesi, genel mimari refaktör, animasyon ölçeğini sistemden düşürme, ölçüm eşiğini gevşetme bu planın başlangıç müdahalesi değildir.
- Hukuki metinler DRAFT kalır; özel alan adları, IP'ler, API anahtarları ve hesap verileri performans raporlarına yazılmaz.

## Doğrulanmış başlangıç bulguları

1. Çalışma başlığı `6ceafe08cb21861131f86b7af64dfb0e398ee3f9`; PR #35 açık ve bu başlıktadır.
2. Teslim edilen `OpenSourceClientForNextDNS-CI436-review.apk`, `aapt dump badging` sonucunda `application-debuggable` bildiriyor. Eski `OpenSourceClientForNextDNS-1.0-review.apk` bunu bildirmiyor. Kullanıcı sorunu CI436'da doğruladı. Derleme farkı güçlü hipotezdir, tek başına kök neden kanıtı değildir.
3. `app/build.gradle.kts`: release için R8/minify kapalı; benchmark release'ten türetilmiş ve non-debuggable; debug için birim test coverage yapılandırması açık. Bu son ayarın APK'ya instrumentation eklediği ayrıca incelenmeden iddia edilmez.
4. Macrobenchmark; iki ısınma ve on ölçümlü açılışla Home, 200 satır günlük ve analiz/harita senaryolarını içeriyor. `docs/performance/README.md` fiziksel cihaz karşılaştırmasının henüz kaydedilmediğini açıkça söylüyor.
5. Sentetik günlük alanları `site-*.invalid`. Favicon üreticisi `.invalid` alanlarını dışlıyor; mevcut senaryo yeni favicon indirme/çözümleme yükünü kapsamıyor.
6. Favicon bitmap çözümlemesi IO dispatcher'da, harita geometrisi de IO'da yükleniyor. Bu işler kanıt olmadan ana thread'de çalışıyor diye sınıflandırılmaz.
7. ADB'nin izinli kontrolü başarılı fakat bağlı cihaz listesi boş. Windows CrossDevice dosya erişimi, profil çıkarılabilir ADB bağlantısı değildir.
8. Ek salt okunur karşılaştırmada eski review APK'sının SHA-256 değeri `103063ea0979bdb900142551b064e857fdad206308c47a55cf35856dbf8a90f3` çıktı; yerel `signed-review-evidence.json` bu dosyayı `e383e45d29e036b808c83ee3e23936bac32194ae` kaynak commit'ine ve CI payload eşdeğerliğine bağlıyor. Bu, mevcut doğrulanmış referanstır; kullanıcının en son akıcı bulduğu kurulumun bu paket mi yoksa PR35 benchmark paketi mi olduğu henüz kesin değildir.
9. Aynı karşılaştırmada eski review: 14.522.446 byte / 5 DEX / `baseline.prof` var; CI436 Debug: 22.110.479 byte / 12 DEX / `baseline.prof` yok. Paket boyutu, DEX sayısı veya profil varlığı tek başına yavaşlama kanıtı değildir; bulgular eşit derleme koşulu gereğini destekler. İki APK'nın DEX'lerinde kontrol edilen `$jacocoInit`, `$jacocoData` ve `Lorg/jacoco/` markerları bulunmadı; coverage kaynak ayarı üzerinden APK instrumentation maliyeti çıkarımı yapılmaz.
10. `e383e45..6ceafe0` diff'inde `AppStrings.kt`, `LocalePreferences.kt` ve `WorldMapChart.kt` değişmemiş. Bu noktalar önceden mevcut sıcak-yol adaylarıdır; yeni regresyona neden oldukları varsayılmaz. Son diff, günlük satırına gerçek favicon yüklemesini ve ikon cache/boyut politikasını ekliyor. Bu nedenle derleme türünden sonra yeni favicon yolunu ölçmek önceliklidir. 192px sınırı her gelen ikonun 192px olduğu anlamına gelmez; gerçek decoded boyutlar ölçülmeden bellek maliyeti tahminle sonuçlandırılmaz.
11. CI436 artefaktındaki `app-debug.apk` SHA-256'sı telefondaki `OpenSourceClientForNextDNS-CI436-review.apk` ile eşleşti (`502a5243e47bab9564424abfa86c81b96bf90ceab2b46a8656c13cfb9b02fcc7`). Aynı artefakttaki `app-debug-androidTest.apk` de paket kanıtında imzası doğrulanmış. Önceki `1.0-review.apk` ise non-debuggable ve `baseline.prof` içeriyor; bu nedenle mevcut kullanıcı karşılaştırması aynı çalışma türü değildir ve Debug APK'nın yavaşlığı kaynak regresyonunu tek başına kanıtlamaz.
12. Son kaynak farkında `AnalyticsScreen` alan adı satırlarına `FaviconImage` ekliyor; Logs ekranında da CI436 ile favicon bileşeni kullanılıyor. Yükleyici görüntü indirme ve decode işlerini IO dispatcher'a taşıyor; `load` öncesi alan adı/URL normalleştirmesi Compose çağrısından başlıyor, cache toplamı 4 MiB ve hedef dekode kenarı 192 px. Aynı alana ait eşzamanlı yüklemeleri birleştirme bulunmuyor. Bu, ölçülmesi gereken yeni iş yüküdür; henüz kanıtlanmış ana-thread bloklaması değildir.
13. `HomeScreen` sekme değişiminde mevcut `AnimatedContent` ile giriş/çıkış ekranlarını kısa süre birlikte çiziyor; animasyon süreleri 220/180 ms. Bu yol mevcut mimariyi koruyarak Analytics/Logs geçişinde frame-overrun ve GPU/CPU yüküyle eşleştirilecek. Tek başına animasyon kullanımı kusur sayılmaz.
14. BrowserStack App Automate hesabı tarayıcıda açık ve panel 100 ücretsiz dakika gösteriyor. Fakat mevcut Windows Process/User/Machine ortamında `BROWSERSTACK_USERNAME` ve `BROWSERSTACK_ACCESS_KEY` yok; `scripts/run_browserstack_espresso.py --preflight` **NOT RUN** döndü. Uygulama APK'sı ve test APK'sı yüklenmedi, build gönderilmedi, dakika harcanmadı. Dashboard oturumu API kimlik bilgilerini çalıştırıcı ortamına sağlamıyor.
15. BrowserStack Android Espresso App Performance V1 belgelerine göre CPU, bellek, batarya ve ağ ölçümleri Android koşularında varsayılan olarak sunuluyor. V2 FPS, ANR ve daha ayrıntılı UI çizim metrikleri `appProfiling=true` ile etkinleştiriliyor; resmi belge V2'yi Device Cloud Pro/Pro+Visual/Enterprise Pro planlarıyla sınırlandırıyor. Mevcut panel Free olarak göründüğünden V2 erişimi doğrulanmış değil. BStack koşusu sağlanırsa V1 kaynak/ ağ tanı verisi alınacak; kare jank kararı Macrobenchmark `frameOverrunMs`/frame count ve mümkünse Perfetto ile verilecek.

## Ölçülecek hipotezler

| Öncelik | Kaynak ve gözlem | Kanıtlayacak ölçüm |
|---|---|---|
| 1 | Debug APK ile non-debuggable review karşılaştırılıyor | Aynı kaynak revizyonunun Debug ve non-debuggable varyantlarında aynı gezinme senaryosu |
| 2 | Yeni günlük/favicon yolu; aynı anda aynı URL için istek birleştirme ve başarısız sonuç cache'i yok; URL normalleştirme cache kontrolünden önce her yükte çalışıyor | Tekrarlanan/benzersiz alanlarda istek sayısı, ana thread CPU, gerçek decoded boyutlar, bitmap allocation, cache hit/miss ve GC |
| 3 | Önceden de mevcut `AppStrings.get` her çağrıda `localizedContext` kuruyor; API 33+ dil çözümü platform LocaleManager çağrıları yapıyor | Ana thread binder/context allocation ve Compose trace maliyeti; yalnız anlamlı maliyet ölçülürse değişiklik |
| 4 | `emitLogEntry` ana thread'de duplicate taraması ve liste kopyası yapıyor; canlı akışta satırlar yer değiştirme animasyonu kullanıyor | 200/1.000 kayıtta kontrollü SSE yükü; kayıt sayısı/sırası ile frame-overrun/GC ilişkisinin izi |
| 5 | Harita draw döngüsünde polygon Path'leri ve seam kopyaları yeniden kuruluyor | Harita görünürken/ülke seçerken/scroll sırasında draw CPU ve allocation |
| 6 | Sekme/lifecycle/polling state güncellemeleri ile pahalı kompozisyonlar çakışabilir | Ana thread trace, istek sayısı, görünür/gizli sekme görev yaşam süresi ve yeniden kompozisyon sebepleri |

Bu tablo hata listesi veya uygulanacak toplu düzeltme listesi değildir. İzle doğrulanmayan hipotez için ürün kodu değiştirilmez.

## Task 1: Karşılaştırılabilir paketleri ve ölçüm koşullarını belirle

**Files:** `app/build.gradle.kts`; `docs/performance/README.md`; oluşturulacak `docs/performance/2026-10-25-ui-regression/run-metadata.json`.

- [ ] Eski review ve varsa önceki PR35 benchmark APK'sının SHA-256, paket, debuggable/profileable, imza ve kaynak commit bilgisini teslim kanıtlarından eşleştir. En son gerçekten akıcı kullanılan revizyon belirsizse bunu raporda açık bırak; otomatik olarak e383e45'i seçme.
- [ ] Aynı kaynak CI436 için Debug/non-debuggable farkını karşılaştır; eski/yeni kaynak karşılaştırmasını aynı non-debuggable derleme ve aynı benchmark harness ile yap.
- [ ] Başlangıçta mevcut release/minify ayarlarını sabit tut; R8 veya Baseline Profile değişikliğini farklı bir deney olarak değerlendirmek için ayır.
- [ ] Galaxy S20 FE veya üzerinde anlaşılan fiziksel referans cihaz için API seviyesi, yenileme hızı, çözünürlük/font ölçeği, dil/tema, güç/termal koşul ve compilation/profile durumunu kaydet.
- [ ] `adb devices -l` ile cihaz hazır olmasını doğrula. Ardından yalnız sentetik `.benchmark` paketinde ölçüm yap; üretim uygulamasının verisini temizleme.

**Çıkış:** Kaynak, paket türü ve cihaz koşulları belli iki karşılaştırma; kök neden henüz varsayılmıyor. Debug farkı sorunu tamamen açıklıyorsa kod optimizasyonu gereksinimi yeniden değerlendirilir.

## Task 2: Gerçek gezinmeyi ve yeni ikon yükünü ölçüme dahil et

**Files:** `macrobenchmark/src/main/java/com/example/macrobenchmark/AuthenticatedClientBenchmark.kt`, `ClientStartupBenchmark.kt`, `SyntheticNextDnsApi.kt`; gerekirse yalnız benchmark source set'indeki fixture; `scripts/tests/test_benchmark_fixture_isolation.py`; `docs/performance/2026-10-25-ui-regression/`.

- [ ] Mevcut senaryoları koruyarak genel sekme geçişleri, Hesabım menüsü aç/kapat, ayarlar kaydırma ve geri dönüş yolculuğunu ekle.
- [ ] Günlükler için 200 ve mevcut üst sınır olan 1.000 kayıt; tekrarlanan/benzersiz alanlar, kaydırma/geri kaydırma, filtre/arama, sayfalama ve kontrollü canlı ekleme senaryolarını çalıştır.
- [ ] Favicon cold/warm cache, yavaş/başarısız yanıt ve aynı alan için birden çok görünür satırı kapsa. Fixture için ağ override gerekirse yalnız benchmark varyantındaki yerel görsel sunucusunu kullan; üretim HTTPS/host/redirect/byte/dimension sınırlarını gevşetme ve release DEX/manifest izolasyon testini çalıştır.
- [ ] Analizler/IP, seri grafik ve harita kaydırma/ülke seçimi yolculuklarını koru; günlük ve ikon senaryosu kapsamını yalnız eski `.invalid` fallback yoluyla kapatma.
- [ ] Aynı harness ve compilation ayarıyla iki warmup, en az on ölçümlü tekrar kullan. Önce/sonra koşularını termal ve çalışma sırası etkisini görecek şekilde yinele.
- [ ] Startup TTID; `frameDurationCpuMs` ve `frameOverrunMs` P50/P95/P99; frame sayısı ve deadline aşma oranı; ana thread CPU/binder/disk/network; allocation/GC ve bellek; ikon istek/cache sayaçlarını kaydet.
- [ ] Ham JSON ve Perfetto izlerini build/device metadata ile sakla; istek URL'si ve kullanıcı verisi içeren tanı kayıtlarını rapora koyma.

**Commands:** `./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark`; cihaz hazır olduğunda `./gradlew :macrobenchmark:connectedCheck`.

**Çıkış:** Hangi fonksiyon/iş yükünün gecikmeye katkı yaptığına dair iz ve aynı koşullarda baseline. Harness derlenmesi veya normal CI yeşili performans kanıtı sayılmaz.

### BrowserStack gerçek cihaz koşusu

- [ ] Mevcut `scripts/run_browserstack_espresso.py` akışını kullan; uygulama/test APK hash ve imza kanıtı CI artefaktındaki `package-evidence.json` ile aynı olmalı. Cihaz adını BrowserStack hesabının gerçek Android kataloğundan seç; cihaz adını tahmin etme.
- [ ] Oturumda app profiling V1 ölçümlerini, video/logcat ve cihaz/OS metadata'sını sakla. Her testte aynı UI gezinme adımlarını kullan; Debug ve non-debug APK sonuçlarını aynı grafik üzerinde doğrudan eşdeğer kabul etme.
- [ ] Eğer hesap V2 yetkisi veriyorsa `appProfiling=true` ile FPS/slow-frame/ANR raporunu etkinleştir. Ücretsiz plan bunu göstermiyorsa, bunun yerine frame metriği varmış gibi raporlama; jank ölçümü için Macrobenchmark/Perfetto kullan.
- [ ] BrowserStack oturumu/performans logs olmadan BStack sonucu **NOT_RUN** kalır; web paneline giriş yapılmış olması gerçek cihaz testi kanıtı sayılmaz.

## Task 3: Yalnız doğrulanan pahalı işleri küçük değişikliklerle gider

**Conditional files:** `app/src/main/java/com/example/i18n/AppStrings.kt`, `LocalePreferences.kt`; `ui/screens/FaviconImage.kt`, `LogsScreen.kt`, `WorldMapChart.kt`, `HomeScreen.kt`, `AnalyticsScreen.kt`, `SetupScreen.kt`; `data/repository/NextDnsRepository.kt`; `ui/viewmodel/NextDnsViewModel.kt`.

- [ ] Trace'te en büyük maliyet için tek hipotez ve başarısızlığı gösteren davranış testi/ölçümünü yaz; birden çok optimizasyonu tek deneyde birleştirme.
- [ ] Dil context/binder maliyeti doğrulanırsa locale/config değişiminde doğru geçersizleşen çözüm kullan; worker/retained ViewModel eski dilde kalmasın. Mevcut `LocalePreferencesTest` ve `LocaleRenderingTest` kapsamını koru.
- [ ] Favicon maliyeti doğrulanırsa normalleştirme/anahtar işini sıcak yoldan çıkarma, aynı devam eden isteği paylaşma ve sınırlı başarısız cache seçeneklerinden ölçümün gerektirdiğini uygula. Bir abonenin iptali diğerini bozmasın; cache'ten atılan ama ekranda kullanılan bitmap recycle edilmesin. İstek ve cache üst sınırları açık kalsın.
- [ ] Canlı günlük maliyeti doğrulanırsa işlem/kompozisyon işini azaltırken session/filter generation, tekil kimlik, sıralama ve bütün olayların korunmasını test et. Akışı kapatma veya olay düşüren throttle uygulama.
- [ ] Harita maliyeti doğrulanırsa viewport/geometriye bağlı çizim kaynaklarını yeniden kullan; renk/ülke seçimi, resize, seam çizimi, hit-test ve erişilebilirlik aynı kalsın.
- [ ] Gereksiz sekme/polling/kompozisyon maliyeti doğrulanırsa sahiplik ve invalidation noktasını daralt; elle yenileme, tazelik ve hata geri bildirimi korunmalı. Mevcut güncelleme süresini ölçümden kaçmak için uzatma.
- [ ] Her küçük değişiklikten sonra aynı senaryoyu yeniden ölç; yararı koşular arası oynaklığı aşmıyorsa değişikliği geri al ve denemeyi kaydet.

**Çıkış:** Her kalan değişiklik için kök neden, fonksiyonel test ve sayısal önce/sonra kanıtı. Hangi dosyaların gerçekten değişeceği trace sonucuna bağlıdır.

## Task 4: İşlev kaybı ve tekrar performans gerilemesini engelle

**Files:** İlgili mevcut testler; `docs/release/quality-ledger.json`; `docs/performance/2026-10-25-ui-regression/report.md` ve ham ölçümler.

- [ ] İkon/HTTP iptali, başarısız yanıt, çoklu tüketici, günlük filter-generation/sayfalama/sıra, dil değişimi ve harita etkileşimini yalnız değişen davranışa uygun regresyonlarla doğrula.
- [ ] Tam mevcut PR CI kapılarını çalıştır: Lint; JVM/contract/Compose/screenshot; 60 rota/profil alanı matrisi; beş dil; DRAFT bütünlüğü; Debug/Release/benchmark derlemeleri; SDK/NOTICE/DEX/Snyk ve release Android runtime kontrolleri.
- [ ] Aynı non-debuggable referansa göre TTID medyanı ve P95 frame metrikleri %10'dan fazla gerilemesin; referans cihazdaki deadline aşan frame oranı mevcut plandaki %5 hedefini aşmasın. Refresh rate ve API destek durumunu raporla; sabit 16 ms'yi her cihaz için deadline sayma.
- [ ] Önce/sonra farkı ölçüm oynaklığını aşsın; uzun donmalar ve açıklanmamış ana thread disk/network ihlalleri kalmasın. Eşik aşılırsa sonucu başarısız/inceleme gerekli olarak yaz; hedefi değiştirme.
- [ ] Hata defterine belirti, etkilenen paket/commit, doğrulanmış kök neden, tutulan/geri alınan değişiklik ve yeniden ölçüm kanıtını ekle.
- [ ] CI emülatörü, fiziksel cihaz performansı ve BrowserStack sonuçlarını ayrı bildir. Şu anda bağlı cihaz olmadığı için fiziksel performans sonucu açık durumdadır.

## Task 5: İnceleme ve doğru APK teslimi

- [ ] Değişiklikleri ayrı performans dalı ve normal PR sürecinde gözden geçir; önceden açık PR35 içeriğini tekrarlama veya ilgisiz refaktörle büyütme.
- [ ] Kullanıcıya non-debuggable inceleme paketi hazırla; kaynak commit, APK SHA-256, manifest ve signer fingerprint doğrulamasını raporla. CI436'nın geçici Debug imzası ile eski inceleme imzasının farklı olduğunu teslimde hesaba kat; uygulamayı veya veriyi sessizce silme.
- [ ] Kullanıcının bildirdiği genel gezinme yolunu fiziksel cihazda tekrar doğrula. Son rapor; bulunan neden, önce/sonra ölçüm, işlevsel test sonucu, bilinen sınırlamalar ve APK yolunu birlikte içersin.

## Kaynak değerlendirmesi

- Google Android: [Compose performance](https://developer.android.com/develop/ui/compose/performance), [lists](https://developer.android.com/develop/ui/compose/lists), [best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices), [tracing](https://developer.android.com/develop/ui/compose/tooling/tracing), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics). Context7 ile incelendi. Android'in izleme kılavuzu önce düşük ek yüklü sistem/Compose izleriyle darboğaz hipotezi kurmayı, ağır method tracing'i ilk ölçüm olarak kullanmamayı destekliyor; `frameOverrunMs` kullanılabildiğinde tercih ediliyor.
- BrowserStack'in birincil kaynakları: [Espresso App Performance](https://www.browserstack.com/docs/app-automate/espresso/debug-failed-tests/app-performance), [performance trends](https://www.browserstack.com/docs/app-performance/performance-trends/automate-performance-trends/trends), [Espresso ilk koşu ve APK gereksinimleri](https://www.browserstack.com/docs/app-automate/espresso/getting-started). Ücretsiz hesapta V1 kaynak ölçümleri doğrulanabilir; V2 FPS kapsamı plan yetkisine bağlı.
- Akademik tarama: [GUIWatcher (2025)](https://arxiv.org/abs/2502.04202) janky frame, uzun yükleme ve donmuş görüntüyü ekran kaydı üzerinden ayırıyor; [Screencast-Based Analysis of User-Perceived GUI Responsiveness (2025)](https://arxiv.org/abs/2508.01337) sistem kaynak metriklerinin kullanıcıya görünen ilk tepki/son güncelleme süresiyle aynı şeyi ölçmediğini vurguluyor; [A Comparative Study of Android Performance Issues in Real-world Applications and Literature (2024)](https://arxiv.org/abs/2407.05090) ana-thread ağır iş, görüntü decode, yeniden çizim, thread bekleme ve cache/ağ örüntülerini hata taksonomisi olarak topluyor. Bunlar bu uygulamanın kök nedenini kanıtlamıyor; ölçüm ve gözlem katmanlarını belirliyor.
- Acumen çağrısı yapıldı; dönen genel Android/LLM/örnek APK büyüklüğü kayıtları bu uygulamanın takılmasına ilişkin uygun kanıt sağlamadı ve kök neden olarak kullanılmadı.
- Scite'ın iki uygun literatür sorgusu `INVALID_ARGUMENT` döndürdü. Bu araçtan doğrulanmış akademik bulgu alınamadı; makale veya bilimsel etki büyüklüğü uydurulmadı.
- Resmî kaynakların önerisi bu uygulamada neden bulunduğu veya iyileşme gerçekleştiği anlamına gelmez; karar uygulamaya ait iz ve ölçüme dayanır.
