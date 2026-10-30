package he.travelphoto.llo.util

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class PlaceEntry(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val keywords: List<String> = emptyList(),
)

data class PlaceSuggestion(
    val name: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * A small offline gazetteer of cities that Chinese travellers actually visit. It gives every
 * coordinate a human name and powers instant, privacy-friendly place search when the network
 * is unavailable. Online results from Nominatim are merged in by [PlaceSearch].
 */
object PlaceDirectory {
    val places: List<PlaceEntry> = listOf(
        // China
        PlaceEntry("北京", "中国 · 北京市", 39.9042, 116.4074, listOf("beijing", "peking")),
        PlaceEntry("上海", "中国 · 上海市", 31.2304, 121.4737, listOf("shanghai")),
        PlaceEntry("广州", "中国 · 广东省", 23.1291, 113.2644, listOf("guangzhou", "canton")),
        PlaceEntry("深圳", "中国 · 广东省", 22.5431, 114.0579, listOf("shenzhen")),
        PlaceEntry("杭州", "中国 · 浙江省", 30.2741, 120.1551, listOf("hangzhou")),
        PlaceEntry("南京", "中国 · 江苏省", 32.0603, 118.7969, listOf("nanjing")),
        PlaceEntry("苏州", "中国 · 江苏省", 31.2989, 120.5853, listOf("suzhou")),
        PlaceEntry("无锡", "中国 · 江苏省", 31.4912, 120.3119, listOf("wuxi")),
        PlaceEntry("扬州", "中国 · 江苏省", 32.3942, 119.4129, listOf("yangzhou")),
        PlaceEntry("宁波", "中国 · 浙江省", 29.8683, 121.5440, listOf("ningbo")),
        PlaceEntry("绍兴", "中国 · 浙江省", 30.0303, 120.5802, listOf("shaoxing")),
        PlaceEntry("舟山", "中国 · 浙江省", 29.9853, 122.2072, listOf("zhoushan")),
        PlaceEntry("温州", "中国 · 浙江省", 27.9938, 120.6994, listOf("wenzhou")),
        PlaceEntry("黄山", "中国 · 安徽省", 29.7147, 118.3376, listOf("huangshan")),
        PlaceEntry("厦门", "中国 · 福建省", 24.4798, 118.0894, listOf("xiamen", "amoy")),
        PlaceEntry("福州", "中国 · 福建省", 26.0745, 119.2965, listOf("fuzhou")),
        PlaceEntry("泉州", "中国 · 福建省", 24.8741, 118.6757, listOf("quanzhou")),
        PlaceEntry("青岛", "中国 · 山东省", 36.0671, 120.3826, listOf("qingdao", "tsingtao")),
        PlaceEntry("济南", "中国 · 山东省", 36.6512, 117.1201, listOf("jinan")),
        PlaceEntry("郑州", "中国 · 河南省", 34.7466, 113.6254, listOf("zhengzhou")),
        PlaceEntry("洛阳", "中国 · 河南省", 34.6197, 112.4540, listOf("luoyang")),
        PlaceEntry("开封", "中国 · 河南省", 34.7972, 114.3076, listOf("kaifeng")),
        PlaceEntry("西安", "中国 · 陕西省", 34.3416, 108.9398, listOf("xian", "xi'an")),
        PlaceEntry("成都", "中国 · 四川省", 30.5728, 104.0668, listOf("chengdu")),
        PlaceEntry("重庆", "中国 · 重庆市", 29.5630, 106.5516, listOf("chongqing")),
        PlaceEntry("武汉", "中国 · 湖北省", 30.5928, 114.3055, listOf("wuhan")),
        PlaceEntry("长沙", "中国 · 湖南省", 28.2282, 112.9388, listOf("changsha")),
        PlaceEntry("张家界", "中国 · 湖南省", 29.1170, 110.4792, listOf("zhangjiajie")),
        PlaceEntry("凤凰", "中国 · 湖南省", 27.9483, 109.5996, listOf("fenghuang")),
        PlaceEntry("桂林", "中国 · 广西", 25.2736, 110.2900, listOf("guilin")),
        PlaceEntry("阳朔", "中国 · 广西", 24.7785, 110.4966, listOf("yangshuo")),
        PlaceEntry("南宁", "中国 · 广西", 22.8170, 108.3665, listOf("nanning")),
        PlaceEntry("昆明", "中国 · 云南省", 25.0389, 102.7183, listOf("kunming")),
        PlaceEntry("大理", "中国 · 云南省", 25.6065, 100.2676, listOf("dali")),
        PlaceEntry("丽江", "中国 · 云南省", 26.8721, 100.2299, listOf("lijiang")),
        PlaceEntry("香格里拉", "中国 · 云南省", 27.8299, 99.7065, listOf("shangri-la")),
        PlaceEntry("景洪", "中国 · 云南省", 22.0017, 100.7979, listOf("xishuangbanna", "jinghong")),
        PlaceEntry("贵阳", "中国 · 贵州省", 26.6470, 106.6302, listOf("guiyang")),
        PlaceEntry("三亚", "中国 · 海南省", 18.2528, 109.5119, listOf("sanya")),
        PlaceEntry("海口", "中国 · 海南省", 20.0440, 110.1999, listOf("haikou")),
        PlaceEntry("哈尔滨", "中国 · 黑龙江省", 45.8038, 126.5349, listOf("harbin")),
        PlaceEntry("沈阳", "中国 · 辽宁省", 41.8057, 123.4315, listOf("shenyang")),
        PlaceEntry("大连", "中国 · 辽宁省", 38.9140, 121.6147, listOf("dalian")),
        PlaceEntry("长春", "中国 · 吉林省", 43.8171, 125.3235, listOf("changchun")),
        PlaceEntry("通辽", "中国 · 内蒙古", 43.6524, 122.2437, listOf("tongliao")),
        PlaceEntry("呼和浩特", "中国 · 内蒙古", 40.8426, 111.7492, listOf("hohhot")),
        PlaceEntry("太原", "中国 · 山西省", 37.8706, 112.5489, listOf("taiyuan")),
        PlaceEntry("石家庄", "中国 · 河北省", 38.0428, 114.5149, listOf("shijiazhuang")),
        PlaceEntry("兰州", "中国 · 甘肃省", 36.0611, 103.8343, listOf("lanzhou")),
        PlaceEntry("西宁", "中国 · 青海省", 36.6171, 101.7782, listOf("xining")),
        PlaceEntry("银川", "中国 · 宁夏", 38.4872, 106.2309, listOf("yinchuan")),
        PlaceEntry("乌鲁木齐", "中国 · 新疆", 43.8256, 87.6168, listOf("urumqi")),
        PlaceEntry("喀什", "中国 · 新疆", 39.4704, 75.9898, listOf("kashgar")),
        PlaceEntry("拉萨", "中国 · 西藏", 29.6520, 91.1721, listOf("lhasa")),
        PlaceEntry("林芝", "中国 · 西藏", 29.6491, 94.3615, listOf("nyingchi")),
        PlaceEntry("南昌", "中国 · 江西省", 28.6820, 115.8579, listOf("nanchang")),
        PlaceEntry("景德镇", "中国 · 江西省", 29.2688, 117.1784, listOf("jingdezhen")),
        PlaceEntry("合肥", "中国 · 安徽省", 31.8206, 117.2272, listOf("hefei")),
        PlaceEntry("香港", "中国 · 香港", 22.3193, 114.1694, listOf("hong kong")),
        PlaceEntry("澳门", "中国 · 澳门", 22.1987, 113.5439, listOf("macau")),
        PlaceEntry("台北", "中国 · 台湾", 25.0330, 121.5654, listOf("taipei")),
        PlaceEntry("高雄", "中国 · 台湾", 22.6273, 120.3014, listOf("kaohsiung")),
        PlaceEntry("花莲", "中国 · 台湾", 23.9911, 121.6112, listOf("hualien")),
        // Japan / Korea
        PlaceEntry("东京", "日本", 35.6762, 139.6503, listOf("tokyo")),
        PlaceEntry("大阪", "日本", 34.6937, 135.5023, listOf("osaka")),
        PlaceEntry("京都", "日本", 35.0116, 135.7681, listOf("kyoto")),
        PlaceEntry("奈良", "日本", 34.6851, 135.8048, listOf("nara")),
        PlaceEntry("神户", "日本", 34.6901, 135.1955, listOf("kobe")),
        PlaceEntry("名古屋", "日本", 35.1815, 136.9066, listOf("nagoya")),
        PlaceEntry("札幌", "日本", 43.0618, 141.3545, listOf("sapporo")),
        PlaceEntry("函馆", "日本", 41.7687, 140.7288, listOf("hakodate")),
        PlaceEntry("冲绳", "日本", 26.2124, 127.6809, listOf("okinawa", "naha")),
        PlaceEntry("富士山", "日本", 35.3606, 138.7274, listOf("fuji")),
        PlaceEntry("首尔", "韩国", 37.5665, 126.9780, listOf("seoul")),
        PlaceEntry("釜山", "韩国", 35.1796, 129.0756, listOf("busan")),
        PlaceEntry("济州", "韩国", 33.4996, 126.5312, listOf("jeju")),
        // Southeast / South Asia & Middle East
        PlaceEntry("曼谷", "泰国", 13.7563, 100.5018, listOf("bangkok")),
        PlaceEntry("清迈", "泰国", 18.7883, 98.9853, listOf("chiang mai")),
        PlaceEntry("普吉", "泰国", 7.8804, 98.3923, listOf("phuket")),
        PlaceEntry("新加坡", "新加坡", 1.3521, 103.8198, listOf("singapore")),
        PlaceEntry("吉隆坡", "马来西亚", 3.1390, 101.6869, listOf("kuala lumpur", "kl")),
        PlaceEntry("登巴萨", "印度尼西亚 · 巴厘岛", -8.6705, 115.2126, listOf("bali", "denpasar")),
        PlaceEntry("河内", "越南", 21.0278, 105.8342, listOf("hanoi")),
        PlaceEntry("胡志明", "越南", 10.8231, 106.6297, listOf("ho chi minh", "saigon")),
        PlaceEntry("会安", "越南", 15.8801, 108.3380, listOf("hoi an")),
        PlaceEntry("暹粒", "柬埔寨", 13.3671, 103.8448, listOf("siem reap", "angkor")),
        PlaceEntry("琅勃拉邦", "老挝", 19.8860, 102.1350, listOf("luang prabang")),
        PlaceEntry("仰光", "缅甸", 16.8409, 96.1735, listOf("yangon")),
        PlaceEntry("加德满都", "尼泊尔", 27.7172, 85.3240, listOf("kathmandu")),
        PlaceEntry("马累", "马尔代夫", 4.1755, 73.5093, listOf("male", "maldives")),
        PlaceEntry("新德里", "印度", 28.6139, 77.2090, listOf("new delhi", "delhi")),
        PlaceEntry("孟买", "印度", 19.0760, 72.8777, listOf("mumbai")),
        PlaceEntry("迪拜", "阿联酋", 25.2048, 55.2708, listOf("dubai")),
        PlaceEntry("伊斯坦布尔", "土耳其", 41.0082, 28.9784, listOf("istanbul")),
        PlaceEntry("特拉维夫", "以色列", 32.0853, 34.7818, listOf("tel aviv")),
        PlaceEntry("开罗", "埃及", 30.0444, 31.2357, listOf("cairo")),
        PlaceEntry("马拉喀什", "摩洛哥", 31.6295, -7.9811, listOf("marrakech")),
        PlaceEntry("开普敦", "南非", -33.9249, 18.4241, listOf("cape town")),
        PlaceEntry("内罗毕", "肯尼亚", -1.2921, 36.8219, listOf("nairobi")),
        // Europe
        PlaceEntry("巴黎", "法国", 48.8566, 2.3522, listOf("paris")),
        PlaceEntry("伦敦", "英国", 51.5074, -0.1278, listOf("london")),
        PlaceEntry("罗马", "意大利", 41.9028, 12.4964, listOf("rome", "roma")),
        PlaceEntry("佛罗伦萨", "意大利", 43.7696, 11.2558, listOf("florence", "firenze")),
        PlaceEntry("威尼斯", "意大利", 45.4408, 12.3155, listOf("venice", "venezia")),
        PlaceEntry("米兰", "意大利", 45.4642, 9.1900, listOf("milan", "milano")),
        PlaceEntry("巴塞罗那", "西班牙", 41.3874, 2.1686, listOf("barcelona")),
        PlaceEntry("马德里", "西班牙", 40.4168, -3.7038, listOf("madrid")),
        PlaceEntry("里斯本", "葡萄牙", 38.7223, -9.1393, listOf("lisbon", "lisboa")),
        PlaceEntry("阿姆斯特丹", "荷兰", 52.3676, 4.9041, listOf("amsterdam")),
        PlaceEntry("柏林", "德国", 52.5200, 13.4050, listOf("berlin")),
        PlaceEntry("慕尼黑", "德国", 48.1351, 11.5820, listOf("munich", "münchen")),
        PlaceEntry("布拉格", "捷克", 50.0755, 14.4378, listOf("prague", "praha")),
        PlaceEntry("维也纳", "奥地利", 48.2082, 16.3738, listOf("vienna", "wien")),
        PlaceEntry("布达佩斯", "匈牙利", 47.4979, 19.0402, listOf("budapest")),
        PlaceEntry("雅典", "希腊", 37.9838, 23.7275, listOf("athens")),
        PlaceEntry("圣托里尼", "希腊", 36.3932, 25.4615, listOf("santorini")),
        PlaceEntry("雷克雅未克", "冰岛", 64.1466, -21.9426, listOf("reykjavik")),
        PlaceEntry("奥斯陆", "挪威", 59.9139, 10.7522, listOf("oslo")),
        PlaceEntry("斯德哥尔摩", "瑞典", 59.3293, 18.0686, listOf("stockholm")),
        PlaceEntry("哥本哈根", "丹麦", 55.6761, 12.5683, listOf("copenhagen")),
        PlaceEntry("赫尔辛基", "芬兰", 60.1699, 24.9384, listOf("helsinki")),
        PlaceEntry("莫斯科", "俄罗斯", 55.7558, 37.6173, listOf("moscow")),
        PlaceEntry("圣彼得堡", "俄罗斯", 59.9311, 30.3609, listOf("saint petersburg")),
        PlaceEntry("苏黎世", "瑞士", 47.3769, 8.5417, listOf("zurich")),
        PlaceEntry("因特拉肯", "瑞士", 46.6863, 7.8632, listOf("interlaken")),
        // Americas
        PlaceEntry("纽约", "美国", 40.7128, -74.0060, listOf("new york", "nyc")),
        PlaceEntry("旧金山", "美国", 37.7749, -122.4194, listOf("san francisco", "sf")),
        PlaceEntry("洛杉矶", "美国", 34.0522, -118.2437, listOf("los angeles", "la")),
        PlaceEntry("西雅图", "美国", 47.6062, -122.3321, listOf("seattle")),
        PlaceEntry("芝加哥", "美国", 41.8781, -87.6298, listOf("chicago")),
        PlaceEntry("波士顿", "美国", 42.3601, -71.0589, listOf("boston")),
        PlaceEntry("华盛顿", "美国", 38.9072, -77.0369, listOf("washington", "dc")),
        PlaceEntry("温哥华", "加拿大", 49.2827, -123.1207, listOf("vancouver")),
        PlaceEntry("多伦多", "加拿大", 43.6532, -79.3832, listOf("toronto")),
        PlaceEntry("墨西哥城", "墨西哥", 19.4326, -99.1332, listOf("mexico city")),
        PlaceEntry("里约热内卢", "巴西", -22.9068, -43.1729, listOf("rio", "rio de janeiro")),
        PlaceEntry("布宜诺斯艾利斯", "阿根廷", -34.6037, -58.3816, listOf("buenos aires")),
        PlaceEntry("圣地亚哥", "智利", -33.4489, -70.6693, listOf("santiago")),
        PlaceEntry("利马", "秘鲁", -12.0464, -77.0428, listOf("lima")),
        PlaceEntry("库斯科", "秘鲁", -13.5319, -71.9675, listOf("cusco", "cuzco")),
        // Oceania
        PlaceEntry("悉尼", "澳大利亚", -33.8688, 151.2093, listOf("sydney")),
        PlaceEntry("墨尔本", "澳大利亚", -37.8136, 144.9631, listOf("melbourne")),
        PlaceEntry("奥克兰", "新西兰", -36.8509, 174.7645, listOf("auckland")),
        PlaceEntry("皇后镇", "新西兰", -45.0312, 168.6626, listOf("queenstown")),
    )

    private val featuredNames = setOf("上海", "北京", "东京", "巴黎", "伦敦", "纽约", "悉尼", "新加坡", "迪拜")

    fun featured(): List<PlaceEntry> = places.filter { it.name in featuredNames }

    fun nearest(latitude: Double, longitude: Double): Pair<PlaceEntry, Double>? {
        var best: PlaceEntry? = null
        var bestDistance = Double.MAX_VALUE
        for (place in places) {
            val distance = distanceKm(latitude, longitude, place.latitude, place.longitude)
            if (distance < bestDistance) {
                bestDistance = distance
                best = place
            }
        }
        return best?.let { it to bestDistance }
    }

    /**
     * Human-readable Chinese label for a coordinate. Never exposes raw latitude/longitude.
     */
    fun nameFor(latitude: Double, longitude: Double): String {
        val (place, distance) = nearest(latitude, longitude) ?: return "旅途中的一站"
        return when {
            distance <= 35.0 -> place.name
            distance <= 180.0 -> "${place.name}附近"
            else -> "旅途中的一站"
        }
    }

    fun detailFor(latitude: Double, longitude: Double): String {
        val (place, distance) = nearest(latitude, longitude) ?: return "已记录足迹"
        return if (distance <= 180.0) place.region else "已记录足迹"
    }

    fun search(query: String, limit: Int = 8): List<PlaceSuggestion> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) {
            return featured().map { it.toSuggestion() }.take(limit)
        }
        val scored = places.mapNotNull { place ->
            val haystack = buildString {
                append(place.name.lowercase())
                append(' ')
                append(place.region.lowercase())
                place.keywords.forEach { append(' ').append(it.lowercase()) }
            }
            val nameStarts = place.name.lowercase().startsWith(clean)
            val anyStarts = place.keywords.any { it.lowercase().startsWith(clean) }
            val contains = haystack.contains(clean)
            when {
                nameStarts -> Triple(place, 0, distanceKm(place.latitude, place.longitude, 0.0, 0.0))
                anyStarts -> Triple(place, 1, 0.0)
                contains -> Triple(place, 2, 0.0)
                else -> null
            }
        }.sortedWith(compareBy({ it.second }, { if (it.second == 0) it.third else 0.0 }))
        return scored.map { it.first.toSuggestion() }.take(limit)
    }

    private fun PlaceEntry.toSuggestion() = PlaceSuggestion(
        name = name,
        detail = region,
        latitude = latitude,
        longitude = longitude,
    )

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2 * asin(min(1.0, sqrt(a)))
    }

    fun normalizeDegrees(value: Double): Double = abs(value) % 360.0
}
