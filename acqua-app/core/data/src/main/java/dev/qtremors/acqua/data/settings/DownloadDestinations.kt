package dev.qtremors.acqua.data.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.qtremors.acqua.domain.MediaKind
import dev.qtremors.acqua.domain.ResolvedMedia
import org.json.JSONObject

// Folder names stay stable across locale changes and retries.
private const val UNKNOWN_SOURCE_FOLDER = "Unknown source"
private const val COLLECTION_FOLDER = "Collection"

enum class FolderOrganization { NONE, SOURCE, COLLECTION, SOURCE_COLLECTION }

data class DownloadDestination(
    val treeUri: String? = null,
    val organization: FolderOrganization = FolderOrganization.NONE,
    val hidden: Boolean = false
) {
    fun validated(): DownloadDestination = apply {
        require(!hidden || treeUri != null)
        treeUri?.let {
            require(it.length <= 4096 && Uri.parse(it).scheme == "content" && DocumentsContract.isTreeUri(Uri.parse(it)))
        }
    }

    fun folders(media: ResolvedMedia, sourceUrl: String): List<String> = buildList {
        if (hidden) add("Acqua hidden")
        if (organization == FolderOrganization.SOURCE || organization == FolderOrganization.SOURCE_COLLECTION) {
            add(segment(Uri.parse(sourceUrl).host ?: UNKNOWN_SOURCE_FOLDER))
        }
        if (organization == FolderOrganization.COLLECTION || organization == FolderOrganization.SOURCE_COLLECTION) {
            media.collectionTitle?.takeIf(String::isNotBlank)?.let { add(segment(it)) }
                ?: media.collectionUrl?.let { add(segment(Uri.parse(it).lastPathSegment ?: COLLECTION_FOLDER)) }
        }
    }

    private fun segment(value: String): String {
        val cleaned = value.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim(' ', '.')
        val result = StringBuilder()
        var bytes = 0
        cleaned.codePoints().limit(80).forEachOrdered { point ->
            val text = String(Character.toChars(point))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (bytes + size <= 80) { result.append(text); bytes += size }
        }
        return result.toString().ifBlank { COLLECTION_FOLDER }
    }
}

data class DownloadDestinations(
    val images: DownloadDestination = DownloadDestination(),
    val audio: DownloadDestination = DownloadDestination(),
    val video: DownloadDestination = DownloadDestination()
) {
    operator fun get(kind: MediaKind): DownloadDestination = when (kind) {
        MediaKind.IMAGE -> images
        MediaKind.AUDIO -> audio
        MediaKind.VIDEO -> video
    }

    fun replacing(kind: MediaKind, value: DownloadDestination): DownloadDestinations = when (kind) {
        MediaKind.IMAGE -> copy(images = value.validated())
        MediaKind.AUDIO -> copy(audio = value.validated())
        MediaKind.VIDEO -> copy(video = value.validated())
    }

    fun json(): JSONObject = JSONObject().apply {
        MediaKind.entries.forEach { kind ->
            val item = get(kind).validated()
            put(kind.name, JSONObject().put("tree", item.treeUri ?: JSONObject.NULL)
                .put("organization", item.organization.name).put("hidden", item.hidden))
        }
    }

    companion object {
        fun parse(json: JSONObject?): DownloadDestinations {
            if (json == null) return DownloadDestinations()
            var destinations = DownloadDestinations()
            MediaKind.entries.forEach { kind ->
                val item = json.getJSONObject(kind.name)
                val tree = if (item.isNull("tree")) null else item.getString("tree")
                destinations = destinations.replacing(kind, DownloadDestination(tree,
                    FolderOrganization.valueOf(item.getString("organization")), item.getBoolean("hidden")))
            }
            return destinations
        }
    }
}

class DownloadDestinationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("download_destinations_v1", Context.MODE_PRIVATE)
    fun get(): DownloadDestinations = DownloadDestinations.parse(preferences.getString("destinations", null)?.let(::JSONObject))
    fun put(kind: MediaKind, value: DownloadDestination) = synchronized(DownloadDestinationStore::class.java) {
        check(preferences.edit().putString("destinations", get().replacing(kind, value).json().toString()).commit())
    }
}
