package dev.qtremors.acqua.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R

@Composable
internal fun CookieProfileEditor(name: String, url: String, refresh: Boolean, authorized: Boolean, busy: Boolean, issue: Boolean,
    onName: (String) -> Unit, onUrl: (String) -> Unit, onAuthorized: (Boolean) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val validUrl = runCatching { java.net.URI(url).let { it.scheme in setOf("http", "https") && it.host != null && it.userInfo == null } }.getOrDefault(false)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(if (refresh) R.string.cookie_profile_refresh else R.string.cookie_profile_new)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.cookie_profile_capture_hint))
            OutlinedTextField(name, { if (it.length <= 60 && it.none(Char::isISOControl)) onName(it) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.name)) }, modifier = Modifier.testTag("cookie-profile-name"))
            OutlinedTextField(url, { if (it.length <= 8192) onUrl(it) }, enabled = !busy, readOnly = refresh, singleLine = true,
                label = { Text(stringResource(R.string.cookie_profile_url)) }, modifier = Modifier.testTag("cookie-profile-url"))
            Row(Modifier.heightIn(min = 48.dp).toggleable(authorized, enabled = !busy, role = Role.Checkbox, onValueChange = onAuthorized),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(authorized, onCheckedChange = null)
                Text(stringResource(R.string.cookie_profile_authorize))
            }
            if (issue) Text(stringResource(R.string.cookie_profile_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy && authorized && validUrl && name.isNotBlank(), onClick = onSave) {
        Text(stringResource(R.string.cookie_profile_save))
    } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
