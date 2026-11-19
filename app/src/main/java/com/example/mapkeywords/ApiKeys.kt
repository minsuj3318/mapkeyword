package com.example.mapkeywords

import android.content.Context

/** 카카오 REST API 키. 설정 화면에서 입력하며 폰에만 저장된다. */
object ApiKeys {
    @Volatile
    var kakaoRestKey: String = ""

    fun load(context: Context) {
        kakaoRestKey = Prefs.of(context).getString(Prefs.KEY_KAKAO, "")?.trim().orEmpty()
    }
}
