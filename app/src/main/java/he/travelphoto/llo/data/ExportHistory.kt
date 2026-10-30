package he.travelphoto.llo.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One file produced by the exporter, kept so the export screen can offer "my exports". */
data class ExportedFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val albumTitle: String,
    val format: String,
    val mime: String,
    val path: String?,
    val contentUri: String?,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Small local history of exported files. Paths and SAF URIs stay on the device; nothing is
 * uploaded. The list is intentionally capped so it never grows without bound.
 */
class ExportHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<ExportedFile> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ExportedFile(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name"),
                            albumTitle = item.optString("album"),
                            format = item.optString("format"),
                            mime = item.optString("mime", "application/octet-stream"),
                            path = item.optString("path").takeIf { it.isNotBlank() },
                            contentUri = item.optString("contentUri").takeIf { it.isNotBlank() },
                            size = item.optLong("size", 0L),
                            createdAt = item.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(file: ExportedFile) {
        val current = list().filterNot { old ->
            old.name == file.name && old.path == file.path && old.contentUri == file.contentUri
        }
        val next = (listOf(file) + current).take(MAX_ITEMS)
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("album", item.albumTitle)
                    put("format", item.format)
                    put("mime", item.mime)
                    put("path", item.path.orEmpty())
                    put("contentUri", item.contentUri.orEmpty())
                    put("size", item.size)
                    put("createdAt", item.createdAt)
                },
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun remove(id: String) {
        val array = JSONArray()
        list().filterNot { it.id == id }.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("album", item.albumTitle)
                    put("format", item.format)
                    put("mime", item.mime)
                    put("path", item.path.orEmpty())
                    put("contentUri", item.contentUri.orEmpty())
                    put("size", item.size)
                    put("createdAt", item.createdAt)
                },
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "travelphoto_exports"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 40
    }
}

/** A readable URI for sharing/opening; SAF exports use their content URI, file exports use FileProvider. */
fun ExportedFile.readableUri(context: Context): Uri? {
    contentUri?.let { return Uri.parse(it) }
    val file = path?.let(::File) ?: return null
    if (!file.isFile) return null
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

fun ExportedFile.exists(): Boolean {
    if (contentUri != null) return true
    return path?.let { File(it).isFile } == true
}
