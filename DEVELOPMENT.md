# Acqua Development Guide

| Metadata | Value |
| :--- | :--- |
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
| Debug | Acqua Debug | `dev.qtremors.acqua.debug` | `<version>-debug` |
| Release | Acqua | `dev.qtremors.acqua` | `<version>` |

Both use the version code derived from the release version by removing its dots. The distinct application IDs allow both variants to be installed on the same device without sharing app data or sessions.

APK output names are generated from the variant version and ABI:

- `Acqua-<version>-debug-arm64-v8a.apk`
- `Acqua-<version>-arm64-v8a.apk`

Equivalent `armeabi-v7a`, `x86`, and `x86_64` outputs are produced. There is no universal APK; this avoids packaging four complete native processing runtimes into every download. Release builds enable R8 minification and resource shrinking.

Variant configuration lives in `acqua-app/app/build.gradle.kts`. The manifest reads `${appLabel}`, so do not hard-code the display name in `AndroidManifest.xml`.

## Architecture

Acqua is a Compose-based Android application with a main dashboard activity, a persistent shared-browser activity, a dedicated download activity, and a temporary rendered-page resolver activity.

```text
Shared link / pasted URL
          |
          v
   MainActivity (Android boundary)
          |
          v
   Feature ViewModels + Compose screens
          |
          +---- source adapters ----> direct or processed media
          |
          +---- browser extraction -> RenderedPageResolverActivity
                                             |
                                             v
                                      shared WebView profile
                                             |
                                             v
                                      candidate media URLs
                                             |
                                             v
                                    validation and download

BrowserActivity (kept alive)
          |
          +---- current URL -------> DownloadActivity
                                             |
                                             +---- extraction -> RenderedPageResolverActivity
                                             +---- preview and storage
                                             |
                                             v
                                  finish back to the same WebView
```

### Key files

| File | Responsibility |
| :--- | :--- |
| `MainActivity.kt` | Permission and activity-result launchers at the Android boundary. |
| `DashboardScreen.kt` | Top-level feature navigation. |
| `feature/*/*ViewModel.kt` | Lifecycle-aware state and feature orchestration. |
| `feature/*/*Screen.kt` | Downloader, browser, history, and settings UI. |
| `resolver/*` | Source adapters, network extraction fallbacks, and authenticated requests. |
| `downloader/*Engine.kt` | Processed-media inspection and download requests, cookies, progress, cancellation, and temporary-file cleanup. |
| `downloader/*DownloadWorker.kt` | Persisted foreground direct and processed downloads, notifications, retry, and storage import. |
| `downloader/*DownloadCoordinator.kt` | Enqueues, observes, and cancels foreground download work. |
| `downloader/*Runtime.kt` | Serialized runtime initialization, execution, updates, and cancellation. |
| `MediaDownloader.kt` | Source-neutral media validation, preview fetching, and complete-file streaming. |
| `ResolvedMedia.kt` | Resolved-media model with kind, MIME type, and file extension. |
| `MediaResolver.kt` | Resolver contract shared by source adapters. |
| `MediaResolutionService.kt` | Source selection, browser fallback, validation, and result selection. |
| `MediaStorage.kt` | MediaStore and legacy FileProvider-backed download storage. |
| `HistoryRepository.kt` | History schema, migration, and persistence. |
| `AppSettingsRepository.kt` | Typed download and browser-extraction preferences. |
| `MediaContentDetector.kt` | Content sniffing and structural validation for supported image and MP4 responses. |
| `BrowserActivity.kt` | Shared browser UI, login persistence, and full-screen floating page controls. |
| `DownloadActivity.kt` | Browser-launched extraction, preview, permission, and save flow that leaves the live browser underneath. |
| `SavedWebsiteRepository.kt` | Saved website and favicon persistence. |
| `BrowserDataManager.kt` | Selected-origin and full browser-data clearing. |
| `WebLink.kt` | Generic HTTP(S) normalization, shared-text extraction, and known-source detection. |
| `RenderedPageResolverActivity.kt` | Temporary WebView that observes a submitted page and returns media candidates. |
| `session/*` | Session persistence and Android Keystore protection for reusable extraction cookies. |

The UI remains source-agnostic. Source-specific extraction stays in resolver components, while validation, storage, history, settings, and session persistence use source-neutral models and repositories. Stateful services are constructed per application boundary rather than exposed as global Kotlin objects. Keep each Kotlin source file below 700 lines; split by responsibility before it reaches that limit.

## Media resolution

Resolution is layered because known sources and generic websites expose media differently.

1. Normalize any valid HTTP or HTTPS URL.
2. Ask matching source adapters for direct or selectable media formats.
3. Prefer an already-resolved 1080p result; otherwise compare available candidates and choose the larger valid video.
4. Load other pages in the rendered-page resolver when automatic browser sessions are enabled or the user explicitly downloads from the live browser.
5. Observe document markup, media elements, metadata, performance entries, and network requests.
6. Strip byte-range fragments and other partial-response parameters from candidates.
7. Carry the page referrer and relevant domain cookies into validation and download requests.
8. Deduplicate and cap candidates, validating at most four direct files concurrently.
9. Report a clean unsupported-media error when no complete file is exposed.

The resolver must never assume that a URL ending in `.jpg` or `.mp4` contains that format. Services frequently return HTML error pages, partial byte ranges, or audio streams under misleading URLs.

## Authenticated sessions

### Browser flow

`BrowserActivity` provides one address bar and one shared WebView profile for all websites. Credentials are submitted directly to the loaded website; Acqua does not receive or store passwords. Download launches `DownloadActivity` above the browser instead of finishing it, preserving the live page, scroll position, navigation history, forms, session storage, and transient JavaScript state during normal operation. `WebView.saveState()` and `restoreState()` provide best-effort URL and history restoration after activity or process recreation.

