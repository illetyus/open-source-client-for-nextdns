# Günlükler, ikonlar ve arayüz bilgi mimarisi — araştırma ve tasarım taslağı

**Durum:** Araştırma taslağı. Kullanıcı, aşağıdaki UX ve teknik kapsamın uygulanmasını onayladı; bu uygulama onayı hukuki metinlerin veya uygulamanın yayına hazır olduğu anlamına gelmez.

**İnceleme tarihi:** 20 Ekim 2026 | **İncelenen checkout:** `fix/settings-account-sync-ux-2026-10-19`; çalışma ağacında önceki hata düzeltmesi değişiklikleri bulunuyordu ve korunmuştur.

## Amaç ve kapsam

Bu araştırma; NextDNS API günlüklerinin eksik görünmesini, alan adı simgelerinin gösterilmemesini, hizmet verisi ile uygulama tercihlerinin birbirine karışmasını, Ayarlar ve ilk açılış/misafir arayüzlerinin bilgi düzenini ve launcher ikonunun görsel yönünü inceler. Çalışma yalnızca bağımsız NextDNS istemcisi içindir. NextDNS API adresleri, Android paket kimliği, Kotlin sınıf isimleri ve protokol tanımları yeniden adlandırma gerekçesiyle değiştirilmeyecektir. Uygulamanın bağımsız/gayriresmî konumu korunacak; hukuki metinlerin `DRAFT` statüsü değiştirilmeyecektir.

## Mevcut kaynak koddan doğrulanan bulgular

### Alan adı simgeleri

9 Ekim kaynak anlık görüntüsünde `FaviconImage` `domain` parametresini almıyor ve her durumda Material `Language` simgesi çiziyordu; o sürümde ikon isteği hiç yapılmıyordu. Daha sonraki, kullanıcı tarafından onaylanmış uygulama sabit HTTPS `favicons.nextdns.io` hostuna normalize edilmiş hostname ile sınırlı best-effort istek ekledi. Yayımlanmış NextDNS API belgesinde ikon alanı veya uç noktası yoktur; host ve `/hex:<hostname>@2x.png` yolu genel API sözleşmesi değildir. İstemci yönlendirmeleri izlemez, yanıt boyutu ve toplam süreyi sınırlar, görseli yalnız bellekte önbelleğe alır ve başarısızlıkta yerel simge gösterir.

Güvenlik belirsizliği ayrıca kaydedilmelidir: NextDNS Yardım Merkezi’ndeki topluluk başlığında bir kullanıcı, aynı servisin istek yönlendirme/SSRF için kötüye kullanılabildiğini ileri sürmüş; davranışın tutarlı olmadığını ve sonraki denemelerin başarısız kaldığını da belirtmiştir. Bu içerik doğrulanmış bir güvenlik bildirimi veya NextDNS tarafından teyit edilmiş bir bulgu değildir; fakat uç nokta için resmî güvenlik garantisi bulunmadığından risk değerlendirmesinde göz ardı edilemez. İstemci yalnız sabit hosta, port/kullanıcı bilgisi/yol/şema kabul etmeyen DNS hostname değerini yollar; bunun sunucu tarafı davranışın güvenli olduğunu kanıtlamadığı ve rotanın değişebileceği araştırma sınırlamasıdır. Üçüncü taraf favicon hizmeti kullanılmaz.

P05 kaynak denetiminde `FaviconImage` çağrıları izin/engel listeleri ile gizlilik ve ebeveyn denetimi kataloglarıyla sınırlıdır; `LogsScreen` bu bileşeni kullanmaz. Bu nedenle DNS günlük satırlarının alan adları favicon hostuna gönderilmez. Hostname içeren istekler yalnız bu liste/katalog girdileri için yapılabilir; HTTPS yolundaki hex değer geri çözülebilir, API anahtarı isteğe eklenmez ve ikon hostu hostname ile olağan bağlantı verilerini görebilir. 2023 PETS çalışması Android tarayıcıları üzerindedir ve bu uygulamayı ölçmemiştir; burada yalnız üçüncü taraf favicon hizmetlerini varsayılan kaynak yapmama kararına bağlamsal destek sağlar.

