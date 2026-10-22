# NextDNS Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hız testini mevcut sürümden çıkararak bağımsız Android istemcisinin doğrulanabilir API işlevlerini, cihaz/performance kabulünü ve Faz 9 yayın hazırlığını tamamlamak; ardından kullanıcının son incelemesine aynı adayla bağlı rapor ve paket sunmak.

**Architecture:** Mevcut Kotlin/Compose, Retrofit/OkHttp, repository ve ViewModel sınırları korunur. Çalışma API/günlükler, analitik, cihaz/performance ve yayın alt projelerine ayrılır; her PR kendi çalışan davranışı, regresyonları ve CI kanıtıyla kapanır. Belgelenmiş API, gözlenmiş servis davranışı, yalnız kodda tanımlı rota ve uygulamaya özgü yerel işlev ayrı sınıflandırılır.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit, OkHttp, Moshi, coroutines, Robolectric/Compose, Android instrumentation, AndroidX Macrobenchmark/Perfetto, GitHub Actions, BrowserStack, mevcut imzalama ve yayın doğrulayıcıları.

**Spec:** Önceden onaylanmış docs/UX_LOGS_ICON_DESIGN_RESEARCH_DRAFT_2026-10-20.md; docs/superpowers/plans/2026-10-20-nextdns-feature-audit-and-implementation.md; docs/PHASE9_BROWSERSTACK_RELEASE_GATE.md; kullanıcının 21 Ekim 2026 tarihli “hepsini bitirmeyi planla; hız testi şimdilik iptal” kapsam kararı.

**Rapor tarihi:** 22 Ekim 2026, Europe/Istanbul.
**Yürütme durumu (22 Ekim 2026):** P00–P05 PR #31 ve P06 emülatör dilimi PR #32 normal PR/CI ile `main`'e alındı; güncel `main` commit'i `c3dc9183aaa8b787a2107ee792660c3aa1563d27`. PR #32 head `8635c1c28619ba11f82f343651555ac5c1d4a917` için CI run `39811062359` tüm 26 adımda başarılı; API 35 release emülatöründe 17/17 Android runtime testi geçti. P07 performans harness'i, P08 teknik düzeltmeleri ve 22 Ekim resmî kaynak notu `feat/p07-performance` dalında; yerel Python suite 24/24, Python derlemesi, legal DRAFT bütünlük denetimi, 60 rota/39 profil alanı matrisi ve `git diff --check` geçti. PR #33 head `d0a4b72c9efe519a05512b78d57e8005249753ba` için Android CI run `39815738190` kuyruğa alındı. P06'nın split izin koşuları, altı native test sınıfı ve 17/17 başlangıç/başarı kanıtı P07 doğrulayıcısında korunup regresyonla denetleniyor. P08 kaynak güvenlik taraması kısmi kapsamda tek düşük bulgu kaydetti. BrowserStack/gerçek cihaz, ölçülmüş performans, yayıncı/hukuk incelemesi ve Google Play kanıtları açık. Hız testi bu sürüm için ertelendi; bu belge yayın onayı değildir.
**Yöntem:** Mevcut izole worktree üzerinde native yürütme; her dilimde PR/CI ve değişiklik kapsamına uygun inceleme. Yeni toplu mimari dönüşüm yapılmayacak.

## Global Constraints

- Ürün adı Open Source Client for NextDNS; depo illetyus/open-source-client-for-nextdns olarak korunur.
- applicationId com.aistudio.nextdns.mgrqvt, Kotlin paket/sınıf kimlikleri ve doğrulanmış NextDNS protokolleri adlandırma gerekçesiyle değiştirilmeyecek.
- İlk sürüm ücretsiz, reklamsız ve uygulama içi satın almasız; Apache-2.0 korunur.
- TR/EN/DE/FR/ES arayüz ve Terms kapsamı korunur. Ayrı Privacy belgesi mevcut karara göre İngilizcedir; okuyucuda dili açıkça belirtilir.
- NextDNS bağlantı tanısı, dil, tema, bildirim ve yerel uygulama tercihleri profil API eşitlemesine dahil edilmez.
- Hız testi bu sürümde iptaldir: kullanıcı arayüzü, çalıştırılabilir istemci ve yeni sunucu istekleri kapsam dışıdır. Geçmiş Git/araştırma kayıtları korunur; gelecekte dönüşü ayrı kapsam kararı gerektirir.
- API anahtarı, linked-IP updateToken, gerçek DNS geçmişi ve imzalama sırları sohbete, CI çıktısına, ekran görüntüsüne veya rapora konmaz.
- Bilinmeyen API alanına varsayılan anlam/yazma yetkisi atanmaz. HTTP 2xx tek başına bir profil değişikliğinin uygulandığı kanıt sayılmaz.
- Mevcut bağımsız/gayriresmî konum korunur; NextDNS ortaklığı veya onayı iddia edilmez.
- Hukuki DRAFT işaretleri ancak doğrulanmış içerik ve gerçek son inceleme tamamlandığında kaldırılır. Yayıncı veya iletişim bilgisi üretilmez.
- Geçmiş cihaz sonuçları yeni adayın PASS kaydına taşınmaz. Paket commit'i, APK/AAB/test APK hash'i ve imza kimliği her kanıtta eşleşir.
- Yayın ve uzaktaki profil/IP mutasyonları yalnız uygun somut kapsam/onay ve doğrulanmış test hesabıyla yürütülür. Plan hazırlanması canlı mutasyon veya mağaza yayını değildir.

## Review Focus

1. Profil/filtre değişiminden sonra geç gelen log, SSE, seri veya linked-IP yanıtı yeni ekrana/hesaba yazılamamalı.
2. Tarih sınırı, saat dilimi ve yaz/kış saati değişimi eksik veya çift günlük üretmemeli; from dahil, to hariç olmalı.
3. linked-IP alanı ile mevcut istemci IP'si ayrılmalı; eksik servis verisi sabit IPv4 adresiyle veya 2xx ile başarılı gösterilmemeli.
4. Hız testi kaldırılırken yalnız kendi eski tercihleri temizlenmeli; API anahtarı, tema, dil, sözleşme kabulü ve diğer kalite kapıları korunmalı.
5. Sonraki belge/ikon/konfigürasyon değişikliği önceki adayın imza, hash, cihaz veya hukuk onayını sessizce geçerli kılmamalı.

## 1. Doğrulanmış başlangıç ve önceki rapora düzeltmeler

