# Faz 9 — BrowserStack gerçek cihaz test kapısı (ZORUNLU)

**Karar:** Google Play herkese açık yayınından **önce** BrowserStack üzerinde test yapılmadan release kabul edilmeyecek. Bu dosya Faz 8'de planlandı. 10 Ekim 2026'da Espresso ile dört gerçek cihazda hedefli düzeltmeler doğrulandı: `NDNS-011` hukuk ekranı ve `Q-005` bildirim testi kapatıldı. Son koşu **62 başarılı, 0 başarısız, 2 işletim sistemi koşullu atlama** içerir; 40 güvenlik kontrolü bu sayıya dahildir. **B01–B22 tam cihaz kabulü ve yayın hazırlığı tamamlanmadı; hukuki DRAFT statüsü korunur.** Ayrıntılar: [hedefli düzeltme sonuçları](BROWSERSTACK_LEGAL_NOTIFICATION_FIX_RESULTS_TR_2026-10-10.md) ve [adayla bağlı kayıt](release/browserstack-legal-notification-fix-results-2026-10-10.json). [İlk cihaz sonuçları](BROWSERSTACK_INITIAL_RESULTS_TR_2026-10-10.md) tarihsel olarak saklanır.

## 1. Kullanılacak hatlar

İlk App Live kaydından sonra yerel BrowserStack kimlik doğrulaması yapılmış ve Espresso hatları çalıştırılmıştır. Yöntem sonuçları, ayrı debug/release paketleri, çalışma kimlikleri ve kalan bulgular [Espresso cihaz raporunda](BROWSERSTACK_ESPRESSO_RESULTS_TR_2026-10-10.md) izlenir. İlk kaydın `NOT_RUN` bilgisi kendi yürütüm anına aittir.

- **App Live:** Gerçek Android cihazlarında manuel keşif, kullanıcı akışı, konuşma ekranları, farklı form faktörleri, ekran okuyucu ve izin akışları.
- **App Automate / Espresso:** Uygulama APK (veya AAB) ve ayrıca `androidTest` test APK'sı ile tekrarlanabilir otomatik E2E.
- Android `testDebugUnitTest` / Robolectric/Compose JVM testleri **BrowserStack gerçek cihaz Espresso testi değildir**; ayrı gerçek cihaz testleri yazılmalıdır.
- BrowserStack gerektiğinde paketleri kendi sertifikasıyla yeniden imzalayabilir; sertifika fingerprint'ine bağlı başka SDK'lar varsa etki ayrıca test edilir.

Referanslar:
- https://www.browserstack.com/docs/app-automate/espresso/getting-started
- https://www.browserstack.com/docs/app-automate/espresso/upload-app-from-filesystem

## 2. Ön koşul

1. Faz 8 marka/API kullanım kararı ve kaynak lisansı kontrolü belgelendi.
2. Entegre development head/main üzerinde compile, lint, unit/API contract/Compose, Snyk, coverage yeşil.
3. Test edilecek **aynı sürüm**, git commit SHA + `versionCode` + artifact hash ile sabitlendi.
4. Gerçek kullanıcı API anahtarı kullanılmaz. Ayrı bir **test NextDNS hesabı, ayrı test profilleri**, önceden hazırlanmış sentetik domain/rule/log senaryoları kullanılır.
5. Geçici test credentials GitHub deposuna, issue'ya, loga veya ekran görüntüsüne konmaz; test sonrası revocation uygulanır.
6. BrowserStack'e yüklenen paketlerde geliştirici kimliği/marka durumu yanıltıcı olmamalı; üretim varlıkları ile test varlıkları ayrıştırılmalı.

## 3. Cihaz matrisi (somut stok test gününde seçilecek)

| Sınıf | Koşul / örnek | Neyi test eder |
|---|---|---|
| Android API 24–25 | desteklenen en eski OS; BrowserStack stok durumuna bağlı | minSdk=24 ve `java.time` desugaring |
| Android 9–11 | mevcut gerçek Android cihaz | ağ/hata ve UI temel akışlar |
| Android 12L+ tablet | medium/expanded pencere | rail/sidebar, landscape, büyük ekran |
| Android 13+ telefon | bildirim izni | `POST_NOTIFICATIONS` opt-in/denial |
| Android 15/16+ telefon | güncel güvenlik/görüntü | lifecycle, edge-to-edge ve bildirimler |
| Foldable | katla/aç, pencere yeniden boyutlandırma | Compose state korunumu, adaptif gezinme |

