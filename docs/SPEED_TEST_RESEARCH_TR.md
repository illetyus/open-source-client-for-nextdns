# Hız testi araştırması ve uygulama kararı

**İnceleme tarihi:** 20 Ekim 2026  
**Kapsam:** NextDNS API'si dışında çalışan, Android'de kullanıcı tarafından başlatılan bağlantı ölçümü.  
**Tarihsel araştırma durumu:** Bu özellik önceki bir PR taslağında kodlanmıştı; bu araştırma kaydı tarihsel tasarımı ve dış kaynakları saklar.

**Güncel sürüm kararı — 21 Ekim 2026:** Hız testi bu sürüm için kullanıcı kararıyla iptal edildi. İlgili ekran, yürütülebilir istemci ve ürün testleri kaynak ağacından kaldırıldı; daha önce kaydedilmiş araştırma ve hata geçmişi yalnız tarihsel bağlam olarak tutuluyor. Bu belge uygulamada hâlen bir hız testi bulunduğunu veya sunucuya istek yapıldığını göstermez.

## Kaynak taraması

NextDNS'in yayımlanmış API'si profil verileri, analitik ve günlükleri kapsar. Hız testi bu API'nin işlevi veya profille eşitlenecek bir ayar değildir. Uygulamanın ölçüm trafiği yalnızca kullanıcı tarafından girilen ayrı sunucuya gider; API anahtarı, profil kimliği ve NextDNS repository katmanı bu akışa verilmez. Kaynak: [NextDNS API belgeleri](https://nextdns.github.io/api/).

LibreSpeed'in Android deposu hazır bir uygulama şablonudur ve LGPL-3.0 lisanslıdır. Şablon kaynak kodu uygulamaya kopyalanmadı, bağımlılık olarak eklenmedi ve kendi varsayılan sunucuları kullanılmadı. Bunun yerine küçük bir yerel OkHttp istemcisi, [LibreSpeed sunucu belgesinde](https://github.com/librespeed/speedtest/blob/master/doc.md) açıklanan `empty.php` ve `garbage.php` HTTP davranışıyla uyumlu olacak şekilde yazıldı. Sunucu kodundaki [garbage.php uygulaması](https://raw.githubusercontent.com/librespeed/speedtest/master/backend/garbage.php), `ckSize` parametresini MiB büyüklüğündeki 1.048.576 baytlık veri parçalarına uygular; bilinmeyen değer için 4 parça döndürür ve üst sınırı 1.024 parçadır. İstemci, 10.000.000 baytlık yerel sınırın altında kalmak için en fazla 9 MiB ister ve yanıt gövdesini ayrıca bayt bazında sınırlar. Sunucu sözleşmesi ayrı sürümlenmediğinden gerçek sunucuyla uyumluluk ayrıca doğrulanmalıdır.

