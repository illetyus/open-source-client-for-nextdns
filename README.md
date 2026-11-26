# Open Source Client for NextDNS

Manage your NextDNS profiles on Android with an independent, open-source client. Change filtering rules, find out why a site was blocked, and check whether your phone is using NextDNS.

[**Download the Android beta**][apk] · [Release notes and checksums][release] · [Türkçe](README.tr.md)

Android 7.0 or later. A NextDNS account is required to manage your own profiles.

## A look at the app

<p>
  <a href="docs/assets/readme/setup.png"><img src="docs/assets/readme/setup.png" width="30%" alt="Setup shows the connection status and the option to run diagnostics"></a>
  <a href="docs/assets/readme/logs.png"><img src="docs/assets/readme/logs.png" width="30%" alt="Logs shows tracker.example blocked by a blocklist"></a>
  <a href="docs/assets/readme/analytics.png"><img src="docs/assets/readme/analytics.png" width="30%" alt="Analytics shows total and blocked queries and the most frequently queried domains"></a>
</p>

The screens use sample data. Select any image to view it at full size.

- **Setup.** Run network diagnostics to check whether this device uses NextDNS. The result includes the connection protocol and latency when available.
- **Logs.** Search for a domain to see whether NextDNS blocked it and why. Filter by device or time range, or export the results as a CSV file.
- **Analytics.** Review how many requests NextDNS handled, what share it blocked, and which domains were requested most often.

Under **Security**, **Privacy**, and **Parental control**, you can review or change the selected profile’s filtering rules. Use the **Allowlist** and **Denylist** for exceptions and restrictions on individual domains.

## Getting started

1. Download the [beta APK][apk] and open it on your Android device. If Android asks whether your browser or file manager can install apps, allow it for the installation.
2. To look around before connecting an account, select **Explore in demo / guest mode**. The interface is available in English, Turkish, German, French, and Spanish.
3. To manage your own profiles, copy the API key at the bottom of [your NextDNS account page][account], enter it in the app, and select a profile. The key gives the app access to your NextDNS account.

To route this phone’s DNS requests through NextDNS, follow the separate instructions in **Setup**. On Android 9 or later, **Open Android Private DNS settings** takes you to the system setting where you can enter your profile’s address.

## How the app connects to NextDNS

NextDNS operates the DNS service and provides the accounts. The app connects directly to NextDNS and stores the API key encrypted on the device. When loading a site icon, the app may send the displayed domain to NextDNS’s icon service.

## Support and source code

- [Report a bug][issues]. Include the app version, Android version, and the steps to reproduce it. Keep API keys, profile identifiers, and DNS logs out of public reports.
- [Report a security issue](SECURITY.md).
- [Build and test the app](docs/README.md#build-and-verify) or read the [contribution guide](CONTRIBUTING.md).

The source code is available under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for third-party attributions.

[apk]: https://github.com/illetyus/open-source-client-for-nextdns/releases/download/v1.0.0-beta.1/open-source-client-for-nextdns-v1.0.0-beta.1.apk
[release]: https://github.com/illetyus/open-source-client-for-nextdns/releases/tag/v1.0.0-beta.1
[account]: https://my.nextdns.io/account
[issues]: https://github.com/illetyus/open-source-client-for-nextdns/issues
