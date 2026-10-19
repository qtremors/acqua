package dev.qtremors.acqua.feature.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.session.CookieProfileStore
import dev.qtremors.acqua.ui.components.EmptyState
import dev.qtremors.acqua.ui.components.EmptyStateVariant
import dev.qtremors.acqua.downloader.YtDlpDownloadOptions

@Composable
fun CookieProfileSelector(options: YtDlpDownloadOptions, store: CookieProfileStore? = null, onApply: (YtDlpDownloadOptions) -> Unit) {
    val context = LocalContext.current
    val profiles = remember(context, store) { CookieProfileChoices(context, store) }
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(options.cookieProfileId) }
    val entries = remember(open, options.cookieProfileId, options.cookieProfileFingerprint) { runCatching { profiles.entries() }.getOrNull() }
    val selected = entries?.firstOrNull { it.id == options.cookieProfileId }
    val missing = options.cookieProfileId != null && (selected == null || options.cookieProfileFingerprint != null && selected.fingerprint != options.cookieProfileFingerprint)
    val enabled = profiles.enabled
    val label = if (missing) stringResource(R.string.cookie_profile_changed) else selected?.name ?: stringResource(R.string.cookie_profile_browser)
    OutlinedButton(onClick = { draft = options.cookieProfileId; open = true }) { Text(stringResource(R.string.cookie_profile_selection, label)) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.cookie_profiles)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            Text(stringResource(R.string.cookie_profile_selection_hint))
            if (!enabled) Text(stringResource(R.string.cookie_profile_disabled), color = MaterialTheme.colorScheme.error)
            if (entries == null) Text(stringResource(R.string.cookie_profile_error), color = MaterialTheme.colorScheme.error)
            CookieProfileChoice(stringResource(R.string.cookie_profile_browser), draft == null, true) { draft = null }
            if (entries?.isEmpty() == true) EmptyState(variant = EmptyStateVariant.Sessions, compact = true)
            entries.orEmpty().forEach { profile ->
                CookieProfileChoice("${profile.name}\n${profile.scope}", draft == profile.id, enabled) { draft = profile.id }
            }
        }
    }, confirmButton = { TextButton(enabled = draft == null || enabled && entries.orEmpty().any { it.id == draft }, onClick = {
        val profile = entries.orEmpty().firstOrNull { it.id == draft }
        onApply(options.copy(cookieProfileId = profile?.id, cookieProfileFingerprint = profile?.fingerprint)); open = false
    }) { Text(stringResource(R.string.done)) } }, dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CookieProfileChoice(label: String, selected: Boolean, enabled: Boolean, onChoose: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onChoose).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