| Alan | 21 Ekim incelemesinde durum |
|---|---|
| Çalışma başı | be8111d37346cfba043166b2b440700958789683; kaynak ağacı temizdi ve bu onaylı plan dosyası eklenmemişti |
| PR | #27 açık ve taslak; main'e birleştirilmemiş; inceleme ve review-thread listeleri boş |
| CI | #358 / 39529639581 bu başta başarılı; bu planın henüz yürütülmüş sonucu değildir |
| Main tabanı | Son yerel origin/main c7db8d9d04d2022741ccc1564877940f45fe485a; uygulama başında yeniden fetch edilerek güncellenecek |
| Kod testleri | Son sayısal kod kanıtı CI #355: 228 test, 26 kusur regresyonu, 89 zorunlu vaka, 12 release-emülatör testi. Hız testi kaldırılıp yeni testler eklenince gerçek sayılar yeniden üretilecek |
| Kaynak envanteri | 61 Retrofit rotası / 39 profil yaprağı mevcut. Sayım, bütün rotaların resmî API'de belgeli veya canlı hesapta çalışır olduğunu kanıtlamaz |
| BrowserStack kabul defteri | docs/release/readiness.json: 13 PARTIAL, 9 NOT_RUN, 0 PASS. “22 NOT_RUN” diyen eski metinler güncel JSON ile uzlaştırılacak |
| Eski cihaz kanıtı | 10 Ekim hedefli 4 cihaz / 62 başarı / 2 OS koşullu atlama tarihsel kanıttır; yeni adayın B01–B22 kabulü değildir |
| Hız testi | AccountScreen, SpeedTestDialog, LibreSpeedClient ve SpeedTestSettingsStore içinde mevcut; iptal için ürün yüzeyinden kaldırılacak |
| linked-IP | SetupScreen → ViewModel.linkIpAddress → repository.linkCurrentIp akışı zaten var; “UI yok” ifadesi yanlıştı. Mevcut doğrudan çağrı link-ip.nextdns.io/profileId adresini kuruyor ve 2xx'i başarı kabul ediyor |
| Kurulum verisi | SetupScreen eksik IPv4 yanıtında 45.90.28.234 / 45.90.30.234 varsayılanına düşüyor; kaynakta görülen doğruluk riski. Canlı hesapta yeniden üretilmiş bulgu olarak sunulmuyor |
| API araştırması | Resmî API metninde ;series var; setup/linked-IP veya yaş doğrulama yazma sözleşmesi bulunamadı. Kodda deklarasyon bulunması yeterli kanıt değil [S1] |
| Android seviyesi | minSdk 24, targetSdk 36, compileSdk 36.1. 31 Ağustos 2026'dan geçerli normal yeni uygulama/güncelleme API 36 koşuluyla uyumlu; gönderim anında tekrar kontrol edilecek [S2] |
| Son yayın alanları | publisherReview, releaseCandidate, productionSigningApproval, licenseApproval ve Play kayıtları boş; belgeler DRAFT |

## 2. Yaklaşım ve çalışma sırası

Tek toplu PR, alan bazında PR ve yalnız yayın düzeltmelerine odaklanma seçenekleri değerlendirildi. Öneri alan bazında PR'lardır: kullanıcının istediği işlevler tamamlanır, her değişikliğin API/cihaz kanıtı ayrı incelenir ve geri alma sınırı açık olur.

| Sıra | İş paketi | Bağımlılık | Bitiş ölçütü |
|---|---|---|---|
| P00 | Kanıt ve kapsam kaydını uzlaştır | Yok | Tek başlangıç kaydı, tarihsel/yeni aday ayrımı, iptal kaydı |
| P01 | Hız testini mevcut sürümden kaldır | P00 | Ekranda/çalışma zamanı kodunda istemci yok; dar kapsamlı tercih temizliği ve regresyonlar yeşil |
| P02 | Kurulum, linked-IP ve API sözleşmesi doğruluğu | P00 | Yanıltıcı veri/başarı yok; yalnız kanıtlı servis eylemi; belirsiz özellik güvenli kapalı |
| M1 | PR #27'nin tamamlanması | P01–P02 kapsamındaki gerekli düzeltmeler | Yeni başta CI, inceleme ve etkilenen akış testleri; normal depo kurallarıyla main entegrasyonu |
| P03 | Günlük tarih/sıralama | M1; erişim bekleniyorsa açıkça bağımlı dal | UTC sınırları, cursor/SSE ve profil yarışları testli |
| P04 | Analitik zaman serileri | M1 | Belgeli 11 endpoint ailesi/12 görünüm yapılandırması; tek sorgu sözleşmesi ve erişilebilir grafik/tablo |
| P05 | UI, beş dil, ikon ve favicon incelemesi | P03–P04 | Kullanıcı akışları ve özgün görsel/provenans kaydı tamam |
| P06 | Cihaz erişimi ve B01–B22 otomasyonu | Hazırlık P00'dan başlayabilir; tam koşu RC adayında | Erişim canlı doğrulanmış; adayla bağlı App Live + Espresso kayıtları |
| P07 | Ölçülen performans | P03–P06 test altyapısı | Aynı fiziksel cihazda main/aday karşılaştırması, JSON/Perfetto ve recomposition raporu |
| P08 | Güvenlik, kalite ve teknik inceleme adayı | P01–P07 | Açık P0/P1 yok; tam CI; imzalı DRAFT inceleme paketi ve teknik rapor |
| P09 | Nihai hukuk/yayın girdileri | Hazırlık erken; sonlandırma teknik kullanıcı incelemesinden sonra | Gerçek bilgiler, içerik incelemesi, belge/lisans/Privacy URL onayı |
| P10 | Son aday ve Play test kanalları | P08–P09 | Aynı son adayda CI+B01–B22, internal/closed/prelaunch ve beyan kanıtları |
| P11 | Son kullanıcı kararı, yayın ve Faz 10 devri | P10 | Somut son aday onayı, doğrulanmış rollout kaydı ve bakım devri |

Takvim cihaz/hesap erişimi ve son incelemeye bağlıdır. Uygun yeni kişisel Play hesaplarında 12 testçinin kesintisiz en az 14 günlük closed test koşulu ayrıca gerçek takvim süresidir; tüm hesaplara otomatik uygulanmaz [S3]. Bu rapor kesin yayın tarihi vaat etmez.

## 3. Uygulanabilir görevler

Yol kuralı: data/ ve ui/ ile başlayan Kotlin yolları app/src/main/java/com/example/ altındadır. Mevcut test sınıfları aynı paketlerinin app/src/test/java/com/example/ karşılığındadır; androidTest yolları ayrıca yazılmıştır. Create yeni dosyayı, Modify mevcut dosyayı belirtir.

### P00 — Kapsam ve kanıtların tek kaydı

**Files:** docs/PROJECT_PHASE_STATUS.md; docs/PHASE9_PUBLICATION_PREPARATION.md; docs/PHASE9_BROWSERSTACK_RELEASE_GATE.md; docs/release/readiness.json; docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md.
**Interfaces:** Mevcut B01–B22 kimlikleri ve releaseCandidate alanları korunur; tarihsel kanıt aday kanıtından ayrılır.

- [x] GitHub main/PR başlarını yeniden oku; son CI ve çalışma ağacını kaydet.
- [x] Durumları JSON'dan say: başlangıç 13 PARTIAL / 9 NOT_RUN / 0 PASS. Eski “22 NOT_RUN” anlatımlarını tarihsel not olarak düzelt.
- [x] Mevcut 61 rotaya DOCUMENTED / OBSERVED / DECLARED_ONLY kanıt niteliği ve kaynak/fixture referansı ekle; sayımı destek garantisi gibi sunma.
- [x] Hız testi kapsamını CANCELLED_FOR_CURRENT_RELEASE olarak kaydet; eski araştırma ve koşu tarihini silme.
- [x] Çalıştır: python scripts/verify_api_capability_matrix.py ve python scripts/verify_publication_ready.py --draft. Gerçek rota/alan sayılarıyla PASS ve tutarlı şema beklenir.
- [x] Ayrı docs commit'i ve inceleme kaydı oluştur.

### P01 — Hız testinin kaldırılması

**Files:** Modify app/src/main/java/com/example/ui/screens/AccountScreen.kt, app/src/main/java/com/example/MainActivity.kt, beş values*/strings.xml; Remove ui/screens/SpeedTestDialog.kt, data/diagnostics/LibreSpeedClient.kt, data/diagnostics/SpeedTestSettingsStore.kt; Create data/local/RetiredFeaturePreferences.kt ve app/src/test/java/com/example/data/local/RetiredFeaturePreferencesTest.kt; update AccountSettingsFlowTest, eski hız testi testleri, scripts/verify_quality_regressions.py, scripts/tests/test_verify_quality_regressions.py ve docs/release/quality-ledger.json.
**Interfaces:** Yeni clearRetiredSpeedTestSettings(context: Context): Boolean suspend yardımcı işlevi yalnız speed_test_local_settings tercih dosyasını temizler; IO üzerinde çalışır, başarısızlık sonraki açılışta yeniden denenebilir.

