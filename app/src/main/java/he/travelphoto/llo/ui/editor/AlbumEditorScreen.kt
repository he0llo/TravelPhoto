@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package he.travelphoto.llo.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import he.travelphoto.llo.data.Album
import he.travelphoto.llo.data.AlbumRepository
import he.travelphoto.llo.data.AppSettings
import he.travelphoto.llo.data.GeoPoint
import he.travelphoto.llo.data.ImportProgress
import he.travelphoto.llo.data.Photo
import he.travelphoto.llo.data.PlaceCluster
import he.travelphoto.llo.data.PlaceSearch
import he.travelphoto.llo.ui.components.AsyncPhoto
import he.travelphoto.llo.ui.components.BusyDialog
import he.travelphoto.llo.ui.components.ConfirmDialog
import he.travelphoto.llo.ui.components.NoteEditDialog
import he.travelphoto.llo.ui.components.RenameDialog
import he.travelphoto.llo.ui.rememberPhotoPicker
import he.travelphoto.llo.util.Formats
import he.travelphoto.llo.util.Geo
import he.travelphoto.llo.util.PlaceDirectory
import he.travelphoto.llo.util.PlaceSuggestion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumEditorScreen(
    repository: AlbumRepository,
    settings: AppSettings,
    albumId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val albums by repository.albums.collectAsState()
    val album = albums.firstOrNull { it.id == albumId }
    if (album == null) {
        MissingAlbum(onBack)
        return
    }

    val scope = rememberCoroutineScope()
    val placeSearch = remember(context) { PlaceSearch(context.applicationContext) }
    val snackbar = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false,
        ),
    )

    var camera by remember { mutableStateOf(MapCamera()) }
    var cameraFitted by remember(album.id) { mutableStateOf(false) }
    var mapBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var mapExpanded by remember { mutableStateOf(false) }
    var selectedCluster by remember { mutableStateOf<PlaceCluster?>(null) }
    var detailPhotoId by remember { mutableStateOf<String?>(null) }
    var locatingPhotoId by remember { mutableStateOf<String?>(null) }
    var focusPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var markerMenu by remember { mutableStateOf<PlaceCluster?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteAlbumOpen by remember { mutableStateOf(false) }
    var deletePhotoId by remember { mutableStateOf<String?>(null) }
    var notePhotoId by remember { mutableStateOf<String?>(null) }
    var notePlace by remember { mutableStateOf<PlaceCluster?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var timePhotoId by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<Long?>(null) }
    var choosingCluster by remember { mutableStateOf<PlaceCluster?>(null) }

    val clusters = remember(album.photos) { Geo.clusterPhotos(album.locatedPhotos) }
    val unlocated = album.unlocatedPhotos
    val locatingPhoto = album.photos.firstOrNull { it.id == locatingPhotoId }
    val detailPhoto = album.photos.firstOrNull { it.id == detailPhotoId }
    val timePhoto = album.photos.firstOrNull { it.id == timePhotoId }
    val notePhoto = album.photos.firstOrNull { it.id == notePhotoId }

    val photoPicker = rememberPhotoPicker { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                val added = repository.importPhotos(album.id, uris) { progress = it }
                busy = false
                progress = null
                if (added == 0) {
                    snackbar.showSnackbar("没有导入新的照片")
                } else {
                    snackbar.showSnackbar("已导入 $added 张照片")
                }
            }
        }
    }

    LaunchedEffect(album.id, clusters.size) {
        if (!cameraFitted && clusters.isNotEmpty()) {
            camera = fittedCamera(clusters)
            cameraFitted = true
        }
    }

    fun startLocating(photo: Photo) {
        locatingPhotoId = photo.id
        // Choosing a point is the primary action here: give the map the whole screen.
        mapExpanded = true
        camera = photo.latitude?.let { lat ->
            photo.longitude?.let { lon -> MapCamera(lat, lon, maxOf(camera.zoom, 13.5f)) }
        } ?: camera
        focusPoint = photo.latitude?.let { lat ->
            photo.longitude?.let { lon -> GeoPoint(lat, lon) }
        }
        scope.launch {
            snackbar.showSnackbar("点地图上的记录点即可归入，点空地则新建记录")
        }
    }

    fun placePhoto(
        photo: Photo,
        destination: GeoPoint,
        cluster: PlaceCluster?,
        nameHint: String? = null,
    ) {
        scope.launch {
            val fallback = if (cluster != null) {
                cluster.coverPhoto?.let { Formats.placeName(it) }
                    ?: Formats.placeName(cluster.latitude, cluster.longitude)
            } else {
                nameHint?.takeIf { it.isNotBlank() }
                    ?: PlaceDirectory.nameFor(destination.latitude, destination.longitude)
            }
            repository.setPhotoLocation(
                album.id,
                photo.id,
                destination.latitude,
                destination.longitude,
                fallback,
            )
            locatingPhotoId = null
            choosingCluster = null
            focusPoint = destination
            scope.launch {
                snackbar.showSnackbar(
                    if (cluster != null) "已把照片归入$fallback" else "已把照片放在$fallback",
                )
            }
            // Show the pin immediately, then refine the caption to a precise address when available.
            if (cluster == null && nameHint.isNullOrBlank()) {
                val precise = withTimeoutOrNull(5_000) {
                    placeSearch.reverseName(destination.latitude, destination.longitude)
                }
                if (!precise.isNullOrBlank() && precise != fallback) {
                    repository.setPhotoLocation(
                        album.id,
                        photo.id,
                        destination.latitude,
                        destination.longitude,
                        precise,
                    )
                }
            }
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        WorldMap(
            initialCamera = camera,
            clusters = clusters,
            focusPoint = focusPoint,
            onCameraChange = { camera = it },
            onMarkerLongPress = { markerMenu = it },
            onMarkerMoved = { cluster, point ->
                scope.launch {
                    val fallback = Formats.placeName(point.latitude, point.longitude)
                    cluster.photos.forEach { photo ->
                        repository.setPhotoLocation(
                            album.id,
                            photo.id,
                            point.latitude,
                            point.longitude,
                            fallback,
                        )
                    }
                    focusPoint = point
                    scope.launch { snackbar.showSnackbar("记录点已移到$fallback") }
                    val precise = withTimeoutOrNull(5_000) {
                        placeSearch.reverseName(point.latitude, point.longitude)
                    }
                    if (!precise.isNullOrBlank() && precise != fallback) {
                        cluster.photos.forEach { photo ->
                            repository.setPhotoLocation(
                                album.id,
                                photo.id,
                                point.latitude,
                                point.longitude,
                                precise,
                            )
                        }
                    }
                }
            },
            onMarkerTap = { cluster ->
                locatingPhoto?.let { target ->
                    placePhoto(
                        target,
                        GeoPoint(cluster.latitude, cluster.longitude),
                        cluster,
                    )
                }
            },
            photoFile = { repository.photoFile(album.id, it) },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { mapBounds = it.boundsInRoot() },
            provider = settings.mapProvider,
            onMapClick = { point ->
                val target = locatingPhoto
                if (target == null) {
                    scope.launch { snackbar.showSnackbar("先点一张照片，再在地图上选点") }
                } else {
                    placePhoto(target, point, null)
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            EditorTopBar(
                title = album.title,
                subtitle = "${album.photos.size} 张照片 · ${Formats.dateRange(album.startAt, album.endAt)}",
                mapExpanded = mapExpanded,
                onBack = onBack,
                onSearch = { searchOpen = true },
                onToggleMap = {
                    mapExpanded = !mapExpanded
                    if (!mapExpanded) {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                },
                trailing = {
                    Box {
                        FilledTonalIconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("继续导入照片") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    photoPicker()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重命名相册") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    renameOpen = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("删除相册", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    deleteAlbumOpen = true
                                },
                            )
                        }
                    }
                },
            )

            AnimatedVisibility(visible = locatingPhoto != null) {
                val target = locatingPhoto
                if (target != null) {
                    Spacer(Modifier.height(8.dp))
                    LocatingHintBar(
                        place = Formats.placeName(target),
                        onCancel = {
                            locatingPhotoId = null
                            mapExpanded = false
                        },
                    )
                }
            }
        }

        if (!mapExpanded) {
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = if (unlocated.isEmpty()) 238.dp else 292.dp,
                sheetContent = {
                    TimelineSheet(
                        album = album,
                        settings = settings,
                        unlocated = unlocated,
                        clusters = clusters,
                        photoFile = { repository.displayFile(album.id, it) },
                        onCollapse = {
                            scope.launch {
                                when (scaffoldState.bottomSheetState.currentValue) {
                                    SheetValue.Expanded -> scaffoldState.bottomSheetState.partialExpand()
                                    SheetValue.PartiallyExpanded -> scaffoldState.bottomSheetState.hide()
                                    SheetValue.Hidden -> scaffoldState.bottomSheetState.expand()
                                }
                            }
                        },
                        onOpenPhoto = { detailPhotoId = it.id },
                        onEditPhotoNote = { notePhotoId = it.id },
                        onOpenCluster = { selectedCluster = it },
                        onEditPlaceNote = { notePlace = it },
                        onLocatePhoto = { startLocating(it) },
                    )
                },
                sheetDragHandle = {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier.padding(top = 6.dp),
                    )
                },
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetContentColor = MaterialTheme.colorScheme.onSurface,
                sheetTonalElevation = 3.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        if (mapExpanded) {
            Surface(
                onClick = { mapExpanded = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("回到时间线", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (!mapExpanded && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
            Surface(
                onClick = {
                    scope.launch { scaffoldState.bottomSheetState.expand() }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 18.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ViewTimeline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("时间线", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (mapExpanded) 82.dp else 0.dp),
        )

        if (busy) BusyDialog(title = "正在导入照片", progress = progress)
    }

    if (searchOpen) {
        PlaceSearchDialog(
            onDismiss = { searchOpen = false },
            onSelect = { place: PlaceSuggestion ->
                searchOpen = false
                camera = MapCamera(place.latitude, place.longitude, 13.5f)
                focusPoint = GeoPoint(place.latitude, place.longitude)
                mapExpanded = true
                val target = locatingPhoto
                if (target != null) {
                    placePhoto(
                        target,
                        GeoPoint(place.latitude, place.longitude),
                        cluster = null,
                        nameHint = place.name,
                    )
                }
            },
        )
    }

    markerMenu?.let { cluster ->
        val name = cluster.coverPhoto?.let { Formats.placeName(it) }
            ?: Formats.placeName(cluster.latitude, cluster.longitude)
        AlertDialog(
            onDismissRequest = { markerMenu = null },
            title = { Text(name) },
            text = { Text("删除这个记录点后，${cluster.photos.size} 张照片会回到「等待定位」，点选照片即可重新在地图上选点。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        markerMenu = null
                        selectedCluster = null
                        scope.launch {
                            cluster.photos.forEach { photo ->
                                repository.clearPhotoLocation(album.id, photo.id)
                            }
                            repository.savePlaceNote(
                                album.id,
                                cluster.latitude,
                                cluster.longitude,
                                name,
                                null,
                            )
                            snackbar.showSnackbar("记录点已删除")
                        }
                    },
                ) {
                    Text("删除记录点", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { markerMenu = null }) { Text("取消") }
            },
        )
    }

    selectedCluster?.let { cluster ->
        ClusterSheet(
            cluster = cluster,
            note = album.placeNoteFor(cluster.latitude, cluster.longitude)?.note,
            settings = settings,
            photoFile = { repository.displayFile(album.id, it) },
            onDismiss = { selectedCluster = null },
            onEditNote = {
                selectedCluster = null
                notePlace = cluster
            },
            onOpenPhoto = {
                selectedCluster = null
                detailPhotoId = it.id
            },
        )
    }

    detailPhoto?.let { photo ->
        PhotoDetailSheet(
            photo = photo,
            album = album,
            settings = settings,
            file = repository.displayFile(album.id, photo),
            onDismiss = { detailPhotoId = null },
            onEditNote = {
                detailPhotoId = null
                notePhotoId = photo.id
            },
            onEditTime = {
                timePhotoId = photo.id
                pickedDate = Formats.pickerDateMillis(photo.takenAt)
                showDatePicker = true
            },
            onRelocate = {
                detailPhotoId = null
                startLocating(photo)
            },
            onSetCover = {
                scope.launch {
                    repository.setCover(album.id, photo.id)
                    snackbar.showSnackbar("已设为相册封面")
                }
            },
            onDelete = {
                detailPhotoId = null
                deletePhotoId = photo.id
            },
            onRemoveLocation = {
                scope.launch {
                    repository.clearPhotoLocation(album.id, photo.id)
                    detailPhotoId = null
                    snackbar.showSnackbar("已移除位置信息")
                }
            },
        )
    }

    if (renameOpen) {
        RenameDialog(
            title = "重命名相册",
            initial = album.title,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                if (name.isNotBlank()) {
                    scope.launch { repository.renameAlbum(album.id, name) }
                }
            },
        )
    }

    if (deleteAlbumOpen) {
        ConfirmDialog(
            title = "删除这个相册？",
            message = "「${album.title}」中的照片与说明会一起删除，无法恢复。",
            confirmLabel = "删除相册",
            destructive = true,
            onDismiss = { deleteAlbumOpen = false },
            onConfirm = {
                deleteAlbumOpen = false
                scope.launch {
                    repository.deleteAlbum(album.id)
                    onBack()
                }
            },
        )
    }

    deletePhotoId?.let { id ->
        ConfirmDialog(
            title = "删除这张照片？",
            message = "照片、说明和位置记录都会从相册中移除，无法恢复。",
            confirmLabel = "删除照片",
            destructive = true,
            onDismiss = { deletePhotoId = null },
            onConfirm = {
                deletePhotoId = null
                scope.launch { repository.deletePhoto(album.id, id) }
            },
        )
    }

    choosingCluster?.let { cluster ->
        val name = cluster.coverPhoto?.let { Formats.placeName(it) }
            ?: Formats.placeName(cluster.latitude, cluster.longitude)
        val candidates = album.unlocatedPhotos
        AlertDialog(
            onDismissRequest = { choosingCluster = null },
            title = { Text("把哪张照片加入「$name」？") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "这些照片还没有位置，选择一张即可归入这个记录点。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { photo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    placePhoto(
                                        photo,
                                        GeoPoint(cluster.latitude, cluster.longitude),
                                        cluster,
                                    )
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncPhoto(
                                file = repository.displayFile(album.id, photo),
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                maxSize = 240,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Formats.dateTime(photo.takenAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = photo.note?.takeIf { it.isNotBlank() } ?: "添加说明",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingCluster = null }) { Text("取消") }
            },
        )
    }

    notePhoto?.let { photo ->
        NoteEditDialog(
            title = "照片说明",
            initial = photo.note.orEmpty(),
            defaultNote = "",
            onDismiss = { notePhotoId = null },
            onSave = { note ->
                notePhotoId = null
                scope.launch {
                    repository.updatePhotoNote(album.id, photo.id, note)
                    snackbar.showSnackbar(if (note == null) "已清空说明" else "说明已保存")
                }
            },
        )
    }

    notePlace?.let { place ->
        val placeName = place.coverPhoto?.let { Formats.placeName(it) }
            ?: PlaceDirectory.nameFor(place.latitude, place.longitude)
        val existing = album.placeNoteFor(place.latitude, place.longitude)?.note
        val defaultNote = Formats.defaultPlaceNote(
            place.photos,
            placeName,
            settings.captionShowsTime,
        )
        NoteEditDialog(
            title = "地点说明 · $placeName",
            initial = existing ?: defaultNote,
            defaultNote = defaultNote,
            onDismiss = { notePlace = null },
            onSave = { note ->
                notePlace = null
                scope.launch {
                    repository.savePlaceNote(
                        album.id,
                        place.latitude,
                        place.longitude,
                        placeName,
                        note,
                    )
                    snackbar.showSnackbar(if (note == null) "已恢复默认地点说明" else "地点说明已保存")
                }
            },
        )
    }

    if (showDatePicker) {
        val current = timePhoto
        if (current != null) {
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = pickedDate ?: Formats.pickerDateMillis(current.takenAt),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDate = dateState.selectedDateMillis ?: pickedDate
                            showDatePicker = false
                            showTimePicker = true
                        },
                    ) { Text("下一步") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                },
            ) {
                DatePicker(state = dateState, title = { Text("选择拍摄日期") })
            }
        }
    }

    if (showTimePicker) {
        val current = timePhoto
        val date = pickedDate
        if (current != null && date != null) {
            val calendar = remember(current.id, date) {
                Calendar.getInstance().apply { timeInMillis = current.takenAt }
            }
            val timeState = rememberTimePickerState(
                initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                initialMinute = calendar.get(Calendar.MINUTE),
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text("选择拍摄时间") },
                text = { TimeInput(state = timeState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val millis = Formats.combinePicker(date, timeState.hour, timeState.minute)
                            showTimePicker = false
                            timePhotoId = null
                            pickedDate = null
                            scope.launch {
                                repository.updatePhotoTime(album.id, current.id, millis)
                                snackbar.showSnackbar("时间已更新，时间线已重新排序")
                            }
                        },
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                },
            )
        }
    }

    BackHandler(enabled = mapExpanded) {
        mapExpanded = false
    }
}

