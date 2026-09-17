# Acqua Changelog

All notable user-visible changes are documented here.

## [0.3.0] - 2026-09-15

### Changed

- Split domain, data, shared UI, downloads, browser, settings, updates, and onboarding into enforced Gradle modules.
- Expand the local release gate across every module's tests, debug and release lint, minified artifacts, build conventions, and static-site validation.
- Make downloader and settings orchestration deterministic through focused dependency contracts and restored-state tests.
- Align website and developer guidance with supported sources, media formats, quality controls, APK architectures, and site maintenance practices.
- Rename GitHub Tracker to Feeds, with GitHub releases as the first source and source-neutral navigation ready for additional feed types.

### Fixed

- Keep notification cancellation safe across WorkManager and Android 14 user-initiated download paths.
- Show installed and GitHub release versions in Feeds with consistent labels, without repeating the current version.
- Resolve complete Instagram carousels from embedded page data and wait for real slide changes during the rendered-page fallback.
- Isolate Instagram request cookies, try public access first, and retry login-gated posts with the saved browser session.

## [0.2.9] - 2026-09-13

### Changed

- Refine accent-color selection, app information, open-source notices, and backup feedback.
- Improve website navigation accessibility and progressively enhance reveal animations.

### Fixed

- Make the website usable without JavaScript and cache public repository statistics without layout thrashing.
- Enforce strict release-link validation and a restrictive website content security policy.
- Keep user-facing resource selection deterministic across updater, onboarding, and settings state changes.

## [0.2.8] - 2026-09-13

### Added

- Add adaptive rail navigation and width-bounded content for larger Android windows.

### Changed

- Keep downloader, browser, and history workflow state through recreation.
- Improve saved-site and toggle accessibility with discoverable full-row actions.

### Fixed

- Make rendered-page carousel extraction language-independent and more resilient across supported sites.

## [0.2.7] - 2026-09-13

### Changed

- Refine Settings and GitHub update management with clearer grouped controls and repository actions.

### Fixed

- Harden GitHub API pagination, timeout, rate-limit, and release-link handling.
- Keep updater failures localized while excluding remote response data and private identifiers from diagnostics.

## [0.2.6] - 2026-09-13

### Added

- Add recoverable storage and notification permission guidance with retry and system-settings paths.

### Changed

- Refresh Downloader and History controls with Material 3 Expressive motion and grouped menus.
- Move History search to the floating action button, tighten spacing, remove source grouping, and scale loading with indexed paging.
- Simplify Fetch, paste, and link guidance across the Downloader.

### Fixed

- Improve media thumbnails and timestamps in galleries and third-party file pickers.
- Report accurate image dimensions and file sizes in previews and filenames.
- Identify completed image, video, and audio downloads clearly in app feedback and system notifications.
- Avoid silent network reloads when browsing download history.

## [0.2.5] - 2026-09-13

### Changed

- Serialize download work, throttle progress updates, and make direct transfers cancellation-aware.
- Run user-started transfers through Android 14 data-transfer jobs with serialized fallback, durable phase recovery, and correct media-processing service classification.

### Fixed

- Store final MediaStore filenames, bound preview image decoding, and remove interrupted pending rows and temporary processing files after process restart.
- Keep backup, onboarding, and download failures localized while excluding private identifiers from diagnostics.
- Exclude private browsing and download state from Android backup and device transfer.
- Prevent recursive WorkManager initialization while creating the application dependency graph.

## [0.2.4] - 2026-09-13

### Changed

- Strengthen local build verification with strict release artifact, version, dependency, string, and source-quality checks.
- Establish enforceable domain and feature dependency boundaries without changing user data.

## [0.2.3] - 2026-09-12

### Changed

- Streamline bottom navigation to 3 core tabs: Download, Browser, and GitHub Tracker.
- Unify the Download screen with dedicated sub-tabs for the Downloader and live Progress & History.
- Display active in-flight downloads with live progress, byte counters, ETA, and cancellation controls directly above completed history records.
- Open the Settings overlay directly when pulling down or tapping the top brand header card, showing "Settings" as the drag destination indicator.
- Feature the brand hero in Settings with dynamic pull-to-expand logo growth that scales to cover the viewport, application ID and version chips on a single horizontal row wrapped in grouped containers, quick action buttons grouped three per container (Issues, GitHub, Releases and Notices, Privacy, License) with a Check for updates action container, and semantically tailored icons.
- Navigate directly to Open-Source Notices and License from the Settings hero buttons and return immediately to Settings upon dismissal, removing redundant intermediate About screens.
- Open the Progress & History tab directly by tapping the active download status card on the Downloader screen.

