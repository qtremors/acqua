# Third-party notices

Acqua includes or can update independent open-source runtime components. Their
names are used only to identify and credit their respective projects. Each
component remains governed by its own copyright and license terms.

## Android application libraries

The AndroidX, Jetpack Compose, Kotlin, Kotlin coroutines, OkHttp, Okio, Apache
Commons, Jackson, JSpecify, and ListenableFuture code used by the application is
distributed under the Apache License 2.0 unless a component states otherwise.
The complete Apache License text is included in the APK as
`Apache-2.0.txt`.

- AndroidX source: <https://android.googlesource.com/platform/frameworks/support/>
- Kotlin source: <https://github.com/JetBrains/kotlin>
- Kotlin coroutines source: <https://github.com/Kotlin/kotlinx.coroutines>
- OkHttp and Okio source: <https://github.com/square/okhttp>
- Apache Commons IO source: <https://github.com/apache/commons-io>
- Apache Commons Compress source: <https://github.com/apache/commons-compress>
- Jackson source: <https://github.com/FasterXML/jackson>
- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>

The OkHttp public suffix database includes data from the Mozilla Public Suffix
List under the Mozilla Public License 2.0.

- Public Suffix List: <https://publicsuffix.org/list/>
- Mozilla Public License 2.0: <https://www.mozilla.org/MPL/2.0/>

## Markdown rendering

Markwon is distributed under the Apache License 2.0. Its CommonMark parser is
distributed under the BSD 2-Clause License.

- Markwon source and license: <https://github.com/noties/Markwon>
- CommonMark Java source: <https://github.com/atlassian/commonmark-java>

### CommonMark Java license

Copyright (c) 2015, Atlassian Pty Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

## youtubedl-android

Android integration library used to initialize and execute the media-processing
runtime. Licensed under GNU GPL v3.0.

- Source and native build instructions: <https://github.com/yausername/youtubedl-android>
- License: <https://github.com/yausername/youtubedl-android/blob/master/LICENSE>

## yt-dlp

Media extractor invoked through youtubedl-android. yt-dlp's own source is
released under The Unlicense. Its license document also identifies bundled
third-party code and the terms that apply to that code.

- Source: <https://github.com/yt-dlp/yt-dlp>
- License and third-party notices: <https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE>

Acqua can replace its app-private yt-dlp executable through the component's
official Stable, Nightly, or Master update channel selected in Settings.

## FFmpeg and native media libraries

The packaged FFmpeg executable was configured with GPL and version 3 features,
so it is distributed under GNU GPL v3 or later. It is accompanied by shared
codec and media libraries from the Termux package build, including AOM, dav1d,
Fontconfig, FreeType, FriBidi, GnuTLS, HarfBuzz, LAME, libass, libbluray,
libopenmpt, libopus, libtheora, libvpx, libwebp, libxml2, libx264, libx265,
libxvid, rav1e, Rubber Band, SRT, SVT-AV1, vid.stab, VMAF, Vorbis, zimg, and
ZeroMQ. Those libraries use GPL-compatible GPL, LGPL, Apache, BSD, ISC, MIT,
MPL, and other permissive terms documented by their projects and Termux package
recipes.

- FFmpeg source: <https://ffmpeg.org/download.html#get-sources>
- FFmpeg legal information: <https://ffmpeg.org/legal.html>
- Termux package recipes and patches: <https://github.com/termux/termux-packages>
- Android packaging source and build instructions: <https://github.com/yausername/youtubedl-android>

The GNU GPL text bundled with Acqua also supplies the GPL terms applicable to
this build. Other license texts and copyright notices remain available from the
source links above.

## Python runtime

The packaged Python interpreter and standard library are distributed under the
Python Software Foundation License. Python includes incorporated software under
additional compatible licenses. The Termux runtime package also contains
support libraries such as OpenSSL, SQLite, Expat, libffi, ncurses, readline,
xz, bzip2, zlib, and their applicable license notices. Mutagen is included for
media metadata and is distributed under GNU GPL v2 or later.

- Python source and license: <https://docs.python.org/3/license.html>
- Mutagen source and license: <https://github.com/quodlibet/mutagen>
- Termux package recipes and patches: <https://github.com/termux/termux-packages>
- Android packaging source and build instructions: <https://github.com/yausername/youtubedl-android>

## QuickJS

JavaScript runtime used by yt-dlp extractors. Copyright Fabrice Bellard and
Charlie Gordon. Distributed under the MIT License.

- Project and source: <https://bellard.org/quickjs/>
- License: <https://github.com/bellard/quickjs/blob/master/LICENSE>

## Source availability

The Acqua source corresponding to a published APK must be made available from
the same release page as that APK, either directly or through a clear link to
the matching release tag. Upstream source and build material for bundled
runtime components is identified above. Distributors must preserve these
notices and satisfy the source-delivery terms of every component they convey.
