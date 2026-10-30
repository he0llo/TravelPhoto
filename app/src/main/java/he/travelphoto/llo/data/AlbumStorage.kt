package he.travelphoto.llo.data

import android.content.Context
import java.io.File

class AlbumStorage(context: Context) {
    private val appContext = context.applicationContext

    /** All albums live in <SD card>/TravelPhoto/Albums. */
    val root: File = AppStorage.albumsDir(appContext)

    val indexFile: File = AppStorage.indexFile(appContext)
    val indexTempFile: File = File(indexFile.parentFile, "${indexFile.name}.tmp")
    val exportDir: File = AppStorage.exportDir(appContext)

    init {
        root.mkdirs()
        AppStorage.migrateLegacyData(appContext)
    }

    fun albumDir(albumId: String): File = File(root, albumId).apply { mkdirs() }

    fun photosDir(albumId: String): File = File(albumDir(albumId), "photos").apply { mkdirs() }

    fun thumbsDir(albumId: String): File = File(albumDir(albumId), "thumbs").apply { mkdirs() }

    fun photoFile(albumId: String, photo: Photo): File =
        File(photosDir(albumId), photo.fileName)

    fun thumbFile(albumId: String, photo: Photo): File =
        File(thumbsDir(albumId), photo.thumbName)

    fun deleteAlbum(albumId: String) {
        runCatching { File(root, albumId).deleteRecursively() }
    }

    fun totalBytes(): Long = runCatching {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}
