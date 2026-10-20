# Hata defteri — yayın öncesi kod incelemesi

## 20 Ekim 2026 — günlükler, ikonlar ve arayüz düzeni

Bu ek kayıt, `c78c9af464a4e23ac926f69478a59c5cc44a05f3` tabanlı `fix/settings-account-sync-ux-2026-10-19` geliştirme dalındaki yerel değişiklikleri kapsar. Önceki hata kayıtları tarihsel halleriyle korunur. Aşağıdaki bulguların kod düzeltmeleri yerel JVM/Compose testleriyle doğrulandı; bu değişiklikler henüz PR CI veya gerçek Android/BrowserStack cihaz tekrarı almadı.

| Kayıt | Öncelik | Bulgu | Yerel durum |
|---|---|---|---|
| NDNS-020 | P2 | Günlük akışı sonraki API sayfalarını yüklemiyor ve cihaz filtresi API cihaz kimliğini kullanmıyor; eski kayıtlar/cihaz günlükleri görünmeyebiliyor | Düzeltildi; yerel regresyon ve Compose testleri PASS; cihaz/CI bekliyor |
| NDNS-021 | P2 | Günlük alan adları için favicon yükleme ve doğrulanmış yerel geri dönüş yok | Sınırlı ilk taraf yükleyici ve yerel fallback eklendi; URL doğrulama testleri PASS; canlı ağ/API sözleşmesi doğrulaması bekliyor |
| NDNS-022 | P2 | Servis ayarları, uygulama tercihleri ve hesap/bağlantı işlemleri tutarsız bölümlenmiş; üst hesap menüsü yinelenen bir giriş oluşturuyor | Düzeltildi; Compose akış testleri PASS; gerçek cihaz/CI bekliyor |
| NDNS-023 | P2 | Misafir giriş öncesi dil ve hukuk kontrolleri dağınık ve tekrarlı görünüyor | Tek bir kabul yüzeyinde toplandı; açık kabul ve salt-okunur belge erişimi regresyonları PASS; gerçek cihaz/CI bekliyor |

NDNS-020 günlük sayfalamayı NextDNS API'nin `meta.pagination.cursor` sözleşmesine ve cihaz filtresinde API kimliklerine dayandırır ([NextDNS API belgeleri](https://nextdns.github.io/api/)). NDNS-021 favicon hostunu sabit HTTPS `favicons.nextdns.io` ile sınırlar; bu rota yayımlanmış API sözleşmesi değildir ve kullanılabilirliği garanti edilmez. Geçersiz/ulaşılamayan görselde yerel ikon gösterilir. NDNS-022 ve NDNS-023 bölümleme kararları Android ayarları ve erişilebilir kaydırılabilir listeler kılavuzlarıyla uyumludur ([Android ayarlar tasarım kılavuzu](https://developer.android.com/design/ui/mobile/guides/patterns/settings?hl=en), [Compose büyük listeler kılavuzu](https://developer.android.com/develop/ui/compose/quick-guides/content/lazily-load-list?authuser=482)).

**Yerel doğrulama:** 152 JVM/Compose testi, 12 ekran görüntüsü, beş dilde 578 kaynak; Android Lint; debug APK, unsigned release APK, AAB ve instrumentation APK paketlemesi başarılı. Çalıştırılabilir kanıtlar ve sınırlar `release/ux-logs-icons-2026-10-20/results.json` içindedir. Snyk CLI bu ortamda yoktu, BrowserStack kimlik bilgileri yapılandırılmamıştı ve yerel ADB sunucusu başlayamadı; bu nedenle bu üç kontrol `NOT_RUN` kalır. Değişiklikler `main`e alınmadı ve bu dilim için CI çalıştırılmadı.

Uygulama logosu aynı kaldı; yalnızca başlatıcı simgesi sadeleştirildi. NextDNS ile resmî ortaklık iddiası eklenmedi. Hukuki metinlerin `DRAFT` durumu ve doğrulanmamış yayıncı/iletişim bilgilerinin eklenmemesi korunur.

## 19 Ekim 2026 — güncel genel hata avı

