package he.travelphoto.llo.util

import he.travelphoto.llo.data.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84 (GPS / photo EXIF) and GCJ-02 (the datum used by Chinese map providers)
 * conversion.  Album data is always stored in WGS-84; only the map layer converts
 * for display when the selected provider uses the Chinese datum.
 */
internal object ChinaCoordinates {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun wgsToGcj(point: GeoPoint): GeoPoint = wgsToGcj(point.latitude, point.longitude)

    fun wgsToGcj(latitude: Double, longitude: Double): GeoPoint {
        if (!isInChina(latitude, longitude)) return GeoPoint(latitude, longitude)
        val dLat = transformLatitude(longitude - 105.0, latitude - 35.0)
        val dLon = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val latitudeOffset = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val longitudeOffset = dLon * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return GeoPoint(latitude + latitudeOffset, longitude + longitudeOffset)
    }

    /** Approximate inverse; accurate to roughly one metre for the visible map. */
    fun gcjToWgs(point: GeoPoint): GeoPoint {
        val forward = wgsToGcj(point.latitude, point.longitude)
        return GeoPoint(
            latitude = point.latitude * 2 - forward.latitude,
            longitude = point.longitude * 2 - forward.longitude,
        )
    }

    private fun isInChina(latitude: Double, longitude: Double): Boolean =
        longitude in 72.004..137.8347 && latitude in 0.8293..55.8271

    private fun transformLatitude(x: Double, y: Double): Double {
        var value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        value += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        value += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        value += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return value
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var value = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        value += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        value += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        value += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return value
    }
}
