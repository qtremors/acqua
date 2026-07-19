# Acqua Changelog

All notable user-visible changes are documented here.

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
- Photo, video, Reel, Story, and carousel extraction for the currently supported source.
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
