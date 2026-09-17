package dev.qtremors.acqua.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.feature.onboarding.OnboardingRestoreState
import dev.qtremors.acqua.feature.onboarding.OnboardingRestoreFailure

@Composable
fun OnboardingRestoreDialog(
    state: OnboardingRestoreState,
    onApplyRestoreBackup: () -> Unit,
    onDismissRestoreBackup: () -> Unit,
    onRestartApp: () -> Unit = {}
) {
    when (state) {
        OnboardingRestoreState.Idle -> Unit

        OnboardingRestoreState.Busy -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.restore_backup)) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                },
                confirmButton = {}
            )
        }

        is OnboardingRestoreState.Preview -> {
            AlertDialog(
                onDismissRequest = onDismissRestoreBackup,
                title = { Text(stringResource(R.string.restore_backup_confirmation_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.restore_backup_confirmation_message),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.backup_preview_items_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        state.preview.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "  ${item.label}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onApplyRestoreBackup) {
                        Text(stringResource(R.string.restore_backup))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissRestoreBackup) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        is OnboardingRestoreState.Success -> {
            AlertDialog(
                onDismissRequest = onDismissRestoreBackup,
                title = { Text(stringResource(R.string.restore_backup)) },
                text = {
                    Text(
                        text = stringResource(R.string.backup_restored_success),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDismissRestoreBackup()
                        if (state.requiresRestart) onRestartApp()
                    }) {
                        Text(stringResource(R.string.done))
                    }
                }
            )
        }

        is OnboardingRestoreState.Error -> {
            AlertDialog(
                onDismissRequest = onDismissRestoreBackup,
                title = { Text(stringResource(R.string.restore_backup)) },
                text = {
                    Text(
                        text = stringResource(
                            when (state.failure) {
                                OnboardingRestoreFailure.INSPECT -> R.string.backup_inspect_failed
                                OnboardingRestoreFailure.RESTORE -> R.string.backup_restore_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissRestoreBackup) {
                        Text(stringResource(R.string.done))
                    }
                }
            )
        }
    }
}
