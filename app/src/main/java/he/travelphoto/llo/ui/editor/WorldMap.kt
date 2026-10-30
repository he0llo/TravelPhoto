package he.travelphoto.llo.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import he.travelphoto.llo.data.GeoPoint
import he.travelphoto.llo.data.MapProvider
import he.travelphoto.llo.data.Photo
import he.travelphoto.llo.data.PlaceCluster
import he.travelphoto.llo.ui.components.AsyncPhoto
import he.travelphoto.llo.util.ChinaCoordinates
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.PI

internal data class MapCamera(
    val centerLatitude: Double = 31.2304,
    val centerLongitude: Double = 121.4737,
    val zoom: Float = 4f,
)

internal object WorldMapMath {
    private const val TILE = 256.0
    const val MIN_ZOOM = 3f
    const val MAX_ZOOM = 18f
    private const val MAX_LAT = 85.05112878

    fun clampLatitude(latitude: Double): Double = latitude.coerceIn(-MAX_LAT, MAX_LAT)

    private fun worldSize(zoom: Float): Double = TILE * 2.0.pow(zoom.toDouble())

    private fun longitudeToX(longitude: Double, zoom: Float): Double =
        (longitude.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * worldSize(zoom)

    private fun latitudeToY(latitude: Double, zoom: Float): Double {
        val sin = sin(clampLatitude(latitude) * PI / 180.0)
        return (0.5 - ln((1 + sin) / (1 - sin)) / (4 * PI)) * worldSize(zoom)
    }

    private fun xToLongitude(x: Double, zoom: Float): Double =
        x / worldSize(zoom) * 360.0 - 180.0

    private fun yToLatitude(y: Double, zoom: Float): Double =
        atan(sinh(PI * (1 - 2 * y / worldSize(zoom)))) * 180.0 / PI

    fun project(
        latitude: Double,
        longitude: Double,
        camera: MapCamera,
        size: Size,
    ): Offset {
        val zoom = camera.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = longitudeToX(camera.centerLongitude, zoom)
        val centerY = latitudeToY(camera.centerLatitude, zoom)
        val x = longitudeToX(longitude, zoom)
        val y = latitudeToY(latitude, zoom)
        return Offset(
            (size.width / 2.0 + (x - centerX)).toFloat(),
            (size.height / 2.0 + (y - centerY)).toFloat(),
        )
    }

    fun unproject(point: Offset, camera: MapCamera, size: Size): GeoPoint? {
        if (size.width <= 0f || size.height <= 0f) return null
        val zoom = camera.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = longitudeToX(camera.centerLongitude, zoom)
        val centerY = latitudeToY(camera.centerLatitude, zoom)
        val worldX = centerX + (point.x - size.width / 2.0)
        val worldY = centerY + (point.y - size.height / 2.0)
        return GeoPoint(
            latitude = yToLatitude(worldY, zoom),
            longitude = xToLongitude(worldX, zoom),
        )
    }

    fun pan(camera: MapCamera, drag: Offset, size: Size): MapCamera {
        val zoom = camera.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centerX = longitudeToX(camera.centerLongitude, zoom) - drag.x
        val centerY = latitudeToY(camera.centerLatitude, zoom) - drag.y
        return camera.copy(
            centerLatitude = clampLatitude(yToLatitude(centerY, zoom)),
            centerLongitude = xToLongitude(centerX, zoom),
        )
    }

    fun zoomAround(
        camera: MapCamera,
        pivot: Offset,
        zoomFactor: Float,
        size: Size,
    ): MapCamera {
        if (zoomFactor <= 0f || size.width <= 0f || size.height <= 0f) return camera
        val oldZoom = camera.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newZoom = (oldZoom + log2(zoomFactor)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == oldZoom) return camera
        val anchor = unproject(pivot, camera, size) ?: return camera
        val anchorX = longitudeToX(anchor.longitude, newZoom)
        val anchorY = latitudeToY(anchor.latitude, newZoom)
        val centerX = anchorX - (pivot.x - size.width / 2.0)
        val centerY = anchorY - (pivot.y - size.height / 2.0)
        return camera.copy(
            centerLatitude = clampLatitude(yToLatitude(centerY, newZoom)),
            centerLongitude = xToLongitude(centerX, newZoom),
            zoom = newZoom,
        )
    }

    fun zoom(camera: MapCamera, targetZoom: Float): MapCamera =
        camera.copy(zoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun minimumZoom(widthPx: Float, heightPx: Float): Float {
        if (widthPx <= 0f || heightPx <= 0f) return MIN_ZOOM
        return max(MIN_ZOOM, log2(heightPx / TILE.toFloat()))
    }
}

internal data class TileKey(val z: Int, val x: Int, val y: Int) {
    override fun toString(): String = "$z/$x/$y"
}

private data class PlacedTile(val key: TileKey, val left: Float, val top: Float)

@Composable
internal fun WorldMap(
    initialCamera: MapCamera,
    clusters: List<PlaceCluster>,
    focusPoint: GeoPoint?,
    onCameraChange: (MapCamera) -> Unit,
    onMarkerLongPress: (PlaceCluster) -> Unit,
    onMarkerMoved: (PlaceCluster, GeoPoint) -> Unit,
    photoFile: (Photo) -> File?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    provider: MapProvider = MapProvider.AMAP,
    onMapClick: (GeoPoint) -> Unit = {},
    onMarkerTap: (PlaceCluster) -> Unit = {},
) {
    val context = LocalContext.current
    val tileLoader = remember(context, provider) { AmapTileLoader(context, provider) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = remember(mapSize) {
        if (mapSize.width <= 0 || mapSize.height <= 0) Size.Zero
        else Size(mapSize.width.toFloat(), mapSize.height.toFloat())
    }
    val minZoom = remember(mapSize) {
        if (mapSize.width <= 0 || mapSize.height <= 0) WorldMapMath.MIN_ZOOM
        else WorldMapMath.minimumZoom(mapSize.width.toFloat(), mapSize.height.toFloat())
    }
    val camera = initialCamera.copy(
        zoom = initialCamera.zoom.coerceIn(minZoom, WorldMapMath.MAX_ZOOM),
        centerLatitude = WorldMapMath.clampLatitude(initialCamera.centerLatitude),
    )

    LaunchedEffect(minZoom) {
        if (initialCamera.zoom < minZoom) {
            onCameraChange(initialCamera.copy(zoom = minZoom))
        }
    }

    val latestCamera = rememberUpdatedState(camera)
    val latestViewport = rememberUpdatedState(viewport)
    val latestProvider = rememberUpdatedState(provider)
    val latestClusters = rememberUpdatedState(clusters)
    val latestOnMapClick = rememberUpdatedState(onMapClick)
    val latestOnMarkerLongPress = rememberUpdatedState(onMarkerLongPress)
    val latestOnMarkerMoved = rememberUpdatedState(onMarkerMoved)
    val latestOnMarkerTap = rememberUpdatedState(onMarkerTap)
    val markerTouchRadius = with(LocalDensity.current) { 26.dp.toPx() }
    val markerSlop = with(LocalDensity.current) { 2.dp.toPx() }

    fun isMarkerHit(point: Offset): Boolean {
        val size = latestViewport.value
        if (size.width <= 0f || size.height <= 0f) return false
        return latestClusters.value.any { cluster ->
            val display = if (latestProvider.value == MapProvider.AMAP) {
                ChinaCoordinates.wgsToGcj(cluster.latitude, cluster.longitude)
            } else {
                GeoPoint(cluster.latitude, cluster.longitude)
            }
            val marker = WorldMapMath.project(
                display.latitude,
                display.longitude,
                latestCamera.value,
                size,
            )
            (marker - point).getDistance() <= markerTouchRadius
        }
    }

    fun displayPoint(latitude: Double, longitude: Double): GeoPoint =
        if (provider == MapProvider.AMAP) {
            ChinaCoordinates.wgsToGcj(latitude, longitude)
        } else {
            GeoPoint(latitude, longitude)
        }



    var tiles by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var tilesProvider by remember { mutableStateOf(provider) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingPoint by remember { mutableStateOf<Offset?>(null) }
    var pressedMarkerKey by remember { mutableStateOf<String?>(null) }
    val mapBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val tilePlaceholder = MaterialTheme.colorScheme.surfaceContainerHigh
    val attributionColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val focusColor = MaterialTheme.colorScheme.primary

    val placedTiles = remember(camera, mapSize) {
        if (mapSize.width <= 0 || mapSize.height <= 0) return@remember emptyList()
        val z = camera.zoom.toInt().coerceIn(3, 18)
        val scale = 2.0.pow((camera.zoom - z).toDouble()).toFloat()
        val tilePx = 256f * scale
        val tilesPerSide = 1 shl z
        val centerX = (camera.centerLongitude.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * tilesPerSide * 256.0
        val sin = sin(WorldMapMath.clampLatitude(camera.centerLatitude) * PI / 180.0)
        val centerY = (0.5 - ln((1 + sin) / (1 - sin)) / (4 * PI)) * tilesPerSide * 256.0
        val originX = mapSize.width / 2.0 - centerX * scale
        val originY = mapSize.height / 2.0 - centerY * scale
        val startX = floor((-originX) / tilePx).toInt() - 1
        val endX = floor((mapSize.width - originX) / tilePx).toInt() + 1
        val startY = floor((-originY) / tilePx).toInt() - 1
        val endY = floor((mapSize.height - originY) / tilePx).toInt() + 1
        buildList {
            var x = startX
            while (x <= endX) {
                var y = startY
                while (y <= endY) {
                    if (y in 0 until tilesPerSide) {
                        val wrappedX = ((x % tilesPerSide) + tilesPerSide) % tilesPerSide
                        add(
                            PlacedTile(
                                key = TileKey(z, wrappedX, y),
                                left = (originX + x * tilePx).toFloat(),
                                top = (originY + y * tilePx).toFloat(),
                            ),
                        )
                    }
                    y++
                }
                x++
            }
        }
    }

    LaunchedEffect(provider, placedTiles) {
        if (tilesProvider != provider) {
            tilesProvider = provider
            tiles = emptyMap()
        }
        val needed = placedTiles.map { it.key }.distinct()
        val missing = needed.filter { tiles[it.toString()] == null }
        missing.chunked(9).forEach { chunk ->
            val loaded = coroutineScope {
                chunk.map { key ->
                    async { key to tileLoader.load(key.z, key.x, key.y) }
                }.awaitAll()
            }
            val additions = loaded
                .mapNotNull { (key, bitmap) -> bitmap?.let { key.toString() to it } }
                .toMap()
            if (additions.isNotEmpty()) {
                tiles = (tiles + additions).filterKeys { key ->
                    key.substringBefore('/') in setOf("3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { mapSize = it },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (isMarkerHit(down.position)) return@awaitEachGesture
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (!moved && (pan.getDistance() > 3f || zoom != 1f)) {
                                moved = true
                            }
                            if (moved) {
                                val size = latestViewport.value
                                var next = WorldMapMath.pan(latestCamera.value, pan, size)
                                if (zoom != 1f) {
                                    next = WorldMapMath.zoomAround(next, centroid, zoom, size)
                                }
                                onCameraChange(next)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (!moved) {
                            WorldMapMath
                                .unproject(down.position, latestCamera.value, latestViewport.value)
                                ?.let { tap ->
                                    val point = if (latestProvider.value == MapProvider.AMAP) {
                                        ChinaCoordinates.gcjToWgs(tap)
                                    } else {
                                        tap
                                    }
                                    latestOnMapClick.value(point)
                                }
                        }
                    }
                },
        ) {
            drawRect(mapBackground)
            placedTiles.forEach { placed ->
                val image = tiles[placed.key.toString()]
                if (image != null) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(placed.left.roundToInt(), placed.top.roundToInt()),
                        dstSize = IntSize(
                            (256f * 2f.pow((camera.zoom - placed.key.z).toFloat())).roundToInt(),
                            (256f * 2f.pow((camera.zoom - placed.key.z).toFloat())).roundToInt(),
                        ),
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    drawRect(
                        color = tilePlaceholder,
                        topLeft = Offset(placed.left, placed.top),
                        size = Size(
                            256f * 2f.pow((camera.zoom - placed.key.z).toFloat()),
                            256f * 2f.pow((camera.zoom - placed.key.z).toFloat()),
                        ),
                    )
                }
            }

            focusPoint?.let { point ->
                val display = displayPoint(point.latitude, point.longitude)
                val offset = WorldMapMath.project(display.latitude, display.longitude, camera, size)
                drawCircle(
                    color = focusColor.copy(alpha = 0.16f),
                    radius = 26.dp.toPx(),
                    center = offset,
                )
                drawCircle(
                    color = focusColor,
                    radius = 5.dp.toPx(),
                    center = offset,
                )
            }

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 11.dp.toPx()
                    color = attributionColor.toArgb()
                }
                canvas.nativeCanvas.drawText(
                    provider.attribution,
                    12.dp.toPx(),
                    size.height - 12.dp.toPx(),
                    paint,
                )
            }
        }

        clusters.forEach { cluster ->
            val isDragging = draggingKey == cluster.key
            val base = if (isDragging && draggingPoint != null) {
                draggingPoint!!
            } else {
                val display = displayPoint(cluster.latitude, cluster.longitude)
                WorldMapMath.project(display.latitude, display.longitude, camera, viewport)
            }
            if (base.x < -80f || base.x > mapSize.width + 80f || base.y < -80f || base.y > mapSize.height + 80f) {
                return@forEach
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (base.x - 22.dp.toPx()).roundToInt(),
                            (base.y - 22.dp.toPx()).roundToInt(),
                        )
                    }
                    .size(44.dp)
                    .zIndex(if (isDragging) 8f else 4f)
                    .pointerInput(cluster.key, camera, provider) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressedMarkerKey = cluster.key
                            var dragging = false
                            var longPressed = false
                            var total = Offset.Zero
                            // Keep a tiny touch tolerance so a simple tap still counts as a tap;
                            // there is no long-press anti-mistouch gate for moving a marker.
                            val slop = markerSlop
                            while (true) {
                                val event = if (!dragging && !longPressed) {
                                    withTimeoutOrNull(520L) { awaitPointerEvent(PointerEventPass.Main) }
                                } else {
                                    awaitPointerEvent(PointerEventPass.Main)
                                }
                                if (event == null) {
                                    longPressed = true
                                    latestOnMarkerLongPress.value(cluster)
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val delta = change.position - change.previousPosition
                                total += delta
                                if (!dragging && !longPressed && total.getDistance() > slop) {
                                    dragging = true
                                    draggingKey = cluster.key
                                    draggingPoint = base
                                }
                                if (dragging) {
                                    draggingPoint = (draggingPoint ?: base) + delta
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (dragging) {
                                        val endPoint = draggingPoint
                                        if (endPoint != null) {
                                            WorldMapMath.unproject(
                                                endPoint,
                                                camera,
                                                viewport,
                                            )?.let { moved ->
                                                val point = if (latestProvider.value == MapProvider.AMAP) {
                                                    ChinaCoordinates.gcjToWgs(moved)
                                                } else {
                                                    moved
                                                }
                                                latestOnMarkerMoved.value(cluster, point)
                                            }
                                        }
                                        draggingKey = null
                                        draggingPoint = null
                                    } else if (!longPressed) {
                                        latestOnMarkerTap.value(cluster)
                                    }
                                    pressedMarkerKey = null
                                    break
                                }
                            }
                        }
                    },
            ) {
                MarkerBubble(
                    cluster = cluster,
                    file = cluster.coverPhoto?.let { photoFile(it) },
                    isDragging = isDragging,
                    isPressed = pressedMarkerKey == cluster.key,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        onCameraChange(WorldMapMath.zoom(camera, camera.zoom + 1f))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "放大")
                }
                FilledTonalIconButton(
                    onClick = {
                        onCameraChange(WorldMapMath.zoom(camera, camera.zoom - 1f))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "缩小")
                }
                FilledTonalIconButton(
                    onClick = {
                        onCameraChange(MapCamera())
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "回到中国")
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 104.dp, end = 16.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = "缩放 ${camera.zoom.roundToInt()} 级",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MarkerBubble(
    cluster: PlaceCluster,
    file: File?,
    isDragging: Boolean,
    isPressed: Boolean,
) {
    val border = if (isDragging || isPressed) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
    }
    Box(contentAlignment = Alignment.TopEnd) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = if (isDragging) 12.dp else 5.dp,
        ) {
            Box(Modifier.fillMaxSize().padding(3.dp)) {
                if (file != null) {
                    AsyncPhoto(
                        file = file,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        shape = CircleShape,
                        maxSize = 180,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (cluster.photos.size > 1) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = cluster.photos.size.toString(),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