@Composable
private fun MissingAlbum(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("相册不存在", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("它可能已经被删除。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    mapExpanded: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToggleMap: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = "搜索地点")
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onToggleMap, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (mapExpanded) {
                        Icons.Rounded.FullscreenExit
                    } else {
                        Icons.Rounded.Fullscreen
                    },
                    contentDescription = if (mapExpanded) "回到时间线" else "放大地图",
                )
            }
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun CandidateBar(
    point: GeoPoint,
    target: Photo?,
    unlocatedCount: Int,
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 5.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "候选记录点",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = Formats.placeName(point.latitude, point.longitude),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    target != null -> "确认后写入「${Formats.placeName(target)}」"
                    unlocatedCount > 0 -> "先从待定位照片中选择一张，再确认写入"
                    else -> "先在时间线中选择一张照片，再记录位置"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(
                    onClick = onPrimary,
                    enabled = target != null || unlocatedCount > 0,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            target != null -> "设定记录点"
                            unlocatedCount > 0 -> "选择照片"
                            else -> "没有待定位照片"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocatingHintBar(place: String, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "点地图上的记录点即可把「$place」归入，点空地则新建记录",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun TimelineSheet(
    album: Album,
    settings: AppSettings,
    unlocated: List<Photo>,
    clusters: List<PlaceCluster>,
    photoFile: (Photo) -> File?,
    onCollapse: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
    onEditPhotoNote: (Photo) -> Unit,
    onOpenCluster: (PlaceCluster) -> Unit,
    onEditPlaceNote: (PlaceCluster) -> Unit,
    onLocatePhoto: (Photo) -> Unit,
) {
    val rows = remember(album.photos, album.placeNotes) {
        buildList {
            Formats.groupByDay(album.sortedPhotos()).forEach { day ->
                add(TimelineRow.Day(day.dayStart, day.photos.size))
                var index = 0
                while (index < day.photos.size) {
                    val first = day.photos[index]
                    val name = if (first.hasLocation) Formats.placeName(first) else "待定位"
                    val group = mutableListOf(first)
                    val baseLat = first.latitude
                    val baseLon = first.longitude
                    index++
                    while (index < day.photos.size) {
                        val next = day.photos[index]
                        val nextName = if (next.hasLocation) Formats.placeName(next) else "待定位"
                        val moved = if (baseLat != null && baseLon != null &&
                            next.latitude != null && next.longitude != null
                        ) {
                            Geo.distanceKm(baseLat, baseLon, next.latitude, next.longitude) > 45.0
                        } else {
                            false
                        }
                        if (nextName != name || moved) break
                        group.add(next)
                        index++
                    }
                    val placeNote = if (baseLat != null && baseLon != null) {
                        album.placeNoteFor(baseLat, baseLon)?.note
                    } else {
                        null
                    }
                    add(TimelineRow.Record(group, name, placeNote))
                    group.forEach { add(TimelineRow.PhotoRow(it)) }
                }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("时间线", style = MaterialTheme.typography.titleLarge)
                Text(
                    "上下拖动抽屉查看全部，点照片可在图上选点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onCollapse, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "展开或收起时间线")
            }
        }

        if (unlocated.isNotEmpty()) {
            PendingPhotosStrip(
                photos = unlocated,
                photoFile = photoFile,
                onLocate = onLocatePhoto,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (clusters.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(clusters, key = { it.key }) { cluster ->
                    Surface(
                        onClick = { onOpenCluster(cluster) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = cluster.coverPhoto?.let { Formats.placeName(it) }
                                    ?: PlaceDirectory.nameFor(cluster.latitude, cluster.longitude),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (cluster.photos.size > 1) {
                                Text(
                                    text = " · ${cluster.photos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        ) {
            items(rows, key = { row ->
                when (row) {
                    is TimelineRow.Day -> "day-${row.dayStart}"
                    is TimelineRow.Record -> "record-${row.photos.first().id}"
                    is TimelineRow.PhotoRow -> row.photo.id
                }
            }) { row ->
                when (row) {
                    is TimelineRow.Day -> DayHeader(row.dayStart, row.count)
                    is TimelineRow.Record -> RecordHeader(
                        record = row,
                        settings = settings,
                        onEdit = {
                            val first = row.photos.first()
                            onEditPlaceNote(
                                PlaceCluster(
                                    key = "record-${first.id}",
                                    latitude = first.latitude ?: 0.0,
                                    longitude = first.longitude ?: 0.0,
                                    photos = row.photos,
                                ),
                            )
                        },
                    )
                    is TimelineRow.PhotoRow -> TimelinePhotoRow(
                        photo = row.photo,
                        settings = settings,
                        file = photoFile(row.photo),
                        onClick = { onOpenPhoto(row.photo) },
                        onEditNote = { onEditPhotoNote(row.photo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(dayStart: Long, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = Formats.monthDay(dayStart),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = Formats.weekday(dayStart),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count 张",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordHeader(
    record: TimelineRow.Record,
    settings: AppSettings,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = Formats.recordNote(
                        photos = record.photos,
                        placeName = record.name,
                        customNote = record.note,
                        showTime = settings.captionShowsTime,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑记录点说明",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelinePhotoRow(
    photo: Photo,
    settings: AppSettings,
    file: File?,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
) {
    val note = Formats.photoNote(photo, settings.captionShowsTime)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncPhoto(
                file = file,
                contentDescription = note.ifBlank { Formats.placeName(photo) },
                modifier = Modifier.size(66.dp),
                shape = RoundedCornerShape(16.dp),
                maxSize = 360,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.ifBlank { "添加说明" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (note.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (photo.hasLocation) {
                            Icons.Rounded.LocationOn
                        } else {
                            Icons.Rounded.Place
                        },
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (photo.hasLocation) {
                            "${Formats.placeName(photo)} · ${Formats.time(photo.takenAt)}"
                        } else {
                            "等待定位 · ${Formats.time(photo.takenAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEditNote, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑说明",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingPhotosStrip(
    photos: List<Photo>,
    photoFile: (Photo) -> File?,
    onLocate: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "等待定位 · ${photos.size} 张",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "点选照片后在地图选点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                photos.forEach { photo ->
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onLocate(photo) },
                        ) {
                            AsyncPhoto(
                                file = photoFile(photo),
                                contentDescription = "在地图上为这张照片选点",
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(18.dp),
                                maxSize = 320,
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(5.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "点此定位",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLocate(photo) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterSheet(
    cluster: PlaceCluster,
    note: String?,
    settings: AppSettings,
    photoFile: (Photo) -> java.io.File?,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
) {
    val names = remember(cluster.photos) {
        cluster.photos.map { Formats.placeName(it) }.distinct().take(3)
    }
    val title = names.joinToString(" · ").ifBlank {
        PlaceDirectory.nameFor(cluster.latitude, cluster.longitude)
    }
    val defaultNote = Formats.defaultPlaceNote(cluster.photos, title, settings.captionShowsTime)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${cluster.photos.size} 张照片 · ${Formats.dateRange(
                            cluster.photos.minOfOrNull { it.takenAt },
                            cluster.photos.maxOfOrNull { it.takenAt },
                        )}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEditNote) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑地点说明")
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("地点说明", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = note?.takeIf { it.isNotBlank() } ?: defaultNote,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onEditNote) { Text("编辑") }
                }
            }
            Spacer(Modifier.height(16.dp))
            cluster.photos.sortedBy { it.takenAt }.forEach { photo ->
                Surface(
                    onClick = { onOpenPhoto(photo) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncPhoto(
                            file = photoFile(photo),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(13.dp),
                            maxSize = 300,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Formats.photoNote(photo, settings.captionShowsTime),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                Formats.time(photo.takenAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoDetailSheet(
    photo: Photo,
    album: Album,
    settings: AppSettings,
    file: java.io.File?,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onEditTime: () -> Unit,
    onRelocate: () -> Unit,
    onSetCover: () -> Unit,
    onDelete: () -> Unit,
    onRemoveLocation: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            AsyncPhoto(
                file = file,
                contentDescription = Formats.photoNote(photo, settings.captionShowsTime),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp),
                shape = RoundedCornerShape(24.dp),
                maxSize = 1280,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Formats.dateTime(photo.takenAt),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (photo.hasLocation) {
                                Formats.placeName(photo)
                            } else {
                                "等待定位"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (album.coverPhotoId == photo.id) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("封面", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Formats.photoNote(photo, settings.captionShowsTime),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onEditNote, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑照片说明",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onEditTime, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("修改时间")
                }
                FilledTonalButton(onClick = onRelocate, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (photo.hasLocation) "调整位置" else "添加位置")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onSetCover,
                    enabled = album.coverPhotoId != photo.id,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("设为封面")
                }
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("删除照片")
                }
            }
            if (photo.hasLocation) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRemoveLocation) {
                    Text("移除位置信息", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CandidatePhotoPicker(
    photos: List<Photo>,
    photoFile: (Photo) -> File?,
    onDismiss: () -> Unit,
    onPick: (Photo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择待定位照片") },
        text = {
            if (photos.isEmpty()) {
                Text("没有待定位照片。")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        Surface(
                            onClick = { onPick(photo) },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncPhoto(
                                    file = photoFile(photo),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    maxSize = 240,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = Formats.photoNote(photo, showTime = true),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private sealed interface TimelineRow {
    data class Day(val dayStart: Long, val count: Int) : TimelineRow
    data class Record(
        val photos: List<Photo>,
        val name: String,
        val note: String?,
    ) : TimelineRow
    data class PhotoRow(val photo: Photo) : TimelineRow
}

private fun fittedCamera(clusters: List<PlaceCluster>): MapCamera {
    if (clusters.isEmpty()) return MapCamera()
    val lats = clusters.map { it.latitude }
    val lons = clusters.map { it.longitude }
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLon = lons.min()
    val maxLon = lons.max()
    val span = maxOf(maxLat - minLat, maxLon - minLon)
    val zoom = when {
        span < 0.8 -> 9.2f
        span < 2.0 -> 7.8f
        span < 5.0 -> 6.4f
        span < 12.0 -> 4.8f
        span < 30.0 -> 3.4f
        else -> 2.0f
    }
    return MapCamera(
        centerLatitude = ((minLat + maxLat) / 2).coerceIn(-50.0, 70.0),
        centerLongitude = ((minLon + maxLon) / 2).let { lon ->
            ((lon + 540.0) % 360.0) - 180.0
        },
        zoom = zoom,
    )
}


