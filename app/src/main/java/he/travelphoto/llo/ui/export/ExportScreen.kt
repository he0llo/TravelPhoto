package he.travelphoto.llo.ui.export

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.documentfile.provider.DocumentFile
import he.travelphoto.llo.data.Album
import he.travelphoto.llo.data.AlbumExporter
import he.travelphoto.llo.data.AlbumRepository
import he.travelphoto.llo.data.AppSettings
import he.travelphoto.llo.data.AppStorage
import he.travelphoto.llo.data.ExportFormat
import he.travelphoto.llo.data.ExportHistory
import he.travelphoto.llo.data.ExportProgress
import he.travelphoto.llo.data.ExportSummary
import he.travelphoto.llo.data.ExportedFile
import he.travelphoto.llo.data.ImportProgress
import he.travelphoto.llo.data.exists
import he.travelphoto.llo.data.readableUri
import he.travelphoto.llo.ui.components.AsyncPhoto
import he.travelphoto.llo.ui.components.BusyDialog
import he.travelphoto.llo.ui.components.EmptyState
import he.travelphoto.llo.ui.components.PageHeader
import he.travelphoto.llo.ui.components.SectionHeader
import he.travelphoto.llo.ui.rememberFolderPicker
import he.travelphoto.llo.util.Formats
import kotlinx.coroutines.launch

