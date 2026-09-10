# Acqua - Releases

> **Project:** Acqua
> **Version:** 0.2.0
> **Last Updated:** 2026-09-10

| Version | Release Date | Key Focus |
| :--- | :--- | :--- |
| [v0.2.0](#v020) | 2026-09-10 | Integrated dashboard browser, swipeable tabs and floating toolbar, adaptive media carousel, audio tagging and filename templates, and history restoration |
| [v0.1.5](#v015) | 2026-08-30 | Minimal onboarding, atomic backup and restore, dynamic M3 theming, fast scrollbar, security protections, Coil thumbnail pipeline, and in-app self-updater |
| [v0.1.0](#v010) | 2026-08-01 | Initial release with smart media extraction, yt-dlp integration, private browser, and history management |

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
