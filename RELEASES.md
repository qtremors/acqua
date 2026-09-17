# Acqua - Releases

> **Project:** Acqua
> **Version:** 0.3.0
> **Last Updated:** 2026-09-17

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v0.3.0](#v030) | 2026-09-15 | Multi-source Feeds foundation, GitHub APK release tracking, resilient downloads, adaptive layouts, Instagram carousel and session reliability, and modular architecture |
| [v0.2.0](#v020) | 2026-09-10 | Integrated dashboard browser, swipeable tabs and floating toolbar, adaptive media carousel, audio tagging and filename templates, and history restoration |
| [v0.1.5](#v015) | 2026-08-30 | Minimal onboarding, atomic backup and restore, dynamic M3 theming, fast scrollbar, security protections, Coil thumbnail pipeline, and in-app self-updater |
| [v0.1.0](#v010) | 2026-08-01 | Initial release with smart media extraction, yt-dlp integration, private browser, and history management |

---

# v0.3.0

**Release Date:** September 15, 2026

**Previous public release:** v0.2.0

**Development range included:** v0.2.1 through v0.3.0

Acqua v0.3.0 adds Feeds as a foundation for tracking multiple source types, beginning with GitHub APK releases. It also strengthens download execution and recovery, expands adaptive layouts and accessibility, improves Instagram carousel and authenticated-session handling, and reorganizes the app into enforced feature and core modules.

## Highlights

- **Feeds with GitHub Releases**: Track public or private GitHub repositories, inspect releases and README content, match APKs to the device architecture, link installed apps, and download verified updates. The former GitHub Tracker is now Feeds, ready for additional source types such as RSS.
- **Reliable Android Downloads**: Serialize transfers, show live progress and cancellation controls, recover durable download phases, and use Android 14 user-initiated data-transfer jobs with a safe fallback path.
- **Safer APK Updates**: Verify package identity, version, Android compatibility, and signing certificates before replacing a linked installed app.
- **Improved Instagram Resolution**: Resolve complete carousels from embedded page data, wait for confirmed slide changes during fallback extraction, isolate request cookies, try public access first, and use the saved browser session only for login-gated posts.
- **Adaptive and Accessible Interface**: Add large-screen navigation rails, width-bounded content, clearer grouped controls, discoverable full-row actions, responsive workflow restoration, and improved website accessibility.
- **Modular Architecture and Release Validation**: Split the application into enforced domain, data, shared UI, download, browser, settings, update, and onboarding modules, backed by broader tests and release checks.

## What's New Since v0.2.0

### Feeds & GitHub Release Tracking

- Added tracking for public and private GitHub repositories with APK releases, optional pre-release selection, automatic architecture matching, and installed-app linking.
- Added release notes and README browsing, resumable APK transfers, visible download progress, and direct Android installer handoff.
- Added Acqua, Arcile, Filion, EarnSlate, Material Design, and Osyster as removable default GitHub feeds.
- Added encrypted personal access token support for authenticated GitHub requests, excluding credentials from app backups and exported feed data.
- Added feed import and export without modifying installed applications.
- Hardened GitHub pagination, timeouts, rate-limit handling, and release-link validation while keeping failures localized and free of private response details.
- Improved repository controls, APK selection, installed-app matching, refresh actions, and version labels that do not repeat the current version.
- Renamed GitHub Tracker to Feeds and made navigation and actions source-neutral for future feed types.

### Downloads, Progress & History

- Unified the Download area with dedicated Downloader and Progress & History views.
- Added live in-flight progress, transferred byte counts, ETA, and cancellation controls above completed history records.
- Made History easier to browse with compact rows, persistent search and filters, date sections, optional source grouping, unavailable-file filtering, and retry actions.
- Added multi-select sharing, undoable record removal that keeps downloaded files, file details, source links, and readable storage locations.
- Serialized download work, throttled progress updates, made direct transfers cancellation-aware, and restored durable phases after process interruption.
- Added Android 14 user-initiated data-transfer jobs with serialized fallback and correct media-processing service classification.
- Improved MediaStore filename persistence, preview decoding bounds, interrupted-file cleanup, gallery metadata, image dimensions, timestamps, and completion feedback.
- Added recoverable storage and notification-permission guidance with retry and system-settings actions.

### Browser & Media Resolution

- Automatically selects Acqua for pasted Instagram posts and yt-dlp for reels while preserving manual engine selection.
- Keeps carousel extraction active across slide URL changes and recognizes role-based Next controls without relying on localized text alone.
- Resolves complete Instagram carousels from embedded page data and confirms that rendered-page media changed before advancing again.
- Isolates Instagram cookies between requests, attempts anonymous resolution first, and retries login-gated public posts with the saved browser session.
- Bounds browser extraction callbacks, handles failures safely, and detaches browser views before releasing them.
- Prevents focused browser pages from crashing when returning Home and moves saved-site disk work off the UI thread.
- Preserves Downloader, Browser, and History workflow state through recreation while avoiding silent history reloads.

### Interface, Settings & Accessibility

- Streamlined primary navigation to Download, Browser, and Feeds, with Settings available from the expandable brand header.
- Refreshed Downloader and History controls with grouped Material 3 actions, tighter spacing, indexed loading, and a floating History search action.
- Redesigned the Settings hero with dynamic pull-to-expand branding, grouped application information, and direct Issues, GitHub, Releases, Notices, Privacy, License, and update actions.
- Added adaptive navigation rails and width-bounded layouts for larger Android windows.
- Improved saved-site and setting-toggle accessibility with discoverable full-row actions.
- Refined accent selection, application information, open-source notices, backup feedback, and deterministic resource selection.

### Architecture, Privacy & Release Quality

- Split domain, data, shared UI, downloads, browser, settings, updates, and onboarding into enforced Gradle modules.
- Expanded automated tests and the local release gate across modules, debug and release lint, minified artifacts, build conventions, and static-site validation.
- Made downloader and settings orchestration deterministic through focused dependency contracts and restored-state coverage.
- Excluded private browsing and download state from Android backup and device transfer.
- Prevented recursive WorkManager initialization and kept notification cancellation safe across supported Android download paths.
- Improved website navigation accessibility, no-JavaScript usability, progressive enhancement, repository-stat caching, release-link validation, and content security policy enforcement.

---

# v0.2.0

**Release Date:** September 10, 2026

**Previous public release:** v0.1.5

**Development range included:** v0.1.6 through v0.2.0

Acqua v0.2.0 brings integrated web browsing directly into the dashboard, swipeable tabs with an adaptive floating navigation toolbar, a rich media preview carousel with detailed metadata cards, customizable audio filename templates with artist and album variables, reliable filename fallbacks for punctuation-only titles, and browser navigation history restoration.

## Highlights

- **Integrated Dashboard Browser**: Browse websites directly within the main dashboard with quick shortcuts, seamless media extraction, and full back and forward navigation history preserved across screen recreations.
- **Swipeable Tabs & Floating Navigation**: Fluidly swipe between Downloader, Browser, and History tabs with an adaptive floating toolbar offering quick contextual actions.
- **Adaptive Media Carousel & Metadata Card**: Dynamically sized video and photo previews paired with a dedicated metadata card previewing proposed filenames, file sizes, artwork, and descriptions with synchronized scrolling.
- **Audio Tagging & Dedicated Filename Templates**: Full audio metadata extraction (title, artist, album, cover art) with independent audio filename patterns, live previews in Settings, and strict audio format distinction (separating AAC from MP3 and M4A from MP4).
- **Reliable Filename Fallback System**: Automatic safety fallbacks that prevent empty filenames when media titles contain only punctuation or symbols by resolving artist, uploader, or clean generated names.
- **Refreshed Layouts & Expressive Empty States**: Polished About, Legal Notices, and History screens with smooth scrolling, consistent layout spacing, and animated motion-respecting empty states.

## What's New Since v0.1.5

### Dashboard & In-App Browser

- Integrated web browsing directly into the main dashboard alongside Downloader and History.
- Enabled horizontal swipe gestures between tabs and introduced a floating toolbar with contextual shortcuts.
- Preserved browser back and forward navigation history across screen recreation while cleanly resetting history on Home or when clearing browser data.
- Applied active session configuration changes directly to the live browser and cleanly released WebView resources when leaving the browser screen.
- Provided a floating toolbar for browser-launched media previews and extraction.

### Media Previews & Downloader

- Added an adaptive media preview carousel that dynamically adjusts to video and image aspect ratios.
- Added a dedicated metadata card displaying the proposed filename, file size, and details before starting a download.
- Added rich audio preview cards with embedded cover art, title, artist, album, and synchronized scrolling for long metadata.
- Added a downward pull gesture and header button to quickly access the About screen directly from the Downloader.

### Audio Metadata & Custom Filenames

- Introduced separate audio filename patterns supporting `{artist}` and `{album}` variables in addition to `{title}`.
- Added a live audio filename pattern preview in Settings with title-only defaults.
- Preserved complete audio tags (title, artist, album, media type) through download execution, file output, and history records.
- Enhanced media signature detection to accurately distinguish AAC from MP3 and M4A from MP4 video containers.
- Implemented automatic fallback resolution for media with punctuation-only titles to prevent blank filenames, trying artist, uploader, then a generated name.

### Visual Design & Documentation

- Refreshed About, legal notices, and History layouts with unified spacing, alignment, and smooth scrolling.
- Added animated empty states for download history and search results that respect system reduced-motion accessibility preferences.
- Configured website download links on the GitHub release page to open in a new tab.

---

# v0.1.5

**Release Date:** August 30, 2026

**Previous public release:** v0.1.0

**Development range included:** v0.1.1 through v0.1.5

Acqua v0.1.5 introduces a minimal onboarding experience for new users, an atomic backup and restore engine with reverse rollback, dynamic Material 3 theming and presets, sensitive memory protections, a high-performance thumbnail pipeline, and an in-app GitHub self-updater for release builds.

## Highlights

- **Expressive Onboarding**: Seamless two-step setup introducing feature highlights, download notifications, storage destination, and appearance selection with predictive back gesture handling.
- **Atomic Backup & Restore Engine**: Structured JSON backup archives with SHA256 integrity verification, schema gating, and automatic reverse rollback on failure.
- **In-App GitHub Self-Updater**: Release-only updater checking GitHub releases, downloading architecture-matched APKs with live progress, and initiating direct device installation.
- **Dynamic Material 3 Theming**: Material Color Utilities tonal schemes, Pure Black OLED mode, Dracula and Tokyo Night presets, color harmonization, and responsive design tokens.
- **AndroidX Splash Screen**: Smooth cold startup animation with asynchronous preference preloading.
- **Acqua Fast Scrollbar**: Physics-based stretch scrollbar with interactive alphabet and index tooltip for download history.
- **Privacy & Security Suite**: Optional, reference-counted `FLAG_SECURE` window protection and sensitive thumbnail memory eviction on background transitions.
- **Coil Thumbnail Pipeline**: Efficient thumbnail fetchers for videos, audio cover art, GIFs, and SVGs.

## What's New Since v0.1.0

### Onboarding & Setup

- Added a two-step onboarding flow guiding users through feature highlights, notification permissions, and appearance personalization.
- Added a direct "Restore settings from backup" shortcut on the welcome page to restore existing preferences instantly.
- Handled predictive back gestures and animated page indicator capsules.

### Backup & Restore

- Added complete DataStore preference serialization and deserialization using official byte serializers.
- Enforced SHA256 integrity checksums and strict schema bounds on all backup stores.
- Built an atomic commit pipeline that rolls back all previous stores in reverse order if any store write fails.
- Integrated Storage Access Framework (SAF) document pickers and itemized restore preview dialogs in Settings.

### In-App Self-Updater

- Added automatic and manual update checking against GitHub release assets for production builds.
- Built architecture-aware asset matching for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and universal packages.
- Added live download progress tracking and direct `FileProvider` package installation.
- Disabled self-update checks on debug builds with clear status indicators.

### Theming & Visual Experience

- Added dynamic HCT tonal color scheme generation from any accent color.
- Added Pure Black OLED mode with true `#000000` surface containers.
- Added Dracula and Tokyo Night preset themes.
- Redesigned Settings and About screens with Material 3 Expressive segmented containers.

### Performance & Security

- Added Core Splash Screen with background preference preloading.
- Added background memory cache clearing and an opt-in screen-protection setting that is disabled by default.
- Integrated Coil image pipeline with custom video and audio artwork decoders.
- Added physics fast scrollbar with index bubble tooltip.

---

# v0.1.0

**Release Date:** August 1, 2026

**Previous public release:** Initial Release

**Development range included:** v0.0.1 through v0.1.0

Acqua v0.1.0 is the first public release of Acqua, a modern, privacy-focused media downloader and browser for Android.

## Highlights

- **Dual-Engine Media Downloader**: Extract media directly from social platforms or utilize yt-dlp for universal video and audio extraction.
- **Embedded Private Browser**: Secure browsing sandbox for logging into content platforms with isolated session storage.
- **Download History & Management**: Searchable download history with storage categorization, re-fetching, and system file actions.
- **Modern Material 3 Interface**: Adaptive layouts for phones and tablets, clean typography, and edge-to-edge support.
- **Full Architecture Support**: Published universal APK along with optimized `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86` packages.
