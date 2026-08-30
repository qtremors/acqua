package dev.qtremors.acqua.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.qtremors.acqua.R
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection

@Composable
fun OpenSourceNoticesScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var showCompleteNotice by remember { mutableStateOf(false) }

    if (showCompleteNotice) {
        LegalDocumentDialog(
            title = stringResource(R.string.notices_full_document),
            assetName = "THIRD_PARTY_NOTICES.md",
            onDismiss = { showCompleteNotice = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                stringResource(R.string.about_open_source_notices),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Text(
                stringResource(R.string.notices_introduction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item {
            SettingsSection(title = stringResource(R.string.licenses_section_libraries)) {
                AcquaOpenSourceComponents.all.forEachIndexed { index, component ->
                    SettingsActionRow(
                        index = index,
                        count = AcquaOpenSourceComponents.all.size,
                        title = component.name,
                        description = "${component.purpose} • ${component.license}",
                        leadingIcon = Icons.Filled.Code,
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = { uriHandler.openUri(component.sourceUrl) }
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.notices_full_document)) {
                SettingsActionRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.notices_full_document),
                    description = stringResource(R.string.notices_full_document_description),
                    leadingIcon = Icons.Filled.Balance,
                    trailingIcon = Icons.Filled.Description,
                    onClick = { showCompleteNotice = true }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun LegalDocumentScreen(
    title: String,
    assetName: String,
    modifier: Modifier = Modifier,
    introduction: String? = null
) {
    val document = rememberLegalDocument(assetName)
    SelectionContainer {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            introduction?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Text(
                    document ?: stringResource(R.string.legal_document_unavailable),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentDialog(
    title: String,
    assetName: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxSize(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                SelectionContainer(
                    Modifier.weight(1f).padding(vertical = 14.dp)
                ) {
                    Text(
                        rememberLegalDocument(assetName)
                            ?: stringResource(R.string.legal_document_unavailable),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun rememberLegalDocument(assetName: String): String? {
    val context = LocalContext.current
    return remember(context, assetName) {
        runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}
