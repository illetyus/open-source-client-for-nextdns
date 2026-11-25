<p align="center">
  <img src="docs/brand/store-icon-512.png" width="80" height="80" alt="">
</p>

# Open Source Client for NextDNS

NextDNS profillerini yönetmek için bağımsız bir Android uygulaması. Filtreleme kurallarınızı değiştirebilir, bir alan adının neden engellendiğini inceleyebilir ve telefonunuzun NextDNS bağlantısını denetleyebilirsiniz.

[**Beta APK’sını indir**][apk] · [Sürüm notları ve doğrulama dosyaları][release] · [English](README.md)

Android 7.0 veya üzeri gerekir. Kendi profillerinizi bağlamak için bir NextDNS hesabı kullanın.

## İlk kurulum

1. Android cihazınızda [beta APK dosyasını][apk] indirip açın. Android, tarayıcınızın veya dosya yöneticinizin uygulama yüklemesine izin vermenizi isterse kurulum için izin verin.
2. Hesap bağlamadan arayüzü incelemek için **Demo / Misafir Modu ile Keşfet** seçeneğini kullanın.
3. Kendi profillerinizi yönetmek için [NextDNS hesap sayfanızın](https://my.nextdns.io/account) altındaki API anahtarını kopyalayın, uygulamaya girin ve bir profil seçin. Bu anahtar uygulamaya NextDNS hesabınıza erişim sağlar.

Hesabınızı bağladığınızda profillerinizi yönetebilirsiniz. Telefonunuzun DNS sorgularını da NextDNS üzerinden göndermek için **Kurulum** ekranındaki adımları izleyin. Android 9 ve üzeri sürümlerde **Android Özel DNS Ayarlarını Aç** düğmesi, profil adresini gireceğiniz sistem ayarını açar.

## Uygulamayı keşfedin

Ekran görüntülerinde örnek veriler kullanılmıştır. Bir görsele tıklayarak tam boyutlu hâlini açabilirsiniz.

### Bir alan adının neden engellendiğini bulun

<a href="docs/assets/readme/logs.png"><img src="docs/assets/readme/logs.png" width="320" alt="Zaman ve durum filtreleri ile tracker.example alan adının engellendiğini gösteren Günlükler ekranı"></a>

Bir site veya uygulama bağlantı kuramadığında **Günlükler** ekranında ilgili alan adını arayın. Sorgunun sonucunu ve varsa engelleme nedenini inceleyin. Aramanızı cihaz veya zaman aralığına göre daraltabilir, günlükleri CSV dosyası olarak dışa aktarabilirsiniz.

### NextDNS bağlantısını denetleyin

<a href="docs/assets/readme/setup.png"><img src="docs/assets/readme/setup.png" width="320" alt="Etkin NextDNS bağlantısını ve ağ tanısı düğmesini gösteren Kurulum ekranı"></a>

**Kurulum** ekranından ağ tanısını çalıştırın. Sonuç, cihazın NextDNS kullanıp kullanmadığını gösterir. Tanı verileri mevcutsa bağlantı protokolü ve gecikme süresi de görüntülenir.

### Sorgu sayılarını inceleyin

<a href="docs/assets/readme/analytics.png"><img src="docs/assets/readme/analytics.png" width="320" alt="Toplam ve engellenen sorgu sayılarını, en sık sorgulanan alan adlarıyla birlikte gösteren Analizler ekranı"></a>

**Analizler** ekranında en sık sorgulanan alan adlarını ve NextDNS’in kaç sorguyu engellediğini görebilirsiniz. Özeti cihaz ve zaman aralığına göre inceleyebilirsiniz.

## Profil ayarları ve veriler

Seçili profilin filtreleme kurallarını **Güvenlik**, **Gizlilik** ve **Ebeveyn Kontrolü** ekranlarından düzenleyin. Belirli alan adlarına izin vermek veya bunları engellemek için **Beyaz Liste** ve **Kara Liste** ekranlarını kullanın. Arayüz Türkçe, İngilizce, Almanca, Fransızca ve İspanyolca kullanılabilir.

DNS hizmetini ve hesapları NextDNS sağlar. Uygulama NextDNS’e doğrudan bağlanır ve API anahtarını cihazda şifreli olarak saklar. Site simgesi yüklenirken ilgili alan adı NextDNS’in simge hizmetine gönderilebilir.

## Destek ve geliştirme

- [Hata bildirin](https://github.com/illetyus/open-source-client-for-nextdns/issues). Uygulama ve Android sürümünü, ayrıca hataya yol açan adımları yazın. Herkese açık bildirimlerde API anahtarı, profil kimliği ve DNS günlüğü paylaşmayın.
- [Güvenlik açığı bildirin](SECURITY.md).
- [Uygulamayı kaynak koddan derleyin ve test edin](docs/README.md#build-and-verify) veya [katkı kılavuzunu](CONTRIBUTING.md) okuyun.

Kaynak kod [Apache License, Version 2.0](LICENSE) ile sunulur. Üçüncü taraf kaynaklarına ilişkin atıflar [NOTICE](NOTICE) dosyasında yer alır.

[apk]: https://github.com/illetyus/open-source-client-for-nextdns/releases/download/v1.0.0-beta.1/open-source-client-for-nextdns-v1.0.0-beta.1.apk
[release]: https://github.com/illetyus/open-source-client-for-nextdns/releases/tag/v1.0.0-beta.1
