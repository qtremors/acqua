package dev.qtremors.acqua.feature.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.appinfo.AboutExternalLink
import dev.qtremors.acqua.ui.components.SettingsActionRow
import dev.qtremors.acqua.ui.components.SettingsSection
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    onOpenNotices: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLicense: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme

    val openLink: (AboutExternalLink) -> Unit = { link -> uriHandler.openUri(link.url) }

    val scrollState = rememberScrollState()
    val overscrollOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && overscrollOffset.value > 0) {
                    val toConsume = if (overscrollOffset.value + available.y >= 0) available.y else -overscrollOffset.value
                    coroutineScope.launch { overscrollOffset.snapTo(overscrollOffset.value + toConsume) }
                    return Offset(0f, toConsume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && scrollState.value == 0) {
                    val newOffset = (overscrollOffset.value + available.y * 0.5f).coerceAtMost(300f)
                    coroutineScope.launch { overscrollOffset.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    var isAnimatingIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isAnimatingIn = true }

    val contentAlphaState = animateFloatAsState(
        targetValue = if (isAnimatingIn) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseOut),
        label = "about_content_alpha"
    )
    val contentOffsetState = animateDpAsState(
        targetValue = if (isAnimatingIn) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "about_content_offset"
    )

    val layoutDirection = LocalLayoutDirection.current
    val effectivePadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
        bottom = contentPadding.calculateBottomPadding() + 80.dp
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release && overscrollOffset.value > 0) {
                            coroutineScope.launch {
                                overscrollOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .verticalScroll(scrollState)
            .padding(effectivePadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Compact Branding Header (no duplicate full hero)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = contentAlphaState.value
                    translationY = contentOffsetState.value.toPx()
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.acqua),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(52.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Developer
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_developer)) {
                SettingsActionRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.about_developer_name),
                    description = stringResource(R.string.about_repository_address),
                    leadingIcon = Icons.Filled.Code,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.DEVELOPER) }
                )
            }
        }

        // Section 2: Privacy Policy
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_privacy)) {
                SettingsActionRow(
                    index = 0,
                    count = 1,
                    title = stringResource(R.string.about_privacy_policy),
                    description = stringResource(R.string.about_privacy_description),
                    leadingIcon = Icons.Filled.Lock,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { openLink(AboutExternalLink.PRIVACY) }
                )
            }
        }

        // Section 3: Legal & Notices
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlphaState.value
                translationY = contentOffsetState.value.toPx()
            }
        ) {
            SettingsSection(title = stringResource(R.string.about_support)) {
                SettingsActionRow(
                    index = 0,
                    count = 2,
                    title = stringResource(R.string.about_open_source_notices),
                    description = stringResource(R.string.about_open_source_notices_description),
                    leadingIcon = Icons.AutoMirrored.Filled.Assignment,
                    onClick = onOpenNotices
                )
                SettingsActionRow(
                    index = 1,
                    count = 2,
                    title = stringResource(R.string.about_license),
                    description = stringResource(R.string.about_license_description),
                    leadingIcon = Icons.Filled.Policy,
                    onClick = onOpenLicense
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
