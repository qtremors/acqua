package dev.qtremors.acqua.feature.about

import dev.qtremors.acqua.appinfo.AboutBuildInfo
import dev.qtremors.acqua.appinfo.AboutDestination
import dev.qtremors.acqua.appinfo.AboutExternalLink
import dev.qtremors.acqua.appinfo.deviceDescription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutModelsTest {
    @Test
    fun `release build displays plain version`() {
        val info = AboutBuildInfo(
            versionName = "0.1.0",
            applicationId = "dev.qtremors.acqua",
            buildType = "release"
        )

        assertEquals("0.1.0", info.normalizedVersion)
        assertEquals("0.1.0", info.displayVersion)
        assertFalse(info.isDebug)
    }

    @Test
    fun `debug build displays explicit debug marker`() {
        val info = AboutBuildInfo(
            versionName = "0.1.0-debug",
            applicationId = "dev.qtremors.acqua.debug",
            buildType = "debug"
        )

        assertEquals("0.1.0", info.normalizedVersion)
        assertEquals("0.1.0 (Debug)", info.displayVersion)
        assertTrue(info.isDebug)
    }

    @Test
    fun `debug application id identifies debug build defensively`() {
        val info = AboutBuildInfo(
            versionName = "0.1.0",
            applicationId = "dev.qtremors.acqua.debug",
            buildType = "unknown"
        )

        assertTrue(info.isDebug)
        assertEquals("0.1.0 (Debug)", info.displayVersion)
    }

    @Test
    fun `build type comparison ignores case`() {
        val info = AboutBuildInfo("0.1.0", "dev.qtremors.acqua", "DEBUG")

        assertTrue(info.isDebug)
    }

    @Test
    fun `release substring does not count as debug`() {
        val info = AboutBuildInfo("0.1.0", "dev.qtremors.acqua", "release-debuggable")

        assertFalse(info.isDebug)
    }

    @Test
    fun `only trailing debug suffix is removed from version`() {
        val trailing = AboutBuildInfo("0.1.0-debug", "id", "release")
        val embedded = AboutBuildInfo("debug-0.1.0", "id", "release")

        assertEquals("0.1.0", trailing.normalizedVersion)
        assertEquals("debug-0.1.0", embedded.normalizedVersion)
    }

    @Test
    fun `application id is displayed unchanged`() {
        val info = AboutBuildInfo("0.1.0", "dev.qtremors.acqua.debug", "debug")

        assertEquals("dev.qtremors.acqua.debug", info.displayPackage)
    }

    @Test
    fun `blank application id uses production fallback`() {
        val info = AboutBuildInfo("0.1.0", "", "release")

        assertEquals("dev.qtremors.acqua", info.displayPackage)
    }

    @Test
    fun `external project links are secure`() {
        AboutExternalLink.entries.forEach { link ->
            assertTrue("${link.name} must use HTTPS", link.url.startsWith("https://"))
        }
    }

    @Test
    fun `external project links are unique`() {
        val urls = AboutExternalLink.entries.map(AboutExternalLink::url)

        assertEquals(urls.size, urls.distinct().size)
    }

    @Test
    fun `repository links point to Acqua`() {
        val acquaLinks = listOf(
            AboutExternalLink.REPOSITORY,
            AboutExternalLink.PRIVACY,
            AboutExternalLink.RELEASES,
            AboutExternalLink.REPORT_ISSUE,
            AboutExternalLink.LICENSE,
            AboutExternalLink.THIRD_PARTY_NOTICES
        )

        acquaLinks.forEach { link ->
            assertTrue(link.url.contains("github.com/qtremors/acqua"))
        }
    }

    @Test
    fun `issue link opens new issue flow`() {
        assertTrue(AboutExternalLink.REPORT_ISSUE.url.endsWith("/issues/new"))
    }

    @Test
    fun `privacy link targets privacy document`() {
        assertTrue(AboutExternalLink.PRIVACY.url.endsWith("/PRIVACY.md"))
    }

    @Test
    fun `open source component list matches bundled processing stack`() {
        assertEquals(
            listOf(
                "AndroidX, Compose, Kotlin, and coroutines",
                "OkHttp and Okio",
                "Apache Commons and Jackson",
                "youtubedl-android",
                "yt-dlp",
                "FFmpeg",
                "Python runtime",
                "QuickJS"
            ),
            AcquaOpenSourceComponents.all.map(OpenSourceComponent::name)
        )
    }

    @Test
    fun `open source component metadata is complete`() {
        AcquaOpenSourceComponents.all.forEach { component ->
            assertTrue(component.name.isNotBlank())
            assertTrue(component.purpose.isNotBlank())
            assertTrue(component.license.isNotBlank())
            assertTrue(component.sourceUrl.startsWith("https://"))
            assertTrue(component.licenseUrl.startsWith("https://"))
        }
    }

    @Test
    fun `component lookup ignores case and surrounding whitespace`() {
        assertEquals(
            "yt-dlp",
            AcquaOpenSourceComponents.find("  YT-DLP  ")?.name
        )
        assertEquals(
            "FFmpeg",
            AcquaOpenSourceComponents.find("ffmpeg")?.name
        )
    }

    @Test
    fun `component lookup returns null for unknown dependency`() {
        assertNull(AcquaOpenSourceComponents.find("unknown"))
    }

    @Test
    fun `component lookup returns null for blank name`() {
        assertNull(AcquaOpenSourceComponents.find("   "))
    }

    @Test
    fun `device description combines manufacturer model and Android release`() {
        assertEquals(
            "Google Pixel 9 (Android 16)",
            deviceDescription("Google", "Pixel 9", "16")
        )
    }

    @Test
    fun `device description trims platform values`() {
        assertEquals(
            "Google Pixel (Android 16)",
            deviceDescription(" Google ", " Pixel ", " 16 ")
        )
    }

    @Test
    fun `device description avoids repeated manufacturer model`() {
        assertEquals(
            "Google (Android 16)",
            deviceDescription("Google", "google", "16")
        )
    }

    @Test
    fun `device description tolerates missing manufacturer`() {
        assertEquals(
            "Pixel (Android 16)",
            deviceDescription("", "Pixel", "16")
        )
    }

    @Test
    fun `device description tolerates missing model`() {
        assertEquals(
            "Google (Android 16)",
            deviceDescription("Google", "", "16")
        )
    }

    @Test
    fun `device description provides fallback device name`() {
        assertEquals(
            "Android device (Android 16)",
            deviceDescription("", "", "16")
        )
    }

    @Test
    fun `device description provides fallback release`() {
        assertEquals(
            "Google Pixel (Android unknown)",
            deviceDescription("Google", "Pixel", "")
        )
    }

    @Test
    fun `about destinations include only local app pages`() {
        assertEquals(
            listOf(
                AboutDestination.SETTINGS,
                AboutDestination.ABOUT,
                AboutDestination.NOTICES,
                AboutDestination.LICENSE
            ),
            AboutDestination.entries
        )
    }
}