Gerçek cihaz modeli ve Android sürümü **test anındaki BrowserStack katalogundan kaydedilecek**; burada teyit edilmemiş model/stok iddiası yoktur.

## 4. Kapsam — manuel ve otomatik

| ID | Senaryo | Kabul |
|---|---|---|
| B01 | Uygulama açılışı + login/invalid API key | Sunucu onayı yoksa sahte success yok |
| B02 | Profil listeleme, yaratma, değiştirme, silme | UI ancak authoritative GET ile onaylar |
| B03 | Security/Privacy/Parental/Allow/Deny/Settings değişikliği | Minimal mutation; hatada rollback ve açık mesaj |
| B04 | NextDNS web panelinde dışarıdan ayar değişikliği | Foreground açılış/refresh ile veriler uzlaşır, freshness açıklanır |
| B05 | İnternet kesilmesi/yeniden gelmesi; 401/429/500; timeout | Veri uydurulmaz, backoff, çökme yok, uygun yeniden deneme |
| B06 | Log SSE bağlanma/kesilme/yeniden bağlanma | Duplicate kaydı ve sahte canlı durum yok; background'da kapalı |
| B07 | Analytics ve log CSV export | API sözleşmesi ve gerçek indirme; API key başka hosta gitmez |
| B08 | Foreground/background, profil değişimi | Gereksiz polling durur, eski profil verisi gösterilmez |
| B09 | Notification opt-in, izin ret, channel off | Kullanıcı izni olmadan yayın yok; private lockscreen |
| B10 | WorkManager delay, dedupe, local mutation | Yanlış tekrar uyarısı yok; 30 dk işleyişi tam-zamanlı iddia etmez |
| B11 | Telefon/tablet/foldable compact/medium/expanded | Gezinme, yazı taşması, erişilebilir alanlar |
| B12 | TalkBack, büyük font, dark mode, yüksek kontrast | 48dp hedef, anlamlı semantik ve okunabilirlik |
| B13 | Logout/cache/clipboard/backup ögeleri | API key ifşası yok, hassas sorgu geçmişi diskte kalmaz |
| B14 | İmzasız/üretim varyantı test eşdeğerliği | Paket, applicationId, imza, min/targetSDK ve Crash/ANR kontrol |
| B15 | Güncelleme / eski sürümden veri migrasyonu | Anahtar düz metin bırakılmaz; kayıp profile sahte success yok |
| B16 | Düşük bellek/uygulama tekrar açma | Kalıcı işlem kaybı, çökme veya boş başarı bildirimi yok |
| B17 | **Beş dil**: Türkçe, İngilizce, Almanca, Fransızca, İspanyolca; sistem dili veya kullanıcı seçimi | Sabit arayüz metinleri tamamen çevrili, metin taşması veya karışık dil yok; erişilebilirlik ve bildirimler de çevrili |
| B18 | **Beş dilde Terms of Use**, **İngilizce Privacy Policy**; ilk kabul, güncelleme ve dil değiştirme | İlk checkbox boş; materyal değişiklikte yeniden onay; yalnız dil değişiminde gereksiz yeniden onay yok; gizlilik metni İngilizce olduğu açık; yerel hukuk incelemesi tamam |
| B19 | NextDNS API değişikliği/kesintisi: kaldırılan JSON alanı, hatalı tür, 401/403/404/429/5xx, timeout, eski endpoint | Uygulama çökmez; hata açıkça gösterilir, eski veri canlı olarak sunulmaz, yanlış profil silme/düzenleme gerçekleşmez, API onayı olmadan başarı bildirilmez; API değişikliği uyarısı doğru |
| B20 | Yeni kurulumda onay yok; eski kurulumda kabul sürümü eskimiş | Giriş, misafir modu, auto-login, canlı katalog ve background API isteği kabul öncesinde yok; Terms/Privacy internet olmadan okunabilir; kabul yazılamazsa giriş açılmaz |
| B21 | Açık oturumdan çıkış, devam eden API isteği ve WorkManager eşzamanlılığı | Yerel anahtar/profil/notification_settings temizlenir, tema korunur; sonradan gelen cevap veya worker eski hesap verisini yeniden oluşturmaz |
| B22 | Eksik hukuk dosyası, hukuki taslak veya eski revizyon ile yayın denemesi | scripts/verify_legal_release_ready.sh yayın paketini engeller; nihai metinler hukuki ve beş dilde eşdeğerlik kontrolünden geçirilir |