- [x] Önce failing regression yaz: speedTestEntryIsAbsentFromAccountScreen; retiredSpeedSettingsAreClearedWithoutTouchingCredentialsOrTheme; retiredSpeedRegressionNeedsRemovalEvidence.
- [x] İlgili JVM/Compose ve Python testlerini çalıştır; kaldırılmamış ekran/tercihler için beklenen FAIL'i doğrula.
- [x] Kart, showSpeedTest durumu, dialog ve istemciyi kaldır; yalnız eski sunucu/policy/backend tercihlerini dar kapsamlı, idempotent IO temizliğiyle sil.
- [x] Yeni speed_test kaynaklarını ve mevcut kullanıcı belgelerindeki işlev iddialarını temizle. Gerçekte eklenmemiş bir LibreSpeed bağımlılığı/lisansı varmış gibi kaldırma işlemi yapma; çözülmüş release grafiğini kontrol et.
- [x] NDNS-061/062 tarihsel kayıtlarını koru; mevcut sürümden kaldırılma sebebi ve kaynak commit'ini kaydet. Yalnız bu kaldırılmış işlevin artık geçersiz regresyonlarını yokluk/temizleme testine bağla; genel “skip” veya kalite eşiği düşürme yolu ekleme.
- [x] DEX/manifest doğrulaması: eski LibreSpeedClient ve SpeedTestDialog yürütülebilir sınıfları yok; sentetik sunucu işareti yok; ayarlar veya giriş sırasında hız testi isteği 0.
- [x] Testler + localization + legal DRAFT + kalite defteri kontrolü geçsin; atomik commit ile mevcut PR #27'yi güncelle.

### P02 — Kurulum ve linked-IP doğruluğu; bilinmeyen API alanları

**Files:** data/api/NextDnsApiService.kt; data/repository/NextDnsRepository.kt; data/model/SetupEndpoints.kt; ui/viewmodel/NextDnsViewModel.kt; ui/screens/SetupScreen.kt; docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md; NextDnsApiContractTest.kt, CancellableCallTest.kt, RepositoryRegressionTest.kt, AccountSettingsFlowTest.kt, SetupEndpointsTest.kt.
**Interfaces:** Kamuya açık sözleşmesi doğrulanmayan kurulum/linked-IP istekleri ve mutasyonları kaldırılır. Ekran, doğrulanmış DNS uçlarını profil kimliğinden yerel üretir; bağlama durumu doğrulanamıyorsa NextDNS paneline yönlendirir.

- [x] Kapsamı güncel davranışa göre doğrula: `missingSetupIpv4IsUnavailable`, `linkedIpLabelUsesServiceBindingNotDiagnosticClientIp`, `undocumentedSetupAndLinkedIpRoutesAreNotDeclared`; `SetupEndpointsTest` eksik IPv4, IPv4 literal/tekrarlı değer ve altı karakterli profil kimliği sınırlarını kapsar. Belgelenmeyen mutasyon rotaları kaldırıldığı için `successfulHttpWithoutConfirmedBindingDoesNotReportLinked` ve `lateLinkedIpResponseCannotConfirmAnotherProfile` uygulanabilir testler değildir.
- [x] Mevcut SetupDto.linkedIp.ip/servers/ddns/updateToken alanlarının resmî belge veya redakte yetkili test yanıtı kanıtını ayır. updateToken sırdır; log/ekran/raporda gösterme.
- [x] Doğrudan link-ip adresini ve kullanılmayan POST setup/linkedIp deklarasyonunu ayrı denetle. Toplulukta görülen URL biçimi yazma sözleşmesi sayılmaz; test hesabı dışında canlı bağlama çağrısı yapma.
- [x] Sözleşme doğrulanırsa adresin exact HTTPS host/path'ini ve gerekli token'ını güvenli oluştur; redirect ile anahtar/token aktarımını engelle; aynı profilin güvenilir okumasıyla sonucu doğrula. Doğrulanmazsa bağlama eylemini açıklamalı kullanılamaz yap ve sahte başarı yolunu kapat; yalnız doğrulanmış resmî panel bağlantısı sunulabilir.
- [x] Eksik IPv4 yanıtındaki sabit .234 fallback'ini kaldır. Mevcut ağ IP'si ve hizmette kayıtlı linked-IP ayrı etiketlensin; NextDNS tanısı hesap/profil mutasyonunun kanıtı sayılmasın.
- [x] Yaş doğrulama ve erken erişim: kamuya açık sözleşme yoksa UNVERIFIED/read-only sınırıyla kapat; varsayımsal toggle/PATCH ekleme. Mevcut sınırlı bilinmeyen-alan güvenlik testlerini koru.
- [x] Güvenli hata yollarını doğrula: belgelenmeyen kurulum/linked-IP rotaları tanımlı değildir, credentialed istemci redirect izlemez, geç gelen/cancel edilmiş yanıtlar kapatılır ve kurulum adresleri yalnız doğrulanmış profil kimliğinden/literal IPv4 değerlerinden oluşur. Rota matrisi doğrulayıcısı 60 Retrofit rotasını sayar; kaldırılan mutasyon rotaları için durum kodu taklit eden ağ testi eklenmedi.
- [x] PR #27 düzeltmeleri CI #365 / run 39544925225 başarılı olduktan sonra `a7a1394483fbed0823fc278310073b0920f8ac81` ile main'e alındı; PR #29 test eklemesi CI #372 başarılı olduktan sonra `e7283df8a90436636b2818b08ed732f0808b7a77` ile birleştirildi. Yayın kabulü sonraki aday kapısında kalır.


**Durum:** Teknik/API doğruluğu ve P02 regresyonları tamamlandı; PR #27 ve PR #29 CI doğrulamaları geçti ve iki PR main'e birleştirildi. Resmî sözleşmesi olmayan setup/linked-IP yazma akışı kullanılmıyor.
### P03 — Günlüklerde tarih ve sıralama

**Files:** Create data/model/LogsHistoryFilter.kt ve app/src/test/java/com/example/data/model/LogsHistoryFilterTest.kt; Modify data/repository/NextDnsRepository.kt, ui/viewmodel/NextDnsViewModel.kt, ui/screens/LogsScreen.kt, beş dil kaynakları; mevcut RepositoryRegressionTest.kt, NextDnsApiContractTest.kt, UiAuditTest.kt testlerini genişlet.
**Interfaces:**
~~~kotlin
enum class LogsSort(val apiValue: String) { NEWEST_FIRST("desc"), OLDEST_FIRST("asc") }
data class LogsTimeWindow(val fromInclusive: Instant? = null, val toExclusive: Instant? = null)
data class LogsHistoryFilter(val window: LogsTimeWindow = LogsTimeWindow(), val sort: LogsSort = LogsSort.NEWEST_FIRST)
// Repository: suspend fun setLogsHistoryFilter(filter: LogsHistoryFilter): Boolean
// ViewModel: fun setLogsHistoryFilter(filter: LogsHistoryFilter)
~~~