### Günlüklerin geçmişi ve cihaz filtresi

Başlangıçtaki `applyLogsFromApi` en yeni 100 ham kaydı alır, API’nin `meta.pagination.cursor` değerini saklar ve ekrandaki listeyi bu sayfayla değiştirirdi. `refreshLogsFromApi` da `cursor` olmadan yeni bir ilk sayfa çağırır; eski sayfalara geçiş yapılmadığından kullanıcı daha eski kayıtları göremezdi. Güncelleme kapsamında sayfa imleci, cihaz kimliği filtresi ve yinelenen kayıtları ayıklayan ekleme akışı bağlandı; kullanıcı API’nin döndürdüğü daha eski sayfaları açık eylemle yükleyebilir. Bu davranış, API saklama sınırının ötesinde tarihçe garantisi vermez.

NextDNS belgesi günlüklerde `limit` değerinin 10–1000 aralığında olduğunu, `cursor` ile sonraki sayfanın istendiğini, varsayılan sıralamanın en yeniden eskiye olduğunu ve `device` filtresinde `__UNIDENTIFIED__` değerinin kullanılabildiğini belirtir. Günlük kaydı varsa “Annem” için ekranda görünen ada değil cihaz kimliğine göre sunucu filtresi uygulanabilir. SSE yalnızca canlı yeni kayıtlar içindir; onun olay kimliği, geçmiş sayfalama imlecinden ayrı tutulmalıdır. API’de mevcut olmayan, kapalı veya saklama süresi dolmuş günlükler istemci tarafından üretilemez. Bu sözleşme 20 Ekim 2026 tarihinde tekrar doğrulanmıştır.

### Hesap, eşitleme ve hizmet verisi

