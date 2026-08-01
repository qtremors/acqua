package dev.qtremors.acqua.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                stringResource(R.string.notices_introduction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(AcquaOpenSourceComponents.all, key = OpenSourceComponent::name) { component ->
            OpenSourceComponentCard(
                component = component,
                onOpenSource = { uriHandler.openUri(component.sourceUrl) },
                onOpenLicense = { uriHandler.openUri(component.licenseUrl) }
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .clickable { showCompleteNotice = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Balance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(
                            stringResource(R.string.notices_full_document),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            stringResource(R.string.notices_full_document_description),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Filled.Balance, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
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
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            introduction?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 14.dp)
            )
            Text(
                document ?: stringResource(R.string.legal_document_unavailable),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
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

@Composable
private fun OpenSourceComponentCard(
    component: OpenSourceComponent,
    onOpenSource: () -> Unit,
    onOpenLicense: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    component.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                component.purpose,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                component.license,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenSource) {
                    Text(stringResource(R.string.notices_source))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 3.dp)
                    )
                }
                TextButton(onClick = onOpenLicense) {
                    Text(stringResource(R.string.notices_license))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 3.dp)
                    )
                }
            }
        }
    }
}
