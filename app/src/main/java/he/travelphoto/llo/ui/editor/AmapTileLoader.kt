package he.travelphoto.llo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import he.travelphoto.llo.data.MapProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and caches raster map tiles for the editor.
 *
 * Both sources are plain XYZ tiles in Web Mercator, so the same projection is
 * used for tiles, photo markers and taps. Tiles are cached in memory and on
 * disk so panning and zooming stay smooth after the first visit. No API key is
 * embedded in the application.
 */
internal class AmapTileLoader(
    context: Context,
    private val provider: MapProvider = MapProvider.AMAP,
) {

    private val diskDir = File(context.applicationContext.cacheDir, "${provider.id}_tiles").apply { mkdirs() }

    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        memory.get(key)?.let { return it.asImageBitmap() }
        val bitmap = withContext(Dispatchers.IO) {
            readFromDisk(key) ?: download(z, x, y)?.also { writeToDisk(key, it) }
        } ?: return null
        memory.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    private fun readFromDisk(key: String): Bitmap? {
        val file = File(diskDir, key.replace('/', '_') + ".png")
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun writeToDisk(key: String, bitmap: Bitmap) {
        runCatching {
            val file = File(diskDir, key.replace('/', '_') + ".png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            pruneDiskCache()
        }
    }

    private fun pruneDiskCache() {
        val files = diskDir.listFiles() ?: return
        if (files.size < 600) return
        files.sortedBy { it.lastModified() }
            .take(files.size - 450)
            .forEach { it.delete() }
    }

    private fun tileUrl(z: Int, x: Int, y: Int): String {
        val server = 1 + ((x + y) and 3)
        return when (provider) {
            MapProvider.AMAP ->
                "https://webrd0$server.is.autonavi.com/appmaptile" +
                    "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$z"

            MapProvider.OSM ->
                "https://tile.openstreetmap.org/$z/$x/$y.png"
        }
    }

    private fun download(z: Int, x: Int, y: Int): Bitmap? {
        val url = URL(tileUrl(z, x, y))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TravelPhoto/1.0 (he0llo; github.com/he0llo/TravelPhoto)")
            setRequestProperty("Accept", "image/png,image/*")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