- [x] Regresyonlar eklendi: `rangeUsesInclusiveFromExclusiveTo`, `localDateRangeSurvivesDst`, `historyFilterResetsCursorAndRejectsLatePage`, `ascendingHistoryDoesNotMixLiveSse`, `previouslyActiveLiveStreamResumesWhenReturningToNewestAvailableHistory`.
- [x] Son 24 saat, son 7 gün, kullanılabilir saklanan geçmiş ve özel tarih aralığı kontrolleri eklendi; varsayılan davranış mevcut saklanan geçmişi kullanır.
- [x] Özel bitiş günü kullanıcının ZoneId değerinde ertesi gün başlangıcına çevrilir ve UTC Instant gönderilir. Berlin'de 23/25 saatlik günler ile ters aralık ve isteksiz reddetme testleri eklendi.
- [x] refresh/loadMore profil, cihaz, durum, arama, raw, from/to/sort anlık görüntüsünü doğrular. Filtre değişikliği cursor/generation'ı sıfırlar ve eski sayfa yanıtları reddedilir.
- [x] Tarih aralığı/asc sıralamada canlı akış kapanır ve açıklama görünür; daha önce açıksa uyumlu varsayılan görünüme dönüşte yeniden açılır.
- [x] Var olan saklama sınırı/boş yanıt davranışı korundu; cihaz geçmişi filtrelemesinde ham API ID'leri ve `__UNIDENTIFIED__` korunur.
- [x] `/analytics/devices` `limit=500` ve cursor ile en çok 10 sayfaya kadar okunur. Tekrarlanan cursor ve sayfa sınırında son tam envanter korunur; bu yollar için regresyon eklendi.
- [x] Hedefli Android testleri ve tam CI, lint/localization; PR #28 incelemesi ve main entegrasyonu tamamlandı.

**Durum:** PR #28, CI #371 / run 39551411091 başarılı olduktan sonra 1ed99979b66de99c49270e96e6e97ac81deea7d7 ile main'e birleştirildi. Android Lint, JVM/Compose testleri, Debug ve unsigned Release derlemeleri ile güvenlik kapıları geçti.

### P04 — Analitik zaman serileri

**Files:** Create data/model/AnalyticsSeries.kt, data/api/AnalyticsSeriesDto.kt, ui/screens/AnalyticsSeriesChart.kt; Modify ApiMeta ve NextDnsApiService, data/repository/NextDnsRepository.kt, NextDnsViewModel.kt, AnalyticsScreen.kt; Create app/src/test/java/com/example/data/model/AnalyticsSeriesTest.kt, app/src/test/java/com/example/data/api/AnalyticsSeriesContractTest.kt, app/src/test/java/com/example/audit/AnalyticsSeriesRepositoryTest.kt ve app/src/test/java/com/example/ui/screens/AnalyticsSeriesChartTest.kt.
**Interfaces:** Repository suspend fetchAnalyticsSeries(query: AnalyticsSeriesQuery): Boolean; ViewModel StateFlow<AnalyticsSeriesState>. Metric enum'u yalnız aşağıdaki 12 görünüm yapılandırmasını kabul eder; COUNTRIES/GAFAM aynı destinations yolunun type parametresini belirler.
~~~kotlin
data class AnalyticsSeriesQuery(
  val profileId: String, val deviceId: String?,
  val fromInclusive: Instant, val toExclusive: Instant,
  val metric: SeriesMetric, val intervalSeconds: Long,
  val domainStatus: String? = null, val root: Boolean = false
)
data class SeriesPoint(val at: Instant, val queries: Long?)
data class SeriesRow(val key: String, val label: String, val points: List<SeriesPoint>)
data class AnalyticsSeriesState(
  val query: AnalyticsSeriesQuery?, val rows: List<SeriesRow>,
  val intervalSeconds: Long?, val loading: Boolean,
  val errorMessage: String?, val fetchedAt: Instant?
)
~~~
ApiMeta'ya nullable series: ApiSeriesMeta eklenir; ApiSeriesMeta times: List<String> ve interval: Long taşır. Mevcut pagination/stream alanları korunur. Retrofit GET profiles/{profileId}/analytics/{endpoint};series kullanır; endpoint ve type yalnız enum eşlemesinden gelir, kullanıcıdan serbest path alınmaz.

- [x] Contract/fixture testleri: `officialQueryTypesSeriesPreservesTimesAndCounts`, `missingBucketIsNotZero`, `mismatchedSeriesLengthsAreRejected`, `negativeAndOverflowCountsAreRejected`, `obsoleteProfileSeriesCannotOverwriteCurrentState`, `groupedDomainSeriesUsesTheRootDomainForKeyAndLabel` ve Retrofit route/parameter doğrulaması.
- [x] Belgeli metric allowlist uygulanır: status, devices, domains, reasons, destinations, dnssec, encryption, protocols, ips, queryTypes, ipVersions. Destinations countries ve gafam ayrı görünüm tipleridir; model testi listelenmemiş endpointi reddeder.
- [x] Belgeli entity alanları metric bazında eşlenir; `queries[]`, `meta.series.times` ISO zamanları ve saniye cinsinden `interval` kullanılır. Fixture contract testleri alan eşlemelerini doğrular [S1].
- [x] 24 saat için `interval=3600`, 7/30/90 gün için `interval=86400`; `from` dahil ve `to` hariç aralık, `alignment=end`, `timezone=GMT`, `partials=none` ve gerçek `meta.times` kullanılır.
- [x] Yalnız seçili seri çağrılır; profil değişince eski sonuç reddedilir. Sorgu 500 zaman kovası / 50 entity sınırlarıyla doğrulanır; eksik veya kesilmiş sayfa tam veri gibi sunulmaz.
- [x] Erişilebilir çizgi grafik ve okunabilir tablo sunulur; null veri boşluğu ile gerçek 0 ayrıdır, etiketler renk dışında da bilgi taşır; chart/table regression testleri mevcuttur.
- [ ] Büyük font, dark tema, saat dilimi etiketi ve TalkBack gerçek erişilebilirlik incelemesi doğrulansın; P05/P06 UI ve cihaz kapılarında tamamlanacak.
- [x] Ülke haritası ve altı parçalı GAFAM grafiği regresyon testleriyle korunur; özet ve seçili seri ayrı sorgulanır.
- [x] Metric parser ve ağ yolu contract testleri tam PR CI'da geçti; PR #30, run `39557875934` başarılı olduktan sonra `83c1a585946c1fce2e6b7e8269d2c63b0b5fe17f` main commit'ine alındı. Main run `39559334680` da lint, Compose/JVM testleri, debug/release derleme ve lisans/güvenlik kapılarını başarıyla tamamladı.

**P04 durumu:** API/zaman serisi uygulaması main'de. Büyük font/dark/TalkBack ve timezone sunumunun UI/cihaz kanıtı P05/P06 altında ayrıca açık tutuluyor.

### P05 — Son UI, dil, ikon ve favicon kalite dilimi

**Files:** İlgili Home/Account/Setup/Settings/LegalWelcome/Logs/Analytics ekranları ve testleri; beş dil kaynakları; ic_launcher_* varlıkları; scripts/generate_launcher_assets.py; docs/BRAND_ASSET_PROVENANCE.md; docs/brand/launcher-icon-provenance.md; Privacy taslağı.

**Kabul tablosu — kullanıcı tarafından bildirilen sorunlar:**

| Bildirilen sorun | Uygulama ve regresyon kanıtı | Durum |
|---|---|---|
| Tanı düğmesi çalışmıyor veya yanlış profili bağlı gösteriyor | `accountDiagnosticButtonRunsANewDiagnosticRequest`, `accountDiagnosticDoesNotTreatAnUnmatchedProfileAsConnected`, `DiagnosticAndDeviceMenuTest.diagnosticBannerRequiresSuccessfulMeasurementOfSelectedProfile` | Mevcut korumalar main'de; yeni tıklama/yerleşim testi P05 CI `39741292906` ile geçti |
| Eşitleme yerel bağlantı tercihlerini servis ayarı sanıyor | `fullRefreshReadsNextDnsStateWithoutPostingLocalPreferences`; yalnız sunucu GET'leri doğrulanıyor | Main'de, P04 CI geçti |
| Bazı cihazların günlükleri ve eski kayıtlar görünmüyor | `analyticsDevicePaginationIncludesEveryFilterableDevice`, cursor tekrar/sayfa sınırı testleri, `LogsHistoryFilterTest` | P03 main'de; geçmiş NextDNS saklama/cursor sınırıyla kısıtlı |
| İzin/engel listesi simgeleri gereğinden büyük | `allowAndDenyListFaviconsUseCompactSize`, `localTrackingProtectionIconUsesTheSmallerSize` | Main'de; 12 dp |
| Trafik varış haritası yalnız metin gösteriyor | `tappingAColoredCountryShowsItsNameAndTrafficShare`, erişilebilir ülke seçici testi | Main'de etkileşimli yerel harita |
| Hesap bölümü ve misafir/hukuk bağlantıları dağınık | `accountSectionsPutConnectionAndProfilesBeforeMetricsAndSync`, `connectionAreaOpensFromNavigationWithoutExtraTopBarButton`, `loginDoesNotScatterLegalLinksOutsideTheGroupedWelcomeFlow` | P05 hesap/nav testleri ve main'deki misafir/legal testi geçti |
| Ayarlar sırası karışık | `settingsExposeServiceLegalAppPreferenceAndDeleteGroupsOnce` | Servis → uygulama tercihleri → hukuki belgeler → servis profil silme sırası main'de testli |

