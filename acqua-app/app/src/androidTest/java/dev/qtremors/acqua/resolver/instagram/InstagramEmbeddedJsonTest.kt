package dev.qtremors.acqua.resolver.instagram

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstagramEmbeddedJsonTest {
    @Test
    fun findsCarouselNestedInRenderedPageJson() {
        val html = """
            <html><body><script type="application/json">
            {"require":[["Post",[],{"data":{"xdt_shortcode_media":{
              "display_url":"https://cdn.test/cover.jpg",
              "edge_sidecar_to_children":{"edges":[
                {"node":{"display_url":"https://cdn.test/one.jpg","is_video":false}},
                {"node":{"display_url":"https://cdn.test/two.jpg","is_video":false}}
              ]}
            }}}]]}
            </script></body></html>
        """.trimIndent()

        val media = InstagramEmbeddedJson.findShortcodeMedia(html)

        assertNotNull(media)
        assertEquals(2, media?.getJSONObject("edge_sidecar_to_children")?.getJSONArray("edges")?.length())
    }

    @Test
    fun prefersExplicitPostPayloadOverUnrelatedPageJson() {
        val html = """
            <script type="application/json">{"display_url":"https://cdn.test/avatar.jpg"}</script>
            <script type="application/json">{"shortcode_media":{"display_url":"https://cdn.test/post.jpg"}}</script>
        """.trimIndent()

        assertEquals(
            "https://cdn.test/post.jpg",
            InstagramEmbeddedJson.findShortcodeMedia(html)?.getString("display_url")
        )
    }
}
