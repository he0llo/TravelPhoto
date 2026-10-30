package he.travelphoto.llo.data

/**
 * Tile source used by the editor map. Both entries are ordinary Web Mercator XYZ tile
 * services, so the projection used for markers and taps is identical for either source.
 */
enum class MapProvider(
    val id: String,
    val label: String,
    val shortLabel: String,
    val attribution: String,
) {
    AMAP(
        id = "amap",
        label = "高德地图",
        shortLabel = "高德",
        attribution = "© 高德地图",
    ),
    OSM(
        id = "osm",
        label = "开放地图",
        shortLabel = "开放",
        attribution = "© OpenStreetMap contributors",
    );

    companion object {
        fun fromId(id: String?): MapProvider =
            entries.firstOrNull { it.id == id } ?: AMAP
    }
}