## [0.2.2] - 2026-09-12

### Added

- Track public or private GitHub repositories with APK releases, including pre-release selection, automatic device-architecture matching, and optional installed-app linking.
- Browse available updates, release notes, and repository READMEs from a dedicated navigation tab, then download and open compatible APKs in Android's installer with visible progress and resumable transfers.
- Start with Acqua, Arcile, Filion, EarnSlate, Material Design, and Osyster tracked by default, while keeping every repository removable.
- Refresh from the navigation action and switch it to Update when an update is available.
- Add repositories from a focused link field positioned directly above the keyboard.
- Add encrypted personal access token support for authenticated GitHub requests, with tokens excluded from app backups and repository export files.
- Import and export tracked-repository lists without changing installed apps.

### Changed

- Verify package identity, version, Android compatibility, and signing certificates before updating a linked installed app.

## [0.2.1] - 2026-09-12

### Changed

- Make History easier to browse with compact tappable rows, persistent search and filters, date sections, and optional source grouping.
- Add History multi-select sharing and record removal with Undo, while keeping downloaded files.
- Add file details, source-link actions, readable storage locations, and an unavailable-files filter with retry actions.
- Refresh History when downloads finish and show clear loading, failure, and category-specific empty states.
- Omit filename indexes for single media items and retain carousel numbering in previews and downloads.
- Automatically select Acqua for pasted Instagram posts and yt-dlp for reels, while allowing manual engine changes.
- Hide browser controls when scrolling down and reveal them when scrolling up.
- Keep carousel extraction running through slide URL changes and recognize role-based Next controls.
- Bound browser extraction callbacks, handle extraction failures, and detach browser views before releasing them.
- Prevent a focused browser page from crashing when returning Home, and move saved-site disk work off the UI thread.

## [0.2.0] - 2026-09-10

### Changed

- Preserve browser back and forward history when its screen is recreated, and discard it when returning Home or clearing browser data.
- Keep punctuation-only media titles from producing empty filenames by trying the artist, uploader, then a generated name.
- Open website download links on the GitHub release page in a new tab.

## [0.1.9] - 2026-09-10

### Changed

- Browse websites within the dashboard with saved website shortcuts and media extraction.
- Switch dashboard tabs by swiping or using the floating navigation toolbar and contextual actions.
- Apply session-setting changes to the live browser and disable session reuse when clearing all browser data.
- Cancel pending extraction and release the WebView when leaving the browser.

## [0.1.8] - 2026-09-10

### Changed

- Add a media carousel with adaptive sizing and a separate metadata card showing the proposed filename.
- Show audio artwork, title, artist, album, and file size with synchronized scrolling for long metadata.
- Open About from the Downloader header or a downward pull, including when no media is loaded.
- Use a floating toolbar for browser-launched download previews.

## [0.1.7] - 2026-09-10

### Changed

- Refresh About, legal notices, and History layouts with consistent spacing and scrolling.
- Add animated empty states for History and search results, respecting reduced-motion settings.

## [0.1.6] - 2026-09-10

### Changed

- Add separate audio filename patterns with artist and album variables, a live preview, and title-only defaults.
- Carry audio titles, artists, albums, and media types through downloads and saved files.
- Recognize supported audio file signatures while keeping MP4 videos distinct from M4A and AAC distinct from MP3.

## [0.1.5] - 2026-08-30

### Added

- Add a minimal, expressive onboarding experience guiding new users through feature highlights, notification permissions, storage destination, and appearance setup.
- Add an atomic backup and restore engine with SHA256 integrity verification, schema validation, and automatic reverse rollback on failure.
- Add Backup and Restore management section in Settings with export and restore confirmation previews.
- Add in-app self-updater for release builds fetching latest GitHub releases, architecture-matched APKs, real-time download progress, and direct installation.

### Fixed

- Include app, download, browser-session, and media-processing preferences in settings backups while preserving them when restoring older backups.
- Add an opt-in screen-protection toggle for browser, resolver, downloader, and main app surfaces, disabled by default.
- Load history image, video, and audio thumbnails through the bounded Coil pipeline on every supported Android version.
- Select the correct updater APK on 32-bit x86 devices and keep denied notification permission state accurate during onboarding.
- Validate downloaded updates before installation, reject incompatible APK assets, and continue installation after permission is granted.
- Keep existing users out of first-run onboarding, refresh visible settings after restore, and clearly label values that a backup will restore, reset, or leave unchanged.
- Prevent the hidden History scrollbar from intercepting edge gestures and apply saved appearance settings to browser-launched downloads.

