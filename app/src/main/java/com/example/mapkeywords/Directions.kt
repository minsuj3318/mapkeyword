package com.example.mapkeywords

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 구글맵 길찾기 화면을 여는 공용 함수. 출발지는 생략하면 현재 위치가 사용된다. */
object Directions {
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun open(context: Context, destination: String, mode: String) {
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", destination)
            .appendQueryParameter("travelmode", mode)
            .build()

        try {
            // 구글맵 앱으로 바로 열기
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(MAPS_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            try {
                // 구글맵 앱이 없으면 브라우저 등 다른 앱으로 열기
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_LONG).show()
            }
        }
    }
}
