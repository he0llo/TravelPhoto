package he.travelphoto.llo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import he.travelphoto.llo.data.Album
import he.travelphoto.llo.data.AlbumRepository
import he.travelphoto.llo.ui.components.AsyncPhoto
import he.travelphoto.llo.ui.components.ConfirmDialog
import he.travelphoto.llo.ui.components.EmptyState
import he.travelphoto.llo.ui.components.RenameDialog
import he.travelphoto.llo.ui.components.SectionHeader
import he.travelphoto.llo.util.Formats
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    repository: AlbumRepository,
    onOpenAlbum: (String) -> Unit,
    onCreate: () -> Unit,
    onImportArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums by repository.albums.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Album?>(null) }
    var deleteTarget by remember { mutableStateOf<Album?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("brand") {
            BrandRow(onSearch = { searchOpen = true })
        }
        item("actions") {
            CreateActionButtons(
                onCreate = onCreate,
                onImport = onImportArchive,
            )
        }
        item("hero") {
            TravelHero()
        }
        item("list-header") {
            val photoCount = albums.sumOf { it.photos.size }
            SectionHeader(
                title = "我的旅行",
                subtitle = if (albums.isEmpty()) {
                    "每一次出发，都值得被好好收藏"
                } else {
                    "${albums.size} 个相册 · $photoCount 张照片"
                },
            )
        }
        if (albums.isEmpty()) {
            item("empty") {
                EmptyState(
                    icon = Icons.Rounded.Luggage,
                    title = "还没有旅行相册",
                    message = "从照片开始，TravelPhoto 会自动读出拍摄时间与地点，帮你把旅程排成地图与时间线。",
                    action = {
                        Button(onClick = onCreate) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("创建第一个相册")
                        }
                    },
                )
            }
        } else {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    repository = repository,
                    onOpen = { onOpenAlbum(album.id) },
                    onRename = { renameTarget = album },
                    onDelete = { deleteTarget = album },
                )
            }
        }
    }

    if (searchOpen) {
        SearchDialog(
            albums = albums,
            onDismiss = { searchOpen = false },
            onOpen = { id ->
                searchOpen = false
                onOpenAlbum(id)
            },
        )
    }

    renameTarget?.let { album ->
        RenameDialog(
            title = "重命名相册",
            initial = album.title,
            onConfirm = { title ->
                renameTarget = null
                if (title.isNotEmpty() && title != album.title) {
                    scope.launch { repository.renameAlbum(album.id, title) }
                }
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { album ->
        ConfirmDialog(
            title = "删除这个相册？",
            message = "「${album.title}」中的 ${album.photos.size} 张照片会一并从应用中移除，此操作无法撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch { repository.deleteAlbum(album.id) }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun BrandRow(onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FlightTakeoff, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "旅行相册",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "TravelPhoto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "搜索")
        }
    }
}

@Composable
private fun CreateActionButtons(
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val compact = with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp() < 380.dp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onCreate,
            modifier = Modifier
                .weight(1.55f)
                .fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Column {
                    Text(
                        text = "创建旅行相册",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "从照片开始整理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    )
                }
            }
        }

        Surface(
            onClick = onImport,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text(
                        text = if (compact) "导入相册" else "导入相册文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    if (!compact) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "打开 .travelphoto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelHero() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colors.tertiaryContainer, colors.surfaceContainerLow),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(5) { index ->
                val y = size.height * (0.22f + index * 0.13f)
                val path = Path().apply {
                    moveTo(-size.width * 0.08f, y)
                    cubicTo(
                        size.width * 0.28f,
                        y - size.height * 0.10f,
                        size.width * 0.62f,
                        y + size.height * 0.16f,
                        size.width * 1.08f,
                        y - size.height * 0.02f,
                    )
                }
                drawPath(
                    path = path,
                    color = colors.onTertiaryContainer.copy(alpha = 0.13f),
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
            drawCircle(
                color = colors.tertiary.copy(alpha = 0.20f),
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.80f, size.height * 0.18f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "下一站，\n山海之间",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "自动读取时间与足迹，\n让照片自己讲完这段旅程。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onTertiaryContainer.copy(alpha = 0.78f),
                )
            }
            Spacer(Modifier.width(10.dp))
            PhotoPostcard()
        }
    }
}

@Composable
private fun PhotoPostcard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .width(138.dp)
            .height(168.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(colors.surfaceVariant),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = colors.tertiary.copy(alpha = 0.55f),
                        radius = size.width * 0.14f,
                        center = Offset(size.width * 0.72f, size.height * 0.30f),
                    )
                    val back = Path().apply {
                        moveTo(0f, size.height * 0.74f)
                        lineTo(size.width * 0.32f, size.height * 0.36f)
                        lineTo(size.width * 0.55f, size.height * 0.66f)
                        lineTo(size.width * 0.74f, size.height * 0.46f)
                        lineTo(size.width, size.height * 0.78f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(back, colors.secondary.copy(alpha = 0.65f))
                    val front = Path().apply {
                        moveTo(0f, size.height * 0.92f)
                        quadraticBezierTo(
                            size.width * 0.45f,
                            size.height * 0.64f,
                            size.width,
                            size.height * 0.88f,
                        )
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(front, colors.primary.copy(alpha = 0.85f))
                }
                Icon(
                    Icons.Rounded.Landscape,
                    contentDescription = null,
                    tint = colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    text = "ON THE ROAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    maxLines = 1,
                )
                Text(
                    text = "把风景装进口袋",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    repository: AlbumRepository,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember(album.id) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                val cover = album.coverPhoto
                if (cover != null) {
                    AsyncPhoto(
                        file = repository.displayFile(album.id, cover),
                        contentDescription = album.title,
                        modifier = Modifier
                            .width(104.dp)
                            .height(124.dp),
                        shape = RoundedCornerShape(20.dp),
                        maxSize = 480,
                    )
                } else {
                    AlbumCoverPlaceholder(
                        title = album.title,
                        modifier = Modifier
                            .width(104.dp)
                            .height(124.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Formats.dateRange(album.startAt, album.endAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Formats.albumMeta(album),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (album.locatedPhotos.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = "${album.locatedPhotos.size} 个足迹",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (album.unlocatedPhotos.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = "${album.unlocatedPhotos.size} 张待定位",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除相册") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colors.secondaryContainer, colors.surfaceContainerHighest),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.35f, size.height * 0.42f)
                lineTo(size.width * 0.62f, size.height * 0.66f)
                lineTo(size.width * 0.82f, size.height * 0.50f)
                lineTo(size.width, size.height * 0.70f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, colors.onSecondaryContainer.copy(alpha = 0.18f))
            drawCircle(
                color = colors.primary.copy(alpha = 0.36f),
                radius = size.width * 0.16f,
                center = Offset(size.width * 0.72f, size.height * 0.28f),
            )
        }
        Text(
            text = title.take(1).ifBlank { "旅" },
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSecondaryContainer,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
        )
    }
}

@Composable
private fun SearchDialog(
    albums: List<Album>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, albums) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            albums
        } else {
            albums.filter { album ->
                album.title.contains(keyword, ignoreCase = true) ||
                    album.photos.any { it.fileName.contains(keyword, ignoreCase = true) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "搜索旅行",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("相册名称或照片文件名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.height(12.dp))
                if (results.isEmpty()) {
                    Text(
                        text = "没有找到相关旅行",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(if (results.size > 4) 320.dp else (results.size * 68).dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.id }) { album ->
                            Surface(
                                onClick = { onOpen(album.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${Formats.shortDate(album.startAt ?: album.createdAt)} · ${album.photos.size} 张",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}