Uygulama `GET /account` ile NextDNS hesap verisini alıp e-posta, ad, plan ve abonelik alanlarına eşlemektedir ([NextDnsRepository.kt](../app/src/main/java/com/example/data/repository/NextDnsRepository.kt#L759)). Bu nedenle bu alanların API karşılığı vardır. Mevcut tam yenileme API’den veri okur; tema, dil ve bildirim tercihlerini NextDNS’e yazmaz. Kullanıcıya görünen “Hesabım” ve “eşitle” kavramları, sunucudan okunan hizmet verisini yerel uygulama tercihleriyle karıştırmaktadır. Öneri, bütün verileri kaynağına göre sınıflandırmak ve metinleri yenilemenin gerçekte yaptığı işe göre adlandırmaktır.

### Ayarlar ve giriş öncesi akış

Ayarlar mevcut durumda görünüm/dil ve bildirimleri NextDNS’e ait profil ve hizmet ayarlarından önce sunmaktadır; yasal belge bağlantısı profil eylemlerinden sonra gelmektedir. Ana ekrandaki Hesabım açılır menüsü eşitleme, tanı testi ve çıkış işlevlerini tek yerde toplamaktadır. Mevcut çalışma ağacında bu ekranlarla ilgili ayrı hata düzeltmesi değişiklikleri vardır; bu araştırma bunları değiştirmemiştir.

İlk açılış/misafir akışında dil kontrolü ve kullanım/gizlilik belgeleri birden fazla bağlamda görünmektedir. Öneri; mevcut kabul koşulunu korurken dil değiştirme ve yasal metin erişimini tutarlı bir ilk açılış düzeninde sunmak, aynı belge okuyucusunu kullanmak ve bağlantıları ekranlar boyunca dağınık biçimde tekrarlamamaktır.

Uygulanan sıra, NextDNS hizmet ayarlarını listenin başına alır. Tema, dil ve bildirim gibi yerel tercihler son tercih grubunda toplanır; kullanım koşulları ve gizlilik metinlerine erişim bu grubun altında, profil silme eyleminden hemen önce görünür. Böylece sözleşmeler hizmet kontrollerinin arasına girmez, silme de son ve ayrı eylem olarak kalır.

### Launcher ikonu

İstek yalnızca launcher ikonuna yöneliktir; uygulama içindeki işaret aynı kalmalıdır. Android adaptif ikon sistemi arka plan, ön plan ve isteğe bağlı tek renk katmanını ayrı tanımlar. Android belgesindeki örnek 108 dp katman ve maskeyle kesilmeyen 66 dp güvenli alan kullanır. Beyaz zemin ürün tercihi olarak uygulanabilir; sistem zorunluluğu değildir. Önerilen ön plan, NextDNS logosunu taklit etmeyen sade ve özgün bir ağ/DNS sembolüdür.

## Kaynakların kavramsal çerçevesi

- **Bilgi mimarisi:** Guizani’nin 2022 sistematik taraması, ACM/IEEE’deki 311 yayını inceleyip HCI bağlamında bilgi mimarisini kullanan 25 çalışma belirlemiştir; bu çalışma arXiv ön baskısıdır, hakemli yayın olduğu varsayılmamalıdır. Arayüzde içeriklerin sınıflandırılması, adlandırılması ve hiyerarşisi için kavramsal arka plan sağlar.
- **Ayarların bulunabilirliği:** Chen ve arkadaşlarının IEEE S&P 2019 çalışması yaklaşık 1.000 kullanıcının iki çalışmasını ve 100.000 uygulamanın taramasını birleştirir. Bulunan gizlilik ayarlarının %36,29’unu sınıflandırma ve açıklama sorunları nedeniyle “gizli” saymışlardır. Bu oran NextDNS uygulamasına genellenemez; çalışma, kullanıcıların ayarları nerede aradığını ve başlıkları nasıl yorumladığını ayrıca sınamak gerektiğini destekler.
- **Bilgi mimarisini doğrulama yöntemi:** Cho ve arkadaşlarının hakemli mobil sağlık uygulaması vaka çalışması, kart sıralamayı menü yapısı için; laboratuvar ve saha değerlendirmelerini ise farklı kullanılabilirlik katmanları için kullanır. Sağlık alanındaki sonuçlar bu DNS istemcisine doğrudan taşınmamalıdır; yöntem olarak hizmet/uygulama/yasal kartlarını kullanıcılarla sınıflandırma, ardından ağaç testiyle belirli seçeneğin bulunabilirliğini ölçme uygundur.
- **Mobil kullanılabilirlik:** Harrison, Flood ve Duce’nin hakemli PACMAD çalışması kullanıcı, görev ve kullanım bağlamını birlikte ele alır. Etkililik, verimlilik, memnuniyet, öğrenilebilirlik, hatırlanabilirlik, hata ve bilişsel yük ölçüleri; günlük bulma, hizmet ayarı yapma ve misafirden bağlanmaya geçme görevlerine uygulanabilir.
- **Ayarlar ve erişilebilirlik:** Android’in resmî yönergesi açık etiketleri, anlamlı grupları, sınırlı ve tahmin edilebilir seçenekleri önerir; hesap yönetimi ve uygulama bilgisini ayarlar ana listesine gelişigüzel eklememeyi belirtir. Compose yönergeleri uzun listeler için Paging ve satırların kararlı anahtarlarını, erişilebilirlik belgeleri ise kontrollerin anlamlı durum açıklamalarını destekler.
- **İlk açılış ve dil:** Android’in resmî onboarding rehberi, kullanmaya başlamadan önce zorunlu olan adımları sonradan bağlam içinde öğretilebilecek içerikten ayırmayı; uygulamanın değerini giriş isteminden önce göstermeyi; misafir önizlemesini ve atlama/geri dönüş yolunu dikkate almayı önerir. Per-app language rehberi, uygulama içi dil seçicisini Android sistem dili ayarıyla eşleştirmeyi destekler. Bu nedenle sistem dilini varsayılan almak ve Android 13+ uygulama dili ayarlarıyla tutarlı olmak; eski sürümlerde AndroidX uyumluluğunu korumak uygun araştırma yönüdür.
- **İkon değerlendirmesi:** McDougall ve Isherwood’un hakemli ikon tanıma çalışmasında erken öğrenmede ikon ile işlev arasındaki anlamsal yakınlık, daha sonraki denemelerde ise işlev aşinalığı öne çıkar. EvIcon (2023) bu hattı kullanıcı katılımlı ikon geliştirme ve değerlendirmeyle sürdürür. Bu projede ikon; anlamı anlaşılır, özgün ve ayırt edilebilir olmalı; gerçek launcher maskeleri, küçük görünüm ve tek renk/temalı görünümde incelenmelidir.
- **Veri ve güvenlik:** Android offline-first mimarisi ağ kaynağı ile yerel önbelleği ayrı doğruluk kaynakları olarak ele alır; OWASP MASVS de depolama, kimlik doğrulama, ağ ve gizlilik kontrollerini ayrı gruplar. Bu ayrım, NextDNS’ten gelen profil verisi ile yalnız cihazda saklanan tercihin karışmasını önler.

## Önerilen tasarım şartnamesi

### Veri akışı

1. Günlük ekranı ilk açılışta en yeni sayfayı yükler; kullanıcı aşağı indikçe her API `cursor` değerini kullanarak eski sayfaları getirir. Sayfa anahtarı profil, cihaz filtresi, durum/arama, tarih aralığı ve `raw` seçimiyle ilişkilendirilir; bunlardan biri değiştiğinde ilgili sayfalama sıfırlanır.
2. “Annem” filtresi seçilmiş cihazın NextDNS kimliğiyle, tanımlanamayan filtre API’nin `__UNIDENTIFIED__` değeriyle istek yapar. Liste öğeleri varsa API’nin kararlı kayıt kimliğiyle; yoksa yeterince ayırt edici ve çakışma testinden geçmiş alanlarla birleştirilir. Canlı SSE kayıtları eski sayfaları ezmez ve ayrı akış kimliği taşır.
3. API tarihçesinin sınırı, günlüklerin kapalı olması ve NextDNS profil saklama ayarı ekranda doğru aktarılır. İstemci, API’nin döndürmediği süre için “tüm zamanlar” garantisi vermez.
4. İkon kaynağı için yalnızca sabit `favicons.nextdns.io` HTTPS hostu kullanılır; rota belgelenmemiş best-effort yoldur ve API/SLA desteği varsayılmaz. İstekler allowlist/denylist ve gizlilik/ebeveyn denetimi kataloglarında gösterilen hostname değerlerini içerebilir; günlük satırları bu yola bağlanmaz. Resmî NextDNS API anahtarı favicon isteğine eklenmez, yönlendirme izlenmez, host/hostname doğrulaması ile yanıt boyutu ve toplam süre sınırlanır, önbellek yalnız bellektedir ve hata halinde yerel simge kullanılır. Hostun işletmecisi ve saklama uygulamaları doğrulanmış değildir; bu belirsizlik DRAFT gizlilik metninde açık tutulur.

### Bilgi mimarisi

- Ana ekrandaki üst Hesabım düğmesi kaldırılır; aynı işleve giden yinelenen kapı bırakılmaz.
- Gerçek NextDNS hesap ve profil bilgileri “NextDNS bağlantısı ve profili” altında gösterilir. Tanı testi bu bağlantı bağlamında, çıkış da bu hizmet/profil alanında bulunur. Yenileme eylemi “NextDNS verilerini yenile” gibi kapsamı doğru anlatan bir ad kullanır.
- Ayarlar sırası: **NextDNS bağlantısı/profili ve hizmet ayarları** → **Yasal ve hakkında** tek grubu → **Uygulama tercihleri** (dil, görünüm, bildirimler) → **NextDNS profilini sil** son ve ayrı tehlikeli eylem. Böylece uygulamaya özgü tercih grubu silmeden önceki son ayar grubu olur; yasal belgeler dağılmaz.
- Misafir/ilk açılış akışında sistem dili varsayılandır; ilk açılışta gereken dil kontrolü ortak ve tutarlı tek konumda sunulur, uygulama içindeki sonraki dil değişikliği aynı Android uygulama dili tercihini kullanır. API anahtarıyla bağlan ana eylem, misafir/demo ikincil eylemdir. Kullanım koşulları ve gizlilik belgesi tek bir gruptan açılır; geçerli kabul adımı korunur.
- Hukuki içeriklerin `DRAFT` işaretleri ve bağımsız/gayriresmî açıklamalar korunur. Bu taslak hiçbir hukuki metni nihai veya yayın için onaylı hâle getirmez.

## Kabul ve doğrulama ölçütleri

1. Sahte API yanıtlarıyla en az üç sayfalı tarihçe, sonraki imleç, son imleçte bitiş, cihaz kimliği, `__UNIDENTIFIED__`, arama/durum filtresi ve boş/hatali sayfa yolları sınanır.
2. Bir sonraki sayfa yüklenirken SSE olayı geldiğinde kayıt kaybı ve yinelenme olmadığı; profil/filtre değişince eski isteğin yeni listeye yazmadığı doğrulanır.
3. “Annem günlüğünü bul”, “tanımlanamayan istekleri aç”, “daha eski kayıtları yükle”, “hizmet ayarını yenile” görevleri Compose/UI ve en az bir gerçek cihaz akışında tamamlanır. API’de olmayan tarihçe için uygulama başarı iddiasında bulunmaz.
4. Eşitleme testi `GET /account` ve gerçek hizmet uç noktalarını doğrular; tema/dil/bildirim değişikliklerinin NextDNS’e gönderilmediğini istek kaydıyla kanıtlar.
5. İlk açılış, misafir ve bağlı kullanıcı ekranları beş dilde, büyük yazı boyutunda, TalkBack ile ve açık/koyu temada incelenir. Özel dokunmatik kontroller en az 48 dp hedef alanı, metin de Android’in önerdiği kontrast koşullarını karşılar. PACMAD ölçüleri olarak görev başarısı, süre, hata/geri dönüş ve kısa memnuniyet/bilişsel yük değerlendirmesi kaydedilir.
6. Ağ testi günlük alan adlarının ikon veya analitik amacıyla ilgisiz sunuculara gönderilmediğini; API anahtarı ve hassas günlük içeriğinin uygulama günlüklerine/telemetriye yazılmadığını denetler. OWASP MASVS kapsamındaki depolama, ağ, kimlik doğrulama ve gizlilik kontrolleri değişiklik aşamasında tekrar taranır.
7. Launcher ikonu Android maskeleri, 66 dp güvenli alan, tek renk katmanı, küçük boyut ve düşük kontrastlı/aydınlık duvar kâğıtlarıyla doğrulanır; uygulama içindeki mevcut logo karşılaştırma görüntüsünde değişmez.

NextDNS’in yayımlanmış API belgesi API’yi beta olarak işaretlediğinden, veri sözleşmesi testleri ve anlaşılır hata/yenileme durumu bakım gereksinimidir. API belgesinde ikon uç noktası bulunmaması bu araştırmanın açık belirsizliğidir.

## Araştırma erişimi ve sınırlamalar

Birincil kaynaklar NextDNS API belgeleri, Android Developers ve OWASP MASVS’tir. Akademik kaynaklar PACMAD, EvIcon ve PETS 2023 çalışmasını içerir; bilgi mimarisi çalışması açıkça ön baskı olarak işaretlenmiştir. Firecrawl ve Context7 resmî belgeleri ve makale metinlerini incelemek; Parallel Search Natural Earth lisans bilgisini çapraz doğrulamak için kullanılmıştır. Scite erişimi hesap planı nedeniyle reddedilmiş, Acumen araması iç hata vermiştir; bu nedenle bu araçlardan akademik tarama sonucu raporlanmamıştır. GitBook içerik taraması için gerekli kuruluş/site erişimi bulunmamıştır. MagicPath’te bu çalışma için etkileşimli ülke haritası ve ayrıntı kartı wireframe’i oluşturulmuş; tldraw’da harita → seçili ülke → alan adları akışı ayrıca çizilmiştir. Bu taslaklar tasarım gözden geçirme yardımcılarıdır; nihai uygulama arayüzü kaynak kodundaki Compose bileşenidir. Render dağıtım hizmeti bu yerel Android istemcisiyle ilgili değildir; PostHog/ürün telemetrisi bu araştırmada kullanılmamıştır. Codex Security için bu belge kapsamında tam depo taraması yapılmamış, yalnızca MASVS kontrol çerçevesi incelenmiştir.

## Kaynakça

1. NextDNS, [API Documentation — pagination, logs, devices and streaming](https://nextdns.github.io/api/).
2. Android Developers, [Settings design guidance](https://developer.android.com/design/ui/mobile/guides/patterns/settings), [Authentication and onboarding](https://developer.android.com/design/ui/mobile/guides/patterns/onboarding), [per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages), [accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps), [Compose lists and paging](https://developer.android.com/develop/ui/compose/lists), [Paging from network and database](https://developer.android.com/topic/libraries/architecture/paging/v3-network-db), [offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first), [adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) and [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).
3. Harrison, R., Flood, D. and Duce, D. (2013), [“Usability of mobile applications: literature review and rationale for a new usability model”](https://link.springer.com/article/10.1186/2194-0827-1-1), *Journal of Interaction Science*.
4. Guizani, M. (2022), [“A Decade of Information Architecture in HCI: A Systematic Literature Review”](https://arxiv.org/abs/2202.13412), arXiv preprint.
5. Chen, Y. et al. (2019), [“Demystifying Hidden Privacy Settings in Mobile Apps”](https://doi.org/10.1109/SP.2019.00054), *IEEE Symposium on Security and Privacy*.
6. Cho, H. et al. (2018), [“A multi-level usability evaluation of mobile health applications: A case study”](https://doi.org/10.1016/j.jbi.2018.08.012), *Journal of Biomedical Informatics*.
7. Shen, I.-C. et al. (2023), [“EvIcon: Designing High-Usability Icon with Human-in-the-loop Exploration and IconCLIP”](https://doi.org/10.1111/cgf.14924), *Computer Graphics Forum*.
8. McDougall, S. and Isherwood, S. (2009), [“What’s in a name? The role of graphics, functions, and their interrelationships in icon identification”](https://doi.org/10.3758/BRM.41.2.325), *Behavior Research Methods*.
9. Pradeep, A. et al. (2023), [“Not Your Average App: A Large-scale Privacy Analysis of Android Browsers”](https://petsymposium.org/popets/2023/popets-2023-0003.pdf), *Proceedings on Privacy Enhancing Technologies*.
10. OWASP, [Mobile Application Security Verification Standard (MASVS)](https://mas.owasp.org/MASVS/).
11. NextDNS Help Center, [Using favicons.nextdns.io to request other resources](https://help.nextdns.io/t/m1h08qc/using-favicons-nextdns-io-to-request-other-resources). Topluluk tarafından gözlenen hex rota ve doğrulanmamış SSRF iddiası; resmî güvenlik duyurusu değildir.
