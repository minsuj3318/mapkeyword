package com.example.mapkeywords

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** 앱 전체에서 쓰는 저장값(SharedPreferences) 이름과 공용 함수 */
object Prefs {
    const val NAME = "settings"

    const val KEY_MODE = "travel_mode"
    const val KEY_RADIUS = "radius"
    const val KEY_KAKAO = "kakao_key"

    // 길찾기 목적지 목록과 주변 검색 키워드 목록은 따로 저장
    const val KEYWORDS_DIRECTIONS = "keywords_directions"
    const val KEYWORDS_NEARBY = "keywords_nearby"

    // 화면 분리 전 버전에서 저장했던 목록. 새 목록이 비어 있으면 처음 한 번 이 값을 불러온다.
    private const val KEYWORDS_LEGACY = "keywords"

    const val MODE_DRIVING = "driving"
    const val MODE_TRANSIT = "transit"
    const val MODE_WALKING = "walking"
    const val MODE_BICYCLING = "bicycling"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun currentMode(context: Context): String =
        of(context).getString(KEY_MODE, MODE_DRIVING) ?: MODE_DRIVING

    fun loadKeywords(prefs: SharedPreferences, key: String): MutableList<String> {
        val result = mutableListOf<String>()
        val json = prefs.getString(key, null) ?: prefs.getString(KEYWORDS_LEGACY, null)
            ?: return result
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
        } catch (e: Exception) {
            // 저장된 데이터가 손상된 경우 빈 목록으로 시작
        }
        return result
    }

    fun saveKeywords(prefs: SharedPreferences, key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }
}
