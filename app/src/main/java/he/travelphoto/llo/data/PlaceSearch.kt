package he.travelphoto.llo.data

import android.content.Context
import he.travelphoto.llo.util.PlaceDirectory
import he.travelphoto.llo.util.PlaceSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Local-first place search. Built-in cities answer instantly and work offline; when the query
 * does not match enough local entries we ask OpenStreetMap's Nominatim for more results.
 * Only the typed place name is sent - photos and coordinates never leave the device.
 */
class PlaceSearch(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val local = PlaceDirectory.search(query)
        if (query.isBlank()) return@withContext local
        val exactLocal = local.any { it.name == query || it.detail.contains(query) }
        if (local.isNotEmpty() && (exactLocal || query.length <= 3)) return@withContext local
        // A longer phrase such as "北京菜市口" should still reach the precise remote result.
        val remote = runCatching { remoteSearch(query) }.getOrDefault(emptyList())
        (local + remote)
            .distinctBy { "${it.name}|${it.latitude}|${it.longitude}" }
            .take(12)
    }

    suspend fun reverseName(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                        "&accept-language=zh-CN&zoom=18" +
                        "&lat=$latitude&lon=$longitude",
                )
                val body = request(url) ?: return@runCatching null
                val obj = org.json.JSONObject(body)
                val address = obj.optJSONObject("address") ?: return@runCatching null
                fun value(key: String): String = address.optString(key).trim()
                val admin = listOf("state", "city", "district", "county")
                    .map(::value)
                    .filter { it.isNotBlank() }
                    .distinct()
                val street = listOf("neighbourhood", "suburb", "town", "village", "road", "house_number")
                    .map(::value)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val combined = admin.joinToString("") + street
                combined.ifBlank {
                    obj.optString("display_name").substringBefore(',').trim().takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }

    private fun remoteSearch(query: String): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2" +
                "&accept-language=zh-CN&addressdetails=1&limit=8&q=$encoded",
        )
        val body = request(url) ?: return emptyList()
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val latitude = item.optString("lat").toDoubleOrNull() ?: continue
                val longitude = item.optString("lon").toDoubleOrNull() ?: continue
                val display = item.optString("display_name").takeIf { it.isNotBlank() } ?: continue
                val name = preciseName(item) ?: display.substringBefore(',').trim()
                add(PlaceSuggestion(name, display, latitude, longitude))
            }
        }
    }

    private fun preciseName(item: JSONObject): String? {
        val address = item.optJSONObject("address") ?: return null
        fun value(key: String): String = address.optString(key).trim()
        val admin = listOf("state", "city", "district", "county")
            .map(::value)
            .filter { it.isNotBlank() }
            .distinct()
        val street = listOf("neighbourhood", "suburb", "town", "village", "road", "house_number")
            .map(::value)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val poi = item.optString("name").trim()
        val parts = mutableListOf<String>()
        (admin + listOf(street, poi)).forEach { part ->
            if (part.isNotBlank() && parts.none { it.contains(part) || part.contains(it) }) {
                parts += part
            }
        }
        return parts.joinToString("").ifBlank { null }
    }

    private fun request(url: URL): String? {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500
            readTimeout = 6500
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TravelPhoto/1.0 (he0llo; github.com/he0llo/TravelPhoto)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
