package dev.qtremors.acqua.feature.about

enum class AboutDestination {
    ABOUT,
    NOTICES,
    LICENSE
}

enum class AboutExternalLink(val url: String) {
    DEVELOPER("https://github.com/qtremors"),
    REPOSITORY("https://github.com/qtremors/acqua"),
    PRIVACY("https://github.com/qtremors/acqua/blob/main/PRIVACY.md"),
    RELEASES("https://github.com/qtremors/acqua/releases"),
    REPORT_ISSUE("https://github.com/qtremors/acqua/issues/new"),
    LICENSE("https://github.com/qtremors/acqua/blob/main/LICENSE.md"),
    THIRD_PARTY_NOTICES("https://github.com/qtremors/acqua/blob/main/THIRD_PARTY_NOTICES.md")
}

data class AboutBuildInfo(
    val versionName: String,
    val applicationId: String,
    val buildType: String
) {
    val normalizedVersion: String
        get() = versionName.removeSuffix("-debug")

    val isDebug: Boolean
        get() = buildType.equals("debug", ignoreCase = true) || applicationId.endsWith(".debug")

    val displayVersion: String
        get() = if (isDebug) "$normalizedVersion (Debug)" else normalizedVersion

    val displayPackage: String
        get() = applicationId.ifBlank { "dev.qtremors.acqua" }
}

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
            name = "AndroidX, Compose, Kotlin, and coroutines",
            purpose = "Android application framework, user interface, language runtime, and asynchronous execution.",
            license = "Apache License 2.0",
            sourceUrl = "https://android.googlesource.com/platform/frameworks/support/",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
        ),
        OpenSourceComponent(
            name = "OkHttp and Okio",
            purpose = "HTTP networking and I/O primitives.",
            license = "Apache License 2.0",
            sourceUrl = "https://github.com/square/okhttp",
            licenseUrl = "https://github.com/square/okhttp/blob/master/LICENSE.txt"
        ),
        OpenSourceComponent(
            name = "Apache Commons and Jackson",
            purpose = "Archive, file, and JSON processing used by the media runtime.",
            license = "Apache License 2.0",
            sourceUrl = "https://github.com/FasterXML/jackson",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
        ),
        OpenSourceComponent(
            name = "youtubedl-android",
            purpose = "Android integration for the media-processing runtime.",
            license = "GNU GPL v3.0",
            sourceUrl = "https://github.com/yausername/youtubedl-android",
            licenseUrl = "https://github.com/yausername/youtubedl-android/blob/master/LICENSE"
        ),
        OpenSourceComponent(
            name = "yt-dlp",
            purpose = "Media extraction for supported websites.",
            license = "The Unlicense with bundled third-party notices",
            sourceUrl = "https://github.com/yt-dlp/yt-dlp",
            licenseUrl = "https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE"
        ),
        OpenSourceComponent(
            name = "FFmpeg",
            purpose = "Audio and video merging, conversion, metadata, and artwork processing.",
            license = "GNU GPL v3 or later build with compatible component licenses",
            sourceUrl = "https://ffmpeg.org/",
            licenseUrl = "https://ffmpeg.org/legal.html"
        ),
        OpenSourceComponent(
            name = "Python runtime",
            purpose = "Interpreter and standard library used to run yt-dlp.",
            license = "Python Software Foundation License and incorporated component licenses",
            sourceUrl = "https://www.python.org/downloads/source/",
            licenseUrl = "https://docs.python.org/3/license.html"
        ),
        OpenSourceComponent(
            name = "QuickJS",
            purpose = "JavaScript runtime used by yt-dlp extractors.",
            license = "MIT License",
            sourceUrl = "https://bellard.org/quickjs/",
            licenseUrl = "https://github.com/bellard/quickjs/blob/master/LICENSE"
        )
    )

    fun find(name: String): OpenSourceComponent? =
        all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

internal fun deviceDescription(
    manufacturer: String,
    model: String,
    androidRelease: String
): String {
    val deviceName = listOf(manufacturer.trim(), model.trim())
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .joinToString(" ")
        .ifBlank { "Android device" }
    val release = androidRelease.trim().ifBlank { "unknown" }
    return "$deviceName (Android $release)"
}
