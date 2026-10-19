package dev.qtremors.acqua.feature.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.StateRestorationTester
import dev.qtremors.acqua.downloader.FilenameFormatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.data.settings.LastDownloadSelection
import dev.qtremors.acqua.domain.DownloadEngine
import dev.qtremors.acqua.downloader.AudioOutputFormat
import dev.qtremors.acqua.downloader.VideoContainer
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions
import dev.qtremors.acqua.downloader.YtDlpMaintenanceGateway
import dev.qtremors.acqua.downloader.YtDlpMaintenanceResult
import dev.qtremors.acqua.downloader.DownloadDiagnosticsStore
import dev.qtremors.acqua.downloader.DownloadFailure
import dev.qtremors.acqua.downloader.DownloadPhase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `folder destinations belong to downloads and filenames and remain available with media folders off`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context).apply { setCategorizeMedia(false) }
        val model = SettingsViewModel(repository, object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("current", null, 0L)
        }, { _, _ -> })
        compose.setContent { MaterialTheme { SettingsScreen(model) } }
        compose.onNodeWithTag("settings-list").performScrollToKey("filenames")
        compose.onNodeWithText("Organize into media folders").assertIsOff()
        compose.onAllNodesWithText("Download folders").assertCountEquals(1)
        File("build/reports/settings-downloads-group.png").also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Download folders").performClick()
        compose.onNodeWithText("Photo folder").assertIsDisplayed()
        compose.onNodeWithText("Video folder").assertIsDisplayed()
        compose.onNodeWithText("Audio folder").assertIsDisplayed()
        File("build/reports/settings-folder-overview.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Save location").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        File("build/reports/settings-folder-editor.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Organize into media folders").performClick().assertIsOn()
        compose.onNodeWithText("Download folders").performClick()
        compose.onNodeWithText("Audio folder").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test fun `hero pull keeps original resistance direct reverse drag and spring on release`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SettingsViewModel(AppSettingsRepository(context), object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("current", null, 0L)
        }, { _, _ -> })
        compose.setContent { MaterialTheme { SettingsScreen(model) } }
        fun logoHeight() = compose.onNodeWithTag("settings-hero-logo").fetchSemanticsNode().layoutInfo.height
        val initialHeight = logoHeight()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings-list").performTouchInput {
            down(Offset(centerX, 100f))
            moveBy(Offset(0f, 160f), delayMillis = 300)
        }
        compose.mainClock.advanceTimeByFrame()
        val stretchedHeight = logoHeight()
        assertTrue("The logo should grow while pulled", stretchedHeight > initialHeight + 20f)
        compose.onNodeWithTag("settings-list").performTouchInput {
            moveBy(Offset(0f, 40f), delayMillis = 100)
        }
        compose.mainClock.advanceTimeByFrame()
        val furtherStretchedHeight = logoHeight()
        assertEquals("Pulling keeps the original half-distance resistance", 20f, (furtherStretchedHeight - stretchedHeight).toFloat(), 1f)
        compose.onNodeWithTag("settings-list").performTouchInput {
            moveBy(Offset(0f, -40f), delayMillis = 100)
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("Dragging back shrinks the logo by the full distance", 40f, (furtherStretchedHeight - logoHeight()).toFloat(), 1f)
        compose.onNodeWithTag("settings-list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.mainClock.autoAdvance = true
        assertEquals(initialHeight, logoHeight())
    }

    @Test fun `filename sheet saves video edits and variables reset and external changes stay in sync`() {
        val pattern = mutableStateOf("custom_{title}")
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    FilenameFormatsSetting(pattern.value, FilenameFormatter.DEFAULT_AUDIO_PATTERN, { pattern.value = it }, {})
                }
            }
        }
        compose.onNodeWithText("Filename formats").performClick()
        val overview = File("build/reports/settings-filename-overview.png")
        overview.parentFile?.mkdirs()
        overview.outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("filename-group-video").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("custom_{date}_{title}")
        compose.runOnIdle { assertEquals("custom_{date}_{title}", pattern.value) }
        compose.onNodeWithText("{username}").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals("custom_{date}_{title}_{username}", pattern.value) }
        compose.onNodeWithText("{date}").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertEquals("custom_{title}_{username}", pattern.value) }
        compose.onNodeWithText("Reset filename pattern").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FilenameFormatter.DEFAULT_PATTERN, pattern.value) }
        compose.runOnIdle { pattern.value = "restored_{title}" }
        compose.onNode(hasSetTextAction()).performScrollTo().assertTextContains("restored_{title}")
        val editorPreview = File("build/reports/settings-filename-preview.png")
        editorPreview.parentFile?.mkdirs()
        compose.onNodeWithContentDescription("Close").performScrollTo()
        editorPreview.outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("filename-formats-sheet").assertDoesNotExist()
        compose.onNodeWithText("Filename formats").performClick()
        compose.onNodeWithTag("filename-group-video").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("restored_{title}")
    }

    @Test fun `audio filename group restores its draft and resets without changing video names`() {
        val pattern = mutableStateOf("{artist}_{title}")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                FilenameFormatsSetting("video_{title}", pattern.value, {}, { pattern.value = it })
            }
        }
        compose.onNodeWithText("Filename formats").performClick()
        compose.onNodeWithTag("filename-group-audio").performClick()
        compose.onNodeWithText("{album}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("{artist}_{title}_{album}", pattern.value) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("{artist}_{title}_{album}")
        compose.onNodeWithText("Reset audio filename pattern").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FilenameFormatter.DEFAULT_AUDIO_PATTERN, pattern.value) }
        compose.onNode(hasSetTextAction()).performScrollTo().assertTextContains(FilenameFormatter.DEFAULT_AUDIO_PATTERN)
        compose.onNodeWithTag("filename-group-video").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().assertTextContains("video_{title}")
    }

    @Test fun `failure notifications reach the lazily composed report and are consumed once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DownloadDiagnosticsStore(context)
        store.clear()
        val id = java.util.UUID.randomUUID().toString()
        store.record(id, IllegalStateException("Processing failed"), DownloadFailure.RUNTIME, DownloadPhase.PROCESSING, "job:PROCESSED")
        val initial = mutableStateOf<String?>(id)
        var consumed = 0
        val model = SettingsViewModel(AppSettingsRepository(context), object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("current", null, 0L)
        }, { _, _ -> })
        compose.setContent {
            MaterialTheme { SettingsScreen(model, initialDiagnosticTaskId = initial.value,
                onDiagnosticConsumed = { consumed++; initial.value = null }) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Task: $id", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, consumed)
        compose.onNodeWithText("Copy diagnostics").assertIsDisplayed()
    }

    @Test fun `opening defers offscreen controls and output defaults persist after selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(AppSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val store = AppSettingsRepository(context)
        store.rememberDownloadSelection(MediaKind.VIDEO, LastDownloadSelection(DownloadEngine.YT_DLP, YtDlpDownloadOptions()))
        val model = SettingsViewModel(store, object : YtDlpMaintenanceGateway {
            override suspend fun update(force: Boolean) = YtDlpMaintenanceResult("current", null, 0L)
        }, { _, _ -> })
        var advancedCompositions = 0
        compose.setContent {
            MaterialTheme { SettingsScreen(model, downloadOptionsContent = { advancedCompositions++ }) }
        }
        compose.runOnIdle { assertEquals(0, advancedCompositions) }
        compose.onNodeWithTag("settings-list").performScrollToKey("video-defaults")
        compose.onNodeWithText("Automatic").performClick()
        compose.onNodeWithText("MKV").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals(VideoContainer.MKV, AppSettingsRepository(context).downloadDefaults().videoContainer)
            assertEquals(VideoContainer.MKV, store.lastDownloadSelection(MediaKind.VIDEO)?.options?.videoContainer)
        }
        compose.onNodeWithTag("settings-list").performScrollToKey("audio-defaults")
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("MP3").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onAllNodesWithContentDescription("Quality: Best").onLast().performClick()
        compose.onNodeWithText("192 kbps").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals(AudioOutputFormat.MP3, store.mediaProcessingSettings().audioFormat)
            assertEquals(192, AppSettingsRepository(context).downloadDefaults().audioBitrateKbps)
        }
        compose.onNodeWithTag("settings-list").performScrollToKey("video-defaults")
        compose.onNodeWithText("MKV").assertIsDisplayed()
        val output = File("build/reports/settings-preview.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
