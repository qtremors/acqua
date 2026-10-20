package dev.qtremors.acqua.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.qtremors.acqua.data.session.*
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRenamePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cipher = object : CookieProfileCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun erase() {}
    }
    private fun coordinator(profiles: CookieProfileStore): DownloadWorkCoordinator {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        return DownloadWorkCoordinator(context, profiles)
    }
    @Test fun renamePreservesAcceptedSession() {
        AppSettingsRepository(context).setUseBrowserSessions(true)
        val profiles = CookieProfileStore(context, cipher)
        val profile = CookieProfile(UUID.randomUUID().toString(), "Session", "https://example.com/", "test-agent", System.currentTimeMillis(), listOf(SessionCookie("sid", "value")))
        profiles.put(profile)
        val authorization = profiles.accepted(profile.id!!, profile.captureUrl)
        val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP)
        val options = YtDlpDownloadOptions(cookieProfileId = profile.id)
        val id = UUID.randomUUID()
        val store = DownloadRequestStore(context, profiles)
        store.put(id, DownloadWorkData.processedRequest(media, media, options, media.url), authorization = authorization)
        val reference = Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()
        assertNotNull(store.resolve(reference))
        DownloadNameOverrides.register(context, id.toString())
        assertTrue(coordinator(profiles).rename(id, "New name"))
        assertNotNull("Renaming must leave the accepted session restorable", store.resolve(reference))
    }
    @Test fun renamePreservesAcceptedChapters() {
        val profiles = CookieProfileStore(context, cipher)
        val media = ResolvedMedia("https://example.com/video", MediaKind.VIDEO, backend = MediaBackend.YT_DLP,
            chapters = listOf(MediaChapter("Intro", 0.0, 4.5)), preciseDurationSeconds = 4.5)
        val id = UUID.randomUUID()
        val store = DownloadRequestStore(context, profiles)
        store.put(id, DownloadWorkData.processedRequest(media, media, YtDlpDownloadOptions(splitChapters = true), media.url), MediaTimelineSource.encode(media))
        val reference = Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()
        assertEquals(media.chapters, store.resolve(reference)!!.media.chapters)
        DownloadNameOverrides.register(context, id.toString())
        assertTrue(coordinator(profiles).rename(id, "New name"))
        assertEquals("Renaming must retain the accepted chapter catalog", media.chapters, store.resolve(reference)!!.media.chapters)
    }
}