## 5. Gerçek cihaz otomasyonu ve güvenlik

- `androidTest` altında yeniden kullanılabilir en az **hesap bağlama, izinler, ayar değişikliği ve ekranlar arası gezinme** Espresso senaryoları gerekli. Bu akışlar için [giriş ve gezinme](../app/src/androidTest/java/com/example/NativeLoginNavigationTest.kt), [bildirim izni](../app/src/androidTest/java/com/example/NativeNotificationPermissionTest.kt) ve [ekran akışları](../app/src/androidTest/java/com/example/NativeScreenJourneyTest.kt) testlerine bakın.
- CI/artifact pipeline debug APK + test APK üretmeli; BrowserStack test raporları, ekran görüntüleri, crash/ANR/loglar, SHA ve cihaz kimlikleri ilişkilendirilmeli.
- API anahtarı ve DNS sorgu domainleri test raporlarında redakte edilmeli.
- Gerçek satın alma veya reklam içermeyen ilk sürümde Billing/ads testi **uygulanmaz**; sonradan açılırsa yeni kapsama eklenir.

## 6. Yayın kararı

**PASS için:** B01–B22 içindeki geçerli senaryolar tamamlanmış olmalı; P0/P1 kritik/yüksek açık **0**; güvenlik ve API anahtarının yanlış aktarılması **0**; tüm zorunlu cihaz sınıfları test edilmiş; bulgular ve düzeltme sonrası tekrar testler aynı release adayıyla tutarlı olmalı.

**FAIL/BLOCK:** Kritik crash/ANR, izinsiz veri aktarımı, hatalı ayarın başarılı gösterilmesi, başka profil verisinin görünmesi, gizli API key sızıntısı, ciddi a11y/OS çökmesi veya marka/lisans izni eksikliği.

**Son sıra:** Faz 8 uygunluk -> entegre paket -> BrowserStack App Live + App Automate -> düzeltmeler ve yeniden test -> Play internal/closed testing + prelaunch -> kademeli prod yayın.

**Durum:** **Kısmi gerçek cihaz yürütümü — yayın kapısı açık.** İlk App Live bulguları ve Espresso yöntem sonuçları tam B01–B22 kabulünün yerine geçmez.

## 7. Faz 9 dil kapsamı

İlk sürümde `tr`, `en`, `de`, `fr`, `es` desteklenecek. Kullanım Koşulları da beş dilde; Gizlilik Politikası şimdilik İngilizce. Dil değiştirmenin sözleşme onayına, oturuma, bildirimlere ve logout veri temizliğine etkisi için B17–B18 zorunludur. Ayrıntılar: [PHASE9_LOCALIZATION_PLAN.md](PHASE9_LOCALIZATION_PLAN.md).

## 8. Faz 9 ilk dilimi — uygulanan değişiklikler ve yayın engeli

`phase9-legal-gate-logout` dalında Terms kabulü, çevrimdışı beş dil taslak belgesi ve İngilizce Privacy görüntüleme, auto-login/WorkManager başlangıç kontrolü, oturum kapatmada notification DataStore temizliği ve yedekleme dışlamaları kodlandı. Test senaryoları eklendi. Bu **gerçek cihaz testinin yapıldığı veya nihai hukuki metinlerin onaylandığı anlamına gelmez**.

**Önemli:** Uygulamaya yerleştirilen sözleşmeler hâlen taslaktır; tamamında `DRAFT` uyarıları bulunur. Public Play yayını öncesinde `bash scripts/verify_legal_release_ready.sh` çalıştırılması zorunludur; metinlerin içerik bakımından uzman incelemesi, geliştirici kimliği ve Privacy Policy URL'sinin doğrulanması ayrıca gereklidir. Script yalnız metin/placeholder tespiti yapar ve tek başına hukuk onayı sağlamaz.
