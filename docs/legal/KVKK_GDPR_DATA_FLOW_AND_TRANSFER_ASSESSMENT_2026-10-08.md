# Faz 8 — KVKK / GDPR veri akışı, tarafların hukuki rolleri ve yurt dışı aktarım matrisi

**Tarih:** 8 Ekim 2026  
**Durum:** Kaynak koduna ve resmî mevzuata dayalı ön değerlendirme. Hukuk mütalaası, imzalı sürümün fiilî veri akışı denetimi veya NextDNS tarafından verilmiş izin değildir.  
**Kaynak dal:** phase8-compliance.

## 1. Ürün modeli ve hukuki inceleme yöntemi

Open Source Client for NextDNS bağımsız, resmî olmayan Android hesap-yönetim istemcisidir; DNS çözümleme sunucusu, VPN, abonelik satıcısı veya NextDNS altyapısının işletmecisi değildir. Amaçlanan ilk sürümde uygulama verileri geliştiricinin sunucusuna iletilmemekte; cihaz, NextDNS API uçlarına doğrudan bağlanmaktadır. Bu iddia son imzalı AAB/APK'nın ağ trafiği ile ayrıca doğrulanacaktır.

**Sözleşme veya aydınlatmadaki etiket tek başına belirleyici değildir.** KVKK/GDPR veri sorumlusu, veri işleyen ve bağımsız sağlayıcı sıfatları her işlem için amaç ve esaslı vasıtaları fiilen kimin belirlediğine göre ayrılır. NextDNS sunucu hizmeti ile geliştiricinin cihazda çalışan yazılımı aynı faaliyet değildir. Kullanıcının NextDNS'e doğrudan veri göndermesi de geliştiricinin GDPR/KVKK yurt dışı veri aktaran tarafı olduğu veya olmadığı yönünde kendiliğinden kesin hüküm kurdurmaz.

## 2. Kaynak düzeyinde somut tespitler

- app/src/main/java/com/example/data/api/NextDnsApiService.kt: api.nextdns.io, test.nextdns.io ve link-ip.nextdns.io; OkHttp X-Api-Key, üretim logları NONE; log indirme NextDNS'in sağladığı farklı HTTPS hostuna yönelebilir.
- app/src/main/java/com/example/data/local/NextDnsPreferences.kt: Android Keystore ile API anahtarı korunur; profil/ayar önbelleklerinin tamamı ayrıca şifrelenmiş varsayılamaz.
- app/src/main/java/com/example/data/repository/NextDnsRepository.kt: ilk oluşturulurken canlı katalog/diagnostic ve kayıtlı API key varsa otomatik bağlanma başlar. Kullanım Koşulları kabul ekranı henüz yok.
- app/src/main/java/com/example/NextDnsApp.kt: Application.onCreate bildirim WorkManager reconcile başlatabilir; sözleşme kabul şartına henüz bağlanmadı.
- app/src/main/java/com/example/data/notifications/NotificationPreferences.kt: notification_settings isimli ayrı DataStore, profil bazlı özet/hash ve zaman kayıtlarını tutar. Mevcut logout bu deponun tamamını temizlemiyor.
- app/src/main/res/xml/backup_rules.xml ve data_extraction_rules.xml: nextdns_secure_prefs.xml dışlanmıştır; ayrı bildirim DataStore için nihai backup/restore testi gerekiyor.
- NextDnsRepository içinde deleteProfileRemote(), clearLogs(), log SSE ve istatistik erişimi mevcut. Yıkıcı işlemler için kullanıcı teyidi/sunucu doğrulaması uygulama testlerinde incelenmelidir.

## 3. İşlem ve risk matrisi

