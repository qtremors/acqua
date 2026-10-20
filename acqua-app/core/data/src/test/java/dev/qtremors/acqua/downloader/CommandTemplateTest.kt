package dev.qtremors.acqua.downloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommandTemplateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = CommandTemplateStore(context)
    @Before fun reset() { context.getSharedPreferences("command_templates_v1", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun `quoted options and long option assignments resolve into reviewed settings without a shell`() {
        val template = CommandTemplate(name = "Audio", command = """yt-dlp -x --audio-format=m4a --audio-quality 192K --write-subs --sub-langs "en,hi" --convert-subs lrc --no-embed-metadata -r 1M""").validated()
        val options = template.applyTo(YtDlpDownloadOptions())
        assertEquals(DownloadContentType.AUDIO, options.contentType)
        assertEquals(AudioOutputFormat.M4A, options.audioFormat)
        assertEquals(192, options.audioBitrateKbps)
        assertEquals(CaptionMode.SEPARATE, options.captionMode)
        assertEquals(CaptionFormat.LRC, options.captionFormat)
        assertEquals("en,hi", options.captionLanguages)
        assertEquals(1024, options.network.rateLimitKib)
        assertFalse(options.embedMetadata)
        assertEquals(options, DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(options), YtDlpDownloadOptions()))
        assertEquals(listOf("--format", "bv[vcodec~='^avc']+ba/b"), CommandTemplateParser.tokenize("""--format "bv[vcodec~='^avc']+ba/b""""))
    }

    @Test fun `execution credentials arbitrary destinations batch URLs and ambiguous multi-output formats are rejected without echoing input`() {
        val secret = "secret-account-value"
        val invalid = listOf("--exec $secret", "--cookies $secret", "--config-locations $secret", "-o $secret", "--paths $secret",
            "--add-header token=$secret", "--proxy http://user:$secret@example.com", "https://example.com/?token=$secret",
            "-f all", "-f 'bestvideo,bestaudio'", "-f '(bestvideo,bestaudio)'", "-f \"'all'\"", "-f --exec", "-f best[", "-f", "--format 'unterminated",
            "--embed-metadata;echo $secret", "-f \"bv[format_note~='(a+)+$']\"", "--audio-quality 5", "--audio-quality 0K", "-r 9999999M", "x".repeat(4097))
        invalid.forEach { command ->
            val failure = assertThrows(RuntimeException::class.java) { CommandTemplate(name = "Invalid", command = command).validated() }
            assertFalse(failure.message.orEmpty().contains(secret))
        }
        assertThrows(InvalidCommandTemplateException::class.java) { CommandTemplate(name = "Bad\nname", command = "--embed-metadata").validated() }
    }

    @Test fun `preview preserves originals and imports update only accepted matching identities`() {
        val original = CommandTemplate(name = "Original", command = "--embed-metadata").validated()
        store.put(original)
        val incoming = original.copy(command = "--no-embed-metadata")
        val document = JSONObject(store.export()).apply { getJSONArray("templates").getJSONObject(0).put("command", incoming.command) }.toString()
        val preview = store.previewImport(document, "Imported")
        assertEquals(0, preview.added)
        assertEquals(1, preview.updated)
        assertEquals(listOf(original), store.all())
        store.applyImport(preview)
        assertEquals(listOf(incoming), CommandTemplateStore(context).all())
        val before = store.all()
        for (malformed in listOf("{", document + " trailing", JSONObject(document).put("version", 99).toString(), " ".repeat(CommandTemplateStore.MAX_DOCUMENT_BYTES + 1))) {
            assertThrows(RuntimeException::class.java) { store.previewImport(malformed, "Imported") }
            assertEquals(before, store.all())
        }
    }

    @Test fun `queued choices survive template changes and deletion while new acceptance requires review`() {
        val template = CommandTemplate(name = "Video", command = "--format 'bv*+ba/b' --embed-metadata").validated()
        store.put(template)
        val accepted = template.applyTo(YtDlpDownloadOptions())
        store.validateAccepted(accepted)
        val media = dev.qtremors.acqua.domain.ResolvedMedia("https://example.com/queued", dev.qtremors.acqua.domain.MediaKind.VIDEO,
            backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP)
        val id = java.util.UUID.randomUUID()
        DownloadRequestStore(context).put(id, DownloadWorkData.processedRequest(media, media, accepted, media.url))
        store.put(template.copy(command = "--no-embed-metadata"))
        assertThrows(MissingCommandTemplateException::class.java) { store.validateAccepted(accepted) }
        store.remove(template.id)
        assertThrows(MissingCommandTemplateException::class.java) { store.validateAccepted(accepted) }
        val restored = DownloadOptionsCodec.decode(DownloadOptionsCodec.encode(accepted), YtDlpDownloadOptions())
        assertEquals(accepted, restored)
        assertEquals("bv*+ba/b", restored.formatSelectorOverride)
        assertEquals(accepted, DownloadRequestStore(context).resolve(androidx.work.Data.Builder()
            .putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build())!!.options)
        CommandTemplateParser.validateOptions(restored)
    }

    @Test fun `file and clipboard documents round trip with redacted unique names and bounded reads`() {
        store.put(CommandTemplate(name = "token=private-one", command = "--embed-metadata"))
        store.put(CommandTemplate(name = "token=private-two", command = "--no-embed-metadata"))
        val exported = store.export()
        assertFalse(exported.contains("private-one"))
        assertFalse(exported.contains("private-two"))
        val before = store.all()
        val input = Uri.parse("content://fixture/import")
        shadowOf(context.contentResolver).registerInputStream(input, exported.byteInputStream())
        val preview = store.readImport(input, "Imported")
        assertEquals(2, preview.updated)
        assertEquals(before, store.all())
        val output = Uri.parse("content://fixture/export")
        val bytes = java.io.ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(output, bytes)
        store.writeExport(output)
        assertEquals(exported, bytes.toString(Charsets.UTF_8.name()))
        shadowOf(context.contentResolver).registerInputStream(input, ByteArray(CommandTemplateStore.MAX_DOCUMENT_BYTES + 1).inputStream())
        assertThrows(RuntimeException::class.java) { store.readImport(input, "Imported") }
        assertEquals(before, store.all())
    }

    @Test fun `template provenance and inspection signatures do not change duplicate identity`() {
        val media = dev.qtremors.acqua.domain.ResolvedMedia("https://example.com/video", dev.qtremors.acqua.domain.MediaKind.VIDEO,
            backend = dev.qtremors.acqua.domain.MediaBackend.YT_DLP)
        val first = CommandTemplate(name = "One", command = "--embed-metadata").applyTo(YtDlpDownloadOptions())
        val second = CommandTemplate(name = "Two", command = "--embed-metadata").applyTo(YtDlpDownloadOptions())
        val request = DownloadWorkRequest(media, media, media.url, 0, first)
        assertEquals(DownloadLibraryStore.identity(request), DownloadLibraryStore.identity(request.copy(options = second)))
        val signature = dev.qtremors.acqua.domain.MediaFormatOption("137", "mp4", "avc1", "none")
        assertEquals(DownloadLibraryStore.identity(request), DownloadLibraryStore.identity(request.copy(options = first.copy(acceptedStreams = listOf(signature)))))
        assertTrue(first.commandTemplateId != second.commandTemplateId)
    }

    @Test fun `template output checks reject missing duplicate malformed or incompatible media markers`() {
        val video = YtDlpDownloadOptions(formatSelectorOverride = "best")
        CommandTemplateOutput.verify("ACQUA_TEMPLATE:[\"avc1\",\"aac\"]", video)
        CommandTemplateOutput.verify("ACQUA_TEMPLATE:[\"none\",\"aac\"]", video.copy(contentType = DownloadContentType.AUDIO))
        for (text in listOf("", "ACQUA_TEMPLATE:[]", "ACQUA_TEMPLATE:[\"none\",\"aac\"]", "ACQUA_TEMPLATE:{bad}",
            "ACQUA_TEMPLATE:[\"avc1\",\"aac\"]\nACQUA_TEMPLATE:[\"avc1\",\"aac\"]")) {
            assertThrows(InvalidCommandTemplateException::class.java) { CommandTemplateOutput.verify(text, video) }
        }
        CommandTemplateOutput.verify("", YtDlpDownloadOptions())
        assertEquals(DownloadFailure.COMMAND_TEMPLATE, MissingCommandTemplateException().toDownloadFailure())
    }
}
