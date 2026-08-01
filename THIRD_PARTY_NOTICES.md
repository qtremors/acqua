# Third-party notices

Acqua includes or downloads the following independent runtime components.
Their names are used only to identify and credit those components.

## youtubedl-android

Android integration library used to initialize and execute the media-processing
runtime. Licensed under GNU GPL v3.0.

- Source: <https://github.com/yausername/youtubedl-android>
- License: <https://github.com/yausername/youtubedl-android/blob/master/LICENSE>

## yt-dlp

Media extractor invoked through youtubedl-android. yt-dlp's own source is
released under The Unlicense; its repository documents bundled third-party
components and their applicable licenses.

- Source: <https://github.com/yt-dlp/yt-dlp>
- License and third-party notices: <https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE>

The app can replace its app-private yt-dlp runtime with a stable update from
the component's official update channel.

## FFmpeg

Audio/video merging, conversion, metadata, and artwork processing. FFmpeg is
licensed under GNU LGPL v2.1 or later, with optional parts under GNU GPL v2 or
later depending on the build configuration. The binary distributed through
the youtubedl-android FFmpeg package is covered by that package's corresponding
source and license terms.

- Project and source: <https://ffmpeg.org/>
- Legal information: <https://ffmpeg.org/legal.html>
- Android package source: <https://github.com/yausername/youtubedl-android>