- [x] Kullanıcı şikâyetleri tek kabul tablosuna bağlandı; her satır test veya açıkça belirtilen CI/cihaz kapısına bağlı.
- [x] Settings ekranında servis ayarları önce, uygulama tercihleri ve hukuki belgeler sonra, servis profili silme en sonda yer alıyor; `settingsExposeServiceLegalAppPreferenceAndDeleteGroupsOnce` sıralamayı, `NextDnsPreferencesClearTest.signOutRemovesCredentialAndCachedProfileAtomically` yerel temizliği ve `RepositoryRegressionTest.deletingSelectedProfileVerifiesRemovalBeforeSelectingReplacement` ve `deletingLastProfileVerifiesEmptyListAndClearsSelection` servis tarafındaki profil silmeyi ayrıca doğrular.
- [ ] Beş dilde profesyonel/anadili incelemesi; %200 font ölçeği, TalkBack, tablet/yatay ve dark tema akışları doğrulansın. Statik kaynak/l10n CI kapısı insan incelemesinin yerine geçmez.
- [x] Beyaz alanda merkezî minimal özgün launcher simgesi; adaptive/monochrome/legacy ve mağaza çıktıları tek üreticiden güncellendi. Gerçek OEM maskesi ve bağımsız marka izlenimi incelemesi P06 cihaz kapısında bekliyor.
- [x] Favicon sabit HTTPS host, hostname doğrulama, API anahtarı eklememe, yönlendirme kapatma, yanıt/süre sınırı, bellek önbelleği ve yerel fallback ile best-effort kalıyor. `FaviconImage` günlük ekranında kullanılmıyor; günlük domainleri ikon hostuna gönderilmiyor. Privacy DRAFT ve araştırma metni bu kapsamla güncellendi.
- [x] Özgün ikon ve offline Natural Earth haritası için kaynak/lisans notları mevcut; yeni ikon harici görsel/font/bağımlılık eklemiyor. Çözülmüş dependency/NOTICE paketi main CI'da doğrulanmış.
- [x] PR #31 son kaynak başlığında taze Android Compose/JVM testleri, screenshot, lint, localization, lisans, paket ve security CI geçti; beş eksenli kaynak incelemesinde birleştirmeyi engelleyen bulgu yok.
- [x] PR #31 kanıt güncellemesi ve normal PR akışıyla main entegrasyonu tamamlandı; main CI `39746374794` başarılı.

**P05 durumu (22 Ekim):** Hesap tanı eylemi/sekmesi ve kart sırası, minimal launcher varlığı ve güncel ikon/Privacy DRAFT provenance PR #31 ile main'e alındı. Kaynak başlığı `39fe9b3ed11847dcc4df46b550d22976dffd6213` CI `39741292906` ile; PR kanıt dokümantasyonu ve merge commit `361deef2a218c9625240a9e0cdaa7e436aa57f49` main CI `39746374794` ile geçti. Main koşusunda Lint, API/lokalizasyon/legal-DRAFT kontrolleri, 253 JVM/Compose testi (0 başarısız/0 atlanan), 12 ekran görüntüsü/627 coverage sınıfı, Debug ve unsigned Release paketleri, Macrobenchmark derlemesi, SDK/NOTICE/DEX, Codecov, Snyk ve release-emülatör güvenlik kontrolleri başarılı. İlk PR CI denemesi Gradle/AAPT2 yapılandırma-önbelleği geri yükleme yarışında durmuş; aynı kaynakta yeniden çalıştırma ve sonraki main koşusu geçti. Codecov PR notu %89,33 patch coverage gösterdi; `AccountScreen.kt` içindeki 8 değişmiş satırın 3'ü eksik, 5'i kısmi kapsandı ve P08'de davranış temelli incelenecek. Beş dilde insan incelemesi, gerçek TalkBack/erişilebilirlik akışı ve OEM ikon maskesi cihaz kontrolü açık kalır.

### P06 — Cihaz erişimi ve B01–B22 kabulü

**Files:** scripts/run_browserstack_espresso.py; app/src/androidTest/java/com/example/ mevcut testler ve yeni LogsHistory/Series/Setup testleri; docs/PHASE9_BROWSERSTACK_RELEASE_GATE.md; docs/release/readiness.json; adaya özgü yeni docs/release/<candidate>/ kanıt klasörü.

- [x] Önce altyapıyı doğrula: yerel ADB versiyon/daemon/cihaz listesi ve `--preflight` kontrol edildi. Bu oturumda BrowserStack kimlik bilgileri bu çalışma ortamında yok; ADB `1.0.41` daemon'u başlatılamadı. Hassas değerler okunmadı/çıktılanmadı; canlı koşu yapılmadı.
- [ ] Ayrı sentetik NextDNS test hesabı/profili ve tekrar kurulabilir fixture hazırla. Hesap/IP mutasyonları yalnız bu profilde; gerçek kullanıcının sorgu geçmişi veya router ayarı değiştirilmez.
- [ ] Eksik B02/B03/B04/B06/B08/B10/B16/B21/B22 akışlarını mevcut native test sınıflarıyla karşılaştır; gerçekten eksik olanları ekle, tarihsel PARTIAL'ı otomatik PASS yapma.
- [x] CI release audit'ında native testleri yalnız derlemekle kalma: API 35 emulatoründe TLS API fixture, login/navigation, notification permission, legal viewport, release security ve StrictMode sınıflarının 17 testini çalıştır; atlama/hata kabul etme. PR #32 CI run `39811062359`, head `8635c1c28619ba11f82f343651555ac5c1d4a917` tüm 26 iş adımını başarıyla tamamladı; rapor 17 test, 0 başarısız, 0 atlanan gösteriyor.
- [ ] Test gününde gerçek katalogdan minSdk hattı, orta OS, Android 13+, güncel Android 15/16, tablet ve mümkün foldable seç. API 24 uyumluluğu gerçek cihaz/emülatör kanıtında açık ayrılır; stok yokluğu başarı sayılmaz.
- [ ] Çalıştır: python scripts/run_browserstack_espresso.py --preflight; ardından --app <exact-ci-apk> --test-suite <exact-test-apk> --devices-file <verified-devices.json> --package-evidence <ci-package-evidence.json>.
- [ ] SUBMITTED yalnız gönderimdir. Sonuç, cihaz/OS, test adı, crash/ANR, maskelenmiş log, ekran/video ve hash eşleşmesini incele; hata → defter → düzeltme → yeni adayda ilgili tekrar testi.
- [ ] Yeni evidence alanlarını scriptin beklediği commit/apkSha256/url/device/androidVersion nesnelerine dönüştür; eski dosya-yolu kanıtlarını ayrı tarihsel alanda tut. B14 gerçek üretim/inceleme imzalı APK kanıtı içerir.
- [ ] Teknik DRAFT adayında mümkün vakaları çalıştır; son hukuk/varlık değişikliklerinden sonra final adayda B01–B22'nin tamamını yeniden sonlandır. Kaynak/erişim eksikliği WAITING/BLOCKED kaydıdır, PASS değildir.