## [0.1.4] - 2026-08-30

### Added

- Introduce dynamic Material 3 tonal palette generation, OLED pure black theme, custom presets, and expressive color selectors in Settings.
- Add AndroidX Core Splash Screen with smooth startup transition and preloaded preference state.
- Add fast scrollbar with fluid motion stretch and index label tooltip for media lists.
- Add background memory cache eviction and secure window protection lifecycle support.
- Add high-performance video, audio artwork, GIF, and SVG thumbnail pipeline powered by Coil.

## [0.1.3] - 2026-08-30

### Changed

- Redesign the Settings, About, and Open Source Notices screens with Material 3 Expressive grouped segmented lists and unified design tokens.
- Upgrade Android Gradle Plugin, Kotlin, and Compose toolchains, adding automated build convention and production string verification gates.
- Display live GitHub repository and release statistics on the project website with improved mobile resolution support and zero horizontal overflow.
- Add comprehensive Credits and acknowledgements section to the project website and documentation.

## [0.1.2] - 2026-08-30

### Added

- Send completed download notifications with tap-to-open actions for saved media.

### Changed

- Modernize the Downloader layout with adaptive video and uncropped square photo previews.
- Compact bottom controls into a full-width link field above a side-by-side engine chip and primary action button.
- Label download buttons with specific item counts and media types (such as Download all (5), Download video, or Download image).
- Move active download progress below the preview area and dock bottom controls flush above the keyboard.
- Streamline the configuration sheet to focus on engine switching and yt-dlp media options.
- Adopt official Material 3 Expressive components across the Downloader screen.

### Fixed

- Prevent completed downloads from lingering at 100% in the status bar and app interface.
- Prevent broken video thumbnails in media pickers by restricting cover art embedding to audio files.
- Prevent text clipping in active download progress cards.

## [0.1.1] - 2026-08-30

### Added

- Let users choose video or audio output, quality, format, metadata, and artwork before Acqua fetches a link preview.
- Remember the last download engine and media configuration for the next link.
- Add website and DuckDuckGo search entry from the Browser tab.
- Cache image previews in bounded private temporary storage and decode them at screen size for memory-safe large carousels.
- Show the active WebView provider and version in Settings with a shortcut to check for updates.

### Changed

- Use yt-dlp as the default video and audio download engine while keeping Acqua available for direct media extraction.
- Expand media previews into an edge-to-edge swipeable carousel and replace large progress panels with a compact circular wave indicator.
- Give Acqua Browser a persistent address bar with back, forward, home, refresh or stop, media download, and return controls.
- Keep shared and pasted links in download configuration, and offer browser recovery only after resolution or download failures.
- Make optional Images, Videos, and Audio folder organization explicit, and remove the Acqua prefix from default filenames.
- Report whether yt-dlp was updated or already current, together with the last successful update check.

### Fixed

- Enable individual yt-dlp preview downloads and route them through the configured yt-dlp processing options.

## [0.1.0] - 2026-08-01

### Added

- Add an About page with app and device details, privacy information, project links, support links, and bundled license and open-source notices.
- Support release signing with the Arcile certificate through ignored local signing properties.

### Changed

- Give debug installs a separate application ID, label, and `Acqua-Debug` APK name while keeping production releases under the `Acqua` identity.
- Produce a universal APK alongside the four ABI-specific packages, and make it the website's default release download.
- Show the latest published version and download count dynamically on the website, and use live GitHub release badges in the README.
- Use Acqua as the default download engine and offer yt-dlp as an explicit alternative.

## [0.0.9] - 2026-08-01

### Added

- Search download history by filename, source link, or media type, see category and storage summaries, and sort by newest, oldest, or largest.
- Detect missing downloaded files, re-fetch their source, open the system Downloads view, and preview filename patterns in Settings.

### Changed

- Move history actions below each record so filenames and large text have more room on phones.
- Confirm before clearing history and explain that downloaded files remain on the device.

### Fixed

- Preserve existing files on Android 7 through 9 by adding a numeric suffix when a filename already exists.
- Record the final collision-safe filename in history.

## [0.0.8] - 2026-08-01

### Added

- Add automatic engine selection with manual Acqua and yt-dlp overrides.
- Identify invalid links, session requirements, network failures, unavailable engines, and unsupported media separately.
- Keep direct, carousel, video, and audio downloads running as persistent foreground work.
- Show active background download count, aggregate progress, and cancellation after returning to Acqua.

### Changed