| Kod | Veri/işlem | Teknik kaynak ve hedef | İlk faaliyet ayrımı | Açık hukuki soru / yayın öncesi kanıt |
|---|---|---|---|---|
| D01 | API anahtarı, yetkili API erişimi | Kullanıcı → yerel Keystore → NextDNS API | NextDNS kendi hesap doğrulamasını yürütür; geliştiricinin istemci sürecindeki hukuki sıfatı ayrıca değerlendirilir | Fiilî işleme amacı, veri gönderen taraf, veri işleme şartı; anahtar sızma testi |
| D02 | Hesap adı/e-posta, profil adı/ID | NextDNS API → uygulama belleği, yerel profil önbelleği | Uzaktaki hesap hizmeti ile cihazdaki gösterim farklıdır | Kimin hangi veriyi hangi amaçla tuttuğu, depolama/backup sınırları |
| D03 | Güvenlik/gizlilik/ebeveyn ayarı, engel/izin listesi | Cihaz ↔ NextDNS API | Kullanıcı talepli profil yönetimi; NextDNS sunucudaki sonuçları uygular | API mutation yetkisi, profil hedefi, hata/rollback, veri işleme rolü |
| D04 | DNS sorgu günlükleri, alan adı, zaman, IP/cihaz bilgisi | NextDNS API/SSE → cihaz ekranı/belleği | NextDNS remote loglama ayrı; istemcinin erişim/görüntüleme işlemleri ayrıca sınıflandırılır | Aynı ağdaki üçüncü kişilerin bilgileri, saklama süresi, yetkili erişim |
| D05 | DNS analitiği, cihaz/IP/coğrafi veriler | NextDNS analytics → cihaz | Sunucu istatistiği ile yerel sunum ayrıdır | Yalnız gerekli alanların alınması, bellek/cache kapsamı |
| D06 | Bildirim ayarı, profil digest, zamanlar | Cihaz DataStore → WorkManager → NextDNS kontrol çağrısı | Uygulama içi tercih/scheduler işlemi | Logout sonrası silme; Terms öncesi ağ sorgularının durdurulması; backup |
| D07 | Günlük dışa aktarma dosyası | NextDNS API → NextDNS'in verdiği HTTPS indirme URL'si → kullanıcı seçtiği konum | Remote export verisi ile cihazdaki dosya yönetimi ayrıdır | Host/redirect zinciri, API key başlığı, kullanıcının dış depolama riski |
| D08 | Profil silme ve günlükleri temizleme | Kullanıcı → cihaz → NextDNS mutasyon API'si | İstemci API çağrısı; NextDNS remote veri silme işlemini gerçekleştirir | Yanlış hedefe istek ve geri alınamazlık, sonuç kanıtı, kullanıcının yetkisi |
| D09 | Dil, tema, sözleşme kabul sürümü | Cihazda uygulama tercihi | Öncelikle yerel uygulama işlevi | Onayın kaydının asgariye indirilmesi, anonim kalması, sürüm değişikliği |
| D10 | Reklam, Firebase, analitik, crash SDK | İlk sürüm için planlanmadı; doğrudan dependency listesinde yok | Varsayılan olarak var kabul edilmez | Nihai releaseRuntimeClasspath / AAB ve ağ incelemesi |
| D11 | Açılan NextDNS internet sayfaları | Haricî Android tarayıcısına Intent.ACTION_VIEW | Kullanıcının tarayıcı üzerinden ayrı NextDNS ilişkisi olabilir | URL parametrelerinde anahtar bulunmaması |
| D12 | Kullanıcı tarafından gönderilen destek bilgisi | Gelecekteki e-posta/issue kanalı | Geliştiricinin destek başvurusu süreçleri ayrı incelenir | Gerçek iletişim kanalı, kimlik, log redaksiyonu ve saklama politikası |

**Sonuç:** Buradaki bir satırın NextDNS sunucusuna işaret etmesi, o satır için geliştiricinin kesinlikle veri sorumlusu olmadığına ilişkin nihai hukuk görüşü değildir. Tam tersi, geliştiricinin NextDNS'in sunucu günlüklerinden sorumlu olduğu sonucunu da doğurmaz.

## 4. KVKK m.9 — Türkiye'den yurt dışına aktarım değerlendirmesi

KVKK m.9 rejimi 1 Haziran 2024'te değişti: (1) yeterlilik, (2) uygun güvenceler, (3) uygun güvenceler yoksa belirli arızi istisnalar. 8 Ekim 2026 tarihinde Kurumun resmi sitesinde yeterli koruma bulunan ülkeler hakkında **henüz belirleme yapılmadığı** yazıyor. Düzenli/sürekli API işlemleri için arızi aktarım istisnaları otomatik kalıcı dayanak yapılamaz.

**Senaryo A — Kullanıcı NextDNS ile kendi hizmet ilişkisine dayanarak doğrudan iletişim kuruyor:** Aktarımın hukuki mahiyeti, uygulamanın kullanıcı talimatına tabi araç niteliği, işlem amaçları ve ayrı tarafların rolü üzerinden incelenmeli. Geliştiricinin otomatik olarak yurt dışına veri aktaran taraf olduğu söylenmemeli.

