# Acqua Development Guide

| Metadata | Value |
| :--- | :--- |
| Current version | 0.0.1 |
| Minimum Android version | Android 7.0 (API 24) |
| Target/compile SDK | 37 |

This document describes Acqua's architecture, authenticated extraction flow, development setup, tests, and release process.

## Contents

1. [Development setup](#development-setup)
2. [Build variants](#build-variants)
3. [Architecture](#architecture)
4. [Media resolution](#media-resolution)
5. [Authenticated sessions](#authenticated-sessions)
6. [Validation and downloads](#validation-and-downloads)
7. [Storage and history](#storage-and-history)
8. [Testing](#testing)
9. [Release checklist](#release-checklist)
10. [Troubleshooting](#troubleshooting)

## Development setup

### Requirements

- Android Studio with JDK 11 or newer
- Android SDK 37
- Git
- An Android 7.0+ device or emulator

Clone the repository, open `acqua-app` in Android Studio, and allow Gradle sync to finish.

Command-line builds:

```bash
cd acqua-app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

On Windows, replace `./gradlew` with `gradlew.bat`.

## Build variants

Acqua uses separate identities for local development and production:

| Variant | Label | Application ID | Version name |
| :--- | :--- | :--- | :--- |
| Debug | Acqua Debug | `dev.qtremors.acqua.debug` | `0.0.1-debug` |
| Release | Acqua | `dev.qtremors.acqua` | `0.0.1` |

Both use version code `1`. The distinct application IDs allow both variants to be installed on the same device without sharing app data or sessions.

APK output names are generated from the variant version:

- `Acqua-0.0.1-debug.apk`
- `Acqua-0.0.1.apk`

Variant configuration lives in `acqua-app/app/build.gradle.kts`. The manifest reads `${appLabel}`, so do not hard-code the display name in `AndroidManifest.xml`.

## Architecture

Acqua is a single-activity Compose application with two focused WebView activities for authentication and authenticated media resolution.

```text
Shared link / pasted URL
          |
          v
   MainActivity (Compose)
          |
          +---- public resolution ----> InstagramDownloader
          |                                  |
          |                                  v
          |                         MediaContentDetector
          |
          +---- session required ---> AuthenticatedMediaResolverActivity
                                             |
                                             v
                                    restored WebView session
                                             |
                                             v
                                      candidate media URLs
                                             |
                                             v
                                    validation and download
```

### Key files

| File | Responsibility |
| :--- | :--- |
| `MainActivity.kt` | Compose UI, input handling, extraction orchestration, previews, settings, history, and download actions. |
| `InstagramDownloader.kt` | Network extraction fallbacks, authenticated requests, media model creation, and download streaming. |
| `MediaContentDetector.kt` | Content sniffing and structural validation for supported image and MP4 responses. |
| `InstagramLoginActivity.kt` | Full-screen login WebView and session capture. |
| `AuthenticatedMediaResolverActivity.kt` | Temporary authenticated WebView that observes a media page and returns resolved candidates. |
| `SecureSessionStore.kt` | Android Keystore encryption, session persistence, restoration, and clearing. |

The UI remains platform-neutral. Source-specific network logic is isolated behind the downloader and authentication components so that future resolvers can be added without changing the main interaction model.

## Media resolution

Resolution is intentionally layered because public and signed-in pages expose different data.

1. Normalize and validate the submitted URL.
2. Try public network extraction where possible.
3. If an encrypted session exists or public extraction cannot resolve the item, open the authenticated resolver.
4. Let WebView restore the signed-in session and load the requested page.
5. Observe document markup, media elements, performance entries, and network requests for candidate URLs.
6. Strip byte-range fragments and other partial-response parameters from candidates.
7. Validate candidate responses before presenting or downloading them.
8. Select the strongest complete video candidate while preserving the best available cover image.

The resolver must never assume that a URL ending in `.jpg` or `.mp4` contains that format. Services frequently return HTML error pages, partial byte ranges, or audio streams under misleading URLs.

## Authenticated sessions

### Login flow

`InstagramLoginActivity` opens the service's normal web login page in Android WebView. Credentials are submitted directly to that page; Acqua does not receive or store the password.

After a successful login:

1. WebView cookies and relevant browser state are collected.
2. `SecureSessionStore` encrypts the serialized session with AES-GCM.
3. The encryption key is generated and held by Android Keystore.
4. The encrypted payload is stored in app-private preferences.

Backup rules exclude the session payload so it is not copied to cloud backup or transferred to another device.

### Resolver flow

`AuthenticatedMediaResolverActivity` creates a temporary WebView, restores the saved cookies, visits the submitted URL, and returns media candidates to `MainActivity`. The activity is a resolver, not a second permanent browser UI.

Logout clears both the persisted encrypted session and active WebView cookies. Debug and release builds maintain independent sessions because their application IDs and storage sandboxes differ.

### Security boundaries

- Never log cookies, authorization headers, session payloads, or full authenticated responses.
- Never persist passwords.
- Keep session preferences excluded from backup and device transfer.
- Do not weaken certificate validation or WebView safe-browsing behavior.
- Do not add automation intended to bypass access controls, challenges, rate limits, or account protections.

## Validation and downloads

`MediaContentDetector` examines response bytes and metadata before a file is accepted:

- JPEG, PNG, GIF, and WebP are identified by their signatures.
- MP4 requires a valid `ftyp` box and a complete enough response for safe storage.
- HTML, JSON error payloads, audio-only streams, and unsupported formats are rejected.
- Partial `206` responses and truncated videos are not treated as finished downloads.

Downloads use the same resolved media item shown in preview. This keeps preview dimensions, file size, thumbnail, extension, and saved content aligned.

When changing extraction logic, test at least:

- A public photo post.
- A public video or Reel.
- A carousel containing both image and video items.
- Signed-in-only content visible to the test account.
- A Story visible to the test account.
- An unavailable or deleted URL.
- A response that returns HTML under a media-looking URL.
- A partial video response.

Use test accounts and content you control. Repeated automated requests can trigger service safeguards.

## Storage and history

Acqua writes downloads through Android's supported storage APIs and lets the user select a destination. Filename settings are applied before the file is created.

History stores local download records used by the Compose dashboard. Authentication state is separate from history and must not be mixed into user-visible records or exports.

If the schema changes, preserve existing entries or provide an explicit migration. Avoid destructive database recreation in production builds.

## Testing

Run unit tests:

```bash
cd acqua-app
./gradlew :app:testDebugUnitTest
```

Run Android instrumentation tests on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Create both APK variants:

```bash
./gradlew :app:assembleDebug :app:assembleRelease
```

Useful verification points:

- `MediaContentDetectorTest` covers format detection and corrupt/partial payload rejection.
- Install debug and release together and verify their names and independent data.
- Confirm session login, app restart restoration, authenticated resolution, and logout.
- Confirm image and video previews match the files that are downloaded.
- Check light/dark themes and compact/expanded layouts.

## Release checklist

1. Update `CHANGELOG.md` with concise user-visible changes.
2. Set `versionName` and `versionCode` in `app/build.gradle.kts` only when a version change is requested.
3. Keep debug and release application IDs and labels distinct.
4. Run unit tests and a release build.
5. Verify APK output names.
6. Test a clean install and an upgrade from the previous release.
7. Verify login, authenticated extraction, preview, download, history, and logout.
8. Confirm no secrets, cookies, local paths, or test credentials are committed.
9. Validate the website in `docs/` and update version references if necessary.

## Troubleshooting

### The login page is blank

- Confirm Android System WebView and the browser engine are up to date.
- Check that the device has working network access and correct date/time.
- Inspect Logcat for WebView renderer or TLS failures without printing session data.

### Login succeeds but extraction is unauthenticated

- Confirm the session was saved before closing the login activity.
- Confirm cookies are restored before the resolver loads the media URL.
- Remember that debug and release builds do not share sessions.

### A video opens in WebView but no preview appears

- Inspect which candidate URLs were observed.
- Verify range parameters are removed before the final request.
- Confirm the candidate is a complete video rather than an audio track or thumbnail.
- Preserve the cover image independently from video selection.

### A downloaded file is corrupt

- Check the HTTP status, content length, and signature before writing.
- Reject `206 Partial Content` unless a complete range assembly is implemented.
- Confirm the downloaded response passes `MediaContentDetector` rather than trusting its extension.

## Contributing

Keep changes scoped, update the changelog for user-visible behavior, and include tests for extraction or validation changes. Contributions are accepted under the terms in [LICENSE.md](LICENSE.md).
