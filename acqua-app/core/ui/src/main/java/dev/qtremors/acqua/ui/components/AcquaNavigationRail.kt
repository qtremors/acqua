package dev.qtremors.acqua.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun AcquaNavigationRail(
    items: List<AcquaTabItem>,
    selectedIndex: Int,
    fabAction: AcquaFabAction?,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
        header = {
            if (fabAction != null) {
                FloatingActionButton(
                    onClick = fabAction.onClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    val description = fabAction.contentDescriptionRes?.let { stringResource(it) }
                        ?: fabAction.contentDescription
                    when {
                        fabAction.icon != null -> Icon(fabAction.icon, description)
                        fabAction.iconRes != null -> Icon(painterResource(fabAction.iconRes), description)
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
            }
        }
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = selectedIndex == index,
                onClick = item.onClick,
                icon = {
                    BadgedBox(badge = { if (item.hasBadge) Badge() }) {
                        when {
                            item.icon != null -> Icon(item.icon, stringResource(item.labelRes))
                            item.iconRes != null -> Icon(painterResource(item.iconRes), stringResource(item.labelRes))
                        }
                    }
                },
                label = { Text(stringResource(item.labelRes)) }
            )
        }
    }
}