**Senaryo B — Geliştirici veriyi kendi amaçları için topluyor veya aktarımı bağımsız belirliyor:** İlgili veri kategorileri için veri sorumluluğu ve veri aktarım mekanizması gerekliliği doğabilir. İlk sürümde reklam, geliştirici backend'i veya telemetri eklememe kararı bu riski azaltır ancak hukuki ön analizin yerine geçmez.

**Senaryo C — NextDNS API'si başka kişilere ait DNS günlüklerini de döndürüyor:** Veri içeriğinin yalnız kullanıcının kendi bilgileri olduğu varsayılmaz. Özellikle yönetilen ev/iş ağları ve çocuklarla ilgili kayıtların sonuçları ayrı incelenir.

**Yayımdan önce açıkça belirlenecekler:** NextDNS'in hukuken hangi ülkedeki hangi tüzel kişiliği; API ve log indirme hizmetlerinin gerçek host/işleme ülkeleri; her kişisel veri kategorisi ve aktaran taraf; uygulanabilir işleme şartı; gerekiyorsa uygun güvence; şikâyet ve ilgili kişi başvuru adresi. Geliştirici adına varsayımsal standart sözleşme imzalanmaz.

## 5. GDPR: AB kullanıcıları ve uluslararası aktarım

EDPB Guidelines 07/2020 (controller vs processor) ile Guidelines 05/2021 final (24 Şubat 2023) birlikte okunmalı. İkinci rehber, kişiden üçüncü ülke hizmet sağlayıcısına **doğrudan veri toplama** örneği ile Avrupa Ekonomik Alanı platformunun veri toplayıp üçüncü ülke sağlayıcısına **yeniden aktarmasını** ayrı ele alıyor. Bu örnekler, NextDNS hesabının kullanıcı tarafından doğrudan yönetilmesi senaryosunun incelenmesine yardımcıdır, fakat Android istemci açısından birebir bağlayıcı sınıflandırma değildir.

AB pazarında Almanca, Fransızca ve İspanyolca kullanıcılar hedeflendiği için GDPR'ın hangi işleme ve aktörler bakımından uygulandığı (m.3), m.5/6 işleme şartları, m.13/14 aydınlatma, m.26/28 görevler ve m.44–49 aktarım rejimi ülke/işlem bazında uzman incelemesine açık kalır.

**Tasarım tavsiyesi:** Mevcut NextDNS hesabına doğrudan bağlanan, geliştiricinin kullanıcı API anahtarlarını/loglarını almayan, reklam ve telemetri içermeyen v1 mimarisini koru. Ancak kullanıcıya veya Play Console'a kategorik biçimde 'GDPR uygulanmaz' deme.

## 6. Google Play Data Safety ile hukuki rolün karıştırılmaması

Google Play'in form tanımında **collection**, cihazdan uygulama aracılığıyla veri çıkarılmasıdır; geliştiricinin sunucusu olmasa ve NextDNS'e gönderilse dahi inceleme gerekir. **Sharing** ayrı tanımlıdır; kullanıcı tarafından başlatılan ve beklenen aktarım veya gerçek bir hizmet sağlayıcı ilişkisi gibi belirli istisnalar ayrıca değerlendirilir.

Dolayısıyla Play tablosu (API key, hesap bilgileri, profil, IP/cihaz, domain/log, export, optional notifications, SDK) en güncel release sürümünün fiilî trafiğine göre doldurulmalı. Play kategorisi ile KVKK/GDPR veri sorumlusu kavramı eşanlamlı değildir.

## 7. Türkiye'de sunulacak açıklamanın özellikleri

Kullanım Koşulları tr/en/de/fr/es akademik-hukuki üsluptadır. Gizlilik Politikası mevcut karara göre İngilizce; Türkiye'ye özgü ve gerçekten geliştiricinin veri sorumluluğu kapsamındaki faaliyetler için uygun Türkçe KVKK Aydınlatma Metni gerekliliği ayrıca doğrulanır.

KVKK'nın 18 Şubat 2026 tarihli 2026/347 sayılı kararı: aydınlatma ve açık rıza ayrı; açık rıza gerekmeyen işlemler için gereksiz genel rıza talep edilmemeli; aydınlatma metni ilgili organizasyonun kendi faaliyetlerine uygun, açık ve anlaşılır olmalıdır. Terms onayı, verinin her türlü işlenmesine onay anlamına gelmez.

Mevcut hazırlık: docs/legal/KVKK_AYDINLATMA_TR_TASLAK.md; gerçek geliştirici kimliği, hukuki işlem şartları ve ülke/alıcılara ilişkin alanlar bilinçli olarak açık bırakılmıştır.

