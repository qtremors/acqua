# Acqua Changelog

All notable user-visible changes are documented here.

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
