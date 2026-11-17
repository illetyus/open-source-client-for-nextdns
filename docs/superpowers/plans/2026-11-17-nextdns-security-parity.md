# NextDNS Güvenlik Ekranı Uygulama Planı

> Yürütürken `superpowers:executing-plans` veya `superpowers:subagent-driven-development` kullan. Her görevi kendi testleriyle doğrula.

**Amaç**

Güvenlik ekranındaki anahtarları NextDNS’in güncel davranışıyla eşleştir ve her ayarın ne işe yaradığını beş dilde açıkla.

**Mimari**

Mevcut Retrofit, Moshi, repository ve Compose akışını kullan. Ayar değerinin kaynağı profilin Security API yanıtı, açıklamaların kaynağı NextDNS web arayüzünün yerelleştirme dosyalarıdır. Uygulama açıklamaları kendi dil kaynaklarından sunar.

**Kapsam**

Kullanıcının Güvenlik ekranı talebi ve 17 Kasım kaynak incelemesi. İnceleme APK’sına giden çalışma için `fc6ad94` tabanını ve mevcut taslak PR #37’yi Git’te doğrula. Önceden yapılmış ölçüm dosyalarını ve çalışma ağacındaki benchmark değişikliklerini değişikliğe katma.

## Kaynaklar ve kararlar

