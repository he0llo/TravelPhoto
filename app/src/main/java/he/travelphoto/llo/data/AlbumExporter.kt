package he.travelphoto.llo.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

enum class ExportFormat(
    val label: String,
    val extension: String,
    val mime: String,
    val hint: String,
) {
    WORD(
        label = "Word 文档",
        extension = "docx",
        mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        hint = "适合打印与继续编辑",
    ),
    PDF(
        label = "PDF 文档",
        extension = "pdf",
        mime = "application/pdf",
        hint = "排版固定的分享版本",
    ),
    ARCHIVE(
        label = "旅行相册",
        extension = "travelphoto",
        mime = "application/vnd.travelphoto+zip",
        hint = "可重新导入本应用",
    ),
}

data class ExportProgress(val done: Int, val total: Int, val label: String)

data class ExportSummary(
    val succeeded: Int,
    val failed: Int,
    val folder: String?,
    val message: String? = null,
    val files: List<ExportedFile> = emptyList(),
)

/**
 * Writes selected albums into a user-picked SAF folder, one file per album/format.
 */
class AlbumExporter(
    context: Context,
    private val repository: AlbumRepository,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val documentExporter = DocumentExporter(appContext, repository.storage)
    private val pdfExporter = PdfExporter(repository.storage)
    private val archive = TravelPhotoArchive(appContext, repository.storage, repository.importer)
    private val history = ExportHistory(appContext)

    suspend fun export(
        albums: List<Album>,
        formats: List<ExportFormat>,
        treeUri: Uri,
        accent: Int,
        pdfPhotosPerPage: Int = 4,
        onProgress: suspend (ExportProgress) -> Unit,
    ): ExportSummary = withContext(Dispatchers.IO) {
        if (albums.isEmpty() || formats.isEmpty()) {
            return@withContext ExportSummary(0, 0, null, "请先选择相册与导出格式")
        }
        val tree = runCatching { DocumentFile.fromTreeUri(appContext, treeUri) }.getOrNull()
        if (tree == null || !tree.exists() || !tree.canWrite()) {
            return@withContext ExportSummary(0, 0, null, "无法写入所选文件夹，请重新选择")
        }

        val total = albums.size * formats.size
        var done = 0
        var succeeded = 0
        var failed = 0
        val failures = mutableListOf<String>()
        val produced = mutableListOf<ExportedFile>()

        albums.forEach { album ->
            formats.forEach formatLoop@{ format ->
                done += 1
                report(onProgress, ExportProgress(done, total, "正在导出《${album.title}》· ${format.label}"))
                val base = safeBaseName(album.title)
                val fileName = uniqueName(tree, base, format.extension)
                val target = runCatching { tree.createFile(format.mime, fileName) }.getOrNull()
                if (target == null) {
                    failed += 1
                    failures += "${album.title} · ${format.label}"
                    return@formatLoop
                }
                // A custom MIME type keeps the provider from appending a second .zip suffix to
                // the .travelphoto file. The payload is still a plain ZIP archive.
                val ok = try {
                    resolver.openOutputStream(target.uri, "wt")?.use { output ->
                        writeAlbum(album, format, accent, output, pdfPhotosPerPage)
                    } ?: false
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    succeeded += 1
                    val exported = ExportedFile(
                        name = fileName,
                        albumTitle = album.title,
                        format = format.name,
                        mime = format.mime,
                        path = null,
                        contentUri = target.uri.toString(),
                        size = target.length(),
                    )
                    produced += exported
                    history.add(exported)
                } else {
                    failed += 1
                    failures += "${album.title} · ${format.label}"
                    runCatching { target.delete() }
                }
            }
        }

        val message = when {
            failed == 0 -> null
            succeeded == 0 -> "导出失败，请检查文件权限或存储空间"
            else -> "部分内容未能导出：${failures.take(3).joinToString("、")}"
        }
        ExportSummary(succeeded, failed, tree.name ?: "已选择的文件夹", message, produced)
    }

    /**
     * Direct file export used by the default TravelPhoto/Download folder. This does not need
     * SAF: the folder lives inside the app's shared-storage root (or its fallback).
     */
    suspend fun exportToFolder(
        albums: List<Album>,
        formats: List<ExportFormat>,
        folder: File,
        accent: Int,
        pdfPhotosPerPage: Int = 4,
        onProgress: suspend (ExportProgress) -> Unit,
    ): ExportSummary = withContext(Dispatchers.IO) {
        if (albums.isEmpty() || formats.isEmpty()) {
            return@withContext ExportSummary(0, 0, null, "请先选择相册与导出格式")
        }
        if (!folder.exists() && !folder.mkdirs()) {
            return@withContext ExportSummary(0, 0, folder.name, "无法创建导出文件夹，请检查存储权限")
        }
        val total = albums.size * formats.size
        var done = 0
        var succeeded = 0
        var failed = 0
        val failures = mutableListOf<String>()
        val produced = mutableListOf<ExportedFile>()

        albums.forEach { album ->
            formats.forEach formatLoop@{ format ->
                done += 1
                report(onProgress, ExportProgress(done, total, "正在导出《${album.title}》· ${format.label}"))
                val base = safeBaseName(album.title)
                val target = File(folder, uniqueName(folder, base, format.extension))
                val ok = runCatching {
                    FileOutputStream(target).use { output ->
                        writeAlbum(album, format, accent, output, pdfPhotosPerPage)
                    }
                }.getOrDefault(false)
                if (ok) {
                    succeeded += 1
                    val exported = ExportedFile(
                        name = target.name,
                        albumTitle = album.title,
                        format = format.name,
                        mime = format.mime,
                        path = target.absolutePath,
                        contentUri = null,
                        size = target.length(),
                    )
                    produced += exported
                    history.add(exported)
                } else {
                    failed += 1
                    failures += "${album.title} · ${format.label}"
                    runCatching { target.delete() }
                }
            }
        }

        val message = when {
            failed == 0 -> null
            succeeded == 0 -> "导出失败，请检查文件权限或存储空间"
            else -> "部分内容未能导出：${failures.take(3).joinToString("、")}"
        }
        ExportSummary(succeeded, failed, folder.name, message, produced)
    }

    private fun uniqueName(folder: File, base: String, extension: String): String {
        var candidate = "$base.$extension"
        var index = 2
        while (File(folder, candidate).exists()) {
            candidate = "$base（$index）.$extension"
            index += 1
            if (index > 99) break
        }
        return candidate
    }

    private suspend fun writeAlbum(
        album: Album,
        format: ExportFormat,
        accent: Int,
        output: OutputStream,
        pdfPhotosPerPage: Int,
    ): Boolean {
        return when (format) {
            ExportFormat.WORD -> documentExporter.write(album, accent, output)
            ExportFormat.PDF -> pdfExporter.write(album, accent, output, pdfPhotosPerPage)
            ExportFormat.ARCHIVE -> archive.export(album, output)
        }
    }

    private fun uniqueName(tree: DocumentFile, base: String, extension: String): String {
        var candidate = "$base.$extension"
        var index = 2
        while (tree.findFile(candidate) != null) {
            candidate = "$base（$index）.$extension"
            index += 1
            if (index > 99) break
        }
        return candidate
    }

    private fun safeBaseName(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '-')
            .take(48)
            .lowercase(Locale.US)
        return cleaned.ifBlank { "旅行相册" }
    }

    private suspend fun report(callback: suspend (ExportProgress) -> Unit, progress: ExportProgress) {
        withContext(Dispatchers.Main.immediate) { callback(progress) }
    }
}
