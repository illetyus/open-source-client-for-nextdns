# Open Source Client for NextDNS

**DRAFT · 19 Kasım 2026 tarihli Türkçe inceleme nüshası**

## I. Kullanım Koşulları

**Yayıncı** — [Ad veya unvan]\
**İletişim** — [İletişim bilgisi]\
**Yürürlük tarihi** — [Tarih]

### Madde 1 — Konu ve kapsam

Bu Kullanım Koşulları, tarafımızca geliştirilen ve yayımlanan Open Source Client for NextDNS adlı Android uygulamasının kullanımını düzenler. Uygulama, NextDNS profillerinin yönetilmesi ve NextDNS hizmetindeki sorgu günlükleri ile analiz sonuçlarının görüntülenmesi için geliştirilmiş bağımsız bir istemcidir.

### Madde 2 — NextDNS ile hizmet ilişkisi

Hesap ve abonelik işlemleri ile DNS hizmeti NextDNS tarafından yürütülür. Yayıncı ile NextDNS arasında resmî bir ortaklık veya temsil ilişkisi bulunmaz. Uygulama, profil işlemlerinde kimlik doğrulaması için NextDNS API anahtarını kullanır. Profil ve ayar değişikliği taleplerini, ilgili profilde uygulanmak üzere NextDNS’e iletir.

### Madde 3 — Açık kaynak lisansı

Uygulamanın kaynak kodu Apache License, Version 2.0 hükümleri uyarınca sunulur.

### Madde 4 — Gizlilik

Uygulamanın cihazda sakladığı veya kullandığı bilgiler ve harici hizmetlere yaptığı veri iletimleri, Gizlilik Politikasında açıklanır.

## II. Gizlilik Politikası

**Yayıncı** — [Ad veya unvan]\
**Gizlilik iletişimi** — [İletişim bilgisi]\
**Metin tarihi** — [Tarih]

### 1. Politikanın kapsamı

Yayıncı, uygulama aracılığıyla kullanıcı verisi toplamaz. Bu Politikanın kapsamı, uygulamanın cihaz üzerinde kullandığı bilgiler, cihazdan doğrudan NextDNS’e iletilen bilgiler ve dağıtım veya güvenlik sağlayıcılarının kendi hizmetlerine ilişkin veri işlemleridir.

### 2. Cihazda saklanan ve kullanılan bilgiler

Uygulama, NextDNS API anahtarını Android’in güvenli anahtar deposu aracılığıyla şifreleyerek cihazda saklar. Dil ve görünüm tercihleri cihazda tutulur. NextDNS’ten alınan sorgu günlükleri, görüntüleme amacıyla uygulamanın çalışma belleğinde tutulur.

### 3. NextDNS’e yapılan doğrudan istekler

**Profil ve ayar işlemleri.** Uygulama, kimlik doğrulamak için API anahtarını cihazdan doğrudan NextDNS’e iletir. Talep edilen işleme göre isteğe profil kimliği, cihaz filtresi veya kullanıcının girdiği kural da eklenebilir.

**Ağ tanısı ve bildirimler.** NextDNS, ağ tanısı sırasında bağlantının kaynak IP adresini görebilir. Tanı isteği belirli bir profile ilişkinse profil kimliğini de içerebilir. Kullanıcı bildirimleri etkinleştirdiğinde uygulama, bildirimlere ilişkin güncellemeleri denetlemek amacıyla arka planda NextDNS’e bağlanabilir.

**Site simgeleri.** Uygulama, günlük ve analiz ekranları dâhil bazı ekranlarda görüntülenen alan adını, site simgesini almak amacıyla `favicons.nextdns.io` adresine otomatik olarak iletebilir. Simge hizmeti, alan adını ve bağlantının kaynak IP adresini görebilir. Bu isteğe API anahtarı eklenmez.

NextDNS hizmetinde tutulan sorgu günlükleri ve bunlara ilişkin saklama tercihleri, kullanıcının NextDNS hesabı üzerinden yönetilir. [NextDNS Gizlilik Politikası](https://nextdns.io/privacy)

### 4. Dışa aktarım ve tarayıcı bağlantıları

Kullanıcı sorgu günlüklerini dışa aktarmayı seçtiğinde uygulama, CSV dosyasını Android dosya seçicisiyle belirlenen konuma kaydeder. Seçilen konum bir bulut depolama hizmetine aitse dosya, ilgili sağlayıcının sisteminde saklanabilir.

Uygulama, kullanıcının seçtiği NextDNS hesabı ve yönetim paneli bağlantılarını harici tarayıcıda açar.

### 5. Dağıtım platformları ve Google hizmetleri

Dağıtım platformu, uygulamanın indirilmesine ilişkin bilgileri kendi gizlilik politikası uyarınca işleyebilir.

**Google Play dağıtımı.** Uygulamanın Google Play üzerinden dağıtılması hâlinde Google, indirme, kurulum ve kaldırma bilgileri ile cihaz ve mağaza etkinliği bilgilerini kendi hizmeti kapsamında işleyebilir. Kullanıcı tanılama verilerinin paylaşılmasına izin vermişse Google, Google Play’den yüklenen uygulamalardaki çökme ve yanıt vermeme olaylarını Android vitals kapsamında değerlendirebilir. [Google Play geçmişi ve kişiselleştirme](https://support.google.com/googleplay/answer/16693200?co=GENIE.Platform%3DAndroid&hl=tr) · [Android vitals](https://developer.android.com/google/play/vitals)

**Google Play Protect.** Google, bu hizmetin bulunduğu cihazlarda Google Play dışından yüklenen uygulamaları da güvenlik amacıyla denetleyebilir. Play Protect, tanımadığı bir uygulama paketinin inceleme için Google’a gönderilmesini isteyebilir. İlgili ayar etkinse paket otomatik olarak gönderilebilir. [Google Play Protect](https://support.google.com/googleplay/answer/2812853?hl=tr) · [Google Gizlilik Politikası](https://policies.google.com/privacy?hl=tr)

### 6. Oturum kapatma ve saklanan kayıtlar

Oturum kapatma işlemi, cihazdaki API anahtarını ve ilgili yerel oturum bilgilerini siler. Dil ve görünüm gibi genel tercihler cihazda kalabilir. Kullanıcı, NextDNS hizmetindeki sorgu günlüklerini NextDNS hesabından ve dışa aktardığı dosyaları kaydedildikleri konumdan yönetebilir.
