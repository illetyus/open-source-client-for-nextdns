# NextDNS API yetenek matrisi

**İnceleme tarihi:** 21 Ekim 2026
**Ürün:** NextDNS hizmet API'siyle konuşan bağımsız, gayriresmî Android istemcisi
**Kapsam:** Yayımlanmış API sözleşmesi ile bu depodaki servis, DTO, repository, arayüz ve testlerin karşılaştırması.

Bu belge uygulamanın NextDNS tarafından onaylandığı veya desteklendiği anlamına gelmez. [Yayımlanmış NextDNS API belgesi](https://nextdns.github.io/api/) API'nin beta olduğunu ve değişikliklerin önceden bildirilmeden yapılabileceğini belirtiyor. Bu nedenle aşağıdaki durumlar depo içindeki mevcut uygulamayı gösterir; hizmetin gelecekteki garantisi değildir.

## Uygulamadaki API yüzeyleri

`SUPPORTED_READ_WRITE` mevcut istemcide okuyup değiştirebilen; `SUPPORTED_READ_ONLY` yalnızca okuyabilen; `PARTIAL` sözleşmenin veya UI akışının yalnızca bir kısmının mevcut olduğu; `MISSING` yayımlanmış yüzey olup istemciye eklenmemiş; `UNVERIFIED` yayımlanmış belgede bulunmayan veya gerçek yanıtla doğrulanmamış özellik demektir.

| Yüzey ve sözleşme | Kod eşlemesi | Durum ve sınır |
|---|---|---|
| Profiller: `GET /profiles`, `GET /profiles/{id}`, `POST /profiles`, `PATCH /profiles/{id}`, `DELETE /profiles/{id}` | `NextDnsApiService`; `NextDnsRepository`; profil seçimi ve yönetim ekranları | `SUPPORTED_READ_WRITE`. Liste sayfaları cursor kullanır; oluşturma, yeniden adlandırma ve silme kullanıcı eylemiyle yapılır. |
| Profil kurulumu: `GET /profiles/{id}/setup`; `POST /profiles/{id}/setup/linkedIp` | `getProfileSetup` repository tarafından çağrılır; `updateLinkedIp` Retrofit'te tanımlı ancak repository/UI çağrısı yok | Okuma `SUPPORTED_READ_ONLY`; linked-IP yazma `PARTIAL`, uygulama akışı bulunmuyor. |
| Güvenlik: `GET/PATCH /profiles/{id}/security`; `/security/tlds` kataloğu ve profil TLD ekleme/silme uçları | Sınırlı Moshi `SecurityDto` adaptörü ve `SecurityUpdateRequest`; `NextDnsRepository`; güvenlik ekranı | Belgelenen alanlar ve TLD'ler `SUPPORTED_READ_WRITE`. Güvenlik yanıtı 512 KiB ile sınırlıdır. Yayımlanmamış en fazla 32 üst düzey alan bellek içi DTO'da yakalanır; en fazla 24 güvenli anahtar adı/tür gösterilir, yalnız Boolean değeri görünür. Diğer ham değerler arayüze, kalıcı depoya veya API isteğine girmez. Taşan alanlar sayı ve SHA-256 özetiyle eşitleme anlık görüntüsüne katılır. Bilinmeyen alanların anlamı/yazılabilirliği ve yaş doğrulama alanı `UNVERIFIED`; hiçbir dinamik PATCH yapılmaz. |
| Gizlilik: `GET/PATCH /profiles/{id}/privacy`; blocklist/native katalog ve profil alt kaynakları | `PrivacyDto`, mutasyon DTO'ları, `NextDnsRepository`, Gizlilik ekranı | `SUPPORTED_READ_WRITE`; kataloglar cursor ile okunur, ekleme ve silme profil alt kaynaklarından yapılır. |
| Ebeveyn denetimi: `/parentalControl/services`, `/parentalControl/categories`; profil için `GET/PATCH /parentalControl`, kategori/hizmet alt kaynakları | `ParentalControlDto`, katalog DTO'ları, `NextDnsRepository`, ekran | `SUPPORTED_READ_WRITE`. `blockBypass` yayımlanmış ebeveyn denetimi alanıdır; yaş doğrulamasını atlama ayarı olarak yorumlanmaz. |
| Engelleme/izin listeleri: `GET/POST /profiles/{id}/denylist|allowlist`; öğe `PATCH/DELETE` | İlgili DTO'lar, `NextDnsRepository`, iki liste ekranı | `SUPPORTED_READ_WRITE`; sayfalar cursor ile çekilir, filtre/eylemler servis öğe kimliğini kullanır. Küçük 12 dp favicon listede yalnızca görsel yardımcıdır; servis verisinin yerini almaz. |
| Profil ayarları: `GET/PATCH /profiles/{id}/settings`, ayrıca belgelenmiş `performance`, `logs`, `blockPage` alt yolları | `SettingsDto` ve güncelleme DTO'ları, `NextDnsRepository`, Servis ayarları ekranı | Kullanılan ayarlar için `SUPPORTED_READ_WRITE`. Dil, tema, bildirim tercihleri ve NextDNS bağlantı bilgisi bu payload'a dahil değildir. |
| Günlükler: `GET /profiles/{id}/logs`, `GET /logs/stream`, `GET /logs/download`, `DELETE /logs` | `NextDnsApiService`, repository, Günlükler ekranı | Sayfalama, `cursor`, API `search`, `device`, `status`, `raw=1` ve SSE birleştirmesi uygulanır; durumlar `default/error/blocked/allowed` olarak korunur. Arama ve cihaz/durum değişimleri cursor'ı sıfırlar; eski yanıtlar reddedilir. İndir/sil repository yolları mevcuttur. Tarih (`from/to`) ve sıralama (`sort`) parametreleri servis imzasında var, ancak ekran filtresi yok (`PARTIAL`). Geçmiş, hesabın NextDNS log saklama ayarları ve API'nin döndürdüğü cursor sayfalarıyla sınırlıdır; istemci daha eski verinin varlığını garanti etmez. |
| Analitik: `status`, `devices`, `domains`, `reasons`, `destinations`, `dnssec`, `encryption`, `protocols`, `ips`, `queryTypes`, `ipVersions` | Aynı adlı Retrofit metotları, DTO'lar, `NextDnsRepository.fetchAnalytics`, Analiz ekranı | Okuma `SUPPORTED_READ_ONLY`. Uygulama `destinations?type=countries` ile etkileşimli yerel dünya haritasını; `destinations?type=gafam` ile altı parçalı grafiği besler. Ortak parametreler `device/from/to/limit/cursor`; `domains` ayrıca `status/root`, `destinations` `type` kullanır. API `__UNIDENTIFIED__` cihaz filtresini tanımlar ve istemci filtrede API ID'sini korur. `;series` uçları `MISSING`. |

## Retrofit endpoint envanteri

Bu liste kod içindeki HTTP metotlarını ve servis-relative yolları aynen kaydeder. CI, listenin `NextDnsApiService` anotasyonlarıyla birebir eşleştiğini ve satır tekrarlarının olmadığını denetler. Son satır `https://test.nextdns.io/` tanı servisine aittir; `api.nextdns.io` profili API'si değildir.

<!-- API_ROUTE_INVENTORY_START -->
| Method | Service-relative route |
|---|---|
| `GET` | `/profiles` |
| `POST` | `/profiles` |
| `GET` | `/profiles/{profileId}` |
| `DELETE` | `/profiles/{profileId}` |
| `PATCH` | `/profiles/{profileId}` |
| `GET` | `/profiles/{profileId}/setup` |
| `POST` | `/profiles/{profileId}/setup/linkedIp` |
| `GET` | `/profiles/{profileId}/security` |
| `GET` | `/security/tlds` |
| `PATCH` | `/profiles/{profileId}/security` |
| `POST` | `/profiles/{profileId}/security/tlds` |
| `DELETE` | `/profiles/{profileId}/security/tlds/{tld}` |
| `GET` | `/profiles/{profileId}/privacy` |
| `GET` | `/profiles/{profileId}/privacy/blocklists` |
| `GET` | `/profiles/{profileId}/privacy/natives` |
| `GET` | `/privacy/blocklists` |
| `GET` | `/privacy/natives` |
| `PATCH` | `/profiles/{profileId}/privacy` |
| `POST` | `/profiles/{profileId}/privacy/blocklists` |
| `DELETE` | `/profiles/{profileId}/privacy/blocklists/{blocklistId}` |
| `POST` | `/profiles/{profileId}/privacy/natives` |
| `DELETE` | `/profiles/{profileId}/privacy/natives/{nativeId}` |
| `GET` | `/parentalControl/services` |
| `GET` | `/parentalControl/categories` |
| `GET` | `/profiles/{profileId}/parentalControl` |
| `PATCH` | `/profiles/{profileId}/parentalControl` |
| `POST` | `/profiles/{profileId}/parentalControl/categories` |
| `PATCH` | `/profiles/{profileId}/parentalControl/categories/{categoryId}` |
| `DELETE` | `/profiles/{profileId}/parentalControl/categories/{categoryId}` |
| `POST` | `/profiles/{profileId}/parentalControl/services` |
| `PATCH` | `/profiles/{profileId}/parentalControl/services/{serviceId}` |
| `DELETE` | `/profiles/{profileId}/parentalControl/services/{serviceId}` |
| `GET` | `/profiles/{profileId}/denylist` |
| `POST` | `/profiles/{profileId}/denylist` |
| `PATCH` | `/profiles/{profileId}/denylist/{domain}` |
| `DELETE` | `/profiles/{profileId}/denylist/{domain}` |
| `GET` | `/profiles/{profileId}/allowlist` |
| `POST` | `/profiles/{profileId}/allowlist` |
| `PATCH` | `/profiles/{profileId}/allowlist/{domain}` |
| `DELETE` | `/profiles/{profileId}/allowlist/{domain}` |
| `GET` | `/profiles/{profileId}/logs/stream` |
| `GET` | `/profiles/{profileId}/logs` |
| `GET` | `/profiles/{profileId}/logs/download` |
| `DELETE` | `/profiles/{profileId}/logs` |
| `GET` | `/profiles/{profileId}/analytics/status` |
| `GET` | `/profiles/{profileId}/analytics/devices` |
| `GET` | `/profiles/{profileId}/analytics/domains` |
| `GET` | `/profiles/{profileId}/analytics/reasons` |
| `GET` | `/profiles/{profileId}/analytics/destinations` |
| `GET` | `/profiles/{profileId}/analytics/dnssec` |
| `GET` | `/profiles/{profileId}/analytics/encryption` |
| `GET` | `/profiles/{profileId}/analytics/protocols` |
| `GET` | `/profiles/{profileId}/analytics/ips` |
| `GET` | `/profiles/{profileId}/analytics/queryTypes` |
| `GET` | `/profiles/{profileId}/analytics/ipVersions` |
| `GET` | `/profiles/{profileId}/settings` |
| `PATCH` | `/profiles/{profileId}/settings` |
| `PATCH` | `/profiles/{profileId}/settings/performance` |
| `PATCH` | `/profiles/{profileId}/settings/logs` |
| `PATCH` | `/profiles/{profileId}/settings/blockPage` |
| `GET` | `/` |
<!-- API_ROUTE_INVENTORY_END -->

## API dışı ve doğrulanmamış veriler

Güvenlik yanıtındaki yayımlanmamış anahtarlar yalnızca keşif ve fark algılama amacıyla korunur. Alan adı/türü gerçek API yanıtından alınır; bu istemci tarafından yeni bir hizmet özelliği olarak yorumlanmaz. Boolean dışı değerler gösterilmez, bilinmeyen alanlar PATCH gövdesine eklenmez ve yerel tercih deposuna yazılmaz. Resmî şema alanın anlamını veya ileriye dönük uyumluluk garantisini açıklamadığından yaş doğrulamasını atlama alanı ayrı olarak doğrulanmamış kalır.

| Veri/özellik | Kaynak ve uygulamadaki davranış | Durum |
|---|---|---|
| Bağlantı ve tanı testi | `https://test.nextdns.io/` ayrı tanı servisi. `server`, `resolver` ve `anycast` yanıt alanları; tanı kaynağı ve yerel kontrol saatiyle gösterilir. Yanıt bölge/şehir alanı vermediğinden arayüz bölge bilgisinin sağlanmadığını belirtir; bu değerlerden şehir/ülke türetilmez. Bu uç `api.nextdns.io` profil API'si değildir. | `SUPPORTED_READ_ONLY`, ayrı protokol; profil eşitleme bölümüne dahil değil. Bölge bilgisi yanıtta yok. |
| Hesap, anahtar ve bağlantı durumu | API anahtarı yerel şifreli tercih deposunda; seçili profil/bağlantı durumu uygulama oturum ve yerel durumudur. Yayımlanmış belgede bir `/account` eşitleme kaynağı yok; repository tam eşitlemesi bu tür bir endpoint istemez. | Yerel uygulama verisi; NextDNS profil ayarı olarak gönderilmez. Hesap uç noktası `UNDOCUMENTED / NOT IMPLEMENTED`. |
| Yaş doğrulama atlama | Yayımlanmış profil güvenlik/ayar şemasında böyle adlandırılmış alan veya okuma-yazma sözleşmesi belgelenmiyor. [NextDNS Help Center başlığında](https://help.nextdns.io/t/q6yp92t/bypass-age-verification-not-visible-in-dashboard) bir kullanıcı özelliğin Ayarlar'da bulunduğunu, NextDNS hesabı ise yanlış dağıtımda kaldırıldığını yanıtlıyor; gönderi API alanı veya PATCH sözleşmesi vermiyor. `parentalControl.blockBypass` ayrı bir özelliktir. Sanitize edilmiş gerçek API yanıtı kanıtı bulunmuyor. | Hizmet arayüzünde varlığına dair birinci taraf topluluk yanıtı var; API eşlemesi `UNVERIFIED / NOT IMPLEMENTED`. Alan adı uydurulmaz, API isteği ve senkronizasyon payload'ı eklenmez. |
| Erken erişim/belgelenmemiş güvenlik alanları | DTO yalnızca belgelenmiş alanları tanımlar; kullanıcı tarafından yetkilendirilmiş sanitize gerçek yanıt veya PATCH/read-back kanıtı yok. | `UNVERIFIED / NOT IMPLEMENTED`; alan keşfi ya da yazma iddiası yok. |
| Trafik haritası geometrisi | Uygulamayla birlikte gelen Natural Earth dünya ülke geometrisi ve lisans bildirimi; sorgu/sayı/ülke verisi NextDNS analitiğinden gelir. Harita çevrimdışı da çizilebilir. | Yerel statik varlık + `destinations?type=countries` okuması. |
| Alan adı favicon'ları | `favicons.nextdns.io` için gözlenmiş HTTPS yolu; resmi API şemasında yok. 20 Ekim 2026 salt GET sentetik probe'unda `github.com` ve `google.com` 200 `image/png`, `example.com` 404 döndürdü; 404, bir alan adında favicon bulunmadığında normal fallback yoludur. İzin/engel listeleri ile Native Tracking ve ebeveyn denetimi hizmet kataloglarında gösterilen domainler istenir; günlük satırlarında yerel ikon kullanılır. Kimlik bilgisi eklenmez; bellek önbelleği, boyut sınırı ve toplam istek süresi sınırı ve coroutine iptali OkHttp çağrısını kapatır. | En iyi çaba/garantisiz; örnek alan adları doğrulandı ama hizmet API/SLA'sı veya her domain için ikon garantisi yok. Disk önbelleği yok. |
| Bağlantı hızı ölçümü | NextDNS profil API'sinin parçası değildir. Yerel araç, kullanıcının kendi/izinli HTTPS LibreSpeed uyumlu sunucu URL'sini ve sunucu gizlilik politikası bağlantısını ister; varsayılan kamu sunucusu yoktur. Üç HTTP isteğinin ortalama gidiş-dönüş süresi “HTTP gecikmesi” olarak adlandırılır; ICMP ping olarak sunulmaz. İndirme istek boyutu upstream `garbage.php` sözleşmesine uygun `ckSize` MiB parametresiyle kurulur ve gövde 10.000.000 baytta kesilir. 1.000.000 baytlık sentetik yükleme ayrı kullanıcı onayı ister. IP/ISP sorgusu, telemetry, yönlendirme, çerez, yeniden deneme ve sonuç kalıcılığı kullanılmaz. | Yerel uygulama aracı; NextDNS API'sine gönderilmez ve profil eşitlemesine katılmaz. Kod/mock sunucu sözleşmesi incelenebilir; gerçek HTTPS sunucusu, işletmeci izni, kapasite ve politika kullanıcı tarafından yapılandırılmadan doğrulanamaz. Yayın kapısı açık. |

## Günlük parametreleri ve cursor davranışı

NextDNS belgesi günlükler için `limit` (10–1000), `cursor`, `device`, `status` (`default`, `error`, `blocked`, `allowed`), `search`, `from`, `to`, `sort` ve `raw` tanımlar. `raw=1`, varsayılan deduplikasyon/gürültü süzmesi yerine tüm DNS sorgularını döndürür. `logs/stream`, `from`, `to`, `sort`, `limit` ve `cursor` hariç aynı parametreleri destekler; son stream kimliği sayfa yanıtındaki `meta.stream.id` ile birleştirilebilir. Uygulama durum ve cihaz filtresi değişince cursor'ı sıfırlar ve önceki sorgu yanıtını geçersiz sayar. Gerçek geçmiş kapsamı profilin NextDNS saklama ayarına bağlıdır.

## Kontrol ve regresyon kanıtı

- `RepositoryRegressionTest.fullSyncDoesNotRequestAnAccountEndpointThatIsNotInThePublicApi` tam eşitlemede `/account` istenmediğini ve sonuçların yalnızca tanımlı profil bölümlerine ait olduğunu denetler.
- `AccountSettingsFlowTest.fullRefreshReadsNextDnsStateWithoutPostingLocalPreferences` yenilemenin yalnızca GET yaptığını; `/account` veya `/connection` çağrısı olmadığını doğrular.
- `NextDnsApiContractTest` belgelenmiş servis yolları ve istek sözleşmeleri için ana regresyon kümesidir.
- Bilinmeyen günlük nedeninde yeni regresyon, ham API kimliğinin yeniden biçimlendirilmeden korunmasını denetler.

**Birincil kaynak:** [NextDNS API Documentation](https://nextdns.github.io/api/) — şema, kimlik doğrulama, beta notu, profil, analitik, günlük ve cursor sözleşmesi.
**Ayrı tanı kaynağı:** [test.nextdns.io](https://test.nextdns.io/).
**Sınırlama:** Bu matris herkese açık sözleşme ve repository kodu karşılaştırmasıdır; özel hesap yanıtını veya canlı API anahtarıyla gerçek kullanıcı hesabını doğrulamaz.
