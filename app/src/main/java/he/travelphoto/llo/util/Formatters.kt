package he.travelphoto.llo.util

import he.travelphoto.llo.data.Album
import he.travelphoto.llo.data.Photo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DayGroup(
    val dayStart: Long,
    val photos: List<Photo>,
)

/** A day/place segment used by the timeline and by the exported documents. */
data class ExportRecordGroup(
    val dayStart: Long,
    val name: String,
    val note: String,
    val photos: List<Photo>,
)

object Formats {
    private val locale = Locale.CHINA

    fun dayStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun groupByDay(photos: List<Photo>): List<DayGroup> {
        val map = LinkedHashMap<Long, MutableList<Photo>>()
        photos.sortedBy { it.takenAt }.forEach { photo ->
            map.getOrPut(dayStart(photo.takenAt)) { mutableListOf() }.add(photo)
        }
        return map.map { (start, items) -> DayGroup(start, items) }
    }

    /**
     * Splits a day into the same record points the timeline shows: a new group starts when
     * the place name changes or the next photo is more than 45 km away. Exporters use this
     * so the generated record note appears once per group instead of under every photo.
     */
    fun exportRecordGroups(album: Album): List<ExportRecordGroup> {
        val records = mutableListOf<ExportRecordGroup>()
        groupByDay(album.photos).forEach { day ->
            val bucket = mutableListOf<Photo>()
            var currentName = ""
            fun flush() {
                if (bucket.isEmpty()) return
                val first = bucket.minByOrNull { it.takenAt } ?: return
                val place = currentName.ifBlank { placeName(first) }
                val custom = if (first.hasLocation) {
                    first.latitude?.let { lat ->
                        first.longitude?.let { lon -> album.placeNoteFor(lat, lon)?.note }
                    }
                } else {
                    null
                }
                records += ExportRecordGroup(
                    dayStart = day.dayStart,
                    name = place,
                    note = recordNote(bucket.toList(), place, custom, showTime = true),
                    photos = bucket.toList(),
                )
                bucket.clear()
            }
            day.photos.sortedBy { it.takenAt }.forEach { photo ->
                val place = placeName(photo)
                val first = bucket.firstOrNull()
                val moved = first != null && first.hasLocation && photo.hasLocation &&
                    first.latitude != null && first.longitude != null &&
                    photo.latitude != null && photo.longitude != null &&
                    Geo.distanceKm(first.latitude, first.longitude, photo.latitude, photo.longitude) > 45.0
                if (bucket.isNotEmpty() && (place != currentName || moved)) flush()
                if (bucket.isEmpty()) currentName = place
                bucket += photo
            }
            flush()
        }
        return records
    }

    fun fullDate(time: Long): String = format(time, "yyyy年M月d日")

    fun monthDay(time: Long): String = format(time, "M月d日")

    fun monthYear(time: Long): String = format(time, "yyyy年M月")

    fun weekday(time: Long): String = format(time, "EEEE")

    fun time(time: Long): String = format(time, "HH:mm")

    fun dateTime(time: Long): String = format(time, "yyyy年M月d日 HH:mm")

    fun shortDate(time: Long): String = format(time, "yyyy.MM.dd")

    fun dateRange(start: Long?, end: Long?): String {
        if (start == null || end == null) return "还没有照片"
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        return when {
            dayStart(start) == dayStart(end) -> fullDate(start)
            startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) ->
                "${format(start, "yyyy.MM.dd")} – ${format(end, "MM.dd")}"
            else -> "${format(start, "yyyy.MM.dd")} – ${format(end, "yyyy.MM.dd")}"
        }
    }

    fun albumMeta(album: Album): String {
        val days = groupByDay(album.photos).size
        val dayLabel = if (days > 0) " · $days 天" else ""
        return "${album.photos.size} 张照片$dayLabel"
    }

    fun coordinate(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "北纬" else "南纬"
        val lonDir = if (longitude >= 0) "东经" else "西经"
        return "$latDir ${decimal(latitude)}° · $lonDir ${decimal(longitude)}°"
    }

    fun coordinateShort(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return "${decimal(latitude)}°$latDir · ${decimal(longitude)}°$lonDir"
    }

    /** Chinese place label for a photo; never falls back to raw coordinates. */
    fun placeName(photo: Photo): String {
        photo.placeName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val latitude = photo.latitude
        val longitude = photo.longitude
        return if (latitude != null && longitude != null) {
            PlaceDirectory.nameFor(latitude, longitude)
        } else {
            "待定位"
        }
    }

    fun placeName(latitude: Double, longitude: Double): String =
        PlaceDirectory.nameFor(latitude, longitude)

    /** "10月1日 09:30 到达上海" - the generated description users can edit. */
    fun defaultPhotoNote(photo: Photo, showTime: Boolean = true): String {
        val name = placeName(photo)
        val whenPart = if (showTime) "${monthDay(photo.takenAt)} ${time(photo.takenAt)}" else monthDay(photo.takenAt)
        return if (photo.hasLocation) "$whenPart 到达$name" else "$whenPart · 地点待补充"
    }

    fun photoNote(photo: Photo, showTime: Boolean = true): String =
        photo.note?.trim().orEmpty()

    /** The text shown on a generated record point (a day or place segment). */
    fun recordNote(
        photos: List<Photo>,
        placeName: String,
        customNote: String?,
        showTime: Boolean = true,
    ): String =
        customNote?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultPlaceNote(photos, placeName, showTime)

    fun defaultPlaceNote(photos: List<Photo>, name: String, showTime: Boolean = true): String {
        val first = photos.minByOrNull { it.takenAt } ?: return "到达$name"
        val whenPart = if (showTime) "${monthDay(first.takenAt)} ${time(first.takenAt)}" else monthDay(first.takenAt)
        return "$whenPart 到达$name"
    }

    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun exportStamp(): String = format(System.currentTimeMillis(), "yyyyMMdd_HHmm")

    fun fileStamp(): String = format(System.currentTimeMillis(), "yyyyMMdd")

    fun pickerDateMillis(time: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = time }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }.timeInMillis
    }

    fun combinePicker(dateMillis: Long, hour: Int, minute: Int): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dateMillis
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0,
            )
        }.timeInMillis
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "早安，去收集新的风景"
            in 11..13 -> "午后好，翻翻路上的光"
            in 14..18 -> "下午好，下一站去哪里"
            else -> "晚上好，把今天装进相册"
        }
    }

    private fun decimal(value: Double): String =
        String.format(Locale.US, "%.2f", kotlin.math.abs(value))

    private fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, locale).format(time)
}
