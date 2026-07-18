<p align="center">
  <img src="assets/Acqua.png" width="120" alt="Acqua Logo">
</p>

<h1 align="center"><a href="https://qtremors.github.io/acqua/">Acqua</a></h1>

<p align="center">
  A private, modern Android media downloader built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/qtremors/acqua/releases"><img alt="Version 0.0.2" src="https://img.shields.io/badge/version-0.0.2-8dcfff"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7f52ff">
  <a href="LICENSE.md"><img alt="TSL 1.0" src="https://img.shields.io/badge/license-TSL%201.0-f4c95d"></a>
</p>

Acqua lets you paste a web link, preview available photos or videos, and save them to your device. It tries any valid HTTP or HTTPS link and can use the shared in-app browser for websites that require sign-in.

> **Responsible use:** Only download content you own, have permission to save, or are otherwise legally authorized to access through your account. Do not attempt to access private or restricted content, bypass access controls, or infringe copyright or privacy rights. You are responsible for following applicable laws and the service provider's terms.

## Highlights

- Platform-neutral link and download experience.
- Best-effort extraction from any valid web link, with a specialized resolver for the currently supported source.
- Shared in-app browser for browsing and signing in to websites.
- Named website-login bookmarks with saved icons, one-tap reopening, and a dedicated add button.
- Website sessions kept in app-private WebView storage, with known platform cookies additionally protected by Android Keystore.
- Optional session usage, so browsing can stay signed in while downloads use public-only extraction.
- Media validation that rejects partial, audio-only, or mislabeled responses.
- Image dimensions, video metadata, thumbnails, and download sizes when available.
- Download history, custom folders, filename-variable chips with an Acqua-default reset, and Android share-sheet support.
- Material 3 interface with adaptive light and dark themes.
- No ads, analytics, or telemetry.

## Install

Download the latest APK from [GitHub Releases](https://github.com/qtremors/acqua/releases). Android may ask you to allow installation from your browser or file manager.

For authenticated content, open the **Browser** tab, tap **+**, and enter a name and website URL. The bookmark opens full-screen so you can sign in. Tap its saved icon later to reopen the same session, enable **Use sessions for downloads** when needed, then tap or drag the floating Acqua control to refresh the page, download the current page, or return to Acqua.

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

On Windows, use `gradlew.bat`. Generated APK names follow the same convention as Arcile:

| Variant | App name | Application ID | Version | APK |
| :--- | :--- | :--- | :--- | :--- |
| Debug | Acqua Debug | `dev.qtremors.acqua.debug` | `0.0.2-debug` | `Acqua-0.0.2-debug.apk` |
| Release | Acqua | `dev.qtremors.acqua` | `0.0.2` | `Acqua-0.0.2.apk` |

Debug and release builds can be installed side by side.

## Project layout

```text
acqua/
├── acqua-app/          Android application and Gradle wrapper
├── assets/             Repository artwork
├── docs/               Static project website
├── CHANGELOG.md        Release history
├── DEVELOPMENT.md      Architecture and contributor guide
└── LICENSE.md          Tremors Source License
```

The Android app uses Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, Android WebView, and Android Keystore. See [DEVELOPMENT.md](DEVELOPMENT.md) for the complete architecture and release workflow.

## Security and privacy

- Login happens on the website inside Android WebView; Acqua does not ask for or store your password.
- Browser data stays in app-private storage and is excluded from Android backup and device transfer.
- Known platform session cookies copied for network extraction are encrypted with Android Keystore.
- **Manage Website Data** can clear selected website sessions or wipe all cookies, storage, shared WebView cache, form data, HTTP authentication, and saved session data.
- Downloads remain on your device in the folder you choose.

## Documentation

- [Project website](https://qtremors.github.io/acqua/)
- [Changelog](CHANGELOG.md)
- [Development guide](DEVELOPMENT.md)
- [Issue tracker](https://github.com/qtremors/acqua/issues)

## License

Acqua is source-available under the [Tremors Source License (TSL) v1.0](LICENSE.md). Personal, educational, and attributed derivative use is permitted; commercial use requires written permission.

---

Built by [Tremors](https://github.com/qtremors).
