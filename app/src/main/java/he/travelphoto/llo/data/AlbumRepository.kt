package he.travelphoto.llo.data

import android.content.Context
import android.net.Uri
import he.travelphoto.llo.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

class AlbumRepository(context: Context) {
    private val appContext = context.applicationContext
    val storage = AlbumStorage(appContext)
    val importer = PhotoImporter(appContext, storage)
    private val archive = TravelPhotoArchive(appContext, storage, importer)

    private val indexFile = storage.indexFile
    private val mutex = Mutex()
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    init {
        val raw = runCatching {
            indexFile.takeIf { it.isFile }?.readText()
        }.getOrNull()
        if (!raw.isNullOrBlank()) {
            _albums.value = runCatching { AlbumJson.decode(raw) }
                .getOrDefault(emptyList())
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun createAlbum(
        uris: List<Uri>,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Album? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        val albumId = newAlbumId()
        val photos = mutableListOf<Photo>()
        try {
            uris.forEachIndexed { index, uri ->
                val photo = importer.importUri(uri, albumId, index)
                if (photo != null) photos += photo
                reportProgress(
                    onProgress,
                    ImportProgress(
                        done = index + 1,
                        total = uris.size,
                        label = if (photo != null) "正在整理第 ${index + 1} 张照片" else "已跳过无法读取的照片",
                    ),
                )
            }
            if (photos.isEmpty()) {
                storage.deleteAlbum(albumId)
                return@withContext null
            }
            val sorted = photos.sortedBy { it.takenAt }
            val now = System.currentTimeMillis()
            val album = Album(
                id = albumId,
                title = uniqueTitle(defaultTitle(sorted)),
                createdAt = now,
                updatedAt = now,
                coverPhotoId = sorted.first().id,
                photos = sorted,
            )
            mutate { listOf(album) + it }
            album
        } catch (throwable: Throwable) {
            storage.deleteAlbum(albumId)
            throw throwable
        }
    }

    suspend fun importPhotos(
        albumId: String,
        uris: List<Uri>,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val album = _albums.value.firstOrNull { it.id == albumId } ?: return@withContext 0
        if (uris.isEmpty()) return@withContext 0
        val added = mutableListOf<Photo>()
        uris.forEachIndexed { index, uri ->
            val photo = importer.importUri(uri, albumId, album.photos.size + index)
            if (photo != null) added += photo
            reportProgress(
                onProgress,
                ImportProgress(
                    done = index + 1,
                    total = uris.size,
                    label = if (photo != null) "正在导入第 ${index + 1} 张照片" else "已跳过无法读取的照片",
                ),
            )
        }
        if (added.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        mutate { list ->
            list.map { item ->
                if (item.id != albumId) return@map item
                val merged = (item.photos + added).distinctBy { it.id }.sortedBy { it.takenAt }
                item.copy(
                    photos = merged,
                    coverPhotoId = item.coverPhotoId ?: added.first().id,
                    updatedAt = now,
                )
            }
        }
        added.size
    }

    suspend fun importArchive(
        uri: Uri,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Album? = withContext(Dispatchers.IO) {
        val imported = archive.import(uri, onProgress) ?: return@withContext null
        val album = imported.copy(title = uniqueTitle(imported.title.ifBlank { "导入的旅行" }))
        mutate { listOf(album) + it }
        album
    }

    suspend fun renameAlbum(albumId: String, title: String) {
        val clean = title.trim().take(60)
        if (clean.isEmpty()) return
        updateAlbum(albumId) { it.copy(title = clean) }
    }

    suspend fun setCover(albumId: String, photoId: String) {
        updateAlbum(albumId) { it.copy(coverPhotoId = photoId) }
    }

    suspend fun updatePhotoTime(albumId: String, photoId: String, takenAt: Long) {
        updatePhoto(albumId, photoId) { it.copy(takenAt = takenAt) }
    }

    suspend fun setPhotoLocation(
        albumId: String,
        photoId: String,
        latitude: Double,
        longitude: Double,
        placeName: String? = null,
    ) {
        val lat = Geo.coerceLatitude(latitude)
        val lon = Geo.normalizeLongitude(longitude)
        updatePhoto(albumId, photoId) {
            it.copy(
                latitude = lat,
                longitude = lon,
                placeName = placeName?.trim()?.takeIf { name -> name.isNotEmpty() }
                    ?: he.travelphoto.llo.util.PlaceDirectory.nameFor(lat, lon),
            )
        }
    }

    suspend fun clearPhotoLocation(albumId: String, photoId: String) {
        updatePhoto(albumId, photoId) {
            it.copy(latitude = null, longitude = null, placeName = null)
        }
    }

    suspend fun updatePhotoNote(albumId: String, photoId: String, note: String?) {
        val clean = note?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
        updatePhoto(albumId, photoId) { it.copy(note = clean) }
    }

    /**
     * Saves the note for the place nearest to [latitude]/[longitude]. An empty note removes
     * the custom description so the generated "when + where" default is used again.
     */
    suspend fun savePlaceNote(
        albumId: String,
        latitude: Double,
        longitude: Double,
        name: String,
        note: String?,
    ) {
        val clean = note?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
        updateAlbum(albumId) { album ->
            val nearest = album.placeNoteFor(latitude, longitude)
            val notes = if (clean == null) {
                album.placeNotes.filterNot { it.id == nearest?.id }
            } else if (nearest != null) {
                album.placeNotes.map {
                    if (it.id == nearest.id) it.copy(name = name, note = clean) else it
                }
            } else {
                album.placeNotes + PlaceNote(
                    id = "pl_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(4)}",
                    latitude = Geo.coerceLatitude(latitude),
                    longitude = Geo.normalizeLongitude(longitude),
                    name = name,
                    note = clean,
                )
            }
            album.copy(placeNotes = notes)
        }
    }

    suspend fun deletePhoto(albumId: String, photoId: String) {
        val removed = mutableListOf<File>()
        mutate { list ->
            list.map { album ->
                if (album.id != albumId) return@map album
                val photo = album.photos.firstOrNull { it.id == photoId } ?: return@map album
                removed += storage.photoFile(albumId, photo)
                removed += storage.thumbFile(albumId, photo)
                val remaining = album.photos.filterNot { it.id == photoId }
                album.copy(
                    photos = remaining,
                    coverPhotoId = when {
                        album.coverPhotoId != photoId -> album.coverPhotoId
                        else -> remaining.firstOrNull()?.id
                    },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        removed.forEach { runCatching { it.delete() } }
    }

    suspend fun deleteAlbum(albumId: String) {
        mutate { list -> list.filterNot { it.id == albumId } }
        storage.deleteAlbum(albumId)
    }

    fun photoFile(albumId: String, photo: Photo): File = storage.photoFile(albumId, photo)

    fun thumbFile(albumId: String, photo: Photo): File = storage.thumbFile(albumId, photo)

    fun displayFile(albumId: String, photo: Photo): File {
        val thumb = storage.thumbFile(albumId, photo)
        return if (thumb.isFile) thumb else storage.photoFile(albumId, photo)
    }

    private suspend fun updateAlbum(albumId: String, transform: (Album) -> Album) {
        mutate { list ->
            list.map { album ->
                if (album.id == albumId) {
                    transform(album).copy(updatedAt = System.currentTimeMillis())
                } else {
                    album
                }
            }
        }
    }

    private suspend fun updatePhoto(albumId: String, photoId: String, transform: (Photo) -> Photo) {
        mutate { list ->
            list.map { album ->
                if (album.id != albumId) return@map album
                val photos = album.photos
                    .map { if (it.id == photoId) transform(it) else it }
                    .sortedBy { it.takenAt }
                album.copy(photos = photos, updatedAt = System.currentTimeMillis())
            }
        }
    }

    private suspend fun mutate(transform: (List<Album>) -> List<Album>) {
        mutex.withLock {
            val updated = transform(_albums.value).sortedByDescending { it.updatedAt }
            _albums.value = updated
            withContext(Dispatchers.IO) { persist(updated) }
        }
    }

    private fun persist(albums: List<Album>) {
        runCatching {
            val temp = storage.indexTempFile
            temp.parentFile?.mkdirs()
            temp.writeText(AlbumJson.encode(albums))
            if (indexFile.exists() && !indexFile.delete()) {
                temp.copyTo(indexFile, overwrite = true)
                temp.delete()
            } else if (!temp.renameTo(indexFile)) {
                temp.copyTo(indexFile, overwrite = true)
                temp.delete()
            }
        }
    }

    private suspend fun reportProgress(
        callback: suspend (ImportProgress) -> Unit,
        progress: ImportProgress,
    ) {
        withContext(Dispatchers.Main.immediate) { callback(progress) }
    }

    private fun newAlbumId(): String =
        "a_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(6)}"

    private fun uniqueTitle(base: String): String {
        val titles = _albums.value.map { it.title }.toSet()
        if (base !in titles) return base
        var index = 2
        while ("$base（$index）" in titles) index += 1
        return "$base（$index）"
    }

    private fun defaultTitle(photos: List<Photo>): String {
        val first = photos.firstOrNull() ?: return "新的旅程"
        val start = Calendar.getInstance().apply { timeInMillis = first.takenAt }
        val end = Calendar.getInstance().apply {
            timeInMillis = photos.lastOrNull()?.takenAt ?: first.takenAt
        }
        val startYear = start.get(Calendar.YEAR)
        val endYear = end.get(Calendar.YEAR)
        return when {
            startYear != endYear -> "$startYear – $endYear · 旅行"
            start.get(Calendar.MONTH) != end.get(Calendar.MONTH) ->
                "$startYear 年 ${start.get(Calendar.MONTH) + 1}–${end.get(Calendar.MONTH) + 1} 月 · 旅行"
            else -> "$startYear 年 ${start.get(Calendar.MONTH) + 1} 月 · 旅行"
        }
    }
}
