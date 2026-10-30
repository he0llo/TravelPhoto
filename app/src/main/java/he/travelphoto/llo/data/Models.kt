package he.travelphoto.llo.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A photo that belongs to an album. The bytes live in the portable TravelPhoto folder on
 * shared storage; only file names are persisted so albums survive a move or a restore.
 */
data class Photo(
    val id: String,
    val fileName: String,
    val thumbName: String,
    val takenAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val width: Int = 0,
    val height: Int = 0,
    /** Cached Chinese place label, e.g. 上海 or 通辽附近. */
    val placeName: String? = null,
    /** User-written note for this timeline moment. Empty means "use the generated default". */
    val note: String? = null,
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null
}

data class PlaceNote(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Album(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverPhotoId: String? = null,
    val photos: List<Photo> = emptyList(),
    val placeNotes: List<PlaceNote> = emptyList(),
) {
    val coverPhoto: Photo?
        get() = photos.firstOrNull { it.id == coverPhotoId } ?: photos.firstOrNull()

    val locatedPhotos: List<Photo>
        get() = photos.filter { it.hasLocation }

    val unlocatedPhotos: List<Photo>
        get() = photos.filterNot { it.hasLocation }

    val startAt: Long?
        get() = photos.minOfOrNull { it.takenAt }

    val endAt: Long?
        get() = photos.maxOfOrNull { it.takenAt }

    fun sortedPhotos(): List<Photo> = photos.sortedBy { it.takenAt }

    fun placeNoteFor(latitude: Double, longitude: Double): PlaceNote? =
        placeNotes.minByOrNull {
            he.travelphoto.llo.util.PlaceDirectory.distanceKm(
                latitude,
                longitude,
                it.latitude,
                it.longitude,
            )
        }?.takeIf {
            he.travelphoto.llo.util.PlaceDirectory.distanceKm(
                latitude,
                longitude,
                it.latitude,
                it.longitude,
            ) <= 120.0
        }
}

data class GeoPoint(val latitude: Double, val longitude: Double)

data class PlaceCluster(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val photos: List<Photo>,
) {
    val coverPhoto: Photo?
        get() = photos.firstOrNull()
}

data class ImportProgress(
    val done: Int,
    val total: Int,
    val label: String,
)

internal object AlbumJson {
    fun encode(albums: List<Album>): String {
        val array = JSONArray()
        albums.forEach { array.put(albumToJson(it)) }
        return JSONObject()
            .put("version", 2)
            .put("albums", array)
            .toString()
    }

    fun decode(raw: String): List<Album> {
        if (raw.isBlank()) return emptyList()
        val root = JSONObject(raw)
        val array = root.optJSONArray("albums") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                runCatching { albumFromJson(obj) }.getOrNull()?.let { add(it) }
            }
        }
    }

    private fun albumToJson(album: Album): JSONObject = JSONObject().apply {
        put("id", album.id)
        put("title", album.title)
        put("createdAt", album.createdAt)
        put("updatedAt", album.updatedAt)
        put("coverPhotoId", album.coverPhotoId ?: JSONObject.NULL)
        put("photos", JSONArray().apply { album.photos.forEach { put(photoToJson(it)) } })
        put("placeNotes", JSONArray().apply { album.placeNotes.forEach { put(placeNoteToJson(it)) } })
    }

    private fun albumFromJson(obj: JSONObject): Album {
        val photosArray = obj.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            for (i in 0 until photosArray.length()) {
                photosArray.optJSONObject(i)?.let { add(photoFromJson(it)) }
            }
        }
        val notesArray = obj.optJSONArray("placeNotes") ?: JSONArray()
        val placeNotes = buildList {
            for (i in 0 until notesArray.length()) {
                notesArray.optJSONObject(i)?.let { add(placeNoteFromJson(it)) }
            }
        }
        return Album(
            id = obj.getString("id"),
            title = obj.optString("title", "未命名旅程"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            coverPhotoId = obj.optNullableString("coverPhotoId"),
            photos = photos.sortedBy { it.takenAt },
            placeNotes = placeNotes,
        )
    }

    private fun photoToJson(photo: Photo): JSONObject = JSONObject().apply {
        put("id", photo.id)
        put("fileName", photo.fileName)
        put("thumbName", photo.thumbName)
        put("takenAt", photo.takenAt)
        put("latitude", photo.latitude ?: JSONObject.NULL)
        put("longitude", photo.longitude ?: JSONObject.NULL)
        put("width", photo.width)
        put("height", photo.height)
        put("placeName", photo.placeName ?: JSONObject.NULL)
        put("note", photo.note ?: JSONObject.NULL)
    }

    private fun photoFromJson(obj: JSONObject): Photo = Photo(
        id = obj.getString("id"),
        fileName = obj.getString("fileName"),
        thumbName = obj.optString("thumbName", ""),
        takenAt = obj.optLong("takenAt", System.currentTimeMillis()),
        latitude = obj.optNullableDouble("latitude"),
        longitude = obj.optNullableDouble("longitude"),
        width = obj.optInt("width", 0),
        height = obj.optInt("height", 0),
        placeName = obj.optNullableString("placeName"),
        note = obj.optNullableString("note"),
    )

    private fun placeNoteToJson(note: PlaceNote): JSONObject = JSONObject().apply {
        put("id", note.id)
        put("latitude", note.latitude)
        put("longitude", note.longitude)
        put("name", note.name)
        put("note", note.note ?: JSONObject.NULL)
        put("createdAt", note.createdAt)
    }

    private fun placeNoteFromJson(obj: JSONObject): PlaceNote = PlaceNote(
        id = obj.optString("id", "pl_${System.currentTimeMillis().toString(36)}"),
        latitude = obj.optDouble("latitude", 0.0),
        longitude = obj.optDouble("longitude", 0.0),
        name = obj.optString("name", "旅途中的一站"),
        note = obj.optNullableString("note"),
        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
    )
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isFinite()) value else null
}
