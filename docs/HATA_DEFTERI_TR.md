# Hata defteri — yayın öncesi kod incelemesi

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
