package dev.qtremors.acqua.ui.components

import android.content.Intent
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import io.noties.markwon.linkify.LinkifyPlugin
import java.net.URI

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier, baseUrl: String? = null) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val textSize = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.fontSize.toPx() }
    val renderer = remember(context, baseUrl) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    baseUrl?.let { builder.imageDestinationProcessor(ImageDestinationProcessorRelativeToAbsolute(it.replace("/blob/", "/raw/"))) }
                    builder.linkResolver { view, link ->
                        val resolved = runCatching { baseUrl?.let { URI(it).resolve(link).toString() } ?: link }.getOrNull()
                        if (resolved != null && resolved.toUri().scheme in setOf("https", "http", "mailto")) {
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, resolved.toUri())) }
                        }
                    }
                }
            })
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { TextView(it).apply { setTextIsSelectable(true) } },
        update = { view ->
            view.setTextColor(colors.onSurface.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            // Avoid resetting selection or starting image requests on unrelated recomposition.
            if (view.tag != Pair(markdown, renderer)) {
                renderer.setMarkdown(view, markdown)
                view.movementMethod = TableAwareMovementMethod.create()
                view.tag = Pair(markdown, renderer)
            }
        }
    )
}
