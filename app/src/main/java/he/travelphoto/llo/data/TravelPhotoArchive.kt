package he.travelphoto.llo.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The app's own portable album format: a zip archive with a manifest.json
 * (album + photo metadata) and the original image files under photos/.
 */
class TravelPhotoArchive(
    context: Context,
    private val storage: AlbumStorage,
    private val importer: PhotoImporter,
) {
    private val appContext = context.applicationContext

    suspend fun export(album: Album, output: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val existing = album.photos.filter { storage.photoFile(album.id, it).isFile }
        if (existing.isEmpty()) return@withContext false
        runCatching {
            val manifest = buildManifest(album, existing)
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                existing.forEach { photo ->
                    val file = storage.photoFile(album.id, photo)
                    zip.putNextEntry(ZipEntry("photos/${photo.fileName}"))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun import(
        uri: Uri,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Album? = withContext(Dispatchers.IO) {
        val tempDir = File(appContext.cacheDir, "tp_import_${UUID.randomUUID().toString().take(8)}")
        tempDir.mkdirs()
        try {
            report(onProgress, ImportProgress(0, 0, "正在读取相册文件"))
            val extracted = extract(uri, tempDir, onProgress) ?: return@withContext null
            val manifestText = extracted.manifest
            if (manifestText.isNullOrBlank()) return@withContext null
            val album = buildAlbum(manifestText, extracted.files, onProgress) ?: return@withContext null
            album
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private suspend fun buildAlbum(
        manifestText: String,
        files: MutableList<ExtractedFile>,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Album? {
        val root = runCatching { JSONObject(manifestText) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT_NAME) return null
        val albumObject = root.optJSONObject("album") ?: root
        val title = albumObject.optString("title").trim()
            .ifBlank { "导入的旅行" }
            .take(60)
        val array = albumObject.optJSONArray("photos") ?: JSONArray()
        if (array.length() == 0) return null

        val albumId = newAlbumId()
        val imported = mutableListOf<Photo>()
        val usedIds = mutableSetOf<String>()
        val usedFiles = mutableSetOf<String>()
        val total = minOf(array.length(), MAX_PHOTOS)

        for (index in 0 until total) {
            val item = array.optJSONObject(index) ?: continue
            val originalName = sanitizeName(item.optString("name"))
                ?: sanitizeName(item.optString("fileName"))
                ?: "photo_${index + 1}.jpg"
            val path = item.optString("path")
            val source = findSource(files, path, originalName, usedFiles) ?: continue
            usedFiles += source.name
            val photoId = uniquePhotoId(
                candidate = sanitizePhotoId(item.optString("id")),
                fallback = importer.newPhotoId(index),
                used = usedIds,
            )
            val takenAt = item.optLong("takenAt", 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis()
            val latitude = item.optionalDouble("latitude")?.takeIf { it in -90.0..90.0 }
            val longitude = item.optionalDouble("longitude")
            val photo = importer.importArchivePhoto(
                source = source.file,
                albumId = albumId,
                photoId = photoId,
                fileName = originalName,
                takenAt = takenAt,
                latitude = latitude,
                longitude = longitude,
                placeName = item.optionalString("placeName"),
                note = item.optionalString("note"),
            ) ?: continue
            imported += photo
            report(
                onProgress,
                ImportProgress(imported.size, total, "正在还原第 ${imported.size} 张照片"),
            )
        }

        if (imported.isEmpty()) {
            storage.deleteAlbum(albumId)
            return null
        }
        val sorted = imported.sortedBy { it.takenAt }
        val requestedCover = itemIdAt(albumObject, "coverPhotoId")
        val cover = sorted.firstOrNull { it.id == requestedCover }?.id ?: sorted.first().id
        val notesArray = albumObject.optJSONArray("placeNotes") ?: JSONArray()
        val placeNotes = buildList {
            for (index in 0 until notesArray.length()) {
                val noteObj = notesArray.optJSONObject(index) ?: continue
                val latitude = noteObj.optionalDouble("latitude")?.takeIf { it in -90.0..90.0 }
                    ?: continue
                val longitude = noteObj.optionalDouble("longitude") ?: continue
                add(
                    PlaceNote(
                        id = noteObj.optString("id", "pl_${index}_${System.currentTimeMillis().toString(36)}"),
                        latitude = latitude,
                        longitude = longitude,
                        name = noteObj.optString("name", "旅途中的一站"),
                        note = noteObj.optionalString("note"),
                        createdAt = noteObj.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
        val now = System.currentTimeMillis()
        return Album(
            id = albumId,
            title = title,
            createdAt = albumObject.optLong("createdAt", now).takeIf { it > 0L } ?: now,
            updatedAt = now,
            coverPhotoId = cover,
            photos = sorted,
            placeNotes = placeNotes,
        )
    }

    private suspend fun extract(
        uri: Uri,
        tempDir: File,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Extracted? {
        val files = mutableListOf<ExtractedFile>()
        var manifest: String? = null
        var totalBytes = 0L
        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        BufferedInputStream(input).use { buffered ->
            ZipInputStream(buffered).use { zip ->
                var entry = zip.nextEntry
                var index = 0
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    val safe = !name.startsWith("/") && !name.split('/').contains("..")
                    if (!entry.isDirectory && safe) {
                        index += 1
                        if (index > MAX_ENTRIES) throw IOException("Too many archive entries")
                        if (isManifest(name)) {
                            manifest = readTextLimited(zip, MAX_MANIFEST_BYTES)
                        } else {
                            val displayName = File(name).name.ifBlank { "entry_$index" }
                            val target = File(
                                tempDir,
                                "${index}_${displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)}",
                            )
                            val written = copyLimited(zip, target, MAX_ENTRY_BYTES)
                            totalBytes += written
                            if (totalBytes > MAX_TOTAL_BYTES) throw IOException("Archive too large")
                            files += ExtractedFile(name, displayName, target)
                            if (index % 2 == 0) {
                                report(
                                    onProgress,
                                    ImportProgress(0, 0, "正在解包 · 已读取 $index 个文件"),
                                )
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return Extracted(manifest, files)
    }

    private fun buildManifest(album: Album, photos: List<Photo>): JSONObject {
        val photoArray = JSONArray()
        photos.forEach { photo ->
            photoArray.put(
                JSONObject().apply {
                    put("id", photo.id)
                    put("name", photo.fileName)
                    put("path", "photos/${photo.fileName}")
                    put("takenAt", photo.takenAt)
                    put("latitude", photo.latitude ?: JSONObject.NULL)
                    put("longitude", photo.longitude ?: JSONObject.NULL)
                    put("width", photo.width)
                    put("height", photo.height)
                    put("placeName", photo.placeName ?: JSONObject.NULL)
                    put("note", photo.note ?: JSONObject.NULL)
                },
            )
        }
        val notesArray = JSONArray()
        album.placeNotes.forEach { note ->
            notesArray.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("latitude", note.latitude)
                    put("longitude", note.longitude)
                    put("name", note.name)
                    put("note", note.note ?: JSONObject.NULL)
                    put("createdAt", note.createdAt)
                },
            )
        }
        val albumObject = JSONObject().apply {
            put("title", album.title)
            put("createdAt", album.createdAt)
            put("updatedAt", album.updatedAt)
            put("coverPhotoId", album.coverPhotoId ?: JSONObject.NULL)
            put("photos", photoArray)
            put("placeNotes", notesArray)
        }
        return JSONObject().apply {
            put("format", FORMAT_NAME)
            put("version", FORMAT_VERSION)
            put("generator", "TravelPhoto")
            put("album", albumObject)
        }
    }

    private fun findSource(
        files: List<ExtractedFile>,
        path: String,
        name: String,
        used: Set<String>,
    ): ExtractedFile? {
        val unused = files.filterNot { it.name in used }
        return unused.firstOrNull { it.name == path }
            ?: unused.firstOrNull { it.entryName == path }
            ?: unused.firstOrNull { it.entryName.endsWith("/$path") && path.isNotBlank() }
            ?: unused.firstOrNull { it.name == name }
            ?: unused.firstOrNull { it.entryName.endsWith("/$name") }
    }

    private fun sanitizeName(raw: String): String? {
        val name = File(raw.replace('\\', '/')).name
            .replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5 ]"), "_")
            .trim()
            .take(120)
        if (name.isEmpty() || name == "." || name == "..") return null
        return if (name.substringAfterLast('.', "").isBlank()) "$name.jpg" else name
    }

    private fun sanitizePhotoId(raw: String): String? {
        val clean = raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "").take(64)
        return clean.ifBlank { null }
    }

    private fun uniquePhotoId(candidate: String?, fallback: String, used: MutableSet<String>): String {
        var id = candidate ?: fallback
        if (!used.add(id)) {
            var suffix = 2
            while (!used.add("${id}_$suffix")) suffix += 1
            id = "${id}_$suffix"
        }
        return id
    }

    private fun itemIdAt(albumObject: JSONObject, key: String): String? {
        if (!albumObject.has(key) || albumObject.isNull(key)) return null
        val raw = albumObject.optString(key).trim()
        return raw.ifBlank { null }
    }

    private fun newAlbumId(): String =
        "a_arch_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(5)}"

    private fun isManifest(name: String): Boolean =
        name.equals(MANIFEST_NAME, ignoreCase = true) || name.endsWith("/$MANIFEST_NAME", ignoreCase = true)

    private fun readTextLimited(zip: ZipInputStream, limit: Int): String {
        val buffer = ByteArray(8 * 1024)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("Manifest too large")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun copyLimited(zip: ZipInputStream, target: File, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        target.outputStream().buffered().use { output ->
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IOException("Entry too large")
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private suspend fun report(
        callback: suspend (ImportProgress) -> Unit,
        progress: ImportProgress,
    ) {
        withContext(Dispatchers.Main.immediate) { callback(progress) }
    }

    private fun JSONObject.optionalDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        if (!value.isFinite()) return null
        if (value < -180.0 || value > 180.0) return null
        return value
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private data class ExtractedFile(
        val entryName: String,
        val name: String,
        val file: File,
    )

    private data class Extracted(
        val manifest: String?,
        val files: MutableList<ExtractedFile>,
    )

    private companion object {
        const val FORMAT_NAME = "travelphoto"
        const val FORMAT_VERSION = 1
        const val MANIFEST_NAME = "manifest.json"
        const val MAX_ENTRIES = 1_500
        const val MAX_PHOTOS = 500
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        const val MAX_ENTRY_BYTES = 300L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    }
}