## 8. Teknik ve hukuki yayın kapıları

- [ ] NextDNS'in uygulanan hizmet şartları, servis sağlayıcı kimliği, API/marka yetkileri doğrulansın.
- [ ] D01–D12 için veri sorumluluğu, veri kategorileri, KVKK/GDPR somut işleme şartları, varsa aktarım mekanizması bir hukuk uzmanı tarafından faaliyete özgü doldurulsun.
- [ ] İmzalı son AAB üzerinde HTTPS host ve redirect zinciri, resolved SDK'lar ve merged manifest kontrol edilsin.
- [ ] Otomatik açılış katalog/diagnostic ve kayıtlı API key login ile NotificationWorkScheduler, gerekli ilk Terms kabulünden önce engellensin.
- [ ] Logout API key+profil önbelleği+ayrı notification_settings DataStore ve WorkManager durumunu tam temizlesin; eski hesap in-flight cevapları ekranı kirletmesin.
- [ ] DNS logları başka kişilere ait bilgiler içerebildiğinden debug, export ve ekran korumaları ölçülsün.
- [ ] Play Data Safety gerçek veri türleriyle; Privacy Policy ve Türkçe KVKK bildirimi aynı işleyişle uyumlu olsun.
- [ ] Google Play / BrowserStack test hesabı kullanılsın; gerçek API anahtarı ve kişisel DNS logları kamuya açık issue veya artefakta girilmesin.
- [ ] Nihai doğrulanmış yayıncı adı, destek/başvuru adresi, tarih ve dağıtım ülkeleri girilsin.

## 9. Resmî kaynaklar

- KVKK veri sorumlusu: https://www.kvkk.gov.tr/Icerik/2032/Veri-Sorumlusu-Kimdir
- KVKK yurt dışına aktarım (1 Haziran 2024 sonrası): https://www.kvkk.gov.tr/Icerik/2053/Yurtdisina-Aktarim
- KVKK aydınlatma ve açık rıza 2026/347: https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu
- EDPB 07/2020 controller and processor: https://www.edpb.europa.eu/documents/guideline/guidelines-072020-on-the-concepts-of-controller-and-processor-in-the-gdpr_en
- EDPB 05/2021 international transfers final 2023: https://www.edpb.europa.eu/documents/guideline/guidelines-052021-on-the-interplay-between-the-application-of-article-3-and-the_en
- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- NextDNS API: https://nextdns.github.io/api/
- NextDNS Privacy: https://nextdns.io/privacy

## 9 Ekim 2026 — kaynak davranışı güncellemesi

Faz 9'un ilk teknik dilimi ana dala alınmıştır. Yukarıdaki 8 Ekim tespitlerinden “kabul ekranı henüz yok”, “kabul şartına bağlanmadı” ve “logout bildirim deposunun tamamını temizlemiyor” ifadeleri tarihsel başlangıç durumunu anlatır. Güncel kaynakta ilk kullanım için açık Terms kabul kapısı, repository/scheduler erişim kontrolü ve hesap kapsamlı bildirim deposu temizliği bulunmaktadır. Ana dalın 17eb30e sürümündeki CI 37851315911 geçmiştir; son imzalı paket ve gerçek cihaz veri akışı kanıtı ayrı aşamadır.

Güncel mevzuat, yargısal emsaller ve akademik araştırmalar [9 Ekim araştırma dosyasında](LITERATURE_REVIEW_TR_EU_2026-10-09_DRAFT.md) değerlendirilmiştir. Geliştirici sıfatı veya aktarım hukuki sebebi, yalnızca bu teknik güncellemeyle kesinleştirilmemiştir.

## 21 Ekim 2026 — aday kaynak akışı güncellemesi

Bu dosyanın 8 Ekim kaynak envanteri tarihsel bir başlangıçtır. PR #27 adayı `link-ip.nextdns.io` doğrudan isteğini ve belgelenmemiş `GET /profiles/{profileId}/setup` ile `POST /profiles/{profileId}/setup/linkedIp` API deklarasyonlarını kaldırır. Linked-IP yönetimi resmi NextDNS kontrol panelini tarayıcıda açar; istemci bağlı IP değerini API veya tanı sonucundan okumaz. Bu not kaynak değişikliğini açıklar; imzalı paket ağı, gerçek servis hesabı, yayıncı kimliği ve hukuki inceleme sonucu hakkında kanıt oluşturmaz. Hukuki belgelerin DRAFT statüsü korunur.
