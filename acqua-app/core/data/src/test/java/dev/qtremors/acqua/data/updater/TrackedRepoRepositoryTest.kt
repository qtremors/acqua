package dev.qtremors.acqua.data.updater

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackedRepoRepositoryTest {
    @Test
    fun `concurrent imports mapping edits removals and renames preserve unrelated writes`() = runBlocking {
        withRepository { repository ->
            repository.updateAll(listOf(repo("mapped"), repo("removed"), repo("renamed")))
            val start = CompletableDeferred<Unit>()
            val imports = (1..12).map { index ->
                async(Dispatchers.Default) {
                    start.await()
                    repository.importBackup(backup(repo("import$index"))).getOrThrow()
                }
            }
            val edits = listOf(
                async(Dispatchers.Default) {
                    start.await()
                    repository.updateRepo("owner/mapped") {
                        it.copy(mappedPackageName = "chosen.package", autoMatchInstalledApp = false)
                    }
                },
                async(Dispatchers.Default) { start.await(); repository.removeRepo("owner/removed") },
                async(Dispatchers.Default) {
                    start.await()
                    repository.updateRepo("owner/renamed") { it.copy(name = "new-name", fullName = "owner/new-name") }
                }
            )
            start.complete(Unit)
            imports.awaitAll()
            edits.awaitAll()
            val saved = repository.trackedRepos.first()
            assertEquals(14, saved.size)
            assertEquals(14, saved.map { it.fullName }.distinct().size)
            assertTrue(saved.any { it.fullName == "owner/new-name" })
            assertFalse(saved.any { it.name in listOf("removed", "renamed") })
            assertEquals("chosen.package", saved.single { it.name == "mapped" }.mappedPackageName)
            assertFalse(saved.single { it.name == "mapped" }.autoMatchInstalledApp)
        }
    }

    @Test
    fun `invalid imports leave every existing repository untouched`() = runBlocking {
        withRepository { repository ->
            val original = repo("saved")
            repository.addOrUpdateRepo(original)
            val malformed = listOf("not json", backup(repo("valid"), repo("invalid").copy(fullName = "wrong/name")))
            malformed.forEach { payload ->
                assertTrue(repository.importBackup(payload).isFailure)
                assertEquals(listOf(original), repository.trackedRepos.first())
            }
        }
    }

    @Test
    fun `versioned and legacy imports merge case insensitive identities and discard asset endpoints`() = runBlocking {
        withRepository { repository ->
            repository.addOrUpdateRepo(repo("unrelated"))
            repository.addOrUpdateRepo(repo("saved"))
            val imported = repo("Saved").copy(
                mappedPackageName = "chosen.package", selectedApkName = "chosen.apk", notificationsEnabled = false,
                downloadUrl = "https://untrusted.example/app.apk", downloadApiUrl = "https://untrusted.example/api", apkSizeBytes = 100
            )
            val legacy = Json.encodeToString(ListSerializer(TrackedRepo.serializer()), listOf(imported))
            for (payload in listOf(backup(imported), legacy)) {
                assertEquals(1, repository.importBackup(payload).getOrThrow())
                val saved = repository.trackedRepos.first()
                assertEquals(2, saved.size)
                val replaced = saved.single { it.name == "Saved" }
                assertEquals("chosen.package", replaced.mappedPackageName)
                assertEquals("chosen.apk", replaced.selectedApkName)
                assertFalse(replaced.notificationsEnabled)
                assertNull(replaced.downloadUrl)
                assertNull(replaced.downloadApiUrl)
                assertEquals(0L, replaced.apkSizeBytes)
                assertTrue(repository.exportBackup().contains("chosen.package"))
            }
        }
    }

    private fun repo(name: String) = TrackedRepo("owner", name, "owner/$name", addedAt = 1L)

    private fun backup(vararg repos: TrackedRepo) = Json.encodeToString(
        TrackedRepoBackup.serializer(), TrackedRepoBackup(repositories = repos.toList())
    )

    private suspend fun withRepository(block: suspend (TrackedRepoRepository) -> Unit) {
        val directory = Files.createTempDirectory("acqua-feed-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("feeds.preferences_pb") }
        try {
            block(TrackedRepoRepository(ApplicationProvider.getApplicationContext<Context>(), store))
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
