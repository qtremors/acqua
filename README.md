<p align="center">
  <img src="assets/Acqua.png" width="120" alt="Acqua Logo">
</p>

<h1 align="center"><a href="https://qtremors.github.io/acqua/">Acqua</a></h1>

<p align="center">
  A private, modern Android media downloader built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/qtremors/acqua/releases"><img alt="Version 0.0.1" src="https://img.shields.io/badge/version-0.0.1-8dcfff"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7f52ff">
  <a href="LICENSE.md"><img alt="TSL 1.0" src="https://img.shields.io/badge/license-TSL%201.0-f4c95d"></a>
</p>

Acqua lets you paste a link, preview available photos or videos, and save them to your device. Public links work without a session; content available only after sign-in can use Acqua's encrypted browser session.

> **Responsible use:** Only download content you own, have permission to save, or are otherwise legally authorized to access through your account. Do not attempt to access private or restricted content, bypass access controls, or infringe copyright or privacy rights. You are responsible for following applicable laws and the service provider's terms.

## Highlights

- Platform-neutral link and download experience.
- Photo, video, Reel, Story, and carousel extraction for the currently supported source.
- Optional authenticated browser flow for media available to your account.
- Encrypted session storage backed by Android Keystore.
- Media validation that rejects partial, audio-only, or mislabeled responses.
- Image dimensions, video metadata, thumbnails, and download sizes when available.
- Download history, custom folders, configurable filenames, and Android share-sheet support.
- Material 3 interface with adaptive light and dark themes.
- No ads, analytics, or telemetry.

## Install

Download the latest APK from [GitHub Releases](https://github.com/qtremors/acqua/releases). Android may ask you to allow installation from your browser or file manager.

For authenticated content, open **Settings → Login**, sign in inside the embedded browser, then return to the downloader. Acqua restores that encrypted browser session only when resolving a link that needs it.

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
| Debug | Acqua Debug | `dev.qtremors.acqua.debug` | `0.0.1-debug` | `Acqua-0.0.1-debug.apk` |
| Release | Acqua | `dev.qtremors.acqua` | `0.0.1` | `Acqua-0.0.1.apk` |

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

- Login happens on the service's own website inside Android WebView; Acqua does not ask for or store your password.
- Session cookies are encrypted at rest with a device-bound Android Keystore key.
- Authentication data is excluded from Android backup and device transfer.
- Logging out removes the stored browser session.
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
