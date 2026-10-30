package he.travelphoto.llo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import he.travelphoto.llo.data.AlbumRepository
import he.travelphoto.llo.data.AppSettings
import he.travelphoto.llo.data.AppStorage
import he.travelphoto.llo.data.ImportProgress
import he.travelphoto.llo.data.rememberAppSettings
import he.travelphoto.llo.ui.components.BusyDialog
import he.travelphoto.llo.ui.components.CapsuleBottomBar
import he.travelphoto.llo.ui.editor.AlbumEditorScreen
import he.travelphoto.llo.ui.export.ExportScreen
import he.travelphoto.llo.ui.home.HomeScreen
import he.travelphoto.llo.ui.settings.AboutScreen
import he.travelphoto.llo.ui.settings.SettingsScreen
import he.travelphoto.llo.ui.theme.ThemeState
import he.travelphoto.llo.ui.rememberArchivePicker
import he.travelphoto.llo.ui.rememberPhotoPicker
import kotlinx.coroutines.launch

@Composable
internal fun TravelPhotoApp(
    repository: AlbumRepository,
    themeState: ThemeState,
) {
    val context = LocalContext.current
    val settings: AppSettings = rememberAppSettings(context)
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var editingAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showStoragePrompt by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var busyTitle by remember { mutableStateOf("正在整理照片") }
    var progress by remember { mutableStateOf(ImportProgress(0, 0, "正在准备…")) }
    var createAfterPick by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (!settings.storageNoticeSeen && !AppStorage.hasSharedStorageAccess(context)) {
            showStoragePrompt = true
        }
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val photoPicker = rememberPhotoPicker { uris ->
        if (uris.isEmpty()) {
            createAfterPick = false
            return@rememberPhotoPicker
        }
        scope.launch {
            busyTitle = "正在创建旅行相册"
            busy = true
            progress = ImportProgress(0, uris.size, "正在读取照片信息")
            var message: String? = null
            try {
                val album = repository.createAlbum(uris) { update ->
                    progress = update
                }
                if (album == null) {
                    message = "没有读取到可用的照片，请换一组再试"
                } else {
                    editingAlbumId = album.id
                }
            } catch (throwable: Throwable) {
                message = throwable.message?.takeIf { it.isNotBlank() } ?: "创建相册失败，请重试"
            } finally {
                busy = false
                createAfterPick = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    val archivePicker = rememberArchivePicker { uri ->
        scope.launch {
            busyTitle = "正在导入旅行相册"
            busy = true
            progress = ImportProgress(0, 0, "正在解包相册文件")
            var message: String? = null
            try {
                val album = repository.importArchive(uri) { update ->
                    progress = update
                }
                if (album == null) {
                    message = "无法识别这个相册文件，可能已损坏或格式不兼容"
                } else {
                    editingAlbumId = album.id
                }
            } catch (throwable: Throwable) {
                message = throwable.message?.takeIf { it.isNotBlank() } ?: "导入相册失败，请重试"
            } finally {
                busy = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    BackHandler(enabled = showAbout) { showAbout = false }
    BackHandler(enabled = !showAbout && editingAlbumId != null) {
        editingAlbumId = null
    }
    BackHandler(enabled = !showAbout && editingAlbumId == null && tab != MainTab.HOME) {
        tab = MainTab.HOME
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val editingId = editingAlbumId
            val screenKey = when {
                showAbout -> "about"
                editingId != null -> "editor:$editingId"
                else -> "main"
            }
            AnimatedContent(
                targetState = screenKey,
                transitionSpec = {
                    val forward = targetState != "main"
                    if (forward) {
                        (slideInHorizontally(tween(300)) { it } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(180)),
                            )
                    } else {
                        (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { it } + fadeOut(tween(180)),
                            )
                    }
                },
                label = "sub-page",
            ) { key ->
                when {
                    key == "about" -> AboutScreen(onBack = { showAbout = false })

                    key.startsWith("editor:") -> AlbumEditorScreen(
                        repository = repository,
                        settings = settings,
                        albumId = key.removePrefix("editor:"),
                        onBack = { editingAlbumId = null },
                    )

                    else -> {
                        val page = tab
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (page) {
                                MainTab.HOME -> HomeScreen(
                                    repository = repository,
                                    onOpenAlbum = { editingAlbumId = it },
                                    onCreate = {
                                        createAfterPick = true
                                        photoPicker()
                                    },
                                    onImportArchive = archivePicker,
                                )

                                MainTab.EXPORT -> ExportScreen(
                                    repository = repository,
                                    settings = settings,
                                    onGoHome = { tab = MainTab.HOME },
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    themeState = themeState,
                                    settings = settings,
                                    onOpenAbout = { showAbout = true },
                                )
                            }

                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(start = 24.dp, end = 24.dp, bottom = 88.dp),
                            )

                            CapsuleBottomBar(
                                selected = page,
                                onSelect = { tab = it },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStoragePrompt) {
        AlertDialog(
            onDismissRequest = { showStoragePrompt = false },
            title = { Text("把旅行相册存到 SD 卡") },
            text = {
                Text(
                    "为了让你能在文件管理器里直接找到照片，TravelPhoto 会把相册、缩略图和索引放进 " +
                        "SD 卡/TravelPhoto。需要授予“所有文件访问权限”，照片不会上传。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.markStorageNoticeSeen()
                        showStoragePrompt = false
                        AppStorage.openSharedStorageSettings(context)
                    },
                ) { Text("去开启") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        settings.markStorageNoticeSeen()
                        showStoragePrompt = false
                    },
                ) { Text("暂用应用目录") }
            },
        )
    }

    if (busy) {
        BusyDialog(title = busyTitle, progress = progress)
    }
}
