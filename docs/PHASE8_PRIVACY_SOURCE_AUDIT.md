# Faz 8 — Kaynak düzeyinde veri akışı ve gizlilik denetimi

**Tarih:** 2026-10-08. **Durum:** Kaynak kodu ile kontrol edildi; **cihaz trafiği veya APK reverse-engineering analizi yapılmadı**.

## Kullanıcı kararı (Faz 8 soru 5)

Mimari: kullanıcı cihazı **doğrudan NextDNS API** ile çalışacak; geliştiriciye ait aracı sunucuda API anahtarı, log veya kullanıcı istatistiği toplanmayacak. Bu, *tasarım gereksinimidir*. Gerçek sürümde ağ gözlemi yapılmadan “hiçbir üçüncü taraf aktarımı yoktur” iddiası kullanılmaz.

## Kod ve somut kanıtlar

| İncelenen davranış | Dosya ve kaynak seviyesi gözlemi | Güvenlik / sonraki kontrol |
|---|---|---|
| API erişimi | `app/src/main/java/com/example/data/api/NextDnsApiService.kt` içinde `BASE_URL=https://api.nextdns.io/`, `TEST_URL=https://test.nextdns.io/` | NextDNS'e kimlik doğrulamalı HTTPS çağrıları |
| Bağlı IP / bağlantı testi | Aynı dosyada `https://link-ip.nextdns.io/`, `*.test.nextdns.io` uçları | NextDNS adresleri; ağ trafiğinde profil ID ve kaynak IP NextDNS tarafından görülebilir |
| API anahtarı | `NextDnsPreferences.kt` ve `ApiKeyProtector.kt`: Android Keystore + AES/GCM; tercih dosyası `nextdns_secure_prefs.xml` | Legacy plaintext key'ler migrate ediliyor; gerçek cihaz restorasyon testi bekliyor |
| Yedeklerden hariç tutma | `app/src/main/res/xml/backup_rules.xml` ve `data_extraction_rules.xml`: `nextdns_secure_prefs.xml` hem cloud backup hem transfer için hariç tutuluyor | Pozitif kaynak kontrol; Android/OEM restore deneyleri bekliyor |
| Yerel profil/ayar cache'i | `NextDnsPreferences.kt` içinde `saved_profiles_json`, security/privacy/parental/allowlist/denylist/settings cache; tümü `nextdns_secure_prefs` içinde | Profil bilgileri cihazda tutulur; encrypted at-rest garantisi yalnız API anahtarı içindir. Backup kuralı tüm tercih dosyasını hariç tutuyor |
| Bildirim tercihi ve hash | `NotificationPreferences.kt` — `notification_settings` adlı DataStore, yapılandırma özeti hash, profil ID'den türetilmiş anahtar, zaman damgaları | Bu DataStore güvenli prefs dışındadır, Android backup/transfer kapsamında ayrıca test edilmeli; profil açık kimliği ile hassas veri ayrımı değerlendirilmeli |
| Tema tercihi | `ThemePreferences.kt` — `settings` DataStore | Hassas bilgi beklenmiyor |
| İsteğe bağlı arka plan sorgusu | `NotificationWorkScheduler.kt` `anyEnabled` false ise işleri iptal ediyor; WorkManager `NetworkType.CONNECTED` ile çalışıyor | Bildirim onayı, ret, oturum kapanışı, profil değişimi test edilecek |
| Logs SSE | `NextDnsRepository.kt` `/logs/stream` ile kullanıcının API anahtarı ve NextDNS endpoint'i | Arka plan iptali, profil ayrımı ve loglarda PII test edilecek |
| Logs indirme | `getLogsDownloadLink(key,pid,redirect=0)` NextDNS API'den kamuya açık indirme URL'si alıyor; ayrı **API anahtarı eklemeyen** `publicDownloadClient` ile indiriyor. Faz 8'de `.followSslRedirects(false)` eklendi; HTTPS→HTTP downgrade izlenmez | **Önemli:** Gerçek dosya indirme alan adı NextDNS'in döndürdüğü URL'ye bağlı; `api.nextdns.io` olmayan sunucu olabilir. Web/SDK analitiği gibi yorumlanmamalı ama gizlilik beyanına dahil edilmeli. HTTP downgrade davranışının gerçek cihazda testi yine gereklidir |
| Üçüncü taraf URL'ler | `AccountScreen.kt` ve `SetupScreen.kt` NextDNS web paneli/kurulum sayfalarına `Intent.ACTION_VIEW` açabiliyor | Browser açma ile uygulamanın kendi telemetrisi ayrı değerlendirilir |
| Üretim HTTP logları | `NextDnsApiService.kt` `HttpLoggingInterceptor` `BuildConfig.DEBUG ? BASIC : NONE`, `X-Api-Key` header redacted | Hata metinleri, URL ve id'lerde hassas veri var mı gerçek loglarla değerlendirilecek |
| Reklam/analitik SDK'ları | Doğrudan Gradle dependency listesinde Firebase Analytics, AdMob, Billing, Sentry görünmüyor | **Sadece doğrudan kaynak düzeyi kanıt:** script ile releaseRuntimeClasspath taraması ve gerçek APK/network kontrolleri gerekli |

