package com.example.mapkeywords

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos

data class Place(val name: String, val lat: Double, val lon: Double)

/**
 * OpenStreetMap 데이터로 반경 안의 장소를 찾는다. (API 키 불필요)
 * 응답이 빠른 Nominatim을 먼저 쓰고, 결과가 없거나 실패하면 Overpass로 한 번 더 검색한다.
 */
object PlaceSearch {

    private const val USER_AGENT = "MapKeywords/1.0 (+https://github.com/minsuj3318/mapkeyword)"

    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )
    private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val NOMINATIM_READ_TIMEOUT_MS = 10_000
    private const val OVERPASS_READ_TIMEOUT_MS = 20_000

    /** 현재 위치(lat, lon) 기준 radius(m) 이내에서 이름/브랜드에 keyword가 들어간 장소를 찾는다. */
    @Throws(IOException::class)
    fun search(keyword: String, lat: Double, lon: Double, radius: Int): List<Place> {
        val errors = mutableListOf<String>()
        val kw = keyword.trim()

        // 0) 카카오 로컬 API: 한국 상점 정보가 가장 풍부 (키가 있을 때만)
        if (ApiKeys.KAKAO_REST_KEY.isNotBlank()) {
            try {
                return kakao(kw, lat, lon, radius)
            } catch (e: AuthException) {
                throw e // 키/설정 문제는 바로 알려준다
            } catch (e: IOException) {
                errors.add("kakao: ${e.message}")
            }
        }

        // 1) Nominatim: 보통 1~2초 안에 응답
        var nominatim: List<Place>? = null
        try {
            nominatim = nominatim(kw, lat, lon, radius)
        } catch (e: IOException) {
            errors.add("nominatim: ${e.message}")
        }
        if (!nominatim.isNullOrEmpty()) return nominatim

        // 2) Overpass: 더 넓게 찾지만 서버가 붐비면 느릴 수 있음
        var overpass: List<Place>? = null
        val query = overpassQuery(kw, lat, lon, radius)
        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                overpass = parseOverpass(
                    http(
                        endpoint, "data=" + URLEncoder.encode(query, "UTF-8"),
                        OVERPASS_READ_TIMEOUT_MS
                    )
                )
                break
            } catch (e: IOException) {
                errors.add("${URL(endpoint).host}: ${e.message}")
            }
        }
        if (!overpass.isNullOrEmpty()) return overpass

        // 어느 한쪽이라도 정상 응답했다면 '결과 없음'
        if (nominatim != null || overpass != null) return emptyList()
        throw IOException(errors.joinToString(" / "))
    }

    /** 카카오 키/설정 오류(401, 403). 다른 서버로 넘어가지 않고 사용자에게 바로 알린다. */
    class AuthException(message: String) : IOException(message)

    /** 카카오 로컬 키워드 검색: 현재 위치 중심 radius(m) 이내, 가까운 순, 최대 45곳 */
    private fun kakao(keyword: String, lat: Double, lon: Double, radius: Int): List<Place> {
        val result = mutableListOf<Place>()
        val seen = HashSet<String>()
        for (page in 1..3) {
            val url = "https://dapi.kakao.com/v2/local/search/keyword.json" +
                "?query=${URLEncoder.encode(keyword, "UTF-8")}" +
                "&x=$lon&y=$lat&radius=${radius.coerceAtMost(20_000)}" +
                "&sort=distance&size=15&page=$page"

            val json = try {
                JSONObject(
                    http(url, null, NOMINATIM_READ_TIMEOUT_MS, "KakaoAK ${ApiKeys.KAKAO_REST_KEY}")
                )
            } catch (e: IOException) {
                if (e.message == "HTTP 401" || e.message == "HTTP 403") {
                    throw AuthException(
                        "카카오 인증 실패(${e.message}): REST API 키와 '카카오맵' 사용 설정을 확인하세요"
                    )
                }
                throw e
            } catch (e: Exception) {
                throw IOException("응답 해석 실패")
            }

            val docs = json.optJSONArray("documents") ?: break
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val name = d.optString("place_name", "")
                val pLon = d.optString("x", "").toDoubleOrNull() ?: continue
                val pLat = d.optString("y", "").toDoubleOrNull() ?: continue
                if (name.isNotEmpty()) addUnique(result, seen, name, pLat, pLon)
            }
            if (json.optJSONObject("meta")?.optBoolean("is_end", true) != false) break
        }
        return result
    }

    private fun overpassQuery(keyword: String, lat: Double, lon: Double, radius: Int): String {
        val pattern = toOverpassRegex(keyword)
        val around = "(around:$radius,$lat,$lon)"
        return buildString {
            append("[out:json][timeout:15];(")
            for (key in listOf("name", "name:ko", "brand", "brand:ko")) {
                append("nwr[\"$key\"~\"$pattern\",i]$around;")
            }
            append(");out center 100;")
        }
    }

    /** GET(body == null) 또는 POST 요청을 보내고 본문을 문자열로 돌려준다. */
    private fun http(url: String, body: String?, readTimeoutMs: Int, authorization: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (authorization != null) conn.setRequestProperty("Authorization", authorization)
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOverpass(body: String): List<Place> {
        val result = mutableListOf<Place>()
        val seen = HashSet<String>()
        val elements = try {
            JSONObject(body).getJSONArray("elements")
        } catch (e: Exception) {
            throw IOException("응답 해석 실패")
        }
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val name = listOf("name:ko", "name", "brand:ko", "brand")
                .map { tags.optString(it, "") }
                .firstOrNull { it.isNotEmpty() } ?: continue

            val lat: Double
            val lon: Double
            if (el.has("lat") && el.has("lon")) {
                lat = el.getDouble("lat")
                lon = el.getDouble("lon")
            } else {
                val center = el.optJSONObject("center") ?: continue
                lat = center.getDouble("lat")
                lon = center.getDouble("lon")
            }
            addUnique(result, seen, name, lat, lon)
        }
        return result
    }

    /** Nominatim: 현재 위치 주변 사각형 안에서만 이름 검색 */
    private fun nominatim(keyword: String, lat: Double, lon: Double, radius: Int): List<Place> {
        val dLat = radius / 111_320.0
        val dLon = radius / (111_320.0 * cos(Math.toRadians(lat)))
        val viewbox = "${lon - dLon},${lat + dLat},${lon + dLon},${lat - dLat}"
        val url = "$NOMINATIM?format=jsonv2&limit=40&bounded=1&accept-language=ko" +
            "&q=${URLEncoder.encode(keyword, "UTF-8")}&viewbox=$viewbox"

        val array = try {
            JSONArray(http(url, null, NOMINATIM_READ_TIMEOUT_MS))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("응답 해석 실패")
        }

        val result = mutableListOf<Place>()
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val name = o.optString("name", "").ifEmpty {
                o.optString("display_name", "").substringBefore(",").trim()
            }
            if (name.isEmpty()) continue
            val pLat = o.optString("lat", "").toDoubleOrNull() ?: continue
            val pLon = o.optString("lon", "").toDoubleOrNull() ?: continue
            addUnique(result, seen, name, pLat, pLon)
        }
        return result
    }

    // 같은 매장이 점(node)과 건물(way)로 중복 등록된 경우 하나만 사용
    private fun addUnique(
        out: MutableList<Place>, seen: MutableSet<String>,
        name: String, lat: Double, lon: Double
    ) {
        val key = "$name|${"%.4f".format(lat)}|${"%.4f".format(lon)}"
        if (seen.add(key)) out.add(Place(name, lat, lon))
    }

    /** 정규식 특수문자를 이스케이프한 뒤 Overpass 문자열 규칙(역슬래시, 따옴표)에 맞게 변환한다. */
    private fun toOverpassRegex(s: String): String {
        val special = "\\.^\$|?*+()[]{}"
        val sb = StringBuilder()
        for (c in s) {
            when {
                c in special -> sb.append("\\\\").append(c)
                c == '"' -> sb.append("\\\"")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