WebView keeps cookies and site storage in the application sandbox. The user can add a named website bookmark before opening the browser; `SavedWebsiteRepository` stores its display name, origin, and cached favicon for the Browser tab. `BrowserDataManager` only coordinates data clearing. Cookies reused by network adapters are copied into an encrypted session store protected by AES-GCM and Android Keystore.

The **Use browser sessions for extraction** preference is opt-in. When disabled, network adapters do not proactively reuse saved session cookies. An explicit Download action in the browser authorizes the dedicated download flow to use the shared WebView session for that media while leaving the preference disabled.

Backup rules exclude both the encrypted session payload and WebView data from cloud backup and device transfer.

### Resolver flow

`RenderedPageResolverActivity` creates a temporary WebView using the same cookie profile, visits the submitted URL, and returns media candidates to the calling main or download activity. Existing encrypted adapter cookies are migrated into WebView when required.

**Manage Website Data** can clear selected origins or all browser data. Selected clearing expires addressable cookies, deletes origin storage, removes the bookmark and favicon, and clears matching encrypted adapter sessions. Full clearing additionally removes all WebView cookies and storage, shared cache, form data, HTTP authentication, registry metadata, and encrypted adapter sessions. Debug and release builds maintain independent browser data because their application IDs and storage sandboxes differ.

### Security boundaries

- Never log cookies, authorization headers, session payloads, or full authenticated responses.
- Never persist passwords.
- Keep session preferences excluded from backup and device transfer.
- Accept only HTTP(S) top-level navigation and keep file/content access disabled.
- Do not override SSL errors or disguise WebView as another client.
- Do not weaken certificate validation or WebView safe-browsing behavior.
- Do not add automation intended to bypass access controls, challenges, rate limits, or account protections.

## Validation and downloads

`MediaContentDetector` examines response bytes and metadata before a file is accepted:

- JPEG, PNG, GIF, and WebP are identified by their signatures.
- MP4 requires a structurally complete `ftyp` box followed by another valid ISO media box.
- HTML, JSON error payloads, audio-only streams, and unsupported formats are rejected.
- Partial `206` responses and truncated videos are not treated as finished downloads.

Downloads use the same resolved media item shown in preview. This keeps preview dimensions, file size, thumbnail, extension, and saved content aligned.

All downloads run as persistent foreground work. Direct carousel items use a controlled queue, while processed downloads use an app-cache task directory. The processing engine merges separate streams or converts extracted audio, then `MediaStorage` imports the completed file into Downloads and deletes the temporary task. Notifications and the downloader UI expose progress and cancellation, and transient network failures use bounded retry. Quality selectors account for video orientation; metadata, chapters, and cover artwork are optional. Runtime updates and downloads share a read/write lock so an update cannot replace the executable during an active job.

When changing extraction logic, test at least:

- A public photo post.
- A public video.
- A carousel containing both image and video items.
- Signed-in-only content visible to the test account.
- Signed-in media visible to the test account.
- An unavailable or deleted URL.
- A response that returns HTML under a media-looking URL.
- A partial video response.
- A video with separate video and audio streams.
- An audio link saved as original audio, M4A, and MP3.

Use test accounts and content you control. Repeated automated requests can trigger service safeguards.

## Storage and history

Acqua writes downloads through Android's supported storage APIs into a configurable subfolder under Downloads. Filename settings are applied before the file is created. The Settings page exposes the supported placeholders, including `{title}` for processed media, as toggleable chips and can restore `acqua_{username}_{resolution}_{date}_{time}_{index}` as the default pattern.

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
- `WebLinkTest` covers generic URL normalization, shared-text extraction, and known-source routing.
- `MediaResolutionServiceTest` covers source routing, browser fallback, validation, and quality selection.
- Feature-state tests cover history filtering, while Android tests cover history, settings, saved websites, encrypted sessions, and MediaStore-backed storage.
- Install debug and release together and verify their names and independent data.
- Confirm browsing, login persistence, app restart restoration, authenticated resolution, and **Clear Data**.
- Try a direct media URL, a generic HTML page with media metadata, and an unsupported page.
- Confirm image, video, and audio history entries match the files that are downloaded.
- Check light/dark themes and compact/expanded layouts.

## Release checklist

1. Update `CHANGELOG.md` with concise user-visible changes.
2. Set `versionName` and `versionCode` in `app/build.gradle.kts` only when a version change is requested.
3. Keep debug and release application IDs and labels distinct.
4. Run unit tests and a release build.
5. Verify APK output names.
6. Test a clean install and an upgrade from the previous release.
7. Verify browser navigation, login persistence, generic and specialized extraction, preview, download, history, and data clearing.
8. Confirm no secrets, cookies, local paths, or test credentials are committed.
9. Validate the website in `docs/` and update version references if necessary.

## Troubleshooting

### A website is blank

- Confirm Android System WebView and the browser engine are up to date.
- Check that the device has working network access and correct date/time.
- Inspect Logcat for WebView renderer or TLS failures without printing session data.

### Login succeeds but extraction is unauthenticated

- Confirm the login was completed in Acqua Browser rather than an external browser.
- Confirm the website remains signed in after closing and reopening Acqua Browser.
- Check that the media host receives only cookies valid for its domain and the correct page referrer.
- Remember that debug and release builds do not share sessions.

### A valid link returns no media

- Confirm the page exposes a direct image or video URL in markup, metadata, a media element, or observable requests.
- Generic resolution does not assemble segmented streams or bypass DRM/access controls.
- Add a source adapter when a website needs structured extraction beyond the generic resolver.

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