**P06 ön hazırlık ve güncel durum (22 Ekim):** `scripts/run_browserstack_espresso.py --preflight` bu yerel ortamda `BROWSERSTACK_USERNAME` ve `BROWSERSTACK_ACCESS_KEY` değişkenleri bulunmadığından `NOT RUN` verdi; gizli değerler okunmadı/çıktılanmadı. Yerel ADB 1.0.41 daemon'u başlatılamadı. GitHub artefakt ZIP'ini yerel indirme isteği tarayıcı güvenlik kapısından geçmedi; dosya BrowserStack'e gönderilmedi. PR #32'nin önceki denemesinde bildirim testinin tam etkin-bildirim-sayısı varsayımı başarısız oldu; düzeltme testin yalnız beklenen iki uygulama bildirim kimliğini doğrulamasını sağlıyor. PR head `8635c1c28619ba11f82f343651555ac5c1d4a917` için CI run `39811062359` tüm 26 adımda geçti; 17 Android runtime testi 0 başarısızlık/0 atlama ile tamamlandı. PR #32, merge commit `c3dc9183aaa8b787a2107ee792660c3aa1563d27` ile `main`'e alındı. BrowserStack koşusu ve B01–B22 PASS kaydı yok.

### P07 — Fiziksel cihazda performans ve akıcılık

**Files:** macrobenchmark/ClientStartupBenchmark.kt; Create macrobenchmark/.../AuthenticatedClientBenchmark.kt; benchmark-only fixture Activity/bootstrap ve manifest/source set; app/build.gradle.kts; docs/performance/<candidate>/; mevcut WorldMapStrictModeTest.kt.
**Interfaces:** Fixture yalnız benchmark varyantındadır. Release paketi fixture Activity, değiştirilebilir API base URL, test sertifikası veya fixture anahtarı taşımaz.

- [ ] Önce release DEX/manifest testiyle fixture kaçışını yasakla; benchmark-only senaryolar gerçek Home/Logs/Analytics Compose yollarını sentetik, sabit veriyle açsın.
- [ ] Aynı ölçüm harness'ini hem mevcut main tabanına hem adaya uygula; ölçüm yaması ve uygulama commit'i ayrı kaydedilsin. Guest başlangıcı ile authenticated ekran sonucu birbirine kıyaslanmasın. Önce/sonra kıyası yalnız iki sürümde de bulunan akışlarda yapılır; yeni zaman serisi ekranına başlangıç sürümünde varmış gibi baseline üretilmez, adayın kendi bütçe/iz sonucu kaydedilir.
- [ ] Seçilen fiziksel cihazın model/Android build/yenileme hızı, locale/tema, güç/termal koşullarını kaydet; her senaryo için 2 hazırlık ve 10 ölçülen tekrar kullan.
- [ ] Cold startup, ana ekran, 200 satırlı sayfalı log gezinmesi, seri grafik ve ülke haritasını ölç. Yerel fixture UI performansıdır; gerçek servis ağ gecikmesi ayrıca raporlanır.
- [ ] TTID ve frameDurationCpuMs/frameOverrunMs P50/P95/P99; deadline aşan frame oranı; ana thread disk/network StrictMode; Perfetto Compose recomposition olayları kaydedilsin [S4]. Recomposition sayısı tek başına hız kanıtı sayılmaz.
- [ ] Önerilen iç mühendislik hedefi: aynı cihazda TTID medyanı ve P95 frame süreleri başlangıca göre %10'dan fazla kötüleşmesin; referans cihazda jank oranı %5'i aşmasın; test edilen uygulama yollarında açıklanmamış main-thread disk/network ihlali 0. Hedef aşıldığında neden/iyileştirme/tekrar ölçümü kaydet, hedefi sessizce düşürme.
- [ ] Karşılaştırmalı JSON/Perfetto ve kısa analiz raporunu sakla; cloud/emülatör sayısını fiziksel telefon ölçümü olarak sunma. Fiziksel cihaz yoksa bu iş açık kalır.

**P07 uygulama durumu (22 Ekim):** İzole benchmark varyantında sabit sentetik TLS/API fixture ile gerçek Home/Logs/Analytics Compose yollarını açan Macrobenchmark senaryoları ve release DEX izolasyon regresyonları eklendi. P06'nın 17 Android testlik, split izin koşulu ve altı native sınıf kapsamı korunarak doğrulayıcı/regresyon düzeltildi. `python -m unittest discover -s scripts/tests -q`: 24/24 PASS; `verify_legal_assets.py` DRAFT bütünlüğü ve 60 rota/39 profil alanı API matrisi PASS; Python derleme ve `git diff --check` PASS. Güncel PR #33 başı `d0a4b72c9efe519a05512b78d57e8005249753ba`, Android CI run `39815738190` queued. Fiziksel cihaz performans ölçümü yapılmadı; performans sonucu ileri sürülmüyor.

### P08 — Teknik kapanış, güvenlik ve inceleme paketi

**Files:** .github/workflows/ci.yml mevcut kapıları; kalite/hata defteri; release kanıt araçları; adaya özgü teknik rapor.

- [ ] Değişen kodu doğruluk, concurrency, veri kaybı, hata/rollback, güvenlik, erişilebilirlik ve sürdürülebilirlik açısından incele. P05 Codecov PR notundaki %89,33 patch coverage ve `AccountScreen.kt` 8 satır (3 missing, 5 partial) bulgusunu davranış bağlamında değerlendir; kritik iş mantığına anlamlı test ekle, yalnız oranı yükseltmek için uygulamayı aynalayan test yazma.
- [ ] Tam koşu: Android Lint; JVM/contract/Compose/screenshot; API envanteri; localization/legal-draft; Debug/unsigned Release/benchmark; SBOM/NOTICE/DEX/SDK; Snyk High; release-emülatör güvenlik ve StrictMode.
- [ ] İptal edilen özellik test sayısını değiştirir; gerçek yeni toplamları raporla. Eski 228/89 sayılarını sabit başarı hedefi gibi zorla tutma; mevcut güvenlik testlerini atlama.
- [ ] Açık P0/P1 sıfır; kalan P2'ler kullanıcı etkisi ve kapanış kararıyla kayıtlı olsun. Kişisel sır veya test-only kod release paketine sızmasın.
- [ ] Tamamlanan PR'ları kendi güncel CI başlarında main'e entegre et; main CI'ı da doğrula. İmzalı DRAFT inceleme APK'sını mevcut inceleme signer'ıyla, SHA/commit/versionCode kaydıyla hazırla.
- [ ] Kullanıcıya tek teknik rapor + telefonda indirilebilir, metadata/hash'i doğrulanmış inceleme paketi sun; henüz public sürüm olarak etiketleme.

**P08 güncel kanıt (22 Ekim):** Kaynak güvenlik taraması scan `1ddefb49-2eaf-4d31-9bb4-cef2557720b3` tamamlandı; 360 dosyalı depoda güvenlik açısından kritik yüzeyler incelendi, kapsam `partial` olarak işaretlendi ve düşük önem düzeyinde tek raporlanabilir bulgu kaldı: API anahtarının kopyalanmasından sonra 30 saniyelik pano temizliği uygulama sürecinin çalışmasına bağlıdır. Güvenlik modeli bu süreyi best-effort olarak açıklar; cihaz/OEM davranışı çalıştırılmadı. Yerel Python suite 24/24, `py_compile`, hukuki DRAFT bütünlük, API matrisi ve `git diff --check` geçti. Sonraki sıra P07 CI/main entegrasyonu, davranış temelli panoya kopyalama değerlendirmesi ve imzalı inceleme APK'sı üretimidir; APK henüz hazır değildir.

