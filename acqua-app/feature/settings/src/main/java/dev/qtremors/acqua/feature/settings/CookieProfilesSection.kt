package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
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
import dev.qtremors.acqua.data.session.BrowserCookieCapture
import dev.qtremors.acqua.data.session.BrowserIdentityStore
import dev.qtremors.acqua.data.session.CookieProfile
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
import dev.qtremors.acqua.ui.components.SettingsEditorDialog
import dev.qtremors.acqua.ui.components.SettingsEditorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun CookieProfilesSection(store: CookieProfileStore? = null, capture: ((String, String, String?) -> CookieProfile)? = null) {
    val context = LocalContext.current
    val profiles = store ?: remember(context) { CookieProfileStore(context) }
    val reader = capture ?: remember(context) { BrowserCookieCapture(context)::capture }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var issue by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTime by rememberSaveable { mutableStateOf(0L) }
    var exportFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val entries by produceState<List<CookieProfile>?>(emptyList(), revision, mode) {
        if (mode == "list") {
            loading = true
            value = withContext(Dispatchers.IO) { runCatching { profiles.all() }.getOrNull() }
            loading = false
        }
    }
    fun operation(block: () -> Unit) { scope.launch {
        busy = true; issue = false
        runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { revision++; mode = "list" }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; issue = true }
        busy = false
    } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val selected = exportId
        val fingerprint = exportFingerprint
        val time = exportTime
        exportId = null; exportFingerprint = null
        if (uri != null && selected != null) operation { profiles.export(selected, time, uri, fingerprint) }
    }
    SettingsSection(title = stringResource(R.string.cookie_profiles)) {
        SettingsActionRow(title = stringResource(R.string.cookie_profiles_manage), description = stringResource(R.string.settings_sessions_summary),
            leadingIcon = Icons.Filled.Cookie, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = { loading = true; revision++; issue = false; mode = "list" })
    }
    when (mode) {
        "list" -> CookieProfileList(entries.orEmpty(), issue || entries == null, busy, loading, { mode = null }, onNew = {
            id = null; name = ""; url = BrowserIdentityStore(context).lastUrl().orEmpty(); authorized = false; issue = false; mode = "edit"
        }, onRefresh = { profile -> id = profile.id; name = profile.name; url = profile.captureUrl; authorized = false; issue = false; mode = "edit" },
            onRemove = { profile -> id = profile.id; mode = "remove" }, onExport = { profile ->
                exportId = profile.id; exportTime = profile.capturedAt; exportFingerprint = profile.fingerprint; mode = "export"
            }, onClear = { mode = "clear" })
        "edit" -> CookieProfileEditor(name, url, id != null, authorized, busy, issue, { name = it }, { url = it }, { authorized = it }, { mode = "list" }) {
            val profileId = id ?: UUID.randomUUID().toString()
            val captureName = name; val captureUrl = url
            operation { profiles.put(reader(captureName, captureUrl, profileId).also { require(it.netscape().isNotEmpty()) }) }
        }
        "remove", "clear", "export" -> CookieProfileConfirm(mode!!, busy, issue, { mode = "list" }) {
            when (mode) {
                "remove" -> id?.let { selected -> operation { profiles.remove(selected) } }
                "clear" -> operation { profiles.clear() }
                "export" -> { mode = "list"; export.launch("Acqua-session.json") }
            }
        }
    }
}

@Composable
private fun CookieProfileList(entries: List<CookieProfile>, issue: Boolean, busy: Boolean, loading: Boolean, onDismiss: () -> Unit,
    onNew: () -> Unit, onRefresh: (CookieProfile) -> Unit, onRemove: (CookieProfile) -> Unit, onExport: (CookieProfile) -> Unit, onClear: () -> Unit) {
    SettingsEditorDialog(stringResource(R.string.cookie_profiles), onDismiss, stringResource(R.string.close), onDismiss,
        confirmEnabled = !busy, dismissEnabled = !busy, showCancel = false, tag = "saved-sessions-dialog") {
            Text(stringResource(R.string.settings_sessions_brief), style = MaterialTheme.typography.bodyMedium)
            if (issue) Text(stringResource(R.string.cookie_profile_error), color = MaterialTheme.colorScheme.error)
            FilledTonalButton(enabled = !busy && entries.size < 20, onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cookie_profile_new))
            }
            if (loading) CircularProgressIndicator()
            if (entries.isEmpty() && !issue && !loading) EmptyState(variant = EmptyStateVariant.Sessions, compact = true)
            entries.forEach { profile ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(profile.scope, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(enabled = !busy, onClick = { onRefresh(profile) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cookie_profile_refresh))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy, onClick = { onExport(profile) }, contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cookie_profile_export)) }
                            TextButton(enabled = !busy, onClick = { onRemove(profile) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cookie_profile_remove), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        SettingsEditorInfo(stringResource(R.string.cookie_profile_summary, profile.scope, profile.cookies.size,
                            profile.cookies.count { it.attributesAvailable }) + "\n" + profile.userAgent)
                    }
                }
            }
            if (entries.isNotEmpty()) TextButton(enabled = !busy, onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cookie_profile_clear), color = MaterialTheme.colorScheme.error)
            }
            SettingsEditorInfo(stringResource(R.string.cookie_profiles_hint))
    }
}

@Composable
private fun CookieProfileConfirm(mode: String, busy: Boolean, issue: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val label = when (mode) { "remove" -> R.string.cookie_profile_remove; "clear" -> R.string.cookie_profile_clear; else -> R.string.cookie_profile_export }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(label)) }, text = { Column {
        Text(stringResource(if (mode == "export") R.string.cookie_profile_export_hint else R.string.cookie_profile_remove_hint))
        if (issue) Text(stringResource(R.string.cookie_profile_error), color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text(stringResource(label)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