İncelenen `4812323` sürümünde **8 yeni kod kusuru (1 P1, 7 P2)** bulundu ve `f2e9ebe` ile düzeltildi. Windows ve PR CI doğrulamaları geçti; genel BrowserStack duman testi de tamamlandı. Sekiz kusura özel cihaz vakaları bekliyor. [İlk inceleme ve kapanış ölçütleri](HATA_AVI_TR_2026-10-19.md), [değişmez kırmızı yeniden üretim kaydı](release/bug-hunt-2026-10-19/results.json), [yeşil düzeltme kanıtı](release/bug-fixes-2026-10-19/fix-results.json), [PR #25](https://github.com/illetyus/NextDNS-App/pull/25).

| Kayıt | Öncelik | Bulgu | Durum |
|---|---|---|---|
| NDNS-012 | P1 | Gerçek NextDNS profil parmak izi yapılandırma kimliğiyle karşılaştırılıyor; doğru seçili bağlantı sağlıklı gösterilmiyor | Düzeltildi; JVM/CI PASS; genel cihaz duman testi PASS; hedefli cihaz vakası bekliyor |
| NDNS-013 | P2 | Altı haneli profil kimliğiyle geçersiz IPv6 adresi üretiliyor | Düzeltildi; kurulum/uç nokta regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-014 | P2 | İzin/engel listesi eklemesi başarısız olduğunda yazılan metin siliniyor | Düzeltildi; başarısız/başarılı gönderim regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-015 | P2 | Başarısız analiz alt panelleri görünümün taze başarı sayılmasını engellemiyor | Düzeltildi; bütünlük ve kısmi hata regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-016 | P2 | Eski analiz filtresi yanıtı yeni filtre sonucunu eziyor | Düzeltildi; eski yanıt/loading yarış regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-017 | P2 | Boş uzak günlük yanıtı eski kayıtları ekrandan kaldırmıyor | Düzeltildi; boş snapshot/temizleme yarış regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-018 | P2 | Misafir seçimi bekleyen giriş isteğini geçersiz kılmıyor | Düzeltildi; gecikmiş giriş regresyonu ve CI PASS; hedefli cihaz vakası bekliyor |
| NDNS-019 | P2 | Alan adı regex'i geçerli Punycode'u reddediyor, geçersiz etiketleri kabul ediyor | Düzeltildi; IDN/STD3, joker ve ret regresyonları ve CI PASS; hedefli cihaz vakası bekliyor |

İlk kırmızı koşu yalnız kusurları yeniden üretmek için eklenen testlere aitti ve bu tarihsel kayıt değiştirilmedi. Düzeltme PR’ında **135 JVM testi, 66 zorunlu regresyon, 12 ekran görüntüsü, lint/build, Snyk ve imzalı release-emülatör güvenlik kapıları geçti**. Dört cihazlı BrowserStack Espresso duman koşusu 64 yöntemden 22 PASS, 0 FAIL ve 42 koşullu atlama ile tamamlandı; atlamalar debug APK’daki release-only testleri ve Android 10/12 API koşulunu kapsar. Bu mevcut cihaz paketi NDNS-012–019’a özgü B vakalarını çalıştırmadığından onların hedefli cihaz durumu NOT_RUN kalır. Bağımlılık envanterinde bir POM lisans alanı eksikliği kaydedildi. Windows başlangıç koşusunun native yükleme sorunları Q-008 ortam kaydıdır; sekiz uygulama kusuruna dahil değildir.

## 10 Ekim 2026 — önceki inceleme ve düzeltme geçmişi

İnceleme tarihi: **10 Ekim 2026**. Kaynak: `4abf26a1a556cf42235fbf05c70f89cb5ad79e8c`. Bu dosya ve [makinece okunabilir kayıt](release/quality-ledger.json) aynı bulguları taşır.

**10 kod kusuru düzeltildi ve JVM regresyonuyla doğrulandı; cihaz kabulü bekliyor.** İlk inceleme öncelikleri 3 P1, 7 P2 olarak korunur. [Düzeltme raporu](CODE_QUALITY_FIXES_TR_2026-10-10.md) ve [saklanan kanıt](release/code-quality-fix-evidence-2026-10-10.json) güncel durumu gösterir. Ayrıca 3 kalite/süreç gözlemi vardır. Bu incelemede P0 düzeyinde bir kusur için kanıt elde edilmemiştir; bu ifade kusursuzluk veya tam güvenlik garantisi değildir.

`SOURCE_CONFIRMED` kaynak kodda koşullu hata akışının doğrulandığını belirtir. NDNS-001–010 kendi cihaz kapanış senaryolarını bekler. Sonraki NDNS-011 ve Q-005–007 hedefli gerçek cihaz tekrarlarıyla kapatılmıştır. B01–B22 bütün olarak tamamlanmamıştır; güncel `PARTIAL`/`NOT_RUN` durumları [yayın hazırlığı kaydında](release/readiness.json) izlenir. [İnceleme raporu](CODE_QUALITY_REVIEW_TR_2026-10-10.md) ilk incelemenin yöntem ve kapsam sınırını açıklar.

P1, son kabul aşamasından önce düzeltilmesi gereken yüksek öncelik; P2, belirli akışlarda yanlış davranış/çökme/erişilebilirlik veya kaynak yönetimi kusuru; P3, bakım önerisi anlamındadır. Bir hata, düzeltme commit’i ve saklanan regresyon/tekrar test kanıtı olmadan kapatılmaz.

| Kayıt | Öncelik | Bulgu | Durum | İlgili cihaz vakaları |
|---|---|---|---|---|
| NDNS-001 | P1 | Geciken API yanıtı eski hesap/profil verisini yeniden uygulayabilir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B08, B13, B21 |
| NDNS-002 | P1 | A profiline gönderilen mutasyon B profili okunarak doğrulanabilir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B04, B08, B19, B21 |
| NDNS-003 | P1 | Başarısız veya başka profile ait DNS ölçümü olumlu koruma durumu gösterir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B02, B05, B08, B19 |
| NDNS-004 | P2 | Web3 değeri sunucudan yerel ayar modeline aktarılmıyor | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B04, B15, B19 |
| NDNS-005 | P2 | Kurulum ekranının tanılama döngüsü arka planda da çalışır | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B05, B08, B16 |
| NDNS-006 | P2 | Coroutine iptali senkron OkHttp çağrısını kapatmıyor | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B06, B08, B13, B21 |
| NDNS-007 | P2 | IP bağlama ve katalog hata yollarında Response kaynakları açık kalabilir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B03, B05, B19 |
| NDNS-008 | P2 | Senkronizasyon bayrakları iptal ve eşzamanlı güncellemede tutarsızlaşabilir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B05, B08, B16 |
| NDNS-009 | P2 | Günlük cihaz menüsünde Tüm cihazlar etiketi çevrilmiyor | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B12, B17 |
| NDNS-010 | P2 | Bozuk export URL veya çıktı açma hatası kontrolsüz istisna oluşturabilir | KOD DÜZELTİLDİ; JVM PASS; cihaz bekliyor | B07, B16, B19 |
| NDNS-011 | P2 | Kısa yatay hukuk ekranında belge alanı ve devam kontrolü erişilemiyor | DÜZELTİLDİ; JVM VE HEDEFLİ CİHAZ PASS | B11, B18, B20 |

## Kod kusurları

### NDNS-001 — P1: Geciken API yanıtı eski hesap/profil verisini yeniden uygulayabilir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:1724–1747](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1724-L1747); [NextDnsRepository.kt:1199–1215](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1199-L1215); [NextDnsRepository.kt:1125–1152](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1125-L1152); [NextDnsViewModel.kt:316–332](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt#L316-L332).

**Tetikleyici:** A profilinde manuel log yenileme veya profil oluşturma sürerken B profiline geç ya da çıkış yap; A isteğini daha sonra tamamlat.

**Kaynakta görülen davranış:** Yanıt sonrası mevcut API anahtarı/profil/oturum nesli kontrol edilmeden StateFlow ve preferences yazılıyor. ViewModel kapsamındaki bu işler repository kapsamının cancelChildren çağrısıyla iptal edilmiyor. loadLocalProfileData geçmişi temizler; eksik olan geç yanıtı reddetme kontrolüdür.

**Düzeltme yönü:** Hesap ve profil bağlamına ait bir nesil/snapshot doğrulamasıyla eski cevapların UI ve diske yazılmasını engelle; oturuma ait işlerin kapsamını açıkça yönet.

**Kapanış ölçütü:** Geciktirilmiş A yanıtı B ekranına taşınmaz; çıkıştan sonra prefs/profil listesi yeniden oluşmaz; yeniden girişte önceki hesabın cache verisi gösterilmez.

**Cihaz tekrar testi:** B08, B13, B21.

### NDNS-002 — P1: A profiline gönderilen mutasyon B profili okunarak doğrulanabilir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:197–205](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L197-L205); [NextDnsRepository.kt:242–275](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L242-L275); [NextDnsRepository.kt:152–170](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L152-L170).

**Tetikleyici:** A için PATCH/POST gönder; yanıt beklerken B profiline geç; A yazma yanıtını tamamlat.

**Kaynakta görülen davranış:** mutateSection ilk key/profileId çiftini action için yakalıyor fakat refreshSection yeni aktif key/profileId çiftini yeniden okuyor. B GET başarılı olursa A işlemi doğrulanmış sayılabiliyor.

**Düzeltme yönü:** Yazma ve doğrulama GET isteklerini aynı oturum/profil snapshotına bağla; hedef durumun uygulandığını doğrula ve profil değişmişse yeni ekrana eski işlem başarısı uygulama.

**Kapanış ölçütü:** İstek kaydında PATCH ve doğrulama GET aynı hedefe gider; profil/hesap değişimi doğrulamayı geçersiz kılar; başka profil GET başarısı yazma başarısı sayılmaz.

**Cihaz tekrar testi:** B04, B08, B19, B21.

### NDNS-003 — P1: Başarısız veya başka profile ait DNS ölçümü olumlu koruma durumu gösterir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:498–506](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L498-L506); [SetupScreen.kt:148–187](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/SetupScreen.kt#L148-L187).

**Tetikleyici:** Önce status=ok sonucu al, sonraki ölçümün iki yolunu da başarısız kıl. Ayrı durumda seçili A profili varken ölçüm profileId=B ve status=ok dönsün.

**Kaynakta görülen davranış:** Hata yolu önceki status/protocol/profileId değerlerini copy ile koruyor. Banner hata mesajı varken ok durumuna öncelik veriyor ve activeProfile ile ölçülen profileId değerini karşılaştırmıyor; bu profille kullanılıyor alt yazısı seçili profil için yanlış olabiliyor.

**Düzeltme yönü:** Son ölçüm başarısını, güncelliği ve ölçülen profil eşleşmesini ayrı durumlarla göster; eski ölçümü yeni başarı gibi işaretleme.

**Kapanış ölçütü:** ok→timeout yolu güncel başarı göstermez; A seçili/B ölçülü durum profil uyuşmazlığı gösterir; son başarılı ölçüm zamanı yeni başarısız ölçüm zamanı ile değiştirilmez.

**Cihaz tekrar testi:** B02, B05, B08, B19.

### NDNS-004 — P2: Web3 değeri sunucudan yerel ayar modeline aktarılmıyor

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsApiService.kt:262–267](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/api/NextDnsApiService.kt#L262-L267); [NextDnsRepository.kt:1023–1035](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1023-L1035); [NextDnsRepository.kt:1605–1613](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1605-L1613); [SettingsScreen.kt:221–225](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/SettingsScreen.kt#L221-L225).

**Tetikleyici:** GET settings yanıtı web3=true dönsün veya Web3 açma PATCH işlemini başarılı kıl.

**Kaynakta görülen davranış:** SettingsDto.web3 var; applyConfigSettingsFromApi copy çağrısında s.web3 yok. Varsayılan false korunur ve ekrandaki switch false kalır; yerel cache de eski değeri saklar.

**Düzeltme yönü:** Web3 alanını yetkili sunucu yanıtından modele ve cache içine aktar; true, false ve eksik alan davranışını açıkça sınayarak doğrula.

**Kapanış ölçütü:** GET true/false ekranda ve cache içinde eşleşir; aç/kapat işlemi ve yeniden açılış aynı sunucu durumunu gösterir.

**Cihaz tekrar testi:** B04, B15, B19.

### NDNS-005 — P2: Kurulum ekranının tanılama döngüsü arka planda da çalışır

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [SetupScreen.kt:64–73](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/SetupScreen.kt#L64-L73); [NextDnsViewModel.kt:603–608](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt#L603-L608); [HomeScreen.kt:70–87](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/HomeScreen.kt#L70-L87).

**Tetikleyici:** Kurulum ekranı açıkken uygulamayı arka plana al ve 15 saniyeden uzun bekle.

**Kaynakta görülen davranış:** LaunchedEffect(Unit) bileşimde kaldığı sürece her 15 saniyede ViewModel işi başlatıyor; yaşam döngüsü RESUMED kontrolü yok. HomeScreen yalnız profil/sekme sorgularını durduruyor.

**Düzeltme yönü:** Tanılama üreticisini RESUMED yaşam döngüsüne bağla; başlatılan tanılama işini de aynı kapsama bağlayıp tek aktif isteği koru.

**Kapanış ölçütü:** Arka planda yeni tanılama isteği oluşmaz; ön plana dönüşte tek döngü çalışır; ekran ve Activity kapanışı işi sonlandırır.

**Cihaz tekrar testi:** B05, B08, B16.

### NDNS-006 — P2: Coroutine iptali senkron OkHttp çağrısını kapatmıyor

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:1814–1826](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1814-L1826); [NextDnsRepository.kt:1925–1928](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1925-L1928); [NextDnsRepository.kt:1672–1689](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1672-L1689); [NextDnsApiService.kt:948–963](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/api/NextDnsApiService.kt#L948-L963).

**Tetikleyici:** SSE veya export yanıtını açık/bekleyen durumda tut; profil değiştir, çıkış yap veya ilgili ekranı kapat.

**Kaynakta görülen davranış:** Call.execute/read işlemi bloklayıcı; Call nesnesi saklanmıyor ve Job iptalinde Call.cancel çağrılmıyor. stopLogsStream yalnız Job.cancel kullanır. İşin iptal edilmesi açık ağ aktarımının anında durduğunu kanıtlamaz.

**Düzeltme yönü:** Çağrı iptalini coroutine iptaline bağla; response/body kapatmayı finally/use ile garanti et; sonuç yazımından önce oturum kontrolü yap.

**Kapanış ölçütü:** Bekleyen fixture çağrısının iptal edildiği ve response kaynaklarının kapandığı ölçülür; eski iş tekrar bağlanmaz veya veri yazmaz.

**Cihaz tekrar testi:** B06, B08, B13, B21.

### NDNS-007 — P2: IP bağlama ve katalog hata yollarında Response kaynakları açık kalabilir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsApiService.kt:979–988](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/api/NextDnsApiService.kt#L979-L988); [NextDnsApiService.kt:999–1006](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/api/NextDnsApiService.kt#L999-L1006); [NextDnsApiService.kt:1032–1041](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/api/NextDnsApiService.kt#L1032-L1041).

**Tetikleyici:** IP bağlamayı tekrar çalıştır; katalog fallback isteğine body içeren non-2xx yanıtlar döndür.

**Kaynakta görülen davranış:** linkIpAddress yalnız isSuccessful okur; body tüketilmez ve response kapatılmaz. Katalog fallback erken non-2xx dönüşleri de body tüketilmeden gerçekleşir. Başarılı body.string yolları zaten kapanır ve bu bulguya dahil değildir.

**Düzeltme yönü:** Bütün senkron Response sahipliğini use kapsamına al; erken dönüş/istisnalarda da body kapansın.

**Kapanış ölçütü:** Başarı, non-2xx ve parse hatası yollarında her response kapatılır; tekrarlı fixture çağrılarında açık kaynak birikmez.

**Cihaz tekrar testi:** B03, B05, B19.

### NDNS-008 — P2: Senkronizasyon bayrakları iptal ve eşzamanlı güncellemede tutarsızlaşabilir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:143–150](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L143-L150); [NextDnsRepository.kt:157–180](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L157-L180); [HomeScreen.kt:454–457](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/HomeScreen.kt#L454-L457).

**Tetikleyici:** refreshSection isRefreshing=true olduktan sonra sekme sorgusunu iptal et. Ayrı durumda farklı section güncellemelerini eşzamanlı tamamlat.

**Kaynakta görülen davranış:** CancellationException güncel bayrak temizlenmeden yeniden fırlatılır; temizleme finally içinde değil. Harita read/value→copy→write ile güncellenir; eşzamanlı değişiklikler birbirini kaybedebilir.

**Düzeltme yönü:** İstek kimliğine bağlı finally temizliği ve atomik StateFlow.update kullan; eski isteğin yeni isteğin bayrağını temizlemesine izin verme.

**Kapanış ölçütü:** İptalde eski isRefreshing kalmaz; iki section değişikliği korunur; ardışık eski/yeni istek tamamlanması yeni isteğin durumunu bozmaz.

**Cihaz tekrar testi:** B05, B08, B16.

### NDNS-009 — P2: Günlük cihaz menüsünde Tüm cihazlar etiketi çevrilmiyor

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [LogsScreen.kt:118–120](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/LogsScreen.kt#L118-L120); [LogsScreen.kt:140–142](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/LogsScreen.kt#L140-L142); [LogsScreen.kt:270–271](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/LogsScreen.kt#L270-L271); [LogsScreen.kt:292–299](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/LogsScreen.kt#L292-L299).

**Tetikleyici:** EN/DE/FR/ES seç; Logs cihaz filtresi menüsünü aç.

**Kaynakta görülen davranış:** Seçili başlık UiLabels.canonical ile çevrilir fakat menü Text(text=dev) kullanır. Listeye eklenen Türkçe Tüm cihazlar sentinel değeri menüde aynen görünür; statik kaynak eşitliği kapısı bunu yakalamaz.

**Düzeltme yönü:** Sabit sentinel etiketlerini menüde de çevir; gerçek sunucu cihaz adlarını aynen koru.

**Kapanış ölçütü:** Beş dilde başlık ve menü etiketi aynı anlamda çevrilir; kullanıcı cihaz adları çeviri yüzünden değiştirilmez.

**Cihaz tekrar testi:** B12, B17.

### NDNS-010 — P2: Bozuk export URL veya çıktı açma hatası kontrolsüz istisna oluşturabilir

**Durum:** `FIXED_JVM_VERIFIED_DEVICE_PENDING`. Kontrollü JVM regresyonu: PASS. Cihaz tekrar testi: NOT_RUN. Düzeltme commit’i: `40aefa8a6446bd1f16824cc14e290b6307f35440`. [Vaka bazlı kanıt](release/code-quality-fix-evidence-2026-10-10.json). Kaynakta görülen davranış aşağıda ilk inceleme kaydı olarak korunur.

**Kaynak:** [NextDnsRepository.kt:1615–1618](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1615-L1618); [NextDnsRepository.kt:1662–1673](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L1662-L1673); [SettingsScreen.kt:63–73](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/screens/SettingsScreen.kt#L63-L73); [NextDnsViewModel.kt:678–684](https://github.com/illetyus/open-source-client-for-nextdns/blob/4abf26a1a556cf42235fbf05c70f89cb5ad79e8c/app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt#L678-L684).

**Tetikleyici:** Export link yanıtında https:// gibi geçersiz ama prefix kontrolünü geçen bir değer kullan. Ayrı durumda belge sağlayıcısının openOutputStream çağrısını hata ile sonlandır.

**Kaynakta görülen davranış:** Request.Builder.url try bloğundan önce çalışır; geçersiz URL IllegalArgumentException üretir ve Result akışından kaçar. UI openOutputStream çağrısı da try içinde değildir; sağlayıcı istisnası screenScope.launch içinde kontrolsüz kalır.

**Düzeltme yönü:** URLyi HttpUrl ile doğrula, bütün export/sağlayıcı işlemini yönetilen hata akışına al; cancellation istisnasını yeniden fırlat ve kısmi dosyayı başarı sayma.

**Kapanış ölçütü:** Geçersiz URL, silinen/revoked URI, sağlayıcı erişim hatası ve IO hatası kullanıcıya hata gösterir; çökme/başarı bildirimi olmaz; tüm akışlar kapanır.

**Cihaz tekrar testi:** B07, B16, B19.

### NDNS-011 — P2: Kısa yatay hukuk ekranında belge alanı ve devam kontrolü erişilemiyor

**Durum:** `FIXED_REAL_DEVICE_RETEST_VERIFIED`. İlk gerçek Pixel 7 gözlemi [ilk cihaz kaydında](release/browserstack-initial-results-2026-10-10.json) korunur. Eski yerleşime eklenen regresyon CI `38080658261` üzerinde **0 dp belge yüksekliği** ile başarısız oldu. Düzeltme commit’i: `20d9143f57133b998fe620a05d0fed26a9b006c7`.

Belge alanının sonlu, en az 160 dp yüksekliği ve dış sayfanın kaydırılması, 2× yazı boyutunda kabul kontrollerini erişilebilir tutar. Beş dilde arayüz/Terms ve mevcut İngilizce Privacy, dikey/yatay döndürme, boş checkbox ve seçim öncesi devre dışı devam düğmesi dört gerçek cihazda başarılıdır. [Commit, APK hash ve yöntem kanıtı](release/browserstack-legal-notification-fix-results-2026-10-10.json). Bu kapanış tam B11/B18/B20 kabulü değildir.

## Kalite ve süreç gözlemleri

| Kayıt | Öncelik | Gözlem | Durum |
|---|---|---|---|
| Q-001 | P2 | Repository oturum/mutasyon yollarının hedefli regresyon kanıtı eksik | 66 regresyon/135 JVM CI PASS; 4 cihaz genel smoke PASS; hedefli cihaz testleri bekliyor |
| Q-002 | P3 | Repository çok sayıda sorumluluğu ve statik API bağımlılığını topluyor | AÇIK |
| Q-003 | P2 | PR/CI süreci uygulanıyor fakat main sunucu tarafında korunmuyor | AÇIK |
| Q-004 | P2 | BrowserStack yerel MockWebServer ayarı eksik | ÖNCEKİ CİHAZ TEKRARINDA DÜZELTİLDİ |
| Q-005 | P2 | Bildirim testi sistem kanal görünürlüğünü uygulama garantisi sayıyor | DÜZELTİLDİ; HEDEFLİ CİHAZ PASS |
| Q-006 | P2 | Release test APK desugaring API eksikliği ve izin temizleme sırasında süreç sonlanması | DÜZELTİLDİ; HEDEFLİ CİHAZ PASS |
| Q-007 | P2 | BrowserStack yöntemleri arasında bildirim izin durumu kalıyor | DÜZELTİLDİ; AYNI APK TEKRARI PASS |

**Q-005:** Android kanal görünürlüğünü sistem yönetir. `03ac6dd000363c8ba2b23cf8767e1c140dbb881f` ile yanlış varsayım kaldırıldı; izin reddinde iki bildirim türünün de yayımlanmadığı, izin verildiğinde Android'e ulaşan gerçek bildirimlerin PRIVATE ve genel publicVersion taşıdığı denetlendi. Android 13/16 izin reddi kontrolleri geçti; Android 10/12 sürüm koşulu nedeniyle atlandı. Kullanıcı kanal ayarlarının bütün B09 kapsamı ayrıca açıktır.

**Q-006:** İlk release tekrarındaki dört misafir akışı `j$.time.Instant.parse` test APK uyumsuzluğuyla durdu; iki pozitif izin testi süreç çökmesi bildirdi. `f3c2c465bf4e03678bc5a4c65df05577c07cf5c8` gerçek ISO API/log biçimlendirme kontrolünü test paketinde korur ve pozitif test sonunda süreç öldürebilen izin geri alma işlemini kaldırır. Üretim tarih/protokol kodu değişmedi.

**Q-007:** Sonraki koşuda izin verilmiş yöntemden kalan durum Android 13/16 izin geri alma testini sonlandırdı. `bd2f3806cf5ae208aab67654eed72fe97820bf9f` ile BrowserStack gönderimine `clearPackageData=true` eklendi. Aynı yüklenmiş APK çiftinin tekrarında iki izin yolu bağımsız geçti; assertion atlanmadı. Önceki başarısız koşular ve başarılı son koşu [düzeltme raporunda](BROWSERSTACK_LEGAL_NOTIFICATION_FIX_RESULTS_TR_2026-10-10.md) korunur: toplam 62 PASS, 0 FAIL, 2 OS koşullu atlama; 40 güvenlik kontrolü bu toplam içindedir.

**19 Ekim düzeltme sayacı:** PR CI `39287249075` üzerinde 135 JVM testi, 66 zorunlu hedefli regresyon ve 12 ekran görüntüsü geçti; BrowserStack build `91b080c97113c42aa03c2bdae990247889cd9f54` dört cihazda 22 PASS, 0 FAIL, 42 koşullu atlama verdi. [CI ve test kanıtı](release/bug-fixes-2026-10-19/fix-results.json), [BrowserStack XML özeti](release/bug-fixes-2026-10-19/browserstack-results.json). `38082499286` sayıları önceki dilimin tarihsel CI kanıtıdır.

**Q-001 önceki dilim kanıtı:** 37 gerekli regresyon gerçek XML üzerinden doğrulandı; 104 JVM testi ve 10 native güvenlik testi başarılı. 19 Ekim güncel sayaçları yukarıdaki tabloda ve [düzeltme kanıtında](release/bug-fixes-2026-10-19/fix-results.json) bulunur. **İlk inceleme:** JVM toplam LINE %20.73, BRANCH %10.28; NextDnsRepository sınıfı LINE %7.06. Coroutine/Compose üretilmiş kodu oranları etkiler; API 35 native güvenlik testleri bu JVM raporuna dahil değildir. Kritik bug yolları için mevcut testler başarı kanıtı vermez. Rastgele bir genel yüzde hedefi yerine yukarıdaki kapanış senaryoları gereklidir.

**Q-002 güncelleme:** Enjekte edilebilir ağ/API sınırları ve küçük kaynak/yaşam döngüsü/sunum yardımcıları eklendi; geniş repository sorumlulukları bakım gözlemi olarak açık kalır. **İlk inceleme:** 2456 satırlık repository ağ, cache, profil, SSE, analytics ve sunum formatlamasını birlikte yönetir. Test edilebilir API/clock/session bağımlılıkları ve küçük saf mapper sınırları bakım işidir; önceki fazları baştan geliştirme gerekçesi değildir.

**Q-003:** GitHub branch protection API main için 404 Branch not protected verdi; etkin branch rules yanıtı boş listedir. Normal PR/CI izlendiği doğrulandı; zorunlu review/check kuralı platform tarafından dayatılmıyor. Bu inceleme depo yönetim ayarlarını değiştirmez.

## Önceki dilimde giderilmiş kayıtlar

| Kayıt | Sorun | Düzeltme ve kanıt | Durum |
|---|---|---|---|
| HIST-01 | Üretim kütüphanesindeki sıradan Robolectric/MockWebServer metinlerini test kodu sayan yanlış pozitif | [PR #18](https://github.com/illetyus/open-source-client-for-nextdns/pull/18): gerçek DEX tür tablosu kontrolü; gerçek test APK ve kesilmiş DEX reddi; başarılı main CI | GİDERİLDİ; mevcut paket kanıtında doğrulandı |
| HIST-02 | Korumalı Windows imzalama klasörüne tekrar ACL yazarken SeSecurityPrivilege hatası | [PR #19](https://github.com/illetyus/open-source-client-for-nextdns/pull/19): tekrar kullanımda ACL doğrulaması; aynı yerel anahtarla imzalı paket kanıtı | GİDERİLDİ; mevcut paket kanıtında doğrulandı |
| HIST-03 | Yerel masaüstü dosya bağlantısının telefondan indirilememesi | Önceki teslim turunda APK hesaba özel Drive dosyası olarak yüklendi; ad/boyut metadata ile doğrulandı. Bu APK inceleme adayıdır; Play yayını değildir. | TESLİM DÜZELTİLDİ; cihaz kurulumu/testi iddia edilmez |

## Kapatma ve son aşama sırası

İlk iki adım PR #21 düzeltmeleri ve saklanan CI regresyonuyla tamamlandı. NDNS-011 ve Q-005–007 için hedefli cihaz kapanışı verilmiştir; NDNS-001–010 için kendi kapanış senaryoları beklenir. Aşağıdaki sıra planın izlenebilir kaydıdır.

1. NDNS-001–003 için oturum/profil ayrımı ve bağlantı durumunu düzelt; diğer kayıtları ilgili küçük düzeltme dilimlerine ayır.
2. Her düzeltmeyi hedefli regresyon, PR ve CI kanıtına bağla; mevcut kaydı koruyarak düzeltme commit’i ve tekrar test adresini ekle.
3. Uygulama kodu değiştikten sonra yeni main CI’a bağlı imzalı inceleme adayı üret. Eski APK hash’i yeni kodun kanıtı olamaz.
4. Yeni aday üzerinde ilgili B vakalarını ve B01–B22 kabul matrisini çalıştır. Son yayıncı/metin incelemesi ve Play kanalları bu aşamayı izler.

Mevcut hukuk metinleri **DRAFT** kalır; yayıncı/iletişim/onay bilgisi eklenmez. Bu hata defteri kullanıcıya yeni bir hizmet, SLA veya destek taahhüdü oluşturmaz.

## 20 Ekim uygulama düzeltmeleri

| Kayıt | Sorun | Düzeltme | Doğrulama |
|---|---|---|---|
| NDNS-012 | Tam eşitleme, belgelenmemiş /account yolunu gerçek NextDNS ayarı gibi ele alıyor; hesap planı alanları bu istemcide API eşitlemesine ait değil | Hesap DTO/endpoint ve ACCOUNT eşitleme bölümünü kaldır. API anahtarı ve bağlantı durumu cihazda yerel kalır; profil ve desteklenen yapılandırma uçları eşitlenmeye devam eder. | fullSyncDoesNotRequestAnAccountEndpointThatIsNotInThePublicApi; tam JVM test kümesi |
| NDNS-013 | Analiz varış noktaları metin listesi olarak gösteriliyor, ülke seçimi yok | Yerel Natural Earth ülke geometrisiyle etkileşimli harita ekle. Seçili ülke için API'nin sorgu, yüzde ve alan adı örneklerini göster; ekran okuyucu eylemlerini ve çevrimdışı çalışmayı koru. | WorldMapChartInteractionTest; harita kaynağı ve SHA-256 provenans kaydında |
| NDNS-014 | İzin ver/engelle listelerindeki favicon'lar satır içeriğine göre çok büyük | Liste favicon boyutunu 12 dp yap; satır ve eylem hedeflerinin dokunma alanını koru. | Tam JVM test kümesi; Android Lint |

**Yerel doğrulama, 20 Ekim 2026:** Debug APK derlendi. 33 sınıfta 156 JVM/Robolectric testi geçti, 0 hata. lintDebug başarılı; 0 hata ve 94 uyarı raporlandı. Uyarıların tüm depo için önceki sürüm karşılaştırması yapılmadığından bunlar yeni/eski diye sınıflandırılmaz. APK OpenSourceClientForNextDNS-review-2026-10-20-map-ui.apk adıyla yönlendirilmiş Download klasörüne kopyalandı; boyutu 22.682.757 bayt, SHA-256 E4EE820E39C472A45D0DCF7914E0A1A3C1A52E743013E89BF29D7E1960AD4255 ve kopya hash'i derleme çıktısıyla eşleşiyor. BrowserStack ve fiziksel cihaz bu düzeltme diliminde çalıştırılmadı; GitHub CI veya main birleştirmesi de yapılmadı. APK yerel inceleme adayıdır, Play yayını değildir.

**18 dp takip doğrulaması, 20 Ekim 2026:** Güncel kaynak kopyasından 33 JVM/Robolectric sınıfında 158 test geçti; 0 başarısız, 0 hata, 0 atlanan test. `lintDebug` başarılı; 0 hata ve 99 uyarı. `assembleDebug` başarılı. APK: `OpenSourceClientForNextDNS-review-2026-10-20-map-ui-18dp.apk`, 21.936.035 bayt, SHA-256 `15FF1A10FE176204303F1353428D4DC77A53963C70369BE4B807C07CEB9C7CFC`; paket kimliği `com.aistudio.nextdns.mgrqvt`; `assets/maps/world-countries.tsv` paket içinde doğrulandı. APK yerel çıktı `C:\Users\Cemalettin R. Okatan\Documents\Codex\nx-verify\app\build\outputs\apk\debug\app-debug.apk` altında kaldı. Önceden belirlenen `\\tsclient\Local Storage\Download` yolu erişim reddi verdi. APK'yı Google Drive'a yükleme girişimi, Drive hedefinin kullanıcı tarafından açıkça yetkilendirilmediği gerekçesiyle otomatik incelemede reddedildi; dosya harici depoya yüklenmedi. Bu sürüm için BrowserStack/cihaz, GitHub CI veya `main` birleştirmesi yapılmadı. Bu, yerel debug inceleme adayıdır; yayın adayı değildir.

## 20 Ekim takip düzeltmesi: analiz dayanıklılığı ve görsel ölçüler

| Kayıt | Bulgu ve düzeltme | Kanıt |
|---|---|---|
| NDNS-012 takip | Resmî NextDNS API belgesinde hesap/bağlantı durumu eşitleme ucu bulunmuyor. Hesap ve bağlantı tanı bilgisi cihazda yerel tutuluyor; API anahtarıyla profil/yapılandırma eşitlemesi korunuyor. İngilizce gizlilik taslağı artık hesabın e-posta/plan verisinin alınmadığını ve favicon alan adı aktarımını açıklıyor; DRAFT statüsü sürüyor. | `fullSyncDoesNotRequestAnAccountEndpointThatIsNotInThePublicApi`; privacy registry SHA-256 doğrulaması; NextDNS API belgesi: https://nextdns.github.io/api/ |
| NDNS-013 takip | Analiz yenilemesi bir uç nokta başarısız olunca başarılı varış noktalarını da gizliyordu. Şimdi kullanılabilir paneller/etkileşimli harita gösteriliyor; başarısız panelin son değeri ve kısmi hata uyarısı korunuyor. Uyarı Hesabım özetinde de görünür. | `destinationsMapShowsCountryDataWhenAnotherAnalyticsRequestFails`; `partialAnalyticsWarningIsVisibleInAccountOverview`; `failedCountryPanelRetainsItsLastSuccessfulSnapshot`; `WorldMapChartInteractionTest` |
| NDNS-014 takip | İzin/engelle listesi alan adı favicon'ları 18 dp'den 16 dp'ye indirildi; satır ve eylem dokunma alanları korunuyor. | `AccountSettingsFlowTest` ikon genişliği/yüksekliği 16 dp doğrulaması; `lintDebug` |
| Simge görsel düzeltmesi | Başlatıcı simgesi beyaz arka plan, ortalanmış kalkan ve tek ince/geniş açılı yeşil sinyal çiftiyle sadeleştirildi. Uygulama içi ağ simgesi aynı bırakıldı. | `LauncherAssetsTest`; üretilen Android launcher varyantları ve mağaza PNG'si |

**Yerel yeniden doğrulama, 20 Ekim 2026:** Tam `testDebugUnitTest` çalışmasında 33 sınıfta 163 test geçti; 0 başarısız, 0 hata, 0 atlanan. Son gizlilik taslağı ve görünürlük testi güncellemesinden sonra ilgili 3 test sınıfında 25 test tekrar geçti. `lintDebug`: başarılı, 0 hata ve 99 uyarı. `assembleDebug`: başarılı. Güncel debug APK: `C:\Users\Cemalettin R. Okatan\Documents\Codex\nx-verify\app\build\outputs\apk\debug\app-debug.apk`, 22.493.215 bayt; SHA-256 `70ECBD18C659147F95FB5329F94381AB5B9E7296146BB4A03D544F7550F4FE52`; applicationId `com.aistudio.nextdns.mgrqvt`, version `1.0`. BrowserStack/fiziksel cihaz, GitHub CI, PR ve `main` birleştirmesi bu takip diliminde çalıştırılmadı. Hukuki taslakların DRAFT durumu ve bağımsız istemci ifadesi değişmedi.

**Hesabım gezinme takip düzeltmesi, 20 Ekim 2026:** Ana ekrandaki üst “Hesabım” düğmesi kaldırıldı. Hesabım bölümü Ayarlar gezinme grubuna taşındı; hesap/bağlantı bilgilerine sekme üzerinden erişim sürüyor. Eski başlık düğmesini bekleyen ekran testi bu yeni akışa göre güncellendi.

**Önceki yerel doğrulama (20 Ekim 2026, bu değişikliklerden önce):** Tam Gradle zinciri `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` başarılı tamamlandı. JVM/Robolectric test raporları: 33 sınıf, 163 test; 0 başarısız, 0 hata, 0 atlanan. `lintDebug`: 0 hata, 101 uyarı. Debug APK: `C:\Users\Cemalettin R. Okatan\Documents\Codex\nx-verify\app\build\outputs\apk\debug\app-debug.apk`, 22.491.441 bayt, SHA-256 `F14B4ECCF165286FDD210BDCE64BD5E0CB1699E083CDCF85A11294242AD7DBB`; applicationId `com.aistudio.nextdns.mgrqvt`, version `1.0`, görünen ad `Open Source Client for NextDNS`. Bu önceki test ve APK çıktıları mevcut diff'i doğrulamaz. BrowserStack/fiziksel cihaz, GitHub CI/PR ve `main` birleştirmesi o yerel dilimde yapılmadı. Hukuki belgeler DRAFT statüsünde kalır.

## 20 Ekim 2026 — bağlantı kısayolu ve ikon takip düzeltmesi

| Kayıt | Sorun ve düzeltme | Kanıt |
|---|---|---|
| NDNS-015 | Bağlantı/profil ekranı yalnızca alt/yan Ayarlar gezinmesinden açılıyordu; bağlantı durumu tek profil için çoğul etiket verebiliyordu. `NavTab.ACCOUNT` ana gezinmeden çıkarıldı, üst sağ 48 dp erişilebilir eylem mevcut bağlantı/profil ekranını açıyor. Profil başlığı sade “Profiller”; durum metni beş dilde tekil/çoğul sayıyı doğru gösteriyor. | `connectionAreaOpensFromDedicatedTopRightButton`; `navigationGroupsCoverEveryPrimaryTabButKeepConnectionInTheHeader`; `connectedProfileStatusUsesLocalizedSingularAndPluralCounts` |
| NDNS-016 | Gizlilik ekranındaki yerel izleme koruması simgeleri 32 dp idi. Görsel boyut 24 dp’ye indirildi; kaldırma düğmesinin 48 dp dokunma alanı korundu. | `localTrackingProtectionIconUsesTheSmallerSize`; `allowAndDenyListFaviconsUseCompactSize` |
| NDNS-017 | Başlatıcı işareti sivri ve fazla yüksek DNS parantezleri kullanıyordu. Mavi merkez kalkan ve resmî siteden renk referansı olan yeşil çizgiler korunup tek, ince, kavisli ve daha kısa bir çift hâline getirildi; adaptive, monochrome ve legacy ikonlar aynı betikten yeniden üretildi. | `launcherUsesOneCompactWideAnglePairOfCurvedDnsLinesAroundTheShield`; paketlenmiş launcher testleri; mağaza simgesi görsel denetimi |
| NDNS-014 ikinci takip | Beyaz/siyah liste alan adı favicon'ları 48 dp'den 16 dp'ye indirildi; satır düzeni okunaklı kaldı ve anahtar etkileşim alanları 48 dp olarak korundu. | `allowAndDenyListFaviconsUseCompactSize` 16 dp boyut doğrulaması |

**Önceki yerel doğrulama (20 Ekim 2026, bu değişikliklerden önce):** `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` başarılı tamamlandı. 33 test sınıfında 164 test geçti; 0 başarısız, 0 hata, 0 atlanan test. Lint 0 hata ve 125 uyarıyla tamamlandı. Debug APK `C:\Users\Cemalettin R. Okatan\Documents\Codex\nx-verify\app\build\outputs\apk\debug\app-debug.apk`, 22.745.689 bayt; SHA-256 `04CBC1DED2FA6DEDEBD5955D5527A48F9F868A002A8413BF74021734088FB3D0`. `aapt` paket kimliğini `com.aistudio.nextdns.mgrqvt`, görünen adı `Open Source Client for NextDNS` olarak doğruladı; APK'da etkileşimli haritanın ülke geometrisi varlığı bulundu. Bu önceki test ve APK çıktıları mevcut diff'i doğrulamaz. Fiziksel cihaz/BrowserStack, GitHub CI/PR ve `main` entegrasyonu yapılmadı; hukuk metinleri DRAFT, istemci bağımsız ve gayriresmî kalır.

## 20 Ekim 2026 — NextDNS API, günlük ve analiz doğruluk incelemesi

| Kayıt | Gözlem ve değişiklik | Regresyon / kanıt | Durum |
|---|---|---|---|
| NDNS-024 | Günlük araması yalnızca o ana kadar yüklenmiş satırları süzüyordu; eski cursor sayfalarında eşleşmeler bulunamıyordu. Arama artık API `search` parametresine gider; arama değişince cursor sıfırlanır, önceki sayfa yanıtı reddedilir ve SSE de aynı filtreyi alır. | `searchFilterUsesDocumentedServerQueryAndStartsAtFirstPage`; `changingSearchFilterRejectsAnOlderInFlightPage` | Kod/test eklendi; bu turda Gradle doğrulaması ortam hatasıyla başlamadı. |
| NDNS-025 | Bilinmeyen günlük neden kimliği küçük harf/tire dönüşümüyle API'den farklı gösterilebiliyordu. Tanınmayan nedenlerde ham API kimliği korunur; bilinen eşlemeler ve sunucunun verdiği adlar aynen kalır. | `unknownLogReasonKeepsTheExactApiValueInsteadOfInventingALabel` | Kod/test eklendi; CI doğrulaması bekliyor. |
| NDNS-026 | Analiz GAFAM grafiği ve lejandı aynı altı kategori/count modelini kullanacak şekilde düzenlendi; API'nin küçük harfli `company` değerleri tanınır. Ülke hedefleri yerel geometri ve dokunma seçimine ek olarak tüm harita ülkelerini sunan erişilebilir bir seçiciyle gösteriliyor. | `GafamChartModelTest`, `AccountSettingsFlowTest.gafamChartAndAccessibleDescriptionContainAllSixSegments`, `WorldMapChartInteractionTest` | Kod/test mevcut; bu turda çalıştırılamadı. |
| NDNS-027 | Dünya haritası geometrisi açılışta Compose ana iş parçacığında dosyadan okunuyordu. Asset okuma ve ayrıştırma `Dispatchers.IO`'ya taşındı; yükleme ve hata durumları ekranda belirtiliyor. Harita etkileşim testi önce Türkiye seçiliyken ABD'ye dokunarak seçimin gerçekten değiştiğini sınar. | `WorldMapChartInteractionTest` | Kod/test mevcut; cihaz ölçümü ayrıca gerekli. |
| NDNS-028 | Günlük cihaz adları boş/placeholder olabiliyor; görünen etiket ve filtre kimliği ayrıldı. İstemci API adı/modeliyle etiket çözümler; filtre isteklerinde API device ID'sini kullanır. | `DeviceLabelResolverTest`, API-ID cihaz filtresi ve analiz Compose testleri | Kod/test mevcut; CI doğrulaması bekliyor. |
| NDNS-029 | Yaş doğrulama atlama hizmet özelliğine dair API alanı tahmin edilmemeli. 2026 NextDNS Help Center başlığındaki resmi hesap yanıtı arayüzün hatalı dağıtımla kaldırıldığını söylüyor; API alanı/PATCH sözleşmesi vermiyor. API matrisi bu iki kanıt türünü ayrı kaydeder. | `docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md`; resmi API şeması ile Help Center başlığı | API eşlemesi doğrulanana kadar bilerek uygulanmadı; tahmini alan/yazma eklenmedi. |
| NDNS-030 | Favicon rotasının canlı davranışı örnek `example.com` için 404, `github.com` ve `google.com` için 200 `image/png` verdi. 404, favicon bulunmayan domain için yerel fallback gerektiren geçerli bir sonuçtur; rota resmi API değildir. | 20 Ekim 2026, kimlik bilgisi içermeyen salt GET sentetik probe; URL/IDN/yerel host reddi unit testleri | Seçili örneklerde yanıt doğrulandı; hizmet garantisi ve her domain için ikon varlığı doğrulanmış sayılmaz. |
| NDNS-031 | GAFAM lejandı ve ülke haritasının ekran okuyucu etiketlerinde doğrudan birleştirilmiş İngilizce noktalama metni bulunuyordu. Şirket/sorgu etiketi, erişilebilirlik listesi, ülke seçici ve ülke payı beş dilde kaynaklara taşındı. | `python scripts/verify_localization.py`: 607 kaynak / beş dil statik doğrulaması PASS | Statik kaynak ve biçim denetimi PASS; Kotlin derlemesi PASS, Compose testi bu turda başlatılamadı. |
| NDNS-032 | Günlük DNS alan adları uzaktaki favicon hostuna gönderiliyordu; bu, hassas tarama geçmişini üçüncü tarafa açabilirdi. Günlük satırları artık yerel dil ikonu kullanıyor; allowlist/denylist ve katalog favicon yüzeyleri DRAFT gizlilik açıklamasında belirtiliyor. | `UiAuditTest.logRowsShowLocalDomainIconsForBrowsingHistory`; güncellenmiş `privacy_en.txt` / hash registry | Kod ve Compose testi eklendi; Kotlin derlemesi PASS, JVM testleri Gradle worker loopback hatasıyla başlayamadı. |
| NDNS-033 | Harita ekran okuyucu eylemleri en fazla 10 trafik ülkesini sunuyordu. Harita geometrisindeki tüm ülkeler, veri yoksa bu durum açıkça belirtilen, yerelleştirilmiş seçiciye eklendi. | `WorldMapChartInteractionTest.accessibleCountrySelectorIncludesCountriesWithoutTrafficData` | Kod ve Compose testi eklendi; Kotlin derlemesi PASS, JVM testleri Gradle worker loopback hatasıyla başlayamadı. |
| NDNS-034 | Favicon gövdesinin teker teker kısa aralıklarla gelmesi bağlantıyı toplamda belirsiz süre açık tutabiliyordu. İzole OkHttp 4.10 istemcisinde ortak sekiz saniyelik `callTimeout` DNS, bağlantı, başlık ve gövde okumalarını birlikte sınırlar; yönlendirme ve otomatik tekrar kapalıdır. | `NextDnsFaviconUrlTest.faviconHttpClientBoundsTheFullCallAndDoesNotFollowRedirects`; OkHttp 4.10 API kaynak kodu | Kod ve birim testi eklendi; Kotlin derlemesi PASS, JVM testleri Gradle worker loopback hatasıyla başlayamadı. |
| NDNS-035 | Kalite-regresyon betiği eski test XML raporlarını dirty çalışma ağacındaki yeni kaynak kodun sonucu gibi sayıp `HEAD` ile ilişkilendirebiliyordu. Betik artık değiştirilmiş uygulama kaynaklarından eski raporları reddediyor ve kanıta kaynak ağacı SHA-256 parmak izi ile çalışma ağacındaki kaynak değişikliklerini ekliyor. | `python -m unittest scripts.tests.test_verify_quality_regressions`: 3 test PASS; yerel eski XML raporları yeni koruma ile reddedildi | Önceden üretilmiş “PASS” bu çalışma ağacı için geçersizdir; yeni Gradle XML raporu oluşana kadar güncel JVM test kanıtı yoktur. |

**Bu turdaki Gradle sonucu:** Yerel ve çalışma alanı önbellek kilidi sorunları ayrı Gradle user home ve salt-okunur bağımlılık önbelleğiyle aşıldı. `:app:compileDebugKotlin` PASS oldu; ilk turda derleme üç Kotlin hatası buldu, düzeltmelerden sonra Kotlin derlemesi geçti. `:app:testDebugUnitTest` daha sonra `compileDebugJavaWithJavac` aşamasında Gradle worker loopback soketi açılamadığı (`Unable to establish loopback connection`, `Permission denied`) için durdu; yükseltilmiş yürütme denemesi de aynı sistem hatasını verdi. Testler, lint ve APK üretimi bu turda tamamlanmadı. `python scripts/verify_localization.py` (607 kaynak/beş dil), `python scripts/verify_legal_assets.py` (DRAFT demeti), `git diff --check` ve kalite betiği koruması için 3 Python testi PASS oldu. Eski JUnit XML raporları `verify_quality_regressions.py` tarafından artık doğru biçimde reddediliyor. Mevcut test XML'leri ve debug APK önceki ağaçtan kalmadır ve güncel diff'i doğrulamaz.

**Devam eden yayın/kanıt engelleri:** fiziksel cihazda Macrobenchmark/Perfetto ölçümü; güvenli HTTPS hız testi sunucusu ve kullanım/lisans şartları; sanitize edilmiş gerçek API yanıtı ve sözleşme olmadan erken erişim güvenlik alanları; API'de belgelenmeyen yaş doğrulama alanı; beş dil dışındaki hukuki inceleme ve yayıma hazır olmayan DRAFT metinler. Ayrıntılı API karşılaştırması [NEXTDNS_API_CAPABILITY_MATRIX_TR.md](NEXTDNS_API_CAPABILITY_MATRIX_TR.md) içindedir.

## 20 Ekim 2026 — devam eden uygulama incelemesi

| Kayıt | Bulgu ve düzeltme | Regresyon / kanıt | Durum |
|---|---|---|---|
| NDNS-036 | Canlı SSE olayları ve geriye dönük cursor sayfaları günlük listesini sınırsız büyütebiliyordu; her yeni olayda tam liste üzerinde yinelenen kayıt taraması da yapılıyordu. Bellekte en son 1.000 kayıt tutuluyor, limit dolunca API cursor'ı durduruluyor ve kullanıcıya eski kayıtları filtrelerle aramasını söyleyen yerelleştirilmiş bilgi gösteriliyor. | `liveLogStreamRetainsOnlyTheMostRecentThousandEntries`; Android kaynaklarında 5 dil; GitHub JVM CI bekleniyor. | Kod ve regresyon eklendi; yerel Gradle daemon loopback erişimi açılamadığı için test çalışmadı. |
| NDNS-037 | Günlük yenileme, daha önce cursor ile yüklenmiş eski sayfaları yeni ilk sayfa yanıtıyla değiştirerek görünümden çıkarabiliyordu. Yenileme şimdi eski sayfaları koruyup yeni sunucu kaydı ve canlı olaylarla ID üzerinden birleştiriyor; 1.000 kayıt sınırı geçerli kalıyor. | `refreshingLogsPreservesPreviouslyLoadedOlderPages` | Kod ve regresyon eklendi; GitHub JVM CI bekliyor. |
| NDNS-038 | Hukuk karşılama ekranı testi, kayıt defterinin `privacy-2026-10-DRAFT-6` sürümü yerine eski bir Privacy revizyonunu bekliyordu. Test güncel DRAFT kimliğine uyarlandı; hukuki içerik ve DRAFT durumu değiştirilmedi. | `LegalWelcomeViewportTest.shortLandscapeAtLargeFontKeepsDocumentsAndConsentReachableInFiveLanguages` | Test beklentisi düzeltildi; GitHub JVM CI bekliyor. |
| NDNS-039 | Bundled lisans çıktısına elle eklenen harita atfı, kök NOTICE kaynağında bulunmadığından yeniden üretilebilirlik kapısı başarısız oluyordu. Natural Earth atfı kök NOTICE'a taşındı ve çevrimdışı bildirim demeti üreticiden yeniden oluşturuldu. | `python scripts/prepare_bundled_notices.py --check`; `python scripts/verify_legal_assets.py` | Yerel doğrulama PASS. |
| NDNS-040 | İlk PR CI, SSE günlük sınır regresyon testinde `.$indexZ` Kotlin interpolasyonunu bulunmayan bir `indexZ` değişkeni olarak derledi. Test verisi `${index}Z` biçimine düzeltildi. | CI `39396093759`, `RepositoryRegressionTest.kt` test derleme hatası; sonraki CI çalışması bekleniyor. | Kaynak düzeltildi; yeniden test ve lint CI kapısı bekliyor. |

**Bu uygulama turunun yerel statik doğrulamaları:** Beş dilde 608 kaynak ve biçim kontrolü PASS; hukuk dosyası hash/registry kontrolü PASS ve `DRAFT`; yayın hazırlığı denetimi bilerek yayın izni vermedi; üç kalite-regresyon Python testi PASS; lisans demeti `--check` PASS; `git diff --check` PASS. Güncel `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` çağrısı Gradle 9.3.1 daemon'un localhost loopback soketine bağlanamaması (`Could not connect to the Gradle daemon`) nedeniyle görevleri başlatamadı. Önceki XML raporları ve APK bu çalışma ağacının kanıtı değildir. Pull request CI henüz çalıştırılmadı.
