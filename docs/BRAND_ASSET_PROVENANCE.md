# Faz 9.3 — özgün ağ düğümleri simgesi

9 Ekim 2026: Kullanıcının seçtiği DNS/ağ düğümleri fikri, proje içinde beş daire ve dört bağlantı çizgisinden oluşan özgün vektör geometriyle uygulandı. Kaynak `docs/brand/network-nodes.svg`; tekrar üretim aracı `scripts/generate_launcher_assets.py`. Harici logo, görsel, font veya stok varlık kullanılmadı. Projenin özgün varlıkları Apache-2.0 kapsamındadır.

Koyu zemin, turkuaz bağlantılar, açık düğümler ve amber merkez; NextDNS kalkanı veya harf biçimi kullanılmaz. Önceki launcher kalkanı ve eski WebP dosyaları değiştirildi. Başlık/giriş ekranındaki dekoratif kalkan da yeni düğüm biçimini kullanır; güvenlik sekmelerinin genel Material simgeleri bu marka varlığı değildir.

Adaptive katman 108dp, motif merkezde 66dp güvenli alan içinde; Android 13+ temalı ikon için tek renk katman bulunur. API 24–25 için 48/72/96/144/192px PNG'ler ve yuvarlak karşılıkları vardır. Mağaza simgesi 512px RGBA PNG; mağazanın uyguladığı köşe/ gölge işlemleri kaynakta taklit edilmez.

Kaynaklar: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Google Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).

Bu değişiklik, eski kalkanın benzerlik riskini gideren görsel ayrıştırmadır; marka araştırmasının tamamlandığı veya NextDNS'ten onay alındığı iddiası değildir. Uygulama bağımsız ve gayriresmîdir. Launcher maskeleri ve temalı görünümün gerçek cihaz kontrolü B11–B12 kapsamında kalır.

## 20 Ekim 2026 — launcher simgesi yönü güncellendi

Launcher/store simgesi, kullanıcının tercihiyle beyaz zemin üzerinde merkezî, özgün bir kalkan ve geniş açılı DNS sinyal çizgileri olarak yenilendi. Ön plan ve tek renk adaptive katmanlar Android vektörüdür; `scripts/generate_launcher_assets.py` aynı geometriyle API 24–25 PNG türevlerini ve `docs/brand/store-icon-512.png` dosyasını üretir. Uygulama içindeki ağ düğümü işareti `ic_client_network.xml` olarak korunmuştur.

Mavi `#0055FF` ve yeşil `#008000`, yalnızca NextDNS'in resmî web sitesinde görülen renkler için referanstır. Resmî logo dosyası kullanılmadı; kalkan/sinyal geometrisi projeye özgüdür. Bu görsel benzerlik ve marka izni incelemesini tamamlamaz. Bağımsız/gayriresmî konumlandırma sürer; launcher/OEM maskesi ve temalı ikon gerçek cihazda ayrıca kontrol edilmelidir.

20 Ekim takip düzeltmesinde sinyaller, önceki uzun açılı parantezlerin yerine daha kısa dikey alan kaplayan, ince ve yumuşak kavisli bir çift olarak çizildi. Kalkanın merkezi ve NextDNS'e ait olmayan proje geometrisi korunur.
