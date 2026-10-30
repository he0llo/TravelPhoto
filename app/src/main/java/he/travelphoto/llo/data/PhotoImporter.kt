package he.travelphoto.llo.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import he.travelphoto.llo.util.ImageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class PhotoImporter(
    context: Context,
    private val storage: AlbumStorage,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    fun newPhotoId(index: Int): String =
        "p_${System.currentTimeMillis().toString(36)}_${index}_${UUID.randomUUID().toString().take(6)}"

    suspend fun importUri(uri: Uri, albumId: String, index: Int): Photo? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val extension = resolveExtension(resolver.getType(uri), displayName)
        val id = newPhotoId(index)
        val photoFile = File(storage.photosDir(albumId), "$id.$extension")
        val thumbFile = File(storage.thumbsDir(albumId), "$id.jpg")

        try {
            val input = openOriginalStream(uri) ?: return@withContext null
            input.use { source ->
                photoFile.outputStream().use { target -> source.copyTo(target) }
            }
            val exif = readExif(photoFile)
            val takenAt = readTakenAt(exif, uri)
            val location = readLocation(exif)
            val dimensions = ImageCodec.createThumbnail(photoFile, thumbFile)
                ?: run {
                    photoFile.delete()
                    thumbFile.delete()
                    return@withContext null
                }
            val originalSize = ImageCodec.readSize(photoFile)

            Photo(
                id = id,
                fileName = photoFile.name,
                thumbName = thumbFile.name,
                takenAt = takenAt,
                latitude = location?.first,
                longitude = location?.second,
                width = originalSize?.first ?: dimensions.first,
                height = originalSize?.second ?: dimensions.second,
                placeName = location?.let {
                    he.travelphoto.llo.util.PlaceDirectory.nameFor(it.first, it.second)
                },
            )
        } catch (_: Throwable) {
            photoFile.delete()
            thumbFile.delete()
            null
        }
    }

    suspend fun importArchivePhoto(
        source: File,
        albumId: String,
        photoId: String,
        fileName: String,
        takenAt: Long,
        latitude: Double?,
        longitude: Double?,
        placeName: String? = null,
        note: String? = null,
    ): Photo? = withContext(Dispatchers.IO) {
        val extension = fileName.substringAfterLast('.', "")
            .lowercase(Locale.US)
            .takeIf { it in ALLOWED_EXTENSIONS }
            ?: source.extension.lowercase(Locale.US).takeIf { it in ALLOWED_EXTENSIONS }
            ?: "jpg"
        val photoFile = File(storage.photosDir(albumId), "$photoId.$extension")
        val thumbFile = File(storage.thumbsDir(albumId), "$photoId.jpg")
        try {
            source.copyTo(photoFile, overwrite = true)
            val dimensions = ImageCodec.createThumbnail(photoFile, thumbFile)
                ?: run {
                    photoFile.delete()
                    thumbFile.delete()
                    return@withContext null
                }
            val originalSize = ImageCodec.readSize(photoFile)
            Photo(
                id = photoId,
                fileName = photoFile.name,
                thumbName = thumbFile.name,
                takenAt = takenAt,
                latitude = latitude,
                longitude = longitude,
                width = originalSize?.first ?: dimensions.first,
                height = originalSize?.second ?: dimensions.second,
                placeName = placeName ?: latitude?.let { lat ->
                    longitude?.let { lon ->
                        he.travelphoto.llo.util.PlaceDirectory.nameFor(lat, lon)
                    }
                },
                note = note,
            )
        } catch (_: Throwable) {
            photoFile.delete()
            thumbFile.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    /**
     * Android 17 的 Photo Picker 只有在用户同意「包含位置信息」后才会返回可定位的原图。
     * 此时把 URI 换成 requireOriginal 变体，才能读到未被抹除的 GPS EXIF；
     * 授权缺失时安静回退到普通 URI，照片仍可导入，只是等待用户手动补点。
     */
    private fun openOriginalStream(uri: Uri): java.io.InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val original = runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull()
            if (original != null) {
                runCatching { resolver.openInputStream(original) }
                    .getOrNull()
                    ?.let { return it }
            }
        }
        return resolver.openInputStream(uri)
    }

    private fun readExif(file: File): ExifInterface? =
        runCatching { ExifInterface(file) }.getOrNull()

    private fun readTakenAt(exif: ExifInterface?, uri: Uri): Long {
        val tags = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME,
        )
        if (exif != null) {
            tags.forEach { tag ->
                val raw = runCatching { exif.getAttribute(tag) }.getOrNull()
                parseExifDate(raw)?.let { return it }
            }
        }
        readMediaStoreDate(uri)?.let { return it }
        return System.currentTimeMillis()
    }

    private fun parseExifDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
        val formats = listOf(
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy:MM:dd HH:mm",
        )
        formats.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }.parse(cleaned)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun readMediaStoreDate(uri: Uri): Long? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return uri.path?.let { path -> File(path).takeIf { it.isFile }?.lastModified() }
        }
        return runCatching {
            val projection = arrayOf("datetaken", "date_added", "date_modified")
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val takenIndex = cursor.getColumnIndex("datetaken")
                if (takenIndex >= 0 && !cursor.isNull(takenIndex)) {
                    val value = cursor.getLong(takenIndex)
                    if (value > 0L) return@use value
                }
                val addedIndex = cursor.getColumnIndex("date_added")
                if (addedIndex >= 0 && !cursor.isNull(addedIndex)) {
                    val value = cursor.getLong(addedIndex)
                    if (value > 0L) return@use value * 1000L
                }
                val modifiedIndex = cursor.getColumnIndex("date_modified")
                if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                    val value = cursor.getLong(modifiedIndex)
                    if (value > 0L) return@use value * 1000L
                }
                null
            }
        }.getOrNull()
    }

    private fun readLocation(exif: ExifInterface?): Pair<Double, Double>? {
        if (exif == null) return null
        val result = FloatArray(2)
        val hasLocation = runCatching { exif.getLatLong(result) }.getOrDefault(false)
        if (!hasLocation) return null
        val latitude = result[0].toDouble()
        val longitude = result[1].toDouble()
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return latitude to longitude
    }

    private fun resolveExtension(mime: String?, displayName: String?): String {
        val fromMime = when (mime?.lowercase(Locale.US)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/gif" -> "gif"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            else -> null
        }
        if (fromMime != null) return fromMime
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase(Locale.US)
        return if (fromName != null && fromName in ALLOWED_EXTENSIONS) fromName else "jpg"
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    }
}
