package dev.qtremors.acqua.data.updater

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `version parsing extracts semantic numbers cleanly`() {
        assertEquals(listOf(0, 1, 5), AppUpdater.parseVersionComponents("0.1.5"))
        assertEquals(listOf(0, 1, 6), AppUpdater.parseVersionComponents("v0.1.6"))
        assertEquals(listOf(1, 0, 0), AppUpdater.parseVersionComponents("V1.0.0-release"))
        assertEquals(listOf(2, 4, 10), AppUpdater.parseVersionComponents("2.4.10+build.42"))
    }

    @Test
    fun `version comparison correctly detects newer versions`() {
        assertTrue(AppUpdater.compareVersions("0.1.6", "0.1.5") > 0)
        assertTrue(AppUpdater.compareVersions("0.2.0", "0.1.9") > 0)
        assertTrue(AppUpdater.compareVersions("1.0.0", "0.9.99") > 0)
        assertTrue(AppUpdater.compareVersions("0.1.5", "0.1.6") < 0)
        assertEquals(0, AppUpdater.compareVersions("0.1.5", "0.1.5"))
        assertEquals(0, AppUpdater.compareVersions("v0.1.5", "0.1.5"))
    }

    @Test
    fun `version code derivation removes dots and prefixes`() {
        assertEquals(15, AppUpdater.deriveVersionCode("0.1.5"))
        assertEquals(16, AppUpdater.deriveVersionCode("v0.1.6"))
        assertEquals(123, AppUpdater.deriveVersionCode("1.2.3"))
    }

    @Test
    fun `abi asset selection prefers architecture match`() {
        val assets = listOf(
            GitHubAsset("acqua-x86_64.apk", "https://example.com/x86_64.apk", 1000L),
            GitHubAsset("acqua-arm64-v8a.apk", "https://example.com/arm64.apk", 1000L),
            GitHubAsset("acqua-universal.apk", "https://example.com/universal.apk", 1000L)
        )

        val selectedArm64 = AppUpdater.selectBestApkAsset(assets, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(selectedArm64)
        assertEquals("acqua-arm64-v8a.apk", selectedArm64?.name)

        val selectedX86 = AppUpdater.selectBestApkAsset(assets, arrayOf("x86_64"))
        assertNotNull(selectedX86)
        assertEquals("acqua-x86_64.apk", selectedX86?.name)
    }

    @Test
    fun `abi asset selection falls back to universal`() {
        val assets = listOf(
            GitHubAsset("acqua-x86.apk", "https://example.com/x86.apk", 1000L),
            GitHubAsset("acqua-universal.apk", "https://example.com/universal.apk", 1000L)
        )

        val selected = AppUpdater.selectBestApkAsset(assets, arrayOf("arm64-v8a"))
        assertNotNull(selected)
        assertEquals("acqua-universal.apk", selected?.name)
    }

    @Test
    fun `abi asset selection rejects an incompatible architecture`() {
        val assets = listOf(
            GitHubAsset("acqua-x86.apk", "https://example.com/x86.apk", 1000L),
            GitHubAsset("acqua-x86_64.apk", "https://example.com/x86_64.apk", 1000L)
        )

        assertNull(AppUpdater.selectBestApkAsset(assets, arrayOf("arm64-v8a")))
        assertNull(AppUpdater.selectBestApkAsset(assets.take(1), arrayOf("arm64-v8a")))
    }

    @Test
    fun `abi asset selection accepts an unlabeled universal apk`() {
        val asset = GitHubAsset("Acqua.apk", "https://example.com/acqua.apk", 1000L)

        assertEquals(asset, AppUpdater.selectBestApkAsset(listOf(asset), arrayOf("arm64-v8a")))
    }

    @Test
    fun `x86 asset selection does not match x86_64`() {
        val assets = listOf(
            GitHubAsset("acqua-x86_64.apk", "https://example.com/x86_64.apk", 1000L),
            GitHubAsset("acqua-x86.apk", "https://example.com/x86.apk", 1000L),
            GitHubAsset("acqua-universal.apk", "https://example.com/universal.apk", 1000L)
        )

        val selected = AppUpdater.selectBestApkAsset(assets, arrayOf("x86"))

        assertEquals("acqua-x86.apk", selected?.name)
    }

    @Test
    fun `abi asset selection ignores non-apk assets`() {
        val assets = listOf(
            GitHubAsset("source-code.zip", "https://example.com/source.zip", 1000L),
            GitHubAsset("checksums.txt", "https://example.com/sums.txt", 500L)
        )

        assertNull(AppUpdater.selectBestApkAsset(assets, arrayOf("arm64-v8a")))
    }

    @Test
    fun `github release deserialization parses asset list and release notes`() {
        val rawJson = """
            {
                "tag_name": "v0.1.6",
                "name": "Acqua v0.1.6 Release",
                "body": "### New Features\n- Added self-updater\n- Improved downloads",
                "html_url": "https://github.com/qtremors/acqua/releases/tag/v0.1.6",
                "published_at": "2026-08-30T12:00:00Z",
                "assets": [
                    {
                        "name": "acqua-arm64-v8a.apk",
                        "browser_download_url": "https://github.com/qtremors/acqua/releases/download/v0.1.6/acqua-arm64-v8a.apk",
                        "size": 15728640,
                        "content_type": "application/vnd.android.package-archive"
                    }
                ]
            }
        """.trimIndent()

        val release: GitHubRelease = json.decodeFromString(rawJson)
        assertEquals("v0.1.6", release.tag_name)
        assertEquals("Acqua v0.1.6 Release", release.name)
        assertEquals(1, release.assets.size)
        assertEquals("acqua-arm64-v8a.apk", release.assets[0].name)
        assertEquals(15728640L, release.assets[0].size)
    }
}
