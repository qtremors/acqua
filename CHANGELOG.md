# Acqua Changelog

All notable user-visible changes are documented here.

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
