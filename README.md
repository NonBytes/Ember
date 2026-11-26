<p align="center">
  <img src="artwork/ember/ember-preview.png" width="144" alt="Ember flame icon" />
</p>

<h1 align="center">Ember for Android</h1>

<p align="center">A sing-box-powered proxy client with a dark interface and tools for inspecting network traffic.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-5.0%2B-brightgreen" alt="Android 5.0+" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-orange" alt="GPL-3.0 license" /></a>
</p>

## Interface

<p align="center">
  <img src="docs/images/ember-home.png" width="340" alt="Ember home screen with selected proxy, network IP, endpoint and profile cards" />
</p>

## Built on NekoBox

**Ember is based on [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid).**
It is an independently maintained fork with a new name, visual identity, and additional traffic-inspection features. The original NekoBox developers and contributors deserve credit for the base application, protocol integration, and Android VPN functionality. Ember is not an official upstream release.

## What Ember adds

- **Dark interface:** flame-orange accents, gradient cards, a redesigned drawer, and a new Ember launcher icon.
- **Traffic Log:** a native live connection list with host/process filtering, connection details, JSON/CSV export, and optional export when the VPN disconnects. Enable **Settings → Enable Clash API**, start the VPN, then open **Traffic Log**.
- **Actions from connection details:** copy connection information, add a domain-blocking route, or add an application to proxy scope.
- **Interception presets:** quick-add HTTP profiles for Burp Suite, mitmproxy, and OWASP ZAP. Each preset opens the profile editor immediately so you can set its host and port.
- **Android integration:** shortcuts to install a CA certificate and configure Android's Always-on VPN / Block connections without VPN settings.
- **Secure Browsing:** import a [HaGeZi Threat Intelligence Feeds](https://github.com/hagezi/dns-blocklists) domain blocklist as a route that can be toggled or removed.
- **Build improvements:** macOS native-build support and native linker settings for 16 KB page-size devices.

## Getting started

1. Add a proxy profile, or open **Group → Create group**, choose **Subscription**, and enter your provider's subscription URL.
2. Select a profile on the configuration screen and tap **Connect**. Accept Android's VPN permission prompt when requested.
3. Use **Route** for routing rules and **Stop** to disconnect.

Ember is a client; it does not include a VPN subscription or public proxy servers. Interception presets require your own running Burp Suite, mitmproxy, or ZAP instance. For a desktop proxy connected over USB, forward its port to Android, for example `adb reverse tcp:8080 tcp:8080`.

## Protocols and subscriptions

The inherited protocol support includes SOCKS, HTTP(S), SSH, Shadowsocks, VMess, VLESS, Trojan, AnyTLS, ShadowTLS, TUIC, Hysteria, and WireGuard. Some protocols require external plugins; see the [upstream plugin documentation](https://matsuridayo.github.io/nb4a-plugin/).

Subscription formats include Clash Meta, v2rayN, Shadowsocks, sing-box outbounds, and supported share-link lists. Subscription import extracts proxy nodes; it does not import the provider's complete routing configuration.

See the [upstream Android documentation](https://matsuridayo.github.io/nb4a-configuration/) for inherited configuration behavior.

## Build from source

Clone this repository:

```sh
git clone https://github.com/NonBytes/Ember.git
cd Ember
```

Use a JDK compatible with Gradle 8.10.2 (for example JDK 17), Android SDK platform 35, and the Android build tools. Configure the SDK using `local.properties` or `ANDROID_HOME`.

The application also requires `app/libs/libcore.aar`. To build the native core, use the Go/gomobile and Android NDK setup described by the repository's [build scripts](buildScript/) and [CI workflow](.github/workflows/preview.yml), then run [libcore/build.sh](libcore/build.sh) from the `libcore` directory.

Once the native AAR is available:

```sh
./gradlew :app:assembleOssRelease --console=plain
```

APKs are written to `app/build/outputs/apk/oss/release/`. Sign the APK for your device's ABI before installing it. Release artifacts use the `Ember-<version>-<ABI>` filename prefix.

The application ID remains `moe.nb4a`. An APK signed with the same certificate can update an existing compatible installation while retaining its data.

## Artwork

The Ember icon source, generation prompt, and Android asset-packaging script are in [artwork/ember](artwork/ember/README.md).

## Credits and license

Ember retains the upstream **GNU GPL v3** license. See [LICENSE](LICENSE) and [AUTHORS](AUTHORS) for the license and original authorship information.

- Base application: [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- Core: [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- Android GUI foundations: [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet) and [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- Web dashboard: [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
- Threat intelligence feeds: [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists)