- Deduplicate and cap rendered media candidates while limiting validation to four concurrent requests.
- Require a structurally complete MP4 file-type box and a following media box before accepting video data.
- Queue direct carousel items in order and retry temporary network interruptions with bounded backoff.
- Request notification access for every background download rather than processed media only.

### Fixed

- Avoid excessive validation traffic on pages that expose repeated or noisy media candidates.
- Offer browser recovery only for failures that can reasonably benefit from a browser session.
- Preserve direct download progress and completion when the downloader screen or app process closes.

## [0.0.7] - 2026-08-01

### Added

- Choose Acqua or yt-dlp for each link, with yt-dlp video, audio, quality, format, metadata, and artwork controls.
- Support Android themed icons with a dedicated monochrome Acqua mark.

### Changed

- Use Acqua as the name of the built-in direct-media downloader.
- Use scalable Acqua vector artwork for app launcher, browser action, notification, README, and website branding.

### Fixed

- Keep download previews open after one or all items finish so returning to the browser remains a user choice.
- Resolve floating-ball downloads without always reopening the current page in another WebView.
- Recover complete Instagram carousel data from rendered page JSON instead of stopping at the first fallback item.
- Preserve available source dates in downloaded media and honor the disabled metadata option for audio downloads.
- Limit oversized media previews to prevent excessive memory use.
- Remove abandoned processing files and temporary session cookies after interrupted downloads.

## [0.0.6] - 2026-07-19

### Added

- Add clean, scalable Acqua logo artwork with rounded expressive contours and artifact-free vector edges, used throughout the README and website.

### Changed

- Produce smaller APKs for ARM64, ARM32, x86, and x86_64 instead of one universal package.
- Present Acqua consistently as a media downloader throughout the project documentation and website.
- Redesign the project website with a responsive Material 3 Expressive visual system and clearer download flow.

## [0.0.5] - 2026-07-19

### Added

- Download supported media as video or audio with selectable quality, audio format, metadata, artwork, progress, and cancellation.
- Keep the bundled extraction engine current through automatic weekly or manual stable-channel updates.
- Show audio downloads separately in history and support processed-media titles in filename patterns.
- Continue long-running downloads as foreground work when Acqua's screen closes, with notification and in-app cancellation.

### Changed

- Improve resolution selection and processing across supported media links.
- License Acqua under GNU GPL v3 or later and document bundled runtime components.

### Fixed

- Report and save video resolution from the selected video stream instead of its preview thumbnail.
- Skip unnecessary quality inspection when extraction already provides 1080p, honor disabled chapter metadata, and label portrait quality by its short edge.

## [0.0.4] - 2026-07-19

### Added

- Touch and hold saved browser bookmarks to edit their names and links while retaining automatic website icons.

### Fixed

- Keep signed-in browser pages open and intact while media is previewed and saved in a dedicated download screen.

## [0.0.3] - 2026-07-18

### Fixed

- Preserve JPEG, PNG, GIF, WebP, and MP4 file types and reject partial or truncated downloads.
- Resume downloads after storage permission is granted and restore opening and sharing on Android 7 through 9.

### Changed

- Clarified browser extraction, saved-website, and Downloads-subfolder wording.
- Hardened custom filenames and download-folder names against invalid path characters.
- Isolated downloader, browser, history, settings, session, network, and storage responsibilities for more reliable lifecycle handling and maintenance.
- Moved interface copy into Android resources and expanded regression coverage across resolution, persistence, sessions, and storage.

## [0.0.2] - 2026-07-18

### Added

- Dedicated Browser tab with named website bookmarks, persistent logins, optional session-backed downloads, and selected or complete website-data clearing.
- Generic HTTP and HTTPS media-link handling with a full-screen browser and movable download controls.
- Clickable filename variables with an Acqua-default reset, plus non-intrusive media-resolution status.

## [0.0.1] - 2026-07-17

### Added

- Material 3 downloader with link paste, Android sharing, previews, download history, and storage settings.
- Photo, video, and multi-item media extraction from supported links.
- Optional authenticated browser sessions encrypted with Android Keystore for content available to the signed-in account.
- Media-type and completeness validation to prevent corrupt, partial, audio-only, or mislabeled downloads.
- Custom download folders and configurable filenames.
- Separate `Acqua Debug` and `Acqua` applications that can be installed side by side.
- Project documentation, TSL license, and static website.
- Responsible-use guidance covering ownership, permission, privacy, and service terms.

### Changed

- Simplified the project branding and used the Acqua artwork across the README and website.

### Fixed

- Preserved video thumbnails while authenticated media is resolved in WebView.
- Reported preview dimensions and file sizes from the media selected for download.
