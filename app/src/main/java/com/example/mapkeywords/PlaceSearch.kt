package com.example.mapkeywords

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val lat: Double, val lon: Double)

/** OpenStreetMap 데이터를 Overpass API로 조회해 반경 안의 장소를 찾는다. (API 키 불필요) */
object PlaceSearch {

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    /** 현재 위치(lat, lon) 기준 radius(m) 이내에서 이름/브랜드에 keyword가 들어간 장소를 찾는다. */
    @Throws(IOException::class)
    fun search(keyword: String, lat: Double, lon: Double, radius: Int): List<Place> {
        val pattern = toOverpassRegex(keyword.trim())
        val around = "(around:$radius,$lat,$lon)"
        val query = buildString {
            append("[out:json][timeout:25];(")
            for (key in listOf("name", "name:ko", "brand", "brand:ko")) {
                append("nwr[\"$key\"~\"$pattern\",i]$around;")
            }
            append(");out center 100;")
        }

        var lastError: IOException? = null
        for (endpoint in ENDPOINTS) {
            try {
                return parse(request(endpoint, query))
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("검색 실패")
    }

    private fun request(endpoint: String, query: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "MapKeywords/1.0 (Android)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use {
                it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray())
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Place> {
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

            // 같은 매장이 점(node)과 건물(way)로 중복 등록된 경우 하나만 사용
            val key = "$name|${"%.4f".format(lat)}|${"%.4f".format(lon)}"
            if (seen.add(key)) result.add(Place(name, lat, lon))
        }
        return result
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
