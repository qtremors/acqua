package dev.qtremors.acqua.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.ui.theme.LocalReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class EmptyStateVariant {
    Generic,
    ReadyToDownload,
    InvalidLink,
    History,
    Search,
    Websites,
    Templates,
    Sessions,
    Diagnostics,
    Captions,
    Chapters,
    Apps,
    Playlist,
    MediaDetails,
    Streams,
    AudioTracks
}

private fun createMorphingShape(progress: Float): Shape {
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val numPoints = 8
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.width.coerceAtMost(size.height) / 2f

            val points = List(numPoints) { i ->
                val angle = (i * 2.0 * PI / numPoints).toFloat()

                // Slowly morphing wave components to resemble dynamic liquid shapes
                val wave = sin(angle * 3f + progress * 2f * PI.toFloat()) * 0.12f +
                    cos(angle * 2f - progress * 2f * PI.toFloat() * 0.8f) * 0.08f

                val r = baseR * (0.8f + wave)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                Offset(x, y)
            }

            val path = Path().apply {
                val firstMid = Offset(
                    (points[0].x + points[numPoints - 1].x) / 2f,
                    (points[0].y + points[numPoints - 1].y) / 2f
                )
                moveTo(firstMid.x, firstMid.y)

                for (i in 0 until numPoints) {
                    val current = points[i]
                    val next = points[(i + 1) % numPoints]
                    val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
                    quadraticTo(current.x, current.y, mid.x, mid.y)
                }
                close()
            }
            return Outline.Generic(path)
        }
    }
}

