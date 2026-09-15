package dev.qtremors.acqua.appinfo

enum class AboutDestination {
    SETTINGS,
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
    val normalizedVersion: String get() = versionName.removeSuffix("-debug")
    val isDebug: Boolean
        get() = buildType.equals("debug", ignoreCase = true) || applicationId.endsWith(".debug")
    val displayVersion: String get() = if (isDebug) "$normalizedVersion (Debug)" else normalizedVersion
    val displayPackage: String get() = applicationId.ifBlank { "dev.qtremors.acqua" }
}

fun deviceDescription(
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
