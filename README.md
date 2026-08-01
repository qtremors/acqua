<p align="center">
  <img src="assets/Acqua.svg" width="120" alt="Acqua Logo">
</p>

<h1 align="center"><a href="https://qtremors.github.io/acqua/">Acqua</a></h1>

<p align="center">
  A privacy-focused Android media downloader built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/qtremors/acqua/releases/latest"><img alt="Download latest release" src="https://img.shields.io/github/v/release/qtremors/acqua?label=Download%20APK&amp;color=8dcfff&amp;logo=android&amp;logoColor=white"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7f52ff">
  <a href="LICENSE.md"><img alt="GPL v3 or later" src="https://img.shields.io/badge/license-GPLv3%2B-f4c95d"></a>
</p>

<p align="center">
  <a href="https://github.com/qtremors/acqua/releases"><img alt="Total release downloads" src="https://img.shields.io/github/downloads/qtremors/acqua/total?label=Total%20Downloads&amp;color=3f51b5"></a>
  <a href="https://github.com/qtremors/acqua/releases/latest"><img alt="Latest release downloads" src="https://img.shields.io/github/downloads/qtremors/acqua/latest/total?label=Latest%20Downloads&amp;color=00a6a6"></a>
</p>

Acqua is a media downloader that previews and saves supported media from links.

> **Responsible use:** Only download content you own, have permission to save, or are otherwise legally authorized to access through your account. Do not attempt to access private or restricted content, bypass access controls, or infringe copyright or privacy rights. You are responsible for following applicable laws and the service provider's terms.

## Highlights

- Acqua downloads by default with yt-dlp available as an explicit alternative.
- Persistent direct, video, and audio downloads with queue progress, cancellation, and automatic network retry.
- Best-effort media extraction from supported links and pages.
- Shared in-app browser for browsing and signing in to websites.
- Download and preview screens that open above the live browser without resetting its page or navigation state.
- Named website bookmarks with saved icons, editable names and links, one-tap reopening, and a dedicated add button.
- Website sessions kept in app-private WebView storage, with reusable extraction sessions additionally protected by Android Keystore.
- Opt-in browser session extraction, with matching saved cookies attached only to their media host.
- Media validation that rejects partial, audio-only, or mislabeled responses.
- Image dimensions, video metadata, thumbnails, and download sizes when available.
- Searchable and sortable download history with missing-file recovery, a configurable Downloads subfolder, filename previews, and Android sharing.
- Material 3 interface with adaptive light and dark themes.
- In-app About, privacy, source, support, license, and third-party notice information.
- No ads, analytics, or telemetry.

## Install

Download the universal APK from the [latest GitHub release](https://github.com/qtremors/acqua/releases/latest), or choose a smaller ABI-specific APK if you know your device architecture. Android may ask you to allow installation from your browser or file manager.

For browser-based or authenticated extraction, open the **Browser** tab, tap **+**, and enter a name and website URL. The bookmark opens full-screen so you can sign in. Use the floating Acqua control to refresh the page, download from the current page, or return to Acqua. The download screen opens above the live browser, so returning preserves the page, navigation history, and normal WebView state. **Use browser sessions for extraction** separately controls automatic session reuse outside an explicit browser download.

## Build from source

Requirements:

- Android Studio with JDK 11 or newer
- Android SDK 37
- An Android 7.0 (API 24) or newer device/emulator

```bash
cd acqua-app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

On Windows, use `gradlew.bat`. The universal APK supports every packaged architecture, while the smaller split APKs contain only the native runtime for a specific device:

| ABI | Typical device | Release APK |
| :--- | :--- | :--- |
| Universal | Any supported device | `Acqua-<version>.apk` |
| `arm64-v8a` | Most modern phones | `Acqua-<version>-arm64-v8a.apk` |
| `armeabi-v7a` | Older 32-bit ARM devices | `Acqua-<version>-armeabi-v7a.apk` |
| `x86_64` | 64-bit x86 emulators/devices | `Acqua-<version>-x86_64.apk` |
| `x86` | Older 32-bit x86 emulators/devices | `Acqua-<version>-x86.apk` |

Debug outputs use `Acqua-Debug-<version>.apk` and `Acqua-Debug-<version>-<abi>.apk`; release outputs use the equivalent `Acqua` names. Their separate application IDs allow both builds to be installed side by side.

## Project layout

```text
acqua/
├── acqua-app/          Android application and Gradle wrapper
├── assets/             Repository artwork
├── docs/               Static project website
├── CHANGELOG.md        Release history
├── DEVELOPMENT.md      Architecture and contributor guide
├── LICENSE.md          GNU GPL v3-or-later notice
├── LICENSES/           Bundled third-party license texts
├── PRIVACY.md          User-facing privacy policy
└── THIRD_PARTY_NOTICES.md Runtime dependency notices
```

The Android app uses Kotlin, Jetpack Compose, Material 3, OkHttp, Android WebView, and Android Keystore. See [DEVELOPMENT.md](DEVELOPMENT.md) for the complete architecture and release workflow.

## Security and privacy

- Login happens on the website inside Android WebView; Acqua does not ask for or store your password.
- Browser data stays in app-private storage and is excluded from Android backup and device transfer.
- Session data copied for network extraction is encrypted with Android Keystore.
- **Manage Website Data** can clear selected website sessions or wipe all cookies, storage, shared WebView cache, form data, HTTP authentication, and saved session data.
- Downloads remain on your device in the configured subfolder under Downloads.

## Documentation

- [Project website](https://qtremors.github.io/acqua/)
- [Changelog](CHANGELOG.md)
- [Development guide](DEVELOPMENT.md)
- [Issue tracker](https://github.com/qtremors/acqua/issues)

## License

Acqua is free software licensed under the [GNU General Public License v3 or later](LICENSE.md). Runtime component licensing and source links are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). These documents and applicable license texts are also bundled inside every APK.

---

Built by [Tremors](https://github.com/qtremors).
