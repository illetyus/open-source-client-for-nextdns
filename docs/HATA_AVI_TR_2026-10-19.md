# Genel hata avı — 19 Ekim 2026

İncelenen sürüm: `4812323e588b216617994235011d630d3cd670b5`; telefona verilen `4812323` inceleme APK’sının kaynakları. Üretim kodu ve APK değiştirilmedi. Bulgular **düzeltilmiş değildir**; [taslak PR #25](https://github.com/illetyus/open-source-client-for-nextdns/pull/25) kanıt ve gelecekteki düzeltmeler içindir.

**Sonuç: 1 P1, 7 P2; toplam 8 açık kod kusuru.** Bunların 6’sı gerçek uygulama kodunu kullanan kontrollü JVM/Compose testleriyle, 2’si kaynak ifadeleri ve bağımsız JDK karşı örnekleriyle doğrulandı. Bu bir tam yayın/güvenlik kabulü değildir.

## Doğrulama

[Linux CI 39271056088](https://github.com/illetyus/open-source-client-for-nextdns/actions/runs/39271056088): **114 test; 107 başarılı, 7 başarısız, 0 hata, 0 atlama.** Önceki **105 testin tamamı başarılıdır**. Yeni 9 testin 7’si kusurları yeniden üretir; 2 kontrol testi geçer. CI bu nedenle kırmızıdır; başarılı diye sunulmaz ve PR bu halde birleştirilemez. Derleme/lisans/güvenlik hattının sonraki adımları bu kırmızı koşuda tamamlanmış sayılmaz.

Windows üzerinde önce kısa TEMP ile Java bootstrap, sonra yalnız test JVM’inde İngilizce dil ayarıyla Conscrypt yükleyici sorunu aşıldı. Yeni 9 test Linux ile aynı sonucu verdi. Önceki Windows başlangıç koşusundaki 66 native yükleme hatası uygulama kusuru sayılmadı; Q-008 ayrı ortam kaydıdır. Kalıcı sistem veya uygulama dil ayarı değiştirilmedi.

[Kalıcı JUnit/commit kaydı](release/bug-hunt-2026-10-19/results.json), [53 Kotlin kaynak envanteri ve tarama izleri](release/bug-hunt-2026-10-19/source-inventory.json), [hata defteri](HATA_DEFTERI_TR.md). Envanter/hash taraması ve odaklı veri akışı incelemesi; her kod yolunun cihazda çalıştırıldığı anlamına gelmez.

## Bulgular

| Kayıt | Öncelik | Bulgu | Kanıt |
|---|---|---|---|
| NDNS-012 | P1 | Bağlantı göstergesi gerçek profil parmak izini yapılandırma kimliğiyle karşılaştırıyor | Linux + Windows JVM |
| NDNS-013 | P2 | Kurulum ekranı altı haneli profil için geçersiz IPv6 adresi üretiyor | Kaynak + JDK karşı örneği |
| NDNS-014 | P2 | Başarısız izin/engel listesi eklemesi kullanıcının yazdığı alan adını siliyor | Linux + Windows JVM |
| NDNS-015 | P2 | Kısmi analiz hataları bütün görünümün taze başarı olarak sunulmasına neden oluyor | Linux + Windows JVM |
| NDNS-016 | P2 | Geç tamamlanan analiz isteği yeni filtre seçiminin verisini eziyor | Linux + Windows JVM |
| NDNS-017 | P2 | Boş sunucu günlük yanıtı eski kayıtları ekrandan kaldırmıyor | Linux + Windows JVM |
| NDNS-018 | P2 | Misafir seçimi sürmekte olan giriş isteğini geçersiz kılmıyor | Linux + Windows JVM |
| NDNS-019 | P2 | Alan adı doğrulayıcısı geçerli Punycode adlarını reddedip geçersiz etiketleri kabul ediyor | Kaynak + JDK karşı örneği |

### NDNS-012 — Bağlantı göstergesi gerçek profil parmak izini yapılandırma kimliğiyle karşılaştırıyor

**Tetikleyici:** Seçili profil: id=aaaaaa, fingerprint=fp0123456789abcdef. Tanılama: status=ok ve profile=fp0123456789abcdef.

**Doğrulanan davranış ve neden:** Tanılama profile alanı doğrudan DiagnosticTestResult.profileId içine alınır; ekran yalnız activeProfile.id değerini karşılaştırır. Eşleşen gerçekçi parmak iziyle sağlıklı seçili profil gösterilmez. Önceki testte profile alanına aaaaaa yazıldığı için kusur kaçmıştır.

**Kaynak:** [DiagnosticConnectionState.kt:7](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/model/DiagnosticConnectionState.kt#L7), [SetupScreen.kt:145](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/SetupScreen.kt#L145), [NextDnsRepository.kt:565](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L565).

**Düzeltme yönü:** Hizmet kullanımını ve seçili profil doğrulamasını ayrı modelle; doğrulanmış profil fingerprint bilgisini doğru biçimde eşleştir. Yalnız status=ok olduğu için her profili sağlıklı sayma.

**Kapanış ölçütü:** Aynı fingerprint sağlıklı; farklı fingerprint uyuşmazlık; eksik fingerprint belirsiz; hata ve eski ölçüm yeni başarı değildir. Beş dil ve gerçek cihazda tekrar gerekir.

**Kırmızı regresyon:** `identicalRealisticFingerprintMustShowConnectedForSelectedProfile`.

### NDNS-013 — Kurulum ekranı altı haneli profil için geçersiz IPv6 adresi üretiyor

**Tetikleyici:** Profil kimliği abcdef veya aaaaaa iken Kurulum > Uç noktalar > IPv6 alanını aç/kopyala.

**Doğrulanan davranış ve neden:** 2a07:a8c0::abcdef biçiminde altı hex haneli tek IPv6 grubu üretilir. JDK ayrıştırıcısı bunu reddeder; 2a07:a8c0::ab:cdef kontrolü geçer. Router fallback aynı kalıbı tekrarlar; uç noktalar özeti API IPv6 alanını kullanmaz.

**Kaynak:** [SetupScreen.kt:356](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/SetupScreen.kt#L356), [SetupScreen.kt:1217](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/SetupScreen.kt#L1217).

**Düzeltme yönü:** Öncelikle doğrulanmış setup.ipv6 değerlerini kullan; yedek üretim gerekiyorsa standart IPv6 grup sınırlarına uyan, sınanan tek bir dönüştürücü kullan.

**Kapanış ölçütü:** Altı haneli profil kimliklerinin ürettiği/kopyaladığı her adres geçerli olmalı ve hizmetin setup IPv6 değerleriyle eşleşmelidir.

### NDNS-014 — Başarısız izin/engel listesi eklemesi kullanıcının yazdığı alan adını siliyor

**Tetikleyici:** İzin veya engel listesine keep.synthetic.example yaz; Ekle; sunucu 500 döndürsün.

**Doğrulanan davranış ve neden:** Ekran fire-and-forget ViewModel çağrısından hemen sonra domainInput değerini boşaltır. İstek reddedilip liste değişmediğinde hata gösterilse de yeniden denenecek metin kaybolur. İki ekran testi bunu doğrular.

**Kaynak:** [AllowlistScreen.kt:100](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/AllowlistScreen.kt#L100), [DenylistScreen.kt:99](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/DenylistScreen.kt#L99).

**Düzeltme yönü:** Gönderimin tamamlanma sonucunu ekrana taşı; yalnız başarı doğrulandıktan sonra ilgili girdiyi temizle. Hata ve iptalde girdiyi koru; istek sırasında yeni yazılan metni eski yanıtla silme.

**Kapanış ölçütü:** Offline, 400/401/429/500, doğrulama hatası ve iptalde metin kalır; yalnız doğru girdinin başarılı sunucu onayı metni temizler.

**Kırmızı regresyon:** `failedAllowlistSubmissionMustPreserveTypedDomain`, `failedDenylistSubmissionMustPreserveTypedDomain`.

### NDNS-015 — Kısmi analiz hataları bütün görünümün taze başarı olarak sunulmasına neden oluyor

**Tetikleyici:** analytics/status 200 ve diğer 13 analiz isteği 500 döndürsün.

**Doğrulanan davranış ve neden:** Yalnız statusResp zorunlu kabul edilir. Diğer paneller eski/boş değerlerle birleştirilir; analyticsLastSuccessAt güncellenir, analyticsErrorMessage silinir ve fetchAnalytics true döner.

**Kaynak:** [NextDnsRepository.kt:2362](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L2362).

**Düzeltme yönü:** Panel bazında eksik/eski/hatalı durumu açıkça izle veya bütün snapshot başarı ölçütünü doğrula. Başarısız panelleri yeni ve tamamlanmış sonuç olarak etiketleme.

**Kapanış ölçütü:** Her alt uç nokta tek başına ve toplu hata verdiğinde görünümün kısmi/eski durumu anlaşılır olmalı; yeniden deneme ve başarı zamanı doğru kalmalıdır.

**Kırmızı regresyon:** `failedAnalyticsPanelsMustNotBeReportedAsFreshCompleteSuccess`.

### NDNS-016 — Geç tamamlanan analiz isteği yeni filtre seçiminin verisini eziyor

**Tetikleyici:** Aynı profilde önce -24h isteğini geciktir; sonra -7d/200 sorgu isteğini tamamlat; en son -24h/100 sorgu yanıtını bırak.

**Doğrulanan davranış ve neden:** Oturum/profil aynı kaldığı için iki yanıt da commit edilir. En yeni filtre seçildikten sonra sonuç 200 yerine 100 olur; analiz için istek nesli veya eski isteği iptal/eleme kontrolü yoktur.

**Kaynak:** [NextDnsViewModel.kt:579](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt#L579), [NextDnsRepository.kt:2252](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L2252), [NextDnsRepository.kt:2362](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L2362).

**Düzeltme yönü:** Profil, cihaz ve tarih aralığına bağlı analiz istek kimliği kullan; yalnız en güncel isteğin sonucunu uygula. Loading durumunu da aynı isteğe bağla.

**Kapanış ölçütü:** Geciktirilmiş eski cihaz/tarih yanıtları son seçimin sonuçlarını, hata bilgisini veya loading durumunu değiştirememelidir.

**Kırmızı regresyon:** `olderAnalyticsFilterResponseMustNotOverwriteNewSelection`.

### NDNS-017 — Boş sunucu günlük yanıtı eski kayıtları ekrandan kaldırmıyor

**Tetikleyici:** İlk günlük yenilemesi bir kayıt döndürsün; uzak kayıtlar temizlendikten sonraki yenileme data=[] döndürsün.

**Doğrulanan davranış ve neden:** refreshLogsFromApi yalnız fetchedLogs boş değilse logs durumunu günceller. Boş yetkili yanıt başarı sayılır fakat artık sunucuda bulunmayan geçmiş ekranda kalır.

**Kaynak:** [NextDnsRepository.kt:1855](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1855).

**Düzeltme yönü:** Yeni yetkili snapshot ile stream artımlı birleştirmeyi ayır; boş snapshot durumunu açıkça uygula ve gerekli stream/cursor durumunu uzlaştır.

**Kapanış ölçütü:** Uzak silme/saklama süresi sonrası boş yenileme eski kayıtları kaldırır; stream tekrarları ve yeni gerçek kayıtlar doğru işlenir.

**Kırmızı regresyon:** `emptyAuthoritativeLogRefreshMustRemoveDeletedRemoteHistory`.

### NDNS-018 — Misafir seçimi sürmekte olan giriş isteğini geçersiz kılmıyor

**Tetikleyici:** API anahtarıyla girişi başlat; profil yanıtı beklerken Misafir/Demo düğmesine bas; sonra başarılı profil yanıtını tamamlat.

**Doğrulanan davranış ve neden:** continueAsGuest yalnız isGuestMode=true yapar. authenticationJob sürer; geç gelen başarılı giriş isGuestMode=false yaparak daha yeni misafir tercihini ezer. Giriş sürerken misafir düğmesi aktiftir.

**Kaynak:** [NextDnsViewModel.kt:298](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt#L298), [LoginScreen.kt:243](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/LoginScreen.kt#L243).

**Düzeltme yönü:** Misafir geçişinde eski kimlik doğrulama girişimini iptal/geçersiz kıl; geç gelen sonucun kimlik durumunu değiştirmesine izin verme.

**Kapanış ölçütü:** Başarılı, hatalı ve gecikmiş eski giriş yanıtları misafir tercihini bozmamalı; gerçek hesap durumuna sessiz geçiş olmamalıdır.

**Kırmızı regresyon:** `choosingGuestMustSupersedeAnInFlightLogin`.

### NDNS-019 — Alan adı doğrulayıcısı geçerli Punycode adlarını reddedip geçersiz etiketleri kabul ediyor

**Tetikleyici:** İzin/engel listesinde xn--e1afmkfd.xn--p1ai, a..com, a-.com ve -a.com girdilerini dene.

**Doğrulanan davranış ve neden:** İki ekran aynı regex kullanır: üst seviye alan adını yalnız ASCII harfleriyle sınırlar; etiket sınırlarını denetlemez. JDK IDN/STD3 kontrolü geçerli Punycode örneğini kabul ederken UI regex reddeder; üç geçersiz örnekte tersi olur.

**Kaynak:** [AllowlistScreen.kt:100](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/AllowlistScreen.kt#L100), [DenylistScreen.kt:99](https://github.com/illetyus/open-source-client-for-nextdns/blob/4812323e588b216617994235011d630d3cd670b5/app/src/main/java/com/example/ui/screens/DenylistScreen.kt#L99).

**Düzeltme yönü:** Tek bir alan adı doğrulama/IDNA normalleştirme sınırı kullan; joker, etiket uzunluğu ve tire kurallarını açıkça sınayıp API hatalarını da koru.

**Kapanış ölçütü:** ASCII, geçerli Punycode/IDNA ve desteklenen jokerler kabul edilmeli; boş/uzun/başında-sonunda tire olan etiketler doğru hata vermelidir.

## Bağlantı şikâyetinin özel sonucu

Gerçek NextDNS tanılama servisinin bu bilgisayardaki yanıtında `status=ok`, `protocol=DOH` ve `profile` alanında `fp` ile başlayan 18 karakter gözlendi. Gerçek IP/parmak izi kaydedilmedi. API’den zaten taşınan `NextDnsProfile.fingerprint` ekranda kullanılmıyor. Önceki test gerçekçi parmak izi yerine altı haneli yapılandırma kimliği kullanmış; yeni test tam bu boşluğu ortaya çıkardı. Telefonun fiziksel DNS yolu bu oturumda ölçülmedi.

Seçili profil yokken algılanan NextDNS’in yanlışlıkla “Not configured” etiketi gösterdiği varsayımı ayrıca test edildi ve **yeniden üretilemedi**; bu kontrol geçti, ayrı hata açılmadı. Farklı parmak izinin sağlıklı sayılmaması kontrolü de geçti.

## Sonraki düzeltme sırası

Tekrar üretim için denetim dalında `./gradlew :app:testDebugUnitTest --tests 'com.example.audit.*'` çalıştırılır. Mevcut kusurlarla bu komutun sonucu başarısızdır; bir düzeltme sonrası aynı doğruluk ölçütlerinin geçmesi beklenir. Windows'ta kısa bir geçici klasör ve [yalnız test JVM dilini ayarlayan init dosyası](release/bug-hunt-2026-10-19/reproduction/windows-test-locale.init.gradle) kullanılmıştır.

Bağımsız adres/alan adı karşı örnekleri JDK 17 ile `java docs/release/bug-hunt-2026-10-19/reproduction/StaticCounterexamples.java docs/release/bug-hunt-2026-10-19/reproduction/domain-regex.txt` komutuyla çalışır. Arşivlenen regex incelenen sürümden çıkarılmıştır; güncel bir doğrulayıcının yerine geçmez.

1. NDNS-012: bağlantı/parmak izi modelini düzelt; aynı ve farklı profil kontrollerini birlikte koru.
2. NDNS-015/016/017: analiz snapshot/istek nesli ve günlük uzlaştırmasını düzelt.
3. NDNS-014/018: form sonucu ve misafir/kimlik doğrulama niyetini düzelt.
4. NDNS-013/019: kurulum adreslerini ve ortak alan adı doğrulamasını düzelt.
5. Doğru davranışı bekleyen bütün yeni testler yeşil olmadan hata kapatma veya ana dala birleştirme yapma; ardından ilgili cihaz kapanışlarını uygula.

Önceki NDNS-001–010 cihaz kapanışları, genel B01–B22, API 24/25/katlanabilir/TalkBack, gerçek hesap uçtan uca akışları ve yayın kapıları ayrı açık kalır. Hukuki metinler DRAFT; yayıncı/iletişim/onay bilgisi uydurulmaz.