/**
 * A minimal, context-aware empty state component with liquid physics blobs and morphing icon.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    variant: EmptyStateVariant = EmptyStateVariant.Generic,
    icon: ImageVector? = null,
    title: String? = null,
    description: String? = null,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    val reduceMotion = LocalReducedMotionEnabled.current
    val visuals = emptyStateVisuals(variant)
    val resolvedTitle = title ?: emptyStateTitle(variant)
    val resolvedDescription = description ?: emptyStateDescription(variant)

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val titleBrush = remember(primaryColor, secondaryColor, tertiaryColor) {
        Brush.linearGradient(
            colors = listOf(primaryColor, secondaryColor, tertiaryColor)
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        EmptyStateBlobs(Modifier.matchParentSize())

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 0.dp else 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 20.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (reduceMotion) {
                    EmptyStateIcon(icon ?: visuals.icon, visuals.containerColor, visuals.iconTint, compact)
                } else {
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(durationMillis = 180))) {
                        EmptyStateIcon(icon ?: visuals.icon, visuals.containerColor, visuals.iconTint, compact)
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))

                Text(
                    text = resolvedTitle,
                    style = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(
                        fontWeight = FontWeight.Bold,
                        brush = titleBrush
                    ),
                    textAlign = TextAlign.Center
                )

                if (resolvedDescription != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = resolvedDescription,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = if (compact) 0.dp else 16.dp)
                    )
                }

                if (action != null) {
                    Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))
                    action()
                }
            }
        }
    }
}

@Composable
private fun EmptyStateIcon(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    compact: Boolean
) {
    val reduceMotion = LocalReducedMotionEnabled.current

    val transition = rememberInfiniteTransition(label = "iconPulse")
    val scale = if (reduceMotion) 1f else {
        transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        ).value
    }

    val rotation = if (reduceMotion) 0f else {
        transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation"
        ).value
    }

    val morphProgress = if (reduceMotion) 0f else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "morphProgress"
        ).value
    }

    val colors = MaterialTheme.colorScheme
    val primColor = colors.primary
    val secColor = colors.secondary
    val tertColor = colors.tertiary
    val borderBrush = remember(primColor, secColor, tertColor) {
        Brush.sweepGradient(
            colors = listOf(
                primColor.copy(alpha = 0.7f),
                secColor.copy(alpha = 0.2f),
                tertColor.copy(alpha = 0.7f),
                primColor.copy(alpha = 0.7f)
            )
        )
    }

    val morphingShape = remember(morphProgress) {
        createMorphingShape(morphProgress)
    }

    Surface(
        shape = morphingShape,
        color = containerColor.copy(alpha = 0.25f),
        border = BorderStroke(1.5.dp, borderBrush),
        modifier = Modifier
            .size(if (compact) 64.dp else 96.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 28.dp else 38.dp),
                tint = iconTint
            )
        }
    }
}

private data class EmptyStateVisuals(
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color
)

@Composable
private fun emptyStateVisuals(variant: EmptyStateVariant): EmptyStateVisuals {
    val colors = MaterialTheme.colorScheme
    val quietContainer = colors.surfaceContainerHighest
    val quietTint = colors.onSurfaceVariant
    return when (variant) {
        EmptyStateVariant.Generic -> EmptyStateVisuals(Icons.Default.Inbox, quietContainer, quietTint)
        EmptyStateVariant.ReadyToDownload -> EmptyStateVisuals(Icons.Default.Download, quietContainer, colors.primary)
        EmptyStateVariant.InvalidLink -> EmptyStateVisuals(Icons.Default.Warning, colors.errorContainer, colors.error)
        EmptyStateVariant.History -> EmptyStateVisuals(Icons.Default.History, quietContainer, colors.primary)
        EmptyStateVariant.Search -> EmptyStateVisuals(Icons.Default.SearchOff, quietContainer, colors.secondary)
        EmptyStateVariant.Websites -> EmptyStateVisuals(Icons.Default.Public, quietContainer, colors.primary)
        EmptyStateVariant.Templates -> EmptyStateVisuals(Icons.Default.Terminal, quietContainer, colors.primary)
        EmptyStateVariant.Sessions -> EmptyStateVisuals(Icons.Default.Cookie, quietContainer, colors.primary)
        EmptyStateVariant.Diagnostics -> EmptyStateVisuals(Icons.Default.BugReport, quietContainer, colors.primary)
        EmptyStateVariant.Captions -> EmptyStateVisuals(Icons.Default.Subtitles, quietContainer, colors.primary)
        EmptyStateVariant.Chapters -> EmptyStateVisuals(Icons.Default.ViewList, quietContainer, colors.primary)
        EmptyStateVariant.Apps -> EmptyStateVisuals(Icons.Default.Apps, quietContainer, colors.primary)
        EmptyStateVariant.Playlist -> EmptyStateVisuals(Icons.Default.VideoLibrary, quietContainer, colors.primary)
        EmptyStateVariant.MediaDetails -> EmptyStateVisuals(Icons.Default.Search, quietContainer, colors.primary)
        EmptyStateVariant.Streams -> EmptyStateVisuals(Icons.Default.Tune, quietContainer, colors.primary)
        EmptyStateVariant.AudioTracks -> EmptyStateVisuals(Icons.Default.Audiotrack, quietContainer, colors.primary)
    }
}

@Composable
private fun emptyStateTitle(variant: EmptyStateVariant): String =
    when (variant) {
        EmptyStateVariant.Generic -> stringResource(R.string.no_history)
        EmptyStateVariant.ReadyToDownload -> stringResource(R.string.ready_to_download)
        EmptyStateVariant.InvalidLink -> stringResource(R.string.invalid_link)
        EmptyStateVariant.History -> stringResource(R.string.no_history)
        EmptyStateVariant.Search -> stringResource(R.string.no_history_search_results)
        EmptyStateVariant.Websites -> stringResource(R.string.empty_websites_title)
        EmptyStateVariant.Templates -> stringResource(R.string.empty_templates_title)
        EmptyStateVariant.Sessions -> stringResource(R.string.empty_sessions_title)
        EmptyStateVariant.Diagnostics -> stringResource(R.string.empty_diagnostics_title)
        EmptyStateVariant.Captions -> stringResource(R.string.empty_captions_title)
        EmptyStateVariant.Chapters -> stringResource(R.string.empty_chapters_title)
        EmptyStateVariant.Apps -> stringResource(R.string.empty_apps_title)
        EmptyStateVariant.Playlist -> stringResource(R.string.empty_playlist_title)
        EmptyStateVariant.MediaDetails -> stringResource(R.string.empty_media_details_title)
        EmptyStateVariant.Streams -> stringResource(R.string.empty_streams_title)
        EmptyStateVariant.AudioTracks -> stringResource(R.string.empty_audio_tracks_title)
    }

@Composable
private fun emptyStateDescription(variant: EmptyStateVariant): String? =
    when (variant) {
        EmptyStateVariant.Generic -> null
        EmptyStateVariant.ReadyToDownload -> stringResource(R.string.supported_link_guidance)
        EmptyStateVariant.InvalidLink -> stringResource(R.string.valid_link_guidance)
        EmptyStateVariant.History -> "Media and links you download will appear here."
        EmptyStateVariant.Search -> "Try searching with different keywords or clearing filters."
        EmptyStateVariant.Websites -> stringResource(R.string.empty_websites_description)
        EmptyStateVariant.Templates -> stringResource(R.string.command_templates_empty)
        EmptyStateVariant.Sessions -> stringResource(R.string.empty_sessions_description)
        EmptyStateVariant.Diagnostics -> stringResource(R.string.diagnostics_empty)
        EmptyStateVariant.Captions -> stringResource(R.string.no_captions_available)
        EmptyStateVariant.Chapters -> stringResource(R.string.no_chapters_available)
        EmptyStateVariant.Apps -> stringResource(R.string.empty_apps_description)
        EmptyStateVariant.Playlist -> stringResource(R.string.empty_playlist_description)
        EmptyStateVariant.MediaDetails, EmptyStateVariant.Streams, EmptyStateVariant.AudioTracks -> null
    }
