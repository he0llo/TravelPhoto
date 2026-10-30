package he.travelphoto.llo.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageCodec {
    fun decodeBitmap(file: File, maxSize: Int): Bitmap? {
        if (!file.isFile || maxSize <= 0) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null

            var sample = 1
            while (width / (sample * 2) >= maxSize || height / (sample * 2) >= maxSize) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val orientation = runCatching {
                ExifInterface(file).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            bitmap = applyOrientation(bitmap, orientation)

            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > maxSize) {
                val ratio = maxSize.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    fun readSize(file: File): Pair<Int, Int>? {
        if (!file.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val orientation = runCatching {
                ExifInterface(file).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val swap = orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
            if (swap) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        } catch (_: Throwable) {
            null
        }
    }

    fun createThumbnail(source: File, destination: File, maxSize: Int = 640): Pair<Int, Int>? {
        val bitmap = decodeBitmap(source, maxSize) ?: return null
        return try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)) return null
            }
            bitmap.width to bitmap.height
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun compressToJpeg(bitmap: Bitmap, out: OutputStream, quality: Int = 88): Boolean =
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        } catch (_: Throwable) {
            false
        }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return source
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return try {
            val rotated = Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true,
            )
            if (rotated !== source) source.recycle()
            rotated
        } catch (_: Throwable) {
            source
        }
    }
}

/**
 * Small in-memory cache for UI images. Cached bitmaps are shared and must never be recycled by callers.
 */
object PhotoBitmapLoader {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(file: File, maxSize: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(file, maxSize) ?: return@withContext null
        bitmap.asImageBitmap()
    }

    fun loadBitmap(file: File, maxSize: Int): Bitmap? {
        val key = "${file.absolutePath}|${file.lastModified()}|$maxSize"
        cache.get(key)?.let { return it }
        val bitmap = ImageCodec.decodeBitmap(file, maxSize) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}