- NextDNS, `idnHomographs` ve `typosquatting` yazılarını yok sayıyor. İki koruma `aiThreatDetection` altında açıklanacak. Eski `dga` değeri de yazıldığında etkisiz. [Resmî API belgesi](https://nextdns.github.io/api/#profile)
- Güncel web kodu ayrı `algorithmicallyGeneratedDomains` ayarını aynı Security uç noktasından okuyor ve kaydediyor. `dnsDataExfiltration` ve `fastFluxNetworks` önizleme koşullarıyla gösteriliyor. Android istemcisi profil kimliğine dayanan web koşulunu kopyalamayacak. [NextDNS web kodu](https://my.nextdns.io/static/js/main.e91520be.js)
- Beş dilde, webdeki anlamı yansıtan özgün ve anlaşılır yerel açıklamalar kullanılacak. Şifre veya API anahtarı açıklama kaynağına gönderilmeyecek. [NextDNS Türkçe açıklamaları](https://my.nextdns.io/locales/tr/global.json) · [İngilizce açıklamaları](https://my.nextdns.io/locales/en/global.json)
- Tanınmayan sunucu alanlarının ham değerleri UI, günlük, telemetri ve PATCH dışında kalır. Ayrıştırma sınırları ve mevcut eşitleme özeti korunur. Doğrulanmış diğer ayarların anahtarları, işlevleri ve API adresleri korunur.

## Görev 1 — Güncel alanı bağla ve sunucu teyidini kesinleştir

**Dosyalar**

`NextDnsApiService.kt`, `SecurityDtoJsonAdapterFactory.kt`, `NextDnsModels.kt`, `MutationModels.kt`, `NextDnsRepository.kt`, `NextDnsViewModel.kt`, `ConfigSnapshotHasher.kt` ve ilgili test dosyaları. API kapsam tablosu ve doğrulama betiği aynı değişiklikte güncellenir.

- [ ] `NextDnsApiContractTest` ile `algorithmicallyGeneratedDomains` alanının `true`, `false` ve eksik durumlarını sabitle. Yeni anahtar değiştiğinde gövdenin yalnız ilgili Boolean alanını içerdiğini ve eski `dga` anahtarının gönderilmediğini doğrula.
- [ ] `RepositoryRegressionTest` ile kayıt sonrası GET aynı değeri döndürürse başarılı, alan eksik/null ya da farklıysa başarısız olmasını doğrula. Profil değişimi, yetki hatası ve önceki kayıtla aynı değerin önbellekte bulunması senaryolarını kapsa. Bu kurallar yeni alan, Fast Flux ve DNS veri sızdırma için geçerli.
- [ ] Testleri yeni davranış eklenmeden çalıştır. Beklenen eksikliği doğrula.
- [ ] Yeni Boolean alanını DTO, profil durumu, mutasyon ve eşitleme özetine bağla. Eski kayıtların çözümlenmesini koru. Eski üç alanı uygulamanın yazma yolundan çıkar.
- [ ] Bu üç güncel alanın kullanılabilirliğini seçili profilin başarılı Security GET yanıtındaki Boolean varlığına bağla. Açık ve kapalı değerler geçerli. Alan eksikse bir anahtar üretme. Profil değişince önceki profilin kullanılabilirlik bilgisini temizle. Yenileme başarısızken eski değer kayıt kanıtı olamaz.
- [ ] Mevcut PATCH sonrası GET akışını kullan. Kaydı yalnız o işlemin geçerli ve aynı profile ait GET yanıtındaki ham Boolean istenen değere eşitse başarılı say. Önbellek birleştirmesinden gelen değer bu kontrolü geçiremez. Ek bir ağ turu ekleme.
- [ ] API sözleşmesi, repository, kalıcılık ve snapshot testlerini çalıştır. `python scripts/verify_api_capability_matrix.py` ve `python -m unittest discover -s scripts/tests -q` ile belge ve denetimleri doğrula.

## Görev 2 — Güvenlik ekranını ve beş dildeki açıklamaları güncelle

**Dosyalar**

`SecurityScreen.kt`, `res/values*/strings.xml`, `AccountSettingsFlowTest.kt` ve `UiAuditTest.kt`.

- [ ] Compose testinde yeni alan sunucuda mevcutken anahtarın görünmesini ve doğru değeri göstermesini sabitle. Eksik alan, profil değişimi, eski üç kontrol ve “Ek sunucu alanları” kartının görünmemesini doğrula. Eski kartın ayrıntılarını zorunlu sayan UI testini yeni beklentiyle değiştir.
- [ ] Etkisiz IDN, yazım taklidi ve eski DGA anahtarlarını ekrandan çıkar. Yapay zekâ açıklamasına benzer karakterlerle ve yazım hatalarıyla yapılan taklitleri, ayrıca DGA tespitini ekle.
- [ ] Yeni anahtarı **Algoritmayla Üretilen Alan Adlarını Engelle** olarak göster. Açıklamada rastgele görünen harfler, kelime birleşimleri veya sayılarla üretilen şüpheli alan adlarının saldırgan altyapısını değiştirmek için kullanılabildiğini belirt.
- [ ] Fast Flux ve DNS veri sızdırma ayarlarını koru. Profilden destek görüldüğünde açıklama ve anahtarı göster. Fast Flux açıklamasında aynı alan adının sık değişen IP adreslerini ve DNS yanıtının geçerlilik süresini, DNS veri sızdırmada sorgulara gizlenen veri ve komutları açıkla. Güvenlik ekranındaki diğer sunucu seçeneklerinde ortak “Seçili profile uygulanır” metnini işlevi anlatan açıklamalarla değiştir.
- [ ] “Ek sunucu alanları” kartını, UI’ye özgü bileşenini ve kullanılmayan metinlerini kaldır. Güvenlik yanıtının tanınmayan alanlar için mevcut sınırlı ayrıştırmasını koru.
- [ ] Türkçe, İngilizce, Almanca, Fransızca ve İspanyolca kaynakları tamamla. UI testlerinde açıklamaların ve tıklama hedeflerinin doğru anahtara bağlı olduğunu doğrula.

## Görev 3 — Doğrulama ve APK teslimi

- [ ] `gradlew.bat :app:testDebugUnitTest :app:lintDebug` ve depo Python denetimlerini çalıştır. İlgili Compose ve API test sonuçlarını ayrı kontrol et.
- [ ] Değişiklikleri mevcut taslak PR dalına gönder. Yeni GitHub çalışmasında Lint, birim ve arayüz testleri, bağımlılık taraması ve emülatördeki yayın paketi adımlarını doğrula.
- [ ] Tamamlanan sürümden aynı ayrı kurulum kimliğini ve aynı inceleme imzasını kullanan APK’yı üret. Paket kimliğini, imzayı ve APK’nın test kodu içermediğini doğrula. Galaxy S20 FE Download klasörüne kopyala ve hedef dosyanın SHA-256 değerini yerel dosyayla karşılaştır.
- [ ] Teslimde doğrulanan işlevleri ve canlı hesapta doğrulanmamış davranışları açıkça belirt. Kullanıcının gerçek profilinde test amacıyla ayar değiştirmeden, kontrollü test verisiyle PATCH ve GET akışını doğrula.
