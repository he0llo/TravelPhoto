package he.travelphoto.llo.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Every album, photo, thumbnail and the album index live in one portable folder on the
 * shared storage:  <SD card>/TravelPhoto
 *
 * Android 11+ protects the root of shared storage with the "all files access" app-op. When
 * that permission is missing we keep working in the app-specific external folder and offer a
 * one-tap migration from Settings, so no photo is ever lost while the user grants access.
 */
object AppStorage {
    const val FOLDER_NAME = "TravelPhoto"
    const val ALBUMS_FOLDER = "Albums"
    const val EXPORT_FOLDER = "Download"
    const val INDEX_NAME = "albums.json"

    fun publicRoot(): File? = runCatching {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
    }.getOrNull()

    fun fallbackRoot(context: Context): File {
        val external = context.getExternalFilesDir(null) ?: context.filesDir
        return File(external, FOLDER_NAME)
    }

    fun hasSharedStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun activeRoot(context: Context): File {
        val public = publicRoot()
        if (public != null && hasSharedStorageAccess(context) && ensureWritable(public)) {
            return public
        }
        return fallbackRoot(context).apply { mkdirs() }
    }

    fun isUsingSharedStorage(context: Context): Boolean =
        publicRoot()?.absolutePath == activeRoot(context).absolutePath

    fun displayRoot(context: Context): String =
        activeRoot(context).absolutePath

    /** Human-friendly label for Settings. */
    fun locationLabel(context: Context): String = if (isUsingSharedStorage(context)) {
        "SD 卡 / TravelPhoto"
    } else {
        "应用专属目录 / TravelPhoto"
    }

    /** Opens the system "all files access" screen on Android 11+. */
    fun openSharedStorageSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                true
            }.getOrDefault(false)
        }
    }

    fun albumsDir(context: Context): File =
        File(activeRoot(context), ALBUMS_FOLDER).apply { mkdirs() }

    fun exportDir(context: Context): File =
        File(activeRoot(context), EXPORT_FOLDER).apply { mkdirs() }

    fun indexFile(context: Context): File =
        File(activeRoot(context), INDEX_NAME)

    /**
     * Copies old app-private albums and indexes into the shared folder. The legacy data is
     * kept as a safety net; the copy is skipped when the destination already has an index.
     */
    fun migrateLegacyData(context: Context) {
        runCatching {
            val destinationAlbums = albumsDir(context)
            val destinationIndex = indexFile(context)
            val sources = listOf(
                File(context.filesDir, "albums") to File(context.filesDir, "albums.json"),
                File(fallbackRoot(context), ALBUMS_FOLDER) to File(fallbackRoot(context), INDEX_NAME),
            )
            for ((oldAlbums, oldIndex) in sources) {
                if (oldAlbums.absolutePath == destinationAlbums.absolutePath) continue
                if (oldAlbums.isDirectory) {
                    oldAlbums.listFiles()?.forEach { child ->
                        val target = File(destinationAlbums, child.name)
                        if (!target.exists()) copyRecursively(child, target)
                    }
                }
                if (!destinationIndex.exists() && oldIndex.isFile) {
                    oldIndex.copyTo(destinationIndex, overwrite = true)
                }
            }
        }
    }

    private fun ensureWritable(root: File): Boolean = runCatching {
        if (!root.exists() && !root.mkdirs()) return false
        val probe = File(root, ".write_test")
        probe.writeText("ok")
        probe.delete()
        true
    }.getOrDefault(false)

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { copyRecursively(it, File(target, it.name)) }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }
}