[LibreSpeed Android şablonu](https://github.com/librespeed/speedtest-android) kullanıcıya mevcut LibreSpeed sunucusu sağlamayı şart koşar; test sonuçları/telemetry ve IP/ISP bulma gibi işlevleri isteğe bağlı sunar. Bu istemci bunları çağırmaz. MIT lisanslı JSpeedTest alternatifi kaynak olarak incelendi; örnek uçları eski HTTP/FTP yapılandırması kullandığı ve LibreSpeed backend uyumluluğu doğrulanmadığı için seçilmedi.

[M-Lab'ın 15 Temmuz 2025 tarihli AUP'si](https://www.measurementlab.net/aup/) üçüncü taraf istemcilerden M-Lab gizlilik politikasıyla tutarlı, yayımlanmış politika ve veri sahibi haklarının açıklanmasını ister. [M-Lab test açıklaması](https://www.measurementlab.net/tests/) test verilerinin, bağlantı IP adresleri dahil, herkese açık ve süresiz tutulduğunu; tekil test verisinin sonradan silinemediğini belirtir. Projenin hukuk metinleri hâlâ DRAFT olduğu ve bu paylaşım davranışı ürün kararı olarak seçilmediği için M-Lab veya başka bir ortak kamu sunucusu varsayılan yapılmadı.

## Uygulama sınırları

- Sunucu adresi ve HTTPS gizlilik politikası bağlantısı kullanıcı tarafından girilir. Ayar yalnızca uygulamaya özel yerel tercih deposunda tutulur; NextDNS eşitleme koduna dahil değildir.
- İzin onayı her açılışta ve her test oturumunda yeniden verilir. Ücretli/kamuya açık bir hedef sunucu seçilmez.
- İndirme ve üç HTTP gecikme isteği yalnızca kullanıcı “Test et” düğmesine bastıktan sonra başlar. “Gecikme” HTTP gidiş-dönüş süresidir; ICMP ping iddiası yapılmaz.
- İndirme isteği en fazla 9 MiB sunucu verisi ister; okuyucu 10.000.000 baytı aşmadan durur. Yükleme en fazla 1.000.000 bayt varsayılan veri gönderir, ayrı onay ister ve mobil veri onayıyla da korunur.
- HTTPS sertifika doğrulaması normal Android/OkHttp doğrulamasına bırakılır. URL kullanıcı bilgisi, sorgu ve parça kabul etmez. Göreli backend yolu traversal karakterlerini reddeder. Yönlendirme, çerez, otomatik tekrar, IP/ISP sorgusu, telemetry ve sonuç kaydı kapalıdır.
- Uygulama kendi test sonuçlarını kaydetmez. Sunucu işletmecisinin IP/istek üstverisi günlükleri ve saklama süresi kendi yapılandırmasına bağlıdır; kullanıcıdan sunucunun politikasını incelemesi istenir.
- Gerçek HTTPS sunucusu, kullanılmasına dair yetki ve kapasite doğrulanmadan cihaz testi tamamlanmış sayılmaz. Bu PR, herhangi bir sunucunun izin verdiğini veya performansını kanıtlamaz.

## Doğrulama ve kalan kapılar

MockWebServer testleri endpoint üretimi, HTTPS zorunluluğu, URL ve yol doğrulaması, yönlendirme engeli, ping sayısı, download akışı ve byte sınırı, upload boyutu, istek başlıkları ve hız hesabını denetler. Compose testi boş varsayılan ayarı, gizlilik açıklamasını ve yapılandırma olmadan devre dışı eylemleri denetler. Testin çalıştığı CI sonucu bu PR'da eklenecektir; yerel Gradle wrapper indirmesi ağ/DNS kısıtlaması nedeniyle doğrulanamadı.

Yayın öncesinde bir operatörün izin verdiği HTTPS LibreSpeed sunucusunda TLS, gerçek byte aktarımı, iptal, veri kullanımı ve server-side log davranışı denenmelidir. Fiziksel cihazdaki Macrobenchmark/Perfetto ve BrowserStack koşuları da bu sunucu doğrulamasından bağımsız gereksinimlerdir. Hukuki belgeler DRAFT kalır; bu teknik karar hukuki uygunluk garantisi vermez.

## Referanslar

- [NextDNS API Documentation](https://nextdns.github.io/api/)
- [LibreSpeed server documentation](https://github.com/librespeed/speedtest/blob/master/doc.md)
- [LibreSpeed `garbage.php` source](https://raw.githubusercontent.com/librespeed/speedtest/master/backend/garbage.php)
- [LibreSpeed Android template and LGPL-3.0 license](https://github.com/librespeed/speedtest-android)
- [M-Lab Acceptable Use Policy](https://www.measurementlab.net/aup/)
- [M-Lab tests and data handling](https://www.measurementlab.net/tests/)
- [JSpeedTest source and MIT license](https://github.com/bertrandmartel/speed-test-lib)
- [AndroidX Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing)
- [AndroidX Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