@Composable
internal fun ExportScreen(
    repository: AlbumRepository,
    settings: AppSettings,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val albums by repository.albums.collectAsState()
    val scope = rememberCoroutineScope()
    val exporter = remember(repository) { AlbumExporter(context.applicationContext, repository) }
    val exportHistory = remember(context) { ExportHistory(context) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf(setOf(ExportFormat.PDF)) }
    var pdfPhotosPerPage by remember { mutableIntStateOf(4) }
    var exports by remember { mutableStateOf(exportHistory.list()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(ImportProgress(0, 0, "")) }
    var summary by remember { mutableStateOf<ExportSummary?>(null) }

    LaunchedEffect(albums.map { it.id }) {
        val available = albums.map { it.id }.toSet()
        if (!initialized && albums.isNotEmpty()) {
            // The export screen starts with PDF and the most recently edited album.
            selectedIds = setOf(albums.first().id)
            initialized = true
        } else if (initialized) {
            selectedIds = selectedIds.intersect(available)
        }
    }

    val selectedAlbums = albums.filter { it.id in selectedIds }
    val fileCount = selectedAlbums.size * formats.size

    val folderPicker = rememberFolderPicker { uri ->
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        settings.setExportFolder(uri, name ?: "自定义文件夹")
    }

    val startExport: () -> Unit = {
        val chosen = albums.filter { it.id in selectedIds }
        if (chosen.isNotEmpty() && formats.isNotEmpty()) {
            scope.launch {
                busy = true
                progress = ImportProgress(0, chosen.size * formats.size, "正在准备导出")
                summary = try {
                    val customTree = settings.exportTreeUri?.let(Uri::parse)
                    if (customTree != null) {
                        exporter.export(
                            albums = chosen,
                            formats = formats.toList(),
                            treeUri = customTree,
                            accent = accent,
                            pdfPhotosPerPage = pdfPhotosPerPage,
                        ) { update ->
                            progress = ImportProgress(update.done, update.total, update.label)
                        }
                    } else {
                        exporter.exportToFolder(
                            albums = chosen,
                            formats = formats.toList(),
                            folder = AppStorage.exportDir(context),
                            accent = accent,
                            pdfPhotosPerPage = pdfPhotosPerPage,
                        ) { update ->
                            progress = ImportProgress(update.done, update.total, update.label)
                        }
                    }
                } catch (throwable: Throwable) {
                    ExportSummary(
                        succeeded = 0,
                        failed = chosen.size * formats.size,
                        folder = null,
                        message = throwable.message ?: "导出过程中出现未知错误",
                    )
                } finally {
                    busy = false
                    exports = exportHistory.list()
                }
            }
        }
    }

    val openExport: (ExportedFile) -> Unit = { item ->
        val uri = item.readableUri(context)
        if (uri == null) {
            Toast.makeText(context, "文件已不存在，请重新导出", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show() }
        }
    }

    val shareExport: (ExportedFile) -> Unit = { item ->
        val uri = item.readableUri(context)
        if (uri == null) {
            Toast.makeText(context, "文件已不存在，请重新导出", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, item.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "分享 ${item.name}"))
            }.onFailure {
                Toast.makeText(context, "没有可分享该文件的应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("header") {
            PageHeader(
                eyebrow = "导出与分享",
                title = "把旅程带出去",
                subtitle = "批量导出为 Word、PDF，或可再次导入的旅行相册文件。",
            )
        }

        if (albums.isEmpty()) {
            item("empty") {
                EmptyState(
                    icon = Icons.Rounded.Luggage,
                    title = "还没有可导出的相册",
                    message = "先创建一个旅行相册，整理好照片与足迹，再回来生成属于你的旅行书。",
                    action = { Button(onClick = onGoHome) { Text("去创建相册") } },
                )
            }
        } else {
            item("formats-title") {
                SectionHeader(
                    title = "导出格式",
                    subtitle = "同一本相册可以同时生成多种格式",
                )
            }
            item("formats") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportFormat.entries.forEach { format ->
                        FormatRow(
                            format = format,
                            selected = format in formats,
                            onToggle = {
                                formats = if (format in formats) {
                                    formats - format
                                } else {
                                    formats + format
                                }
                            },
                        )
                    }
                }
            }

            if (ExportFormat.PDF in formats) {
                item("pdf-layout") {
                    Column {
                        SectionHeader(
                            title = "PDF 排版",
                            subtitle = "每页照片数；图片按原比例完整显示，不裁切、不旋转",
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (1..8).forEach { count ->
                                FilterChip(
                                    selected = pdfPhotosPerPage == count,
                                    onClick = { pdfPhotosPerPage = count },
                                    label = { Text("$count 张/页") },
                                )
                            }
                        }
                    }
                }
            }

            item("albums-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "选择相册",
                        subtitle = "已选择 ${selectedAlbums.size} / ${albums.size} 个",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == albums.size) {
                                emptySet()
                            } else {
                                albums.map { it.id }.toSet()
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (selectedIds.size == albums.size) "取消全选" else "全选")
                    }
                }
            }

            items(albums, key = { it.id }) { album ->
                ExportAlbumRow(
                    album = album,
                    repository = repository,
                    checked = album.id in selectedIds,
                    onToggle = {
                        selectedIds = if (album.id in selectedIds) {
                            selectedIds - album.id
                        } else {
                            selectedIds + album.id
                        }
                    },
                )
            }

            item("destination-title") {
                SectionHeader(
                    title = "保存位置",
                    subtitle = "默认保存到 SD 卡 TravelPhoto/Download",
                )
            }

            item("destination") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.exportFolderLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (settings.usesDefaultExportFolder) {
                                        AppStorage.exportDir(context).absolutePath
                                    } else {
                                        "自定义文件夹 · 可在系统选择器中重新选择"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = folderPicker) { Text("更改") }
                        }
                        if (!settings.usesDefaultExportFolder) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { settings.resetExportFolder() }) {
                                    Icon(
                                        Icons.Rounded.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("恢复默认位置")
                                }
                            }
                        }
                    }
                }
            }

            item("action") {
                Column {
                    Text(
                        text = "将生成 $fileCount 个文件，保存到 ${settings.exportFolderLabel}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = startExport,
                        enabled = selectedAlbums.isNotEmpty() && formats.isNotEmpty() && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("开始导出", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.IosShare,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "文件会保存到你选择的位置，不会上传网络",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item("exports-title") {
                SectionHeader(
                    title = "我的导出",
                    subtitle = "最近生成的文件，可以在这里直接打开或分享",
                )
            }

            if (exports.isEmpty()) {
                item("exports-empty") {
                    Text(
                        text = "还没有导出记录。生成的文件会自动出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(exports.take(12), key = { it.id }) { file ->
                    ExportedFileRow(
                        file = file,
                        canOpen = file.exists(),
                        onOpen = { openExport(file) },
                        onShare = { shareExport(file) },
                    )
                }
            }
        }
    }

    if (busy) {
        BusyDialog(title = "正在生成旅行书", progress = progress)
    }

    summary?.let { result ->
        ExportResultDialog(result = result, onDismiss = { summary = null })
    }
}

@Composable
private fun FormatRow(
    format: ExportFormat,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(formatIcon(format), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = format.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = format.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            ) {
                Text(
                    text = ".${format.extension}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

@Composable
private fun ExportAlbumRow(
    album: Album,
    repository: AlbumRepository,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = album.coverPhoto
            AsyncPhoto(
                file = cover?.let { repository.displayFile(album.id, it) },
                contentDescription = null,
                modifier = Modifier.size(66.dp),
                shape = RoundedCornerShape(17.dp),
                maxSize = 280,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Formats.dateRange(album.startAt, album.endAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Formats.albumMeta(album),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun ExportedFileRow(
    file: ExportedFile,
    canOpen: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (file.format) {
                            ExportFormat.PDF.name -> Icons.Rounded.PictureAsPdf
                            ExportFormat.WORD.name -> Icons.Rounded.Description
                            else -> Icons.Rounded.FolderZip
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(file.albumTitle)
                        append(" · ")
                        append(Formats.dateTime(file.createdAt))
                        if (file.size > 0) {
                            append(" · ")
                            append(Formats.fileSize(file.size))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpen, enabled = canOpen) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = "打开")
            }
            IconButton(onClick = onShare, enabled = canOpen) {
                Icon(Icons.Rounded.Share, contentDescription = "分享")
            }
        }
    }
}

@Composable
private fun ExportResultDialog(result: ExportSummary, onDismiss: () -> Unit) {
    val success = result.failed == 0 && result.succeeded > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (success) "导出完成" else "导出结果") },
        text = {
            Column {
                Text(
                    text = "成功 ${result.succeeded} 个 · 失败 ${result.failed} 个",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (result.folder != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "保存位置：${result.folder}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                result.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (success) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private fun formatIcon(format: ExportFormat): ImageVector = when (format) {
    ExportFormat.WORD -> Icons.Rounded.Description
    ExportFormat.PDF -> Icons.Rounded.PictureAsPdf
    ExportFormat.ARCHIVE -> Icons.Rounded.FolderZip
}
