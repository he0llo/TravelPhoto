package he.travelphoto.llo.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Small, observable preference store. Theme colours keep their own store because they are
 * applied above the whole composition; everything else lives here.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("travelphoto_settings", Context.MODE_PRIVATE)

    var mapProvider by mutableStateOf(
        MapProvider.fromId(prefs.getString(KEY_MAP_PROVIDER, null)),
    )
        private set

    var captionShowsTime by mutableStateOf(prefs.getBoolean(KEY_CAPTION_TIME, true))
        private set

    var defaultExportWord by mutableStateOf(prefs.getBoolean(KEY_WORD, true))
        private set

    var defaultExportPdf by mutableStateOf(prefs.getBoolean(KEY_PDF, true))
        private set

    var defaultExportArchive by mutableStateOf(prefs.getBoolean(KEY_ARCHIVE, true))
        private set

    var exportTreeUri: String? by mutableStateOf(prefs.getString(KEY_EXPORT_TREE, null))
        private set

    var exportFolderLabel: String by mutableStateOf(
        prefs.getString(KEY_EXPORT_LABEL, null) ?: "TravelPhoto / Download",
    )
        private set

    var storageNoticeSeen by mutableStateOf(prefs.getBoolean(KEY_STORAGE_NOTICE, false))
        private set

    val usesDefaultExportFolder: Boolean
        get() = exportTreeUri.isNullOrBlank()

    fun changeMapProvider(provider: MapProvider) {
        mapProvider = provider
        prefs.edit().putString(KEY_MAP_PROVIDER, provider.id).apply()
    }

    fun changeCaptionShowsTime(value: Boolean) {
        captionShowsTime = value
        prefs.edit().putBoolean(KEY_CAPTION_TIME, value).apply()
    }

    fun setDefaultFormats(word: Boolean, pdf: Boolean, archive: Boolean) {
        defaultExportWord = word
        defaultExportPdf = pdf
        defaultExportArchive = archive
        prefs.edit()
            .putBoolean(KEY_WORD, word)
            .putBoolean(KEY_PDF, pdf)
            .putBoolean(KEY_ARCHIVE, archive)
            .apply()
    }

    fun setExportFolder(uri: Uri?, label: String?) {
        exportTreeUri = uri?.toString()
        exportFolderLabel = label?.takeIf { it.isNotBlank() } ?: "TravelPhoto / Download"
        prefs.edit()
            .putString(KEY_EXPORT_TREE, exportTreeUri)
            .putString(KEY_EXPORT_LABEL, exportFolderLabel)
            .apply()
    }

    fun resetExportFolder() {
        exportTreeUri = null
        exportFolderLabel = "TravelPhoto / Download"
        prefs.edit().remove(KEY_EXPORT_TREE).remove(KEY_EXPORT_LABEL).apply()
    }

    fun markStorageNoticeSeen() {
        storageNoticeSeen = true
        prefs.edit().putBoolean(KEY_STORAGE_NOTICE, true).apply()
    }

    private companion object {
        const val KEY_MAP_PROVIDER = "map_provider"
        const val KEY_CAPTION_TIME = "caption_shows_time"
        const val KEY_WORD = "default_export_word"
        const val KEY_PDF = "default_export_pdf"
        const val KEY_ARCHIVE = "default_export_archive"
        const val KEY_EXPORT_TREE = "export_tree_uri"
        const val KEY_EXPORT_LABEL = "export_folder_label"
        const val KEY_STORAGE_NOTICE = "storage_notice_seen"
    }
}

@Composable
fun rememberAppSettings(context: Context): AppSettings =
    remember(context.applicationContext) { AppSettings(context.applicationContext) }
