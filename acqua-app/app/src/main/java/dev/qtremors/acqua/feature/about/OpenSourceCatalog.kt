package dev.qtremors.acqua.feature.about

data class OpenSourceComponent(
    val name: String,
    val purpose: String,
    val license: String,
    val sourceUrl: String,
    val licenseUrl: String
)

object AcquaOpenSourceComponents {
    val all = listOf(
        OpenSourceComponent(
            "AndroidX, Compose, Kotlin, and coroutines",
            "Android application framework, user interface, language runtime, and asynchronous execution.",
            "Apache License 2.0",
            "https://android.googlesource.com/platform/frameworks/support/",
            "https://www.apache.org/licenses/LICENSE-2.0"
        ),
        OpenSourceComponent(
            "OkHttp and Okio",
            "HTTP networking and I/O primitives.",
            "Apache License 2.0",
            "https://github.com/square/okhttp",
            "https://github.com/square/okhttp/blob/master/LICENSE.txt"
        ),
        OpenSourceComponent(
            "Apache Commons and Jackson",
            "Archive, file, and JSON processing used by the media runtime.",
            "Apache License 2.0",
            "https://github.com/FasterXML/jackson",
            "https://www.apache.org/licenses/LICENSE-2.0"
        ),
        OpenSourceComponent(
            "youtubedl-android",
            "Android integration for the media-processing runtime.",
            "GNU GPL v3.0",
            "https://github.com/yausername/youtubedl-android",
            "https://github.com/yausername/youtubedl-android/blob/master/LICENSE"
        ),
        OpenSourceComponent(
            "yt-dlp",
            "Media extraction for supported websites.",
            "The Unlicense with bundled third-party notices",
            "https://github.com/yt-dlp/yt-dlp",
            "https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE"
        ),
        OpenSourceComponent(
            "FFmpeg",
            "Audio and video merging, conversion, metadata, and artwork processing.",
            "GNU GPL v3 or later build with compatible component licenses",
            "https://ffmpeg.org/",
            "https://ffmpeg.org/legal.html"
        ),
        OpenSourceComponent(
            "Python runtime",
            "Interpreter and standard library used to run yt-dlp.",
            "Python Software Foundation License and incorporated component licenses",
            "https://www.python.org/downloads/source/",
            "https://docs.python.org/3/license.html"
        ),
        OpenSourceComponent(
            "QuickJS",
            "JavaScript runtime used by yt-dlp extractors.",
            "MIT License",
            "https://bellard.org/quickjs/",
            "https://github.com/bellard/quickjs/blob/master/LICENSE"
        )
    )

    fun find(name: String): OpenSourceComponent? =
        all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