API belgelerinin log-download davranışı: https://nextdns.github.io/api/ — `GET /profiles/:profile/logs/download?redirect=0` kamuya açık indirme URL'si döndürür.

## Netleştirilmiş doğru pazarlama/gizlilik dili

**Kaynak düzeyindeki mevcut tasarım için uygun ifade:**
> Uygulama NextDNS hesabınıza kendi API anahtarınızla doğrudan bağlanır. API anahtarınızı ve DNS günlüklerinizi geliştirici tarafından işletilen bir sunucuda toplamayı planlamıyoruz. NextDNS sunucularına veri iletilir; günlük dışa aktarma sırasında NextDNS'in sağladığı güvenli indirme bağlantısı kullanılabilir.

**Ölçüm olmadan kullanılamayacak ifadeler:**
- “Tüm bağlantılar yalnızca api.nextdns.io'ya gider.”
- “Uygulama hiçbir veri saklamaz.” (yerel cache vardır.)
- “Hiçbir kullanıcı bilgisi üçüncü taraflarla paylaşılmaz.” (NextDNS, API sağlayıcısıdır.)
- “Telefon değiştirdiğinizde bütün yerel ayarlar yedekten geri gelir.” (güvenli prefs hariç tutulur.)
- “%100 güvenli, sıfır izleme.” (görülmemiş SDK/gerçek ağ trafik ve APK kanıtı yok.)

## Faz 9 / BrowserStack zorunlu testleri

1. Gerçek cihazda HTTPS isteklerinin hostnames/redirect chain listesini **gerçek kullanıcı yerine test hesabıyla** kaydet.
2. API key'nin NextDNS dışı download hostlarına, log dosya adlarına, Android logcat'e veya üçüncü taraf servislerine taşınmadığını kontrol et.
3. Uygulamadan logout / eski profilden yeni profile geçiş / bildirim iptali / Android otomatik yedek ve cihaz transferini incele.
4. `notification_settings.preferences_pb` verisinin restore esnasında privacy beklentilerini karşılayıp karşılamadığını kontrol et.
5. Son APK'nın sınıflandırılan SDK lisans/veri toplama davranışı ile Data Safety beyanını eşleştir.
6. NextDNS API yanıtından gelen indirme linkinin başka HTTPS hostuna yönlenmesi ve HTTP downgrade red senaryosunu test et.

**Denetim hükmü:** Kodda geliştiriciye ait veri toplama backend'i tespit edilmedi; bu tam ağ denetimi veya Google Play Data Safety deklarasyonu **değildir**.

Resmî politika: https://support.google.com/googleplay/android-developer/answer/13326895

## 21 Ekim 2026 — kurulum ve bağlı IP çağrısı güncellemesi

Bu denetimin tarihsel kaynak tablosunda görülen `link-ip.nextdns.io` isteği, güncel PR #27 adayından kaldırılmıştır. Kamuya açık NextDNS REST belgesi profil kurulum veya linked-IP rotası tanımlamadığından istemci profil kurulumunu API eşitlemesine dahil etmez ve tanı sonucundaki mevcut istemci IP'sini bağlı IP olarak göstermez. Kurulum ekranı, linked-IP ayarını görüntülemek veya değiştirmek için `https://my.nextdns.io/` adresini kullanıcının tarayıcısında açar. Bu kaynak değişikliği imzalı paket veya canlı ağ gözlemiyle henüz doğrulanmış sayılmaz; belge ve Privacy asset'i DRAFT kalır.
