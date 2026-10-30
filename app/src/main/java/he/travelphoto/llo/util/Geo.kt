package he.travelphoto.llo.util

import he.travelphoto.llo.data.Photo
import he.travelphoto.llo.data.PlaceCluster
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Greedy nearest-centre clustering. Good enough for a travel map and fully deterministic.
     */
    fun clusterPhotos(photos: List<Photo>, radiusKm: Double = 35.0): List<PlaceCluster> {
        val located = photos.filter { it.hasLocation }.sortedBy { it.takenAt }
        if (located.isEmpty()) return emptyList()

        data class MutableCluster(
            var latitude: Double,
            var longitude: Double,
            val photos: MutableList<Photo>,
        )

        val clusters = mutableListOf<MutableCluster>()
        located.forEach { photo ->
            val lat = photo.latitude ?: return@forEach
            val lon = photo.longitude ?: return@forEach
            var best: MutableCluster? = null
            var bestDistance = Double.MAX_VALUE
            clusters.forEach { cluster ->
                val distance = distanceKm(cluster.latitude, cluster.longitude, lat, lon)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = cluster
                }
            }
            if (best != null && bestDistance <= radiusKm) {
                val cluster = best!!
                val count = cluster.photos.size
                cluster.latitude = (cluster.latitude * count + lat) / (count + 1)
                cluster.longitude = (cluster.longitude * count + lon) / (count + 1)
                cluster.photos += photo
            } else {
                clusters += MutableCluster(lat, lon, mutableListOf(photo))
            }
        }

        return clusters.mapIndexed { index, cluster ->
            PlaceCluster(
                key = "cluster_${index}_${(cluster.latitude * 100).roundToInt()}_${(cluster.longitude * 100).roundToInt()}",
                latitude = cluster.latitude,
                longitude = cluster.longitude,
                photos = cluster.photos.sortedBy { it.takenAt },
            )
        }.sortedBy { it.photos.firstOrNull()?.takenAt ?: 0L }
    }

    fun normalizeLongitude(longitude: Double): Double {
        var value = longitude
        while (value > 180.0) value -= 360.0
        while (value < -180.0) value += 360.0
        return value
    }

    fun coerceLatitude(latitude: Double): Double = latitude.coerceIn(-85.0, 85.0)
}
