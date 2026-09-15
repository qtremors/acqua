package dev.qtremors.acqua.feature.updater

import dev.qtremors.acqua.data.updater.DefaultTrackedRepos
import dev.qtremors.acqua.data.updater.InstalledAppMatcher
import dev.qtremors.acqua.data.updater.TrackedRepo
import dev.qtremors.acqua.data.updater.TrackedRepoBackup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedsModelsTest {
    @Test
    fun `version labels omit conventional git tag prefix`() {
        assertEquals("0.2.0", displayVersionLabel("v0.2.0"))
        assertEquals("1.0.0-beta", displayVersionLabel("V1.0.0-beta"))
        assertEquals("version-next", displayVersionLabel("version-next"))
    }

    @Test
    fun `version summary only shows transition for an available update`() {
        assertEquals("0.2.0", displayVersionSummary("0.2.0", "v0.2.0", isUpdateAvailable = false))
        assertEquals("0.2.0", displayVersionSummary("0.2.0", "v0.2.1", isUpdateAvailable = false))
        assertEquals("0.2.0  →  0.2.1", displayVersionSummary("0.2.0", "v0.2.1", isUpdateAvailable = true))
        assertEquals("0.2.1", displayVersionSummary(null, "v0.2.1", isUpdateAvailable = false))
    }

    @Test
    fun `default repositories are complete and unique`() {
        val repositories = DefaultTrackedRepos.repositories.map(TrackedRepo::fullName)

        assertEquals(
            listOf(
                "qtremors/acqua",
                "qtremors/arcile",
                "qtremors/filion",
                "qtremors/earnslate",
                "qtremors/material-design",
                "qtremors/osyster"
            ),
            repositories
        )
        assertEquals(repositories.size, repositories.distinct().size)
    }

    @Test
    fun `repository input accepts shorthand and github urls`() {
        assertEquals("owner" to "repo", FeedsViewModel.parseRepoQuery("owner/repo"))
        assertEquals(
            "owner" to "repo",
            FeedsViewModel.parseRepoQuery("https://github.com/owner/repo/releases/latest")
        )
        assertEquals("owner" to "repo", FeedsViewModel.parseRepoQuery("github.com/owner/repo.git"))
        assertNull(FeedsViewModel.parseRepoQuery("github.com/owner"))
        assertNull(FeedsViewModel.parseRepoQuery("owner/repo/extra"))
    }

    @Test
    fun `app matching favors exact normalized names`() {
        val target = InstalledAppMatcher.normalize("sample-app")
        assertEquals(100, InstalledAppMatcher.matchScore(target, InstalledAppMatcher.normalize("Sample App")))
        assertTrue(InstalledAppMatcher.matchScore(target, InstalledAppMatcher.normalize("Sample App Mobile")) >= 70)
        assertEquals(0, InstalledAppMatcher.matchScore(target, InstalledAppMatcher.normalize("Different")))
    }

    @Test
    fun `tracked repository backup round trips`() {
        val backup = TrackedRepoBackup(
            repositories = listOf(
                TrackedRepo(owner = "owner", name = "repo", fullName = "owner/repo")
            )
        )
        val encoded = Json.encodeToString(backup)
        val decoded = Json.decodeFromString(TrackedRepoBackup.serializer(), encoded)

        assertEquals(1, decoded.schemaVersion)
        assertEquals("owner/repo", decoded.repositories.single().fullName)
        assertEquals(TrackedRepo.AUTO_APK, decoded.repositories.single().selectedApkName)
    }
}
