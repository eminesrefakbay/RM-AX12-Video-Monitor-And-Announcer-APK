<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD logosu"></p>

<p align="center"><a href="README.md">English</a> · <b>Türkçe</b> · <a href="README.de.md">Deutsch</a> · <a href="README.zh.md">中文</a></p>

# AX-HUD: AX12 video monitörü, HUD ve sesli uyarı

**AX-HUD**, RadioMaster AX12 için bir Android uygulamasıdır. Kumandadan yalnızca **okur**, hiçbir ayar yazmaz.

## Ne yapar?

* HDMI girişinden gelen videoyu **tam ekran** gösterir; görüntü yokken altta ince bir durum çubuğu çıkar ve kendiliğinden yeniden bağlanır.
* Yazdığınız kurallarla **sesli uyarı** verir. Örnek: `ch5 >= 1500 -> motorlar aktif`. Kurallar uygulama içinden düzenlenir: eşikler, `and`/`or` mantığı, anahtar algılama, `{vbat/6:2}` gibi canlı değerler.
* Kurallarla aynı mantıkta çalışan bir **uyarı durum çubuğu** gösterir (kritik = kırmızı ve yanıp söner, uyarı = sarı, bilgi = mavi; istenirse N saniye sonra gizlenir).
* **Ekran katmanları:** stick görünümü ve aux anahtar çubukları (adları değiştirilebilir, ters çevrilebilir), uçuş istatistikleri (en yüksek hız, en yüksek irtifa, tepe akım), radar ve uçuş öncesi kontrol listesi.
* **GitHub'dan güncelleme:** Ayarlar > Güncellemeler. İki kanal: Nightly (her push) ve Stable (sürümler). Ayrıntılar: [UPDATES.md](UPDATES.md).
* **Dil:** İngilizce, Türkçe, Almanca, Çince. Ayarlar > Dil. Ses, ekrandan farklı bir dilde konuşabilir.

## Gereksinimler ve kurulum

* RadioMaster **AX12** (Android 9). Yazarın denediği düzen: Ekim 2026 sistem güncellemesi, INAV 9.1, ExpressLRS. Diğer düzenler **denenmedi**.
* **Releases** sayfasından `ax-hud.apk` dosyasını indirin (seri kilit yamalı olan; AX12'de çalışan budur) ve kurun.
* AX12'nin kendi RadioMasterAX uygulamasını **kapatın**: seri portu aynı anda yalnızca bir uygulama kullanabilir. AX-HUD kapanınca port geri verilir.

## Güvenlik

Bu uygulama deneyseldir ve **yedek bir bilgi kaynağı** olarak kullanılmalıdır; uçuş kontrolcünüzün OSD'sinin yerini tutmaz. Kullanım kendi sorumluluğunuzdadır. Bulunduğunuz yerin kurallarına uygun uçun. Uçuş sırasında güncelleme yapmayın.

## Gizlilik

Uygulamanın internet izni yalnızca github.com'dan güncelleme bilgisini ve APK'yı almak içindir. Hiçbir veri gönderilmez.

## Teşekkür

AX-HUD'ı mümkün kılan AX SDK'yı yayımladığı için **RadioMaster**'a teşekkürler. AX-HUD bağımsız bir projedir ve RadioMaster ile bağlantılı değildir.

Tüm ayrıntılar (derleme, seri kilit, kurallar, testler) için [İngilizce README](README.md) dosyasına bakın. Çeviri düzeltmeleri için: [TRANSLATING.md](TRANSLATING.md).
