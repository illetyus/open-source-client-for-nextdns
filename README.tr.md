# Open Source Client for NextDNS

NextDNS profillerini Android’de yönetmek için bağımsız, açık kaynaklı bir uygulama. Filtreleme kurallarınızı düzenleyebilir, bir sitenin neden engellendiğini inceleyebilir ve telefonunuzun NextDNS kullanıp kullanmadığını denetleyebilirsiniz.

[**Android beta sürümünü indir**][apk] · [Sürüm notları ve doğrulama dosyaları][release] · [English](README.md)

Android 7.0 veya üzeri gerekir. Kendi profillerinizi yönetmek için bir NextDNS hesabı gerekir.

## Uygulamaya bir bakış

<p>
  <a href="docs/assets/readme/setup.png"><img src="docs/assets/readme/setup.png" width="30%" alt="Bağlantı durumunu ve ağ tanısı seçeneğini gösteren Kurulum ekranı"></a>
  <a href="docs/assets/readme/logs.png"><img src="docs/assets/readme/logs.png" width="30%" alt="tracker.example alan adının bir engelleme listesi nedeniyle engellendiğini gösteren Günlükler ekranı"></a>
  <a href="docs/assets/readme/analytics.png"><img src="docs/assets/readme/analytics.png" width="30%" alt="Toplam ve engellenen sorgu sayılarını ve en sık sorgulanan alan adlarını gösteren Analizler ekranı"></a>
</p>

Görüntülerde İngilizce arayüz ve örnek veriler kullanılmıştır. Tam boyutta açmak için bir görsel seçin.

- **Kurulum.** Cihazınızın NextDNS kullanıp kullanmadığını ağ tanısıyla denetleyin. Tanı verileri mevcutsa bağlantı protokolünü ve gecikme süresini de görebilirsiniz.
- **Günlükler.** Bir alan adını arayarak NextDNS’in sorguyu engelleyip engellemediğini ve varsa engelleme nedenini görün. Sonuçları cihaza veya zaman aralığına göre süzebilir ve CSV dosyası olarak dışa aktarabilirsiniz.
- **Analizler.** NextDNS’in kaç sorguyu yanıtladığını, bunların ne kadarını engellediğini ve en sık hangi alan adlarının sorgulandığını inceleyin.

Seçili profilin filtreleme kurallarını **Güvenlik**, **Gizlilik** ve **Ebeveyn Kontrolü** ekranlarından düzenleyebilirsiniz. Belirli alan adlarına izin vermek veya bunları engellemek için **Beyaz Liste** ve **Kara Liste** ekranlarını kullanın.

## İlk kurulum

1. Android cihazınıza [beta APK dosyasını][apk] indirip açın. Android, tarayıcınızın veya dosya yöneticinizin uygulama yüklemesine izin vermenizi isterse kurulum için izin verin.
2. Hesap bağlamadan arayüzü incelemek için **Demo / Misafir Modu ile Keşfet** seçeneğini kullanın. Arayüz Türkçe, İngilizce, Almanca, Fransızca ve İspanyolca kullanılabilir.
3. Kendi profillerinizi yönetmek için [NextDNS hesap sayfanızın][account] altındaki API anahtarını kopyalayın, uygulamaya girin ve bir profil seçin. Bu anahtar uygulamaya NextDNS hesabınıza erişim sağlar.

Telefonunuzun DNS sorgularını NextDNS üzerinden göndermek için ayrıca **Kurulum** ekranındaki adımları izleyin. Android 9 ve üzeri sürümlerde **Android Özel DNS Ayarlarını Aç** düğmesi, profil adresini gireceğiniz sistem ayarını açar.

## Uygulama NextDNS’e nasıl bağlanır?

DNS hizmetini ve hesapları NextDNS sağlar. Uygulama NextDNS’e doğrudan bağlanır ve API anahtarını cihazda şifreli olarak saklar. Site simgesi yüklenirken ilgili alan adı NextDNS’in simge hizmetine gönderilebilir.

## Destek ve kaynak kod

- [Hata bildirin][issues]. Uygulama ve Android sürümünü, ayrıca hatayı yeniden görmek için izlenecek adımları yazın. Herkese açık bildirimlerde API anahtarı, profil kimliği veya DNS günlüğü paylaşmayın.
- [Güvenlik açığı bildirin](SECURITY.md).
- [Uygulamayı kaynak koddan derleyin ve test edin](docs/README.md#build-and-verify) veya [katkı kılavuzunu](CONTRIBUTING.md) okuyun.

Kaynak kod [Apache License, Version 2.0](LICENSE) ile sunulur. Üçüncü taraf kaynaklarına ilişkin atıflar [NOTICE](NOTICE) dosyasında yer alır.

[apk]: https://github.com/illetyus/open-source-client-for-nextdns/releases/download/v1.0.0-beta.1/open-source-client-for-nextdns-v1.0.0-beta.1.apk
[release]: https://github.com/illetyus/open-source-client-for-nextdns/releases/tag/v1.0.0-beta.1
[account]: https://my.nextdns.io/account
[issues]: https://github.com/illetyus/open-source-client-for-nextdns/issues
