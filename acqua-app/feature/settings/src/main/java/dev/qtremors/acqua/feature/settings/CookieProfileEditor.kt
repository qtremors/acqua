package dev.qtremors.acqua.feature.settings

import dev.qtremors.acqua.ui.components.ExpressiveTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.components.SettingsCardContainer
import dev.qtremors.acqua.ui.components.SettingsEditorDialog
import dev.qtremors.acqua.ui.components.SettingsEditorInfo

@Composable
internal fun CookieProfileEditor(name: String, url: String, refresh: Boolean, authorized: Boolean, busy: Boolean, issue: Boolean,
    onName: (String) -> Unit, onUrl: (String) -> Unit, onAuthorized: (Boolean) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val validUrl = runCatching { java.net.URI(url).let { it.scheme in setOf("http", "https") && it.host != null && it.userInfo == null } }.getOrDefault(false)
    SettingsEditorDialog(stringResource(if (refresh) R.string.cookie_profile_refresh else R.string.cookie_profile_new), onDismiss,
        stringResource(R.string.cookie_profile_save), onSave, confirmEnabled = !busy && authorized && validUrl && name.isNotBlank(),
        dismissEnabled = !busy, tag = "saved-session-editor") {
            Text(stringResource(R.string.settings_session_brief), style = MaterialTheme.typography.bodyMedium)
            ExpressiveTextField(name, { if (it.length <= 60 && it.none(Char::isISOControl)) onName(it) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth().testTag("cookie-profile-name"))
            ExpressiveTextField(url, { if (it.length <= 8192) onUrl(it) }, enabled = !busy, readOnly = refresh, singleLine = true,
                label = { Text(stringResource(R.string.cookie_profile_url)) }, modifier = Modifier.fillMaxWidth().testTag("cookie-profile-url"),
                isError = url.isNotBlank() && !validUrl)
            SettingsCardContainer(Modifier.heightIn(min = 56.dp).toggleable(authorized, enabled = !busy, role = Role.Checkbox,
                onValueChange = onAuthorized).semantics(mergeDescendants = true) { }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(authorized, onCheckedChange = null, enabled = !busy, modifier = Modifier.clearAndSetSemantics { })
                    Text(stringResource(R.string.cookie_profile_authorize), Modifier.weight(1f))
                }
            }
            if (issue) Text(stringResource(R.string.cookie_profile_error), color = MaterialTheme.colorScheme.error)
            SettingsEditorInfo(stringResource(R.string.cookie_profile_capture_hint))
    }
}
