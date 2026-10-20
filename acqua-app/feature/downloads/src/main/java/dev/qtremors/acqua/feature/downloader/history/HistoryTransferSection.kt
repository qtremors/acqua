package dev.qtremors.acqua.feature.downloader.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.history.HistoryEntry
import dev.qtremors.acqua.data.history.HistoryImportPreview
import dev.qtremors.acqua.data.history.HistoryTransfer
import dev.qtremors.acqua.data.history.HistoryTransferFormat
import dev.qtremors.acqua.data.history.InaccessibleHistoryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HistoryTransferSection(transfer: HistoryTransfer, selectedIds: Set<String>?, selectedEntry: HistoryEntry? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf<String?>(null) }
    var exportToken by rememberSaveable { mutableStateOf<String?>(null) }
    var formatName by rememberSaveable { mutableStateOf(HistoryTransferFormat.JSON.name) }
    var policyName by rememberSaveable { mutableStateOf(InaccessibleHistoryPolicy.KEEP.name) }
    var relinkId by rememberSaveable { mutableStateOf<String?>(null) }
    var relinkPreview by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<HistoryImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var issue by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf<Int?>(null) }
    val format = HistoryTransferFormat.valueOf(formatName)
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    fun operation(block: suspend () -> Unit) { scope.launch {
        busy = true
        try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: RuntimeException) { issue = true }
        catch (_: java.io.IOException) { issue = true }
        finally { busy = false }
    } }
    fun replaceToken(next: String) {
        token?.let { old -> scope.launch(Dispatchers.IO) { transfer.discard(old) } }
        token = next; completed = null; issue = false
    }
    LaunchedEffect(token, revision) {
        preview = null
        token?.let { draft -> operation { val loaded = withContext(Dispatchers.IO) { transfer.preview(draft) }; if (token == draft) preview = loaded } }
    }
    val imported = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) operation { replaceToken(withContext(Dispatchers.IO) { transfer.read(uri) }) }
    }
    fun write(uri: Uri?) {
        val draft = exportToken ?: return
        exportToken = null
        operation { withContext(Dispatchers.IO) { if (uri == null) transfer.discard(draft) else transfer.write(uri, draft) } }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), ::write)
    val saveUrls = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::write)
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkId
        val draft = token
        if (uri != null && id != null) operation {
            withContext(Dispatchers.IO) {
                transfer.retainRead(uri)
                if (relinkPreview && draft != null) transfer.relink(draft, id, uri) else transfer.relinkExisting(id, uri)
            }
            revision++
        }
        relinkId = null
    }
    TextButton(onClick = { open = true }) { Text(stringResource(if (selectedIds == null) R.string.hist_transfer else R.string.hist_transfer_selected)) }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text(stringResource(if (token == null) R.string.hist_transfer else R.string.hist_import_preview)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator()
            if (issue) Text(stringResource(R.string.hist_transfer_error), color = MaterialTheme.colorScheme.error)
            completed?.let { Text(stringResource(R.string.hist_import_complete, it)) }
            val current = preview
            if (token == null) {
                HistoryTransferMenu(format, { formatName = it.name }, busy, selectedIds?.isEmpty() != true, selectedEntry?.isDownloaded == true,
                { operation {
                    val text = withContext(Dispatchers.IO) { transfer.export(format, selectedIds) }
                    require(text.toByteArray(Charsets.UTF_8).size <= HistoryTransfer.MAX_CLIPBOARD_BYTES)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Acqua History", text))
                } },
                { operation {
                    exportToken = withContext(Dispatchers.IO) { transfer.stageExport(format, selectedIds) }
                    if (format == HistoryTransferFormat.JSON) saveJson.launch("Acqua-history.json") else saveUrls.launch("Acqua-links.txt")
                } },
                { operation {
                    val clip = clipboard.primaryClip
                    require(clip != null && clip.itemCount == 1)
                    val text = clip.getItemAt(0).text ?: error("No text")
                    require(text.length <= HistoryTransfer.MAX_CLIPBOARD_BYTES && text.toString().toByteArray(Charsets.UTF_8).size <= HistoryTransfer.MAX_CLIPBOARD_BYTES)
                    replaceToken(withContext(Dispatchers.IO) { transfer.stage(text.toString()) })
                } },
                { imported.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                {
                    relinkId = selectedEntry?.id; relinkPreview = false; relink.launch(arrayOf("*/*"))
                })
            } else if (current != null) {
                HistoryTransferPreview(current, InaccessibleHistoryPolicy.valueOf(policyName), { policyName = it.name }, busy) { id ->
                    relinkId = id; relinkPreview = true; relink.launch(arrayOf("*/*"))
                }
                TextButton(enabled = !busy, onClick = { val draft = token ?: return@TextButton; operation {
                    completed = withContext(Dispatchers.IO) { transfer.apply(draft, InaccessibleHistoryPolicy.valueOf(policyName)) }
                    token = null; preview = null
                } }) { Text(stringResource(R.string.hist_apply_import)) }
            }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            token?.let { draft -> scope.launch(Dispatchers.IO) { transfer.discard(draft) } }
            token = null; preview = null; open = false; issue = false
        }) { Text(stringResource(if (token == null) R.string.close else R.string.cancel)) } }
    )
}

@Composable
private fun HistoryTransferMenu(format: HistoryTransferFormat, onFormat: (HistoryTransferFormat) -> Unit, busy: Boolean,
    canExport: Boolean, canRelink: Boolean, onCopy: () -> Unit, onExport: () -> Unit, onPaste: () -> Unit,
    onImport: () -> Unit, onRelink: () -> Unit) {
    Text(stringResource(R.string.hist_transfer_hint))
    HistoryTransferChoices(format, onFormat)
    TextButton(enabled = !busy && canExport, onClick = onCopy) { Text(stringResource(R.string.hist_copy_export)) }
    TextButton(enabled = !busy && canExport, onClick = onExport) { Text(stringResource(R.string.hist_export_file)) }
    TextButton(enabled = !busy, onClick = onPaste) { Text(stringResource(R.string.hist_paste_import)) }
    TextButton(enabled = !busy, onClick = onImport) { Text(stringResource(R.string.hist_import_file)) }
    if (canRelink) TextButton(enabled = !busy, onClick = onRelink) { Text(stringResource(R.string.hist_relink_selected)) }
}

@Composable
private fun HistoryTransferPreview(preview: HistoryImportPreview, policy: InaccessibleHistoryPolicy,
    onPolicy: (InaccessibleHistoryPolicy) -> Unit, busy: Boolean, onRelink: (String) -> Unit) {
    Text(stringResource(R.string.hist_import_counts, preview.entries.size, preview.duplicates, preview.unavailableIds.size))
    preview.templates?.let { Text(stringResource(R.string.hist_import_templates, it.templates.size)) }
    if (preview.unavailableIds.isNotEmpty()) HistoryImportPolicy(policy, onPolicy)
    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(min = 120.dp, max = 220.dp)) {
        items(preview.entries.size) { index ->
            val entry = preview.entries[index]
            Column {
                Text(entry.title ?: entry.fileName, Modifier.padding(top = 8.dp))
                if (entry.id in preview.unavailableIds) TextButton(enabled = !busy, onClick = { onRelink(entry.id) }) {
                    Text(stringResource(R.string.hist_relink_file))
                }
            }
        }
        preview.templates?.templates?.let { templates -> items(templates.size) { Text(templates[it].name) } }
    }
}