### P09 — Son hukuk, kimlik ve yayın varlıkları

**Files:** docs/legal/ mevcut araştırma/Terms/Privacy; app/src/main/assets/legal/ ve registry.json; fastlane/metadata/android/; docs/release/readiness.json.

- [ ] Önce son paketin veri akışını çıkar: NextDNS API/tanı, favicon hostu, yerel anahtar/ayar/kabul, export/clipboard/backup ve artık çıkarılmış hız testi. SDK/izin/SBOM ile karşılaştır.
- [ ] KVKK/GDPR ve Play araştırma dosyalarını son veri akışına göre uzman/yayıncı incelemesine hazırla; uygulama geliştiricisi ile NextDNS hizmet işleticisinin rolü somut olgularla ayrılsın.
- [ ] Son aşamada doğrulanmış yayıncı adı, destek/gizlilik iletişimi, herkese açık HTTPS Privacy adresi ve inceleme sonucu alınsın. Bilinmeyen alanlar placeholder/DRAFT kalır; kendiliğinden isim/adres/onay üretilmez.
- [ ] Hesap oluşturma/yönlendirme akışlarını Play account-deletion politikası açısından değerlendir [S5–S6]. Yerel veriyi temizlemeyi NextDNS hesabını silmek diye sunma; hayalî /account DELETE isteği ekleme. Özel üçüncü taraf istemci durumu belgede açık çözülmüyorsa bu belirsizlik incelemeye taşınır.
- [ ] Terms beş dilde anlam eşdeğerliği, Privacy İngilizce dil etiketi, ikon/lisans kaynakları ve mağaza açıklamaları son kullanıcı akışıyla tutarlı olsun.
- [ ] Gerçek içerik onayından sonra belge revision/hash ve gerektiğinde yeniden kabul akışını güncelle; o aşamaya kadar DRAFT korunur. Sınırsız sorumsuzluk/uygunluk garantisi üretilmez.
- [ ] python scripts/verify_legal_assets.py --release ve bash scripts/verify_legal_release_ready.sh geçsin; yapısal script başarısını uzman içerik onayı yerine koyma.

**P09 kaynak güncellemesi (22 Ekim):** Mevcut KVKK/GDPR/Google Play araştırma taslaklarına resmî kaynak yeniden kontrolü eklendi. Google Data safety yardım sayfasının tüm Play-published app'ler, üçüncü taraf SDK/veri akışı ve sınırlı kullanıcı-başlatımlı paylaşım istisnası ayrımı; hesap silme rehberinin uygulama içi hesap oluşturma koşulunu belirtmesi ve özel üçüncü taraf istemciye ilişkin yorum alanını açık bırakması; KVKK Kurum sayfasının açıklayıcı metin niteliği ve EDPB rehberindeki Bölüm V ölçütleri kaynak ve sınırlarıyla not edildi. Kaynaklar [Google Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [Google account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en), [KVKK yurt dışına aktarım](https://www.kvkk.gov.tr/Icerik/2053/Yurtdisina-Aktarim) ve [EDPB 05/2021](https://www.edpb.europa.eu/system/files/2023-02/edpb_guidelines_05-2021_interplay_between_the_application_of_art3-chapter_v_of_the_gdpr_v2_en_0.pdf). Hukuki taslakların DRAFT işareti, yayıncı adı ve iletişim alanları aynen korunuyor; nihai uzman incelemesi yapılmış sayılmıyor.

### P10 — Son aday, B01–B22 ve Google Play testleri

**Files:** docs/release/readiness.json; scripts/sign_review_windows.ps1; record_package_evidence.py; verify_signed_review.py; verify_publication_ready.py; fastlane mağaza metin/ekran varlıkları.

- [ ] Play Console hesap türü/oluşturulma tarihi, mevcut applicationId kaydı, en son versionCode ve gerçek imza sahipliğini doğrula. targetSdk=36 şu anki koşulu karşılıyor [S2]; yeni sürüm numarası gerçek Play kaydından yüksek seçilir.
- [ ] Doğrulanmış son kaynak/asset/legal commit'ini dondur; üretim/imzalı APK+AAB, instrumentation APK+test APK için ayrı hash ve signer kanıtı üret. Sonradan paket değişirse etkilenen kanıtı yeniden al.
- [ ] Final adayda CI, App Live ve Espresso B01–B22 kayıtlarını tamamla; raporlar aynı commit/hash'e bağlansın. Re-sign varsa üretim imzalı paket eşdeğerliği B14'te ayrıca doğrulansın.
- [ ] Privacy URL'si aktif, herkese açık, coğrafi engeli olmayan HTML sayfası olsun; uygulama ve mağaza linki aynı son içeriğe gitsin. Data safety son paket/SDK trafiğinden hazırlanır [S5].
- [ ] İç test, kapalı test ve prelaunch raporunu çalıştır/incele. Hesap koşuluna bağlı 12 testçi / 14 gün kuralı uygulanıyorsa gerçek opt-in kayıtları tamamlanmadan üretim erişimi başarı sayılmaz [S3].
- [ ] Beş mağaza dili, ücretsiz/reklamsız seçimleri, içerik derecelendirmesi/hedef kitle, app access inceleme yönergeleri ve TR/AB dağıtım seçimini somut pakete göre doldur. Data safety formunun hazırlanması, gönderilmesi ve gerçek platform inceleme sonucu ayrı kaydedilir; varsayımsal Google onayı yazılmaz. İnceleme hesabı sırları herkese açık belgeye eklenmez.
- [ ] Crash/ANR, izin, görüntü, büyük font, oturum/mutasyon ve veri güvenliği bulguları kapatılsın; son readiness doğrulayıcısı için gerekli bütün alanlar gerçek sonuçlarla dolsun.

### P11 — Son rapor, yayın kararı ve Faz 10

**Files:** docs/PROJECT_PHASE_STATUS.md; docs/PHASE9_RELEASE_EVIDENCE.md; docs/release/readiness.json; docs/PHASE10_MAINTENANCE_RUNBOOK.md.

- [ ] Kullanıcıya son raporu ver: kapanan işler, desteklenmeyen API kararları, kalan bilinen sınırlamalar, test cihazları/başarıları, performans önce–sonra, lisans/hukuk kayıtları, APK/AAB SHA ve signer, Play test sonuçları.
- [ ] Son yayın kararı tam adayın commit/hash'i üzerinden alınsın; publisherReview ve APPROVED yalnız gerçek karar sonrası kaydedilsin.
- [ ] python scripts/verify_publication_ready.py parametresiz PASS versin; scriptin upload/rollout yapmadığı ayrıca korunsun.
- [ ] Onaylanan aday için kademeli Play rollout ve gerçek mağaza durumunu doğrula; sorun halinde rollout durdurma ve yeni yüksek versionCode ile düzeltme yolunu hazır tut.
- [ ] Faz 9'u ancak kabul ve yayın kararı kanıtıyla kapat; Faz 10 API/dependency/sürüm/geri çekme bakım devrini etkinleştir. Otomatik izleme veya SLA kurulmuş gibi raporlama.

## 4. PR ve geri alma sınırları

