# NextDNS API yetenek matrisi

**İnceleme tarihi:** 21 Ekim 2026
**Ürün:** NextDNS hizmet API'siyle konuşan bağımsız, gayriresmî Android istemcisi
**Kapsam:** Yayımlanmış API sözleşmesi ile bu depodaki servis, DTO, repository, arayüz ve testlerin karşılaştırması.

Bu belge uygulamanın NextDNS tarafından onaylandığı veya desteklendiği anlamına gelmez. [Yayımlanmış NextDNS API belgesi](https://nextdns.github.io/api/) API'nin beta olduğunu ve değişikliklerin önceden bildirilmeden yapılabileceğini belirtiyor. Bu nedenle aşağıdaki durumlar depo içindeki mevcut uygulamayı gösterir; hizmetin gelecekteki garantisi değildir.

## Uygulamadaki API yüzeyleri

`SUPPORTED_READ_WRITE` mevcut istemcide okuyup değiştirebilen; `SUPPORTED_READ_ONLY` yalnızca okuyabilen; `PARTIAL` sözleşmenin veya UI akışının yalnızca bir kısmının mevcut olduğu; `MISSING` yayımlanmış yüzey olup istemciye eklenmemiş; `UNVERIFIED` yayımlanmış belgede bulunmayan veya gerçek yanıtla doğrulanmamış özellik demektir.

Rota kanıt sınıfı ayrıca tutulur: `DOCUMENTED` yol veya kullandığı genel API kuralı [S1]'de açıklanır; `OBSERVED` redakte edilmiş gerçek servis yanıtı/koşusuyla gözlenmiştir; `DECLARED_ONLY` yalnızca istemci kaynak kodunda tanımlıdır. MockWebServer ve sentetik fixture'lar servis gözlemi sayılmaz. Bu sınıflar bir rotaya yeni yazma yetkisi vermez.

| Yüzey ve sözleşme | Kod eşlemesi | Durum ve sınır |
|---|---|---|
| Profiller: `GET /profiles`, `GET /profiles/{id}`, `POST /profiles`, `PATCH /profiles/{id}`, `DELETE /profiles/{id}` | `NextDnsApiService`; `NextDnsRepository`; profil seçimi ve yönetim ekranları | `SUPPORTED_READ_WRITE`. Liste sayfaları cursor kullanır; oluşturma, yeniden adlandırma ve silme kullanıcı eylemiyle yapılır. |
| Profil kurulumu: `GET /profiles/{id}/setup`; `POST /profiles/{id}/setup/linkedIp` | `getProfileSetup` repository tarafından çağrılır; `updateLinkedIp` Retrofit'te kullanılmayan bir deklarasyondur. Ekrandaki linked-IP işlemi ayrı `link-ip.nextdns.io` hostuna gider. | Her iki API rotası yayımlanmış belgede yok (`DECLARED_ONLY`). Okuma ve bağlı IP yazma sözleşmesi doğrulanmış hizmet desteği olarak sunulamaz; HTTP 2xx tek başına bağlamayı kanıtlamaz. |
| Güvenlik: `GET/PATCH /profiles/{id}/security`; `/security/tlds` kataloğu ve profil TLD ekleme/silme uçları | Sınırlı Moshi `SecurityDto` adaptörü ve `SecurityUpdateRequest`; `NextDnsRepository`; güvenlik ekranı | Belgelenen alanlar ve TLD'ler `SUPPORTED_READ_WRITE`. Güvenlik yanıtı 512 KiB ile sınırlıdır. Yayımlanmamış en fazla 32 üst düzey alan bellek içi DTO'da yakalanır; en fazla 24 güvenli anahtar adı/tür gösterilir, yalnız Boolean değeri görünür. Diğer ham değerler arayüze, kalıcı depoya veya API isteğine girmez. Taşan alanlar sayı ve SHA-256 özetiyle eşitleme anlık görüntüsüne katılır. Bilinmeyen alanların anlamı/yazılabilirliği ve yaş doğrulama alanı `UNVERIFIED`; hiçbir dinamik PATCH yapılmaz. |
| Gizlilik: `GET/PATCH /profiles/{id}/privacy`; blocklist/native katalog ve profil alt kaynakları | `PrivacyDto`, mutasyon DTO'ları, `NextDnsRepository`, Gizlilik ekranı | `SUPPORTED_READ_WRITE`; kataloglar cursor ile okunur, ekleme ve silme profil alt kaynaklarından yapılır. |
| Ebeveyn denetimi: `/parentalControl/services`, `/parentalControl/categories`; profil için `GET/PATCH /parentalControl`, kategori/hizmet alt kaynakları | `ParentalControlDto`, katalog DTO'ları, `NextDnsRepository`, ekran | `SUPPORTED_READ_WRITE`. `blockBypass` yayımlanmış ebeveyn denetimi alanıdır; yaş doğrulamasını atlama ayarı olarak yorumlanmaz. |
| Engelleme/izin listeleri: `GET/POST /profiles/{id}/denylist|allowlist`; öğe `PATCH/DELETE` | İlgili DTO'lar, `NextDnsRepository`, iki liste ekranı | `SUPPORTED_READ_WRITE`; sayfalar cursor ile çekilir, filtre/eylemler servis öğe kimliğini kullanır. Küçük 12 dp favicon listede yalnızca görsel yardımcıdır; servis verisinin yerini almaz. |
| Profil ayarları: `GET/PATCH /profiles/{id}/settings`, ayrıca belgelenmiş `performance`, `logs`, `blockPage` alt yolları | `SettingsDto` ve güncelleme DTO'ları, `NextDnsRepository`, Servis ayarları ekranı | Kullanılan ayarlar için `SUPPORTED_READ_WRITE`. Dil, tema, bildirim tercihleri ve NextDNS bağlantı bilgisi bu payload'a dahil değildir. |
| Günlükler: `GET /profiles/{id}/logs`, `GET /logs/stream`, `GET /logs/download`, `DELETE /logs` | `NextDnsApiService`, repository, Günlükler ekranı | Sayfalama, `cursor`, API `search`, `device`, `status`, `raw=1` ve SSE birleştirmesi uygulanır; durumlar `default/error/blocked/allowed` olarak korunur. Arama ve cihaz/durum değişimleri cursor'ı sıfırlar; eski yanıtlar reddedilir. İndir/sil repository yolları mevcuttur. API'de `from` dahil alt sınır, `to` hariç üst sınırdır; `sort` `asc` veya `desc` kabul eder ve varsayılanı `desc`'tir. Tarih ve sıralama parametreleri servis imzasında var, ancak ekran filtresi yok (`PARTIAL`). Geçmiş, hesabın NextDNS log saklama ayarları ve API'nin döndürdüğü cursor sayfalarıyla sınırlıdır; istemci daha eski verinin varlığını garanti etmez. |
| Analitik: `status`, `devices`, `domains`, `reasons`, `destinations`, `dnssec`, `encryption`, `protocols`, `ips`, `queryTypes`, `ipVersions` | Aynı adlı Retrofit metotları, DTO'lar, `NextDnsRepository.fetchAnalytics`, Analiz ekranı | Okuma `SUPPORTED_READ_ONLY`. Uygulama `destinations?type=countries` ile etkileşimli yerel dünya haritasını; `destinations?type=gafam` ile altı parçalı grafiği besler. Ortak parametreler `device/from/to/limit/cursor`; `domains` ayrıca `status/root`, `destinations` `type` kullanır. API `__UNIDENTIFIED__` cihaz filtresini tanımlar ve istemci filtrede API ID'sini korur. `;series` uçları `MISSING`. |

## Profil şemasının alan düzeyinde eşlemesi

NextDNS'in yayımlanmış profil örneğindeki her yapılandırma yaprağı aşağıda istemci DTO'su, repository/UI yolu ve gerçek yazma kapsamıyla eşleştirilmiştir. Durum, yayımlanmış sözleşme ve mevcut kaynak kodu karşılaştırmasıdır; API'nin gelecekteki kararlılık garantisi değildir. `SUPPORTED_READ_WRITE`, hem sunucudan okunan hem kullanıcı eylemiyle PATCH/alt kaynak işlemi yapılan alanı; `PARTIAL`, okunan fakat sözleşmede enum'u tam tanımlanmayan veya uygulamanın yalnız belirli değerleri yazabildiği alanı gösterir.

<!-- API_PROFILE_FIELD_INVENTORY_START -->
| Alan yolu | Okuma eşlemesi | Yazma/UI eşlemesi | Durum ve sınır |
|---|---|---|---|
| `name` | `ProfileDto.name`; profil liste/detay yanıtı | Profil adı düzenleme, `NameRequest`, `NextDnsRepository` | `SUPPORTED_READ_WRITE`; profil adı düzenlenir. |
| `security.threatIntelligenceFeeds` | `SecurityDto.threatIntelligenceFeeds` | `SecurityUpdateRequest`, `setSecurityFlag(THREAT_INTELLIGENCE_FEEDS)` | `SUPPORTED_READ_WRITE`. |
| `security.aiThreatDetection` | `SecurityDto.aiThreatDetection` | `SecurityUpdateRequest`, `setSecurityFlag(AI_THREAT_DETECTION)` | `SUPPORTED_READ_WRITE`. |
| `security.googleSafeBrowsing` | `SecurityDto.googleSafeBrowsing` | `SecurityUpdateRequest`, `setSecurityFlag(GOOGLE_SAFE_BROWSING)` | `SUPPORTED_READ_WRITE`. |
| `security.cryptojacking` | `SecurityDto.cryptojacking` | `SecurityUpdateRequest`, `setSecurityFlag(CRYPTOJACKING)` | `SUPPORTED_READ_WRITE`. |
| `security.dnsRebinding` | `SecurityDto.dnsRebinding` | `SecurityUpdateRequest`, `setSecurityFlag(DNS_REBINDING)` | `SUPPORTED_READ_WRITE`. |
| `security.idnHomographs` | `SecurityDto.idnHomographs` | `SecurityUpdateRequest`, `setSecurityFlag(IDN_HOMOGRAPHS)` | `SUPPORTED_READ_WRITE`. |
| `security.typosquatting` | `SecurityDto.typosquatting` | `SecurityUpdateRequest`, `setSecurityFlag(TYPOSQUATTING)` | `SUPPORTED_READ_WRITE`. |
| `security.dga` | `SecurityDto.dga` | `SecurityUpdateRequest`, `setSecurityFlag(DGA)` | `SUPPORTED_READ_WRITE`. |
| `security.nrd` | `SecurityDto.nrd` | `SecurityUpdateRequest`, `setSecurityFlag(NRD)` | `SUPPORTED_READ_WRITE`. |
| `security.ddns` | `SecurityDto.ddns` | `SecurityUpdateRequest`, `setSecurityFlag(DDNS)` | `SUPPORTED_READ_WRITE`. |
| `security.parking` | `SecurityDto.parking` | `SecurityUpdateRequest`, `setSecurityFlag(PARKING)` | `SUPPORTED_READ_WRITE`. |
| `security.csam` | `SecurityDto.csam` | `SecurityUpdateRequest`, `setSecurityFlag(CSAM)` | `SUPPORTED_READ_WRITE`. |
| `security.tlds[].id` | `SecurityDto.tlds[]`, `TldDto.id` | TLD kataloğundan ekleme ve TLD alt kaynağını silme | `SUPPORTED_READ_WRITE`; TLD bir Boolean güvenlik bayrağı değildir. |
| `privacy.blocklists[].id` | `PrivacyDto.blocklists[]`, `BlocklistDto.id` | Blocklist profil alt kaynağı ekleme/silme | `SUPPORTED_READ_WRITE`; metadata katalog DTO'sundan gelir. |
| `privacy.natives[].id` | `PrivacyDto.natives[]`, `NativeTrackingDto.id` | Native profil alt kaynağı ekleme/silme | `SUPPORTED_READ_WRITE`. |
| `privacy.disguisedTrackers` | `PrivacyDto.disguisedTrackers` | `PrivacyUpdateRequest`, `setPrivacyFlag(DISGUISED_TRACKERS)` | `SUPPORTED_READ_WRITE`. |
| `privacy.allowAffiliate` | `PrivacyDto.allowAffiliateLinks` (`allowAffiliate` JSON adı) | `PrivacyUpdateRequest`, `setPrivacyFlag(ALLOW_AFFILIATE_LINKS)` | `SUPPORTED_READ_WRITE`. |
| `parentalControl.services[].id` | `ParentalControlDto.services[]`, `ParentalServiceDto.id` | Hizmet alt kaynağı ekleme/silme | `SUPPORTED_READ_WRITE`. |
| `parentalControl.services[].active` | `ParentalServiceDto.active` | Hizmet alt kaynağı `active` PATCH'i | `SUPPORTED_READ_WRITE`. |
| `parentalControl.categories[].id` | `ParentalControlDto.categories[]`, `ParentalCategoryDto.id` | Kategori alt kaynağı ekleme/silme | `SUPPORTED_READ_WRITE`. |
| `parentalControl.categories[].active` | `ParentalCategoryDto.active` | Kategori alt kaynağı `active` PATCH'i | `SUPPORTED_READ_WRITE`. |
| `parentalControl.safeSearch` | `ParentalControlDto.safeSearch` | `ParentalControlUpdateRequest`, `setParentalFlag(SAFE_SEARCH)` | `SUPPORTED_READ_WRITE`. |
| `parentalControl.youtubeRestrictedMode` | `ParentalControlDto.youtubeRestrictedMode` | `ParentalControlUpdateRequest`, `setParentalFlag(YOUTUBE_RESTRICTED_MODE)` | `SUPPORTED_READ_WRITE`. |
| `parentalControl.blockBypass` | `ParentalControlDto.blockBypass` | `ParentalControlUpdateRequest`, `setParentalFlag(BLOCK_BYPASS)` | `SUPPORTED_READ_WRITE`; yaş doğrulamasını atlama ayarı olduğu anlamına gelmez. |
| `denylist[].id` | `AllowDenyRuleDto.id` | `AllowDenyItemRequest.id`, denylist ekle/sil | `SUPPORTED_READ_WRITE`; öğe kimliği alan adıdır. |
| `denylist[].active` | `AllowDenyRuleDto.active` | `AllowDenyActiveRequest`, öğe PATCH'i | `SUPPORTED_READ_WRITE`. |
| `allowlist[].id` | `AllowDenyRuleDto.id` | `AllowDenyItemRequest.id`, allowlist ekle/sil | `SUPPORTED_READ_WRITE`; öğe kimliği alan adıdır. |
| `allowlist[].active` | `AllowDenyRuleDto.active` | `AllowDenyActiveRequest`, öğe PATCH'i | `SUPPORTED_READ_WRITE`. |
| `settings.logs.enabled` | `SettingsDto.logs.enabled` | `SettingsLogsUpdateRequest.enabled`, günlük aç/kapat | `SUPPORTED_READ_WRITE`. |
| `settings.logs.drop.ip` | `SettingsLogsDto.drop.ip`; UI'daki `logClientIps` ters mantıkla eşlenir | `SettingsLogsUpdateRequest.drop.ip`, IP kaydı seçeneği | `SUPPORTED_READ_WRITE`; `true` API değeri IP alanını düşürür ve UI seçimini kapatır. |
| `settings.logs.drop.domain` | `SettingsLogsDto.drop.domain`; UI'daki `logDomains` ters mantıkla eşlenir | `SettingsLogsUpdateRequest.drop.domain`, alan adı kaydı seçeneği | `SUPPORTED_READ_WRITE`; `true` API değeri alan adını düşürür ve UI seçimini kapatır. |
| `settings.logs.retention` | `SettingsLogsDto.retention`, `LogRetentionCodec` | `SettingsLogsUpdateRequest.retention`, sekiz bilinen süre seçeneği | `PARTIAL`; API alanı tamsayıdır fakat yayımlanmış örnek tüm geçerli süreleri/enumu tanımlamaz. Tanınmayan yanıt, beş dile yerelleştirilmiş bilinmeyen değeriyle gösterilir. |
| `settings.logs.location` | `SettingsLogsDto.location`, `NextDnsRepository.applyConfigSettingsFromApi` | `SettingsLogsUpdateRequest.location`, `ch/eu/us` seçenekleri | `PARTIAL`; yayımlanmış örnek `eu` gösterir, tam izinli kod kümesini tanımlamaz. Tanınmayan kod yerelleştirilmiş bilinmeyen değeriyle gösterilir, eski yerel konum gibi sunulmaz ve yazılamaz. |
| `settings.blockPage.enabled` | `SettingsDto.blockPage.enabled` | `SettingsBlockPageUpdateRequest.enabled` | `SUPPORTED_READ_WRITE`. |
| `settings.performance.ecs` | `SettingsDto.performance.ecs` | `SettingsPerformanceUpdateRequest.ecs` | `SUPPORTED_READ_WRITE`. |
| `settings.performance.cacheBoost` | `SettingsDto.performance.cacheBoost` | `SettingsPerformanceUpdateRequest.cacheBoost` | `SUPPORTED_READ_WRITE`. |
| `settings.performance.cnameFlattening` | `SettingsDto.performance.cnameFlattening` | `SettingsPerformanceUpdateRequest.cnameFlattening` | `SUPPORTED_READ_WRITE`. |
| `settings.web3` | `SettingsDto.web3` | `SettingsUpdateRequest.web3`, `setWeb3` | `SUPPORTED_READ_WRITE`; sunucu GET değeri yerel profil önbelleğine yansıtılır. |
<!-- API_PROFILE_FIELD_INVENTORY_END -->

Tablo, API belgesindeki tam profil örneğinin 39 yaprak alanını içerir. Profil kaynak kimliği (`id`) rota/oluşturma yanıtı kimliğidir; yapılandırma yaprağı değildir. `ProfileDto.fingerprint` DTO'da opsiyoneldir fakat yayımlanmış profil örneğinde belgelenmez; bu nedenle hizmet özelliği olarak kullanılmaz (`UNVERIFIED`). Günlük saklama konumunun bilinmeyen kodu için `RepositoryRegressionTest.unknownLogStorageLocationDoesNotMasqueradeAsThePreviousLocalValue` alan doğruluğunu denetler. Alan listesi CI'da Retrofit envanterinden ayrı olarak birebir kontrol edilir.

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


### Rota kanıt sınıflandırması

[S1] resmî API belgesi rotaları ve genel nested-object/array işlemlerini açıklar. Aşağıdaki `DECLARED_ONLY` satırları için mock testler yalnızca istemci isteğini sınar; gerçek NextDNS yanıtı veya destek garantisi değildir. `OBSERVED` olarak işaretlenmiş rota yoktur.

<!-- API_ROUTE_EVIDENCE_START -->
| Method | Service-relative route | Evidence class | Source or fixture |
|---|---|---|---|
| `GET` | `/profiles` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `POST` | `/profiles` | `DOCUMENTED` | [S1: Profiles; create a profile] |
| `GET` | `/profiles/{profileId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/setup` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `POST` | `/profiles/{profileId}/setup/linkedIp` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `GET` | `/profiles/{profileId}/security` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/security/tlds` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `PATCH` | `/profiles/{profileId}/security` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/security/tlds` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/security/tlds/{tld}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/privacy` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/privacy/blocklists` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/privacy/natives` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/privacy/blocklists` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `GET` | `/privacy/natives` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `PATCH` | `/profiles/{profileId}/privacy` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/privacy/blocklists` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/privacy/blocklists/{blocklistId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/privacy/natives` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/privacy/natives/{nativeId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/parentalControl/services` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `GET` | `/parentalControl/categories` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
| `GET` | `/profiles/{profileId}/parentalControl` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/parentalControl` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/parentalControl/categories` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/parentalControl/categories/{categoryId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/parentalControl/categories/{categoryId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/parentalControl/services` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/parentalControl/services/{serviceId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/parentalControl/services/{serviceId}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/denylist` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/denylist` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/denylist/{domain}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/denylist/{domain}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/allowlist` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `POST` | `/profiles/{profileId}/allowlist` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/allowlist/{domain}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `DELETE` | `/profiles/{profileId}/allowlist/{domain}` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/profiles/{profileId}/logs/stream` | `DOCUMENTED` | [S1: Logs; streaming, download, clear] |
| `GET` | `/profiles/{profileId}/logs` | `DOCUMENTED` | [S1: Logs; streaming, download, clear] |
| `GET` | `/profiles/{profileId}/logs/download` | `DOCUMENTED` | [S1: Logs; streaming, download, clear] |
| `DELETE` | `/profiles/{profileId}/logs` | `DOCUMENTED` | [S1: Logs; streaming, download, clear] |
| `GET` | `/profiles/{profileId}/analytics/status` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/devices` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/domains` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/reasons` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/destinations` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/dnssec` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/encryption` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/protocols` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/ips` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/queryTypes` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/analytics/ipVersions` | `DOCUMENTED` | [S1: Analytics endpoints and query parameters] |
| `GET` | `/profiles/{profileId}/settings` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/settings` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/settings/performance` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `PATCH` | `/profiles/{profileId}/settings/logs` | `DOCUMENTED` | [S1: Logs; streaming, download, clear] |
| `PATCH` | `/profiles/{profileId}/settings/blockPage` | `DOCUMENTED` | [S1: Profile; Nested objects and arrays] |
| `GET` | `/` | `DECLARED_ONLY` | `NextDnsApiService.kt` client declaration; route absent from [S1] |
<!-- API_ROUTE_EVIDENCE_END -->

## API dışı ve doğrulanmamış veriler

Güvenlik yanıtındaki yayımlanmamış anahtarlar yalnızca keşif ve fark algılama amacıyla korunur. Alan adı/türü gerçek API yanıtından alınır; bu istemci tarafından yeni bir hizmet özelliği olarak yorumlanmaz. Boolean dışı değerler gösterilmez, bilinmeyen alanlar PATCH gövdesine eklenmez ve yerel tercih deposuna yazılmaz. Resmî şema alanın anlamını veya ileriye dönük uyumluluk garantisini açıklamadığından yaş doğrulamasını atlama alanı ayrı olarak doğrulanmamış kalır.

| Veri/özellik | Kaynak ve uygulamadaki davranış | Durum |
|---|---|---|
| Bağlantı ve tanı testi | `https://test.nextdns.io/` ayrı tanı servisi. `server`, `resolver` ve `anycast` yanıt alanları; tanı kaynağı ve yerel kontrol saatiyle gösterilir. Yanıt bölge/şehir alanı vermediğinden arayüz bölge bilgisinin sağlanmadığını belirtir; bu değerlerden şehir/ülke türetilmez. Bu uç `api.nextdns.io` profil API'si değildir. | `SUPPORTED_READ_ONLY`, ayrı protokol; profil eşitleme bölümüne dahil değil. Bölge bilgisi yanıtta yok. |
| Hesap, anahtar ve bağlantı durumu | API anahtarı yerel şifreli tercih deposunda; seçili profil/bağlantı durumu uygulama oturum ve yerel durumudur. Yayımlanmış belgede bir `/account` eşitleme kaynağı yok; repository tam eşitlemesi bu tür bir endpoint istemez. | Yerel uygulama verisi; NextDNS profil ayarı olarak gönderilmez. Hesap uç noktası `UNDOCUMENTED / NOT IMPLEMENTED`. |
| Yaş doğrulama atlama | Yayımlanmış profil güvenlik/ayar şemasında böyle adlandırılmış alan veya okuma-yazma sözleşmesi belgelenmiyor. [NextDNS Help Center başlığında](https://help.nextdns.io/t/q6yp92t/bypass-age-verification-not-visible-in-dashboard) bir kullanıcı özelliğin Ayarlar'da bulunduğunu, NextDNS hesabı ise yanlış dağıtımda kaldırıldığını yanıtlıyor; gönderi API alanı veya PATCH sözleşmesi vermiyor. `parentalControl.blockBypass` ayrı bir özelliktir. Sanitize edilmiş gerçek API yanıtı kanıtı bulunmuyor. | Hizmet arayüzünde varlığına dair birinci taraf topluluk yanıtı var; API eşlemesi `UNVERIFIED / NOT IMPLEMENTED`. Alan adı uydurulmaz, API isteği ve senkronizasyon payload'ı eklenmez. |
| Erken erişim/belgelenmemiş güvenlik alanları | `SecurityDtoJsonAdapterFactory` en fazla 32 bilinmeyen alanı sınırlı bellek içi DTO'da yakalar; güvenli ad/tipler salt okunur listelenir ve yalnız Boolean değer gösterilir. Diğer değerler arayüze yazılmaz; alanlar yerel tercihe veya PATCH'e girmez. Sözleşmede bulunmayan gerçek profil alanı kanıtı yoktur. | Keşif altyapısı `SUPPORTED_READ_ONLY` (sentetik sözleşme testleriyle); gerçek hesaptaki görünür alanlar, anlamları ve yazma kabiliyeti `UNVERIFIED`. Dinamik PATCH yok. |
| Trafik haritası geometrisi | Uygulamayla birlikte gelen Natural Earth dünya ülke geometrisi ve lisans bildirimi; sorgu/sayı/ülke verisi NextDNS analitiğinden gelir. Harita çevrimdışı da çizilebilir. | Yerel statik varlık + `destinations?type=countries` okuması. |
| Alan adı favicon'ları | `favicons.nextdns.io` için gözlenmiş HTTPS yolu; resmi API şemasında yok. 20 Ekim 2026 salt GET sentetik probe'unda `github.com` ve `google.com` 200 `image/png`, `example.com` 404 döndürdü; 404, bir alan adında favicon bulunmadığında normal fallback yoludur. İzin/engel listeleri ile Native Tracking ve ebeveyn denetimi hizmet kataloglarında gösterilen domainler istenir; günlük satırlarında yerel ikon kullanılır. Kimlik bilgisi eklenmez; bellek önbelleği, boyut sınırı ve toplam istek süresi sınırı ve coroutine iptali OkHttp çağrısını kapatır. | En iyi çaba/garantisiz; örnek alan adları doğrulandı ama hizmet API/SLA'sı veya her domain için ikon garantisi yok. Disk önbelleği yok. |
| Bağlantı hızı ölçümü | NextDNS profil API'sinin parçası değildir. Kullanıcının güncel sürüm kararıyla özellik iptal edilmiştir: uygulama ekranı ve çalıştırılabilir istemci kaldırılmış, hiçbir hız testi sunucusuna istek gönderilmemiştir. Önceki LibreSpeed araştırması tarihsel kayıttır; eski yerel sunucu tercihleri uygulama başlangıcında temizlenir. | `CANCELLED_FOR_CURRENT_RELEASE`; sunucu, özellik veya canlı ölçüm desteği iddia edilmez. Yeniden ekleme ayrı ürün kararı ve yayın incelemesi gerektirir. |

## Günlük parametreleri ve cursor davranışı

NextDNS belgesi günlükler için varsayılan `limit=100` (10–1000), `cursor`, `device`, `status` (`default`, `error`, `blocked`, `allowed`), `search`, `from`, `to`, `sort` ve `raw` tanımlar. `from` alt sınırı dahildir; `to` üst sınırı hariçtir. `sort=asc` eskiden yeniye, `sort=desc` (varsayılan) yeniden eskiye sıralar. `raw=1`, varsayılan deduplikasyon/gürültü süzmesi yerine tüm DNS sorgularını döndürür. `logs/stream`, `from`, `to`, `sort`, `limit` ve `cursor` hariç aynı parametreleri destekler; son stream kimliği sayfa yanıtındaki `meta.stream.id` ile birleştirilebilir. Uygulama durum ve cihaz filtresi değişince cursor'ı sıfırlar ve önceki sorgu yanıtını geçersiz sayar. Gerçek geçmiş kapsamı profilin NextDNS saklama ayarına bağlıdır. Bu tarih/sıralama parametrelerinin UI kontrolleri mevcut ekranda henüz yoktur.

## Kontrol ve regresyon kanıtı

- `RepositoryRegressionTest.fullSyncDoesNotRequestAnAccountEndpointThatIsNotInThePublicApi` tam eşitlemede `/account` istenmediğini ve sonuçların yalnızca tanımlı profil bölümlerine ait olduğunu denetler.
- `AccountSettingsFlowTest.fullRefreshReadsNextDnsStateWithoutPostingLocalPreferences` yenilemenin yalnızca GET yaptığını; `/account` veya `/connection` çağrısı olmadığını doğrular.
- `NextDnsApiContractTest` belgelenmiş servis yolları ve istek sözleşmeleri için ana regresyon kümesidir.
- Bilinmeyen günlük nedeninde yeni regresyon, ham API kimliğinin yeniden biçimlendirilmeden korunmasını denetler.

**Birincil kaynak:** [NextDNS API Documentation](https://nextdns.github.io/api/) — şema, kimlik doğrulama, beta notu, profil, analitik, günlük ve cursor sözleşmesi.
**Ayrı tanı kaynağı:** [test.nextdns.io](https://test.nextdns.io/).
**Sınırlama:** Bu matris herkese açık sözleşme ve repository kodu karşılaştırmasıdır; özel hesap yanıtını veya canlı API anahtarıyla gerçek kullanıcı hesabını doğrulamaz.