| PR dilimi | Kapsam | Geri alma / uyumluluk |
|---|---|---|
| Mevcut #27 kapanışı | Hız testi temizliği, gerekli mevcut API/kurulum doğruluğu, kaynak matrisi ve kanıt uyumu | Paket/anahtar/tema/dil/kabul korunur; yalnız speed-test yerel tercihleri kaldırılır |
| logs-history-filters | Tarih/sıralama ve cursor/SSE kuralları | Varsayılan mevcut görünüm; yeni parametreler seçilmedikçe davranış korunur |
| analytics-time-series | Belgeli read-only seriler ve erişilebilir gösterim | Özet harita/GAFAM korunur; auth/profil yazma yüzeyi büyümez |
| release-ui-brand-review | Dil, düzen ve özgün ikon sonlandırması | API/paket kimliği ve servis verisi değişmez |
| device-performance-evidence | Benchmark-only fixture, gerçek cihaz testleri, adaya bağlı ölçüm | Release DEX/manifest fixture dışlama kapısı zorunlu |
| publication-finalization | Onaylanmış hukuk/metadata/imza ve Play kabul kaydı | Maddi belge değişikliğinde revizyon/yeniden kabul; veri/anahtar göçü ayrıca test edilir |

Her PR'da ilgili kırmızı test → en küçük düzeltme → hedefli yeşil test → tam gerekli CI → inceleme → entegrasyon akışı kullanılır. Ürün davranışı değiştirmeyen belge işi için uygulamayı aynalayan test eklenmez.

## 5. Dış girdiler ve eksik erişimde davranış

| Girdi | Ne zaman kullanılır | Eksikse |
|---|---|---|
| BrowserStack App Automate/App Live yetkisi | P06 | Bağımsız geliştirme sürer; yeni cihaz PASS kaydı verilmez |
| Uygun fiziksel Android cihazı | P07 | Harness hazırlanır; fiziksel baseline/aday ölçümü açık kalır |
| Ayrı sentetik NextDNS test hesabı/profili | P02/P06 | Mock sözleşme testleri yapılır; canlı mutasyon/uygulandı iddiası yok |
| Yayıncı/iletişim/Privacy URL ve uzman içerik incelemesi | P09 | Teknik DRAFT aday sunulabilir; nihai belgeler ve public yayın kapanmaz |
| Play Console hesap ve imza bilgisi | P10 | Mağaza paketleri hazırlanır; dış platform işlemi/üretim erişimi sonucu uydurulmaz |
| Son somut adayın kullanıcı kararı | P11 | Rapor/paket incelemeye hazır tutulur; APPROVED veya yayın kaydı yazılmaz |

Bu girdiler son teknik raporda toplu gösterilecek; sırlar sohbette talep edilmeyecek.

## 6. Tamamlanma tanımı

- Hız testi mevcut sürümden çıkarılmış; çalışma zamanı isteği ve kullanıcıya açık düğmesi yok.
- Günlük tarih/sıralama ve analitik seriler belgeli sözleşmeyle çalışıyor; yanlış/eksik veri ve profil yarışları güvenli ele alınıyor.
- linked-IP/erken erişim/yaş doğrulama için destek kararı kanıta bağlı; doğrulanmayan yazma eylemi kullanıcıya başarılı gösterilmiyor.
- Kritik UI, beş dil, özgün ikon ve lisans/mahremiyet incelemesi tamam; yanlış NextDNS ortaklığı izlenimi giderilmiş.
- Güncel main CI ve güvenlik kapıları yeşil; açık P0/P1 yok; gerçek cihaz/performance sayıları adaya bağlı.
- Son aday B01–B22, hukuk/lisans/imza ve Play test kanıtlarıyla aynı commit/hash'te; yayın kullanıcı kararı ve gerçek platform sonucuyla kaydedilmiş.
- Önceki Faz 2–8 kodu yeniden geliştirilmemiş; Faz 10 devri hazır. Bir alt PR veya yalnız derleme başarısı Faz 9'un bütünü olarak raporlanmamış.

## 7. Kaynaklar ve araştırma sınırları

- [S1 — Resmî NextDNS API](https://nextdns.github.io/api/): günlük sınırları, cursor, cihaz kimliği ve tüm analitik aileleri için ;series/interval/alignment/timezone/partials. Tam metinde setup/linked-IP veya yaş doğrulama API sözleşmesi bulunamadı; bu, özel bir API'nin var olmadığının kanıtı değildir.
- [S2 — Google Play target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en): 31 Ağustos 2026'dan normal yeni uygulama/güncelleme için API 36; kaynakta platform istisnaları ve uzatma koşulları ayrıca vardır. Proje targetSdk=36.
- [S3 — Yeni kişisel hesapların test koşulları](https://support.google.com/googleplay/android-developer/answer/14151465): 13 Kasım 2023 sonrasında oluşturulmuş kişisel hesaplar için 12 sürekli opt-in testçi / en az 14 gün. Hesabın buna tabi olduğu henüz doğrulanmadı.
- [S4 — Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [metrikler](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics), [Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing): mevcut araştırma/planın resmî ölçüm referansları; bu rapor yeni fiziksel ölçüm sonucu sunmaz.
- [S5 — Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en): gerçek uygulama/SDK veri akışı, Privacy içeriği ve kamusal URL koşulları.
- [S6 — Account deletion açıklaması](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en): üçüncü taraf API-key istemcisinin özel durumu mevcut taramada kesin çözümlenmedi; otomatik muafiyet veya NextDNS hesabını silme yetkisi çıkarılmadı.
- [S7 — BrowserStack Espresso](https://www.browserstack.com/docs/app-automate/espresso/getting-started): mevcut proje kabul yönteminin resmî referansı. Güncel ürün erişimi/stok/test sonucu ayrıca doğrulanacak.
- [S8 — Başarılı CI #358](https://github.com/illetyus/open-source-client-for-nextdns/actions/runs/39529639581), [PR #27](https://github.com/illetyus/open-source-client-for-nextdns/pull/27).
- Yerel esas kayıtlar: docs/release/readiness.json; docs/PHASE9_BROWSERSTACK_RELEASE_GATE.md; docs/PROJECT_PHASE_STATUS.md; docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md; mevcut hukuk ve lisans araştırma dosyaları.
- Firecrawl ile resmî API tam metni ve Play kaynakları okundu; linked-IP araması yalnız topluluk tartışmaları verdi ve yazma sözleşmesi kanıtı sayılmadı.
- Acumen by Talarion çağrısı bu tur zaman aşımına uğradı; kullanılabilir araştırma sonucu alınamadı. Bu araçtan doğrulanmış kaynak gelmiş gibi çıkarım yapılmadı.
- Superpowers using-superpowers, brainstorming ve writing-plans akışı kullanıldı. Bu rapor mevcut onaylı kapsamı alt projeler/PR'lar halinde tamamlamanın yolunu tanımlar; ürün değişiklikleri raporun incelenmesinden sonraki yürütme aşamasına aittir.

## 8. Plan öz denetimi

| Önceki kalan iş | Plandaki karşılığı |
|---|---|
| Güncel PR/CI/main entegrasyonu | P00–P02, M1, P08 |
| Hız testinin iptali | P01 |
| Günlük tarih/sıralama | P03 |
| Analitik zaman serileri | P04 |
| linked-IP / yaş doğrulama / erken erişim | P02; kanıtsız yazma güvenli kapalı |
| Beş dil, düzen, ikon/favicons | P05 |
| BrowserStack B01–B22 | P06, final adayda P10 |
| Aynı cihazda önce–sonra performans | P07 |
| Güvenlik/kod kalitesi/inceleme paketi | P08 |
| Hukuk/kimlik/Privacy/lisans | P09 |
| İmzalı son paket ve Play testleri | P10 |
| Son rapor/yayın/bakım devri | P11 |

Beş Review Focus koşulunun test sahipleri sırasıyla P02–P04, P03, P02, P01 ve P06/P08/P10'dur. Kaldırılmış hız testi ile korunan regresyonlar ayrılmış; bilinmeyen API yazmaları ve dış girdiler için gerçek tamamlanma/erişim durumları açıkça belirtilmiştir.
