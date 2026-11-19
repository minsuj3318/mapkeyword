package com.example.mapkeywords

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 첫 메뉴 화면 */
class MainActivity : AppCompatActivity() {

    private lateinit var keyStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyStatus = findViewById(R.id.keyStatus)

        findViewById<Button>(R.id.btnDirections).setOnClickListener {
            startActivity(Intent(this, DirectionsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNearby).setOnClickListener {
            startActivity(Intent(this, NearbyActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        ApiKeys.load(this)
        keyStatus.setText(
            if (ApiKeys.kakaoRestKey.isBlank()) R.string.key_status_missing else R.string.key_status_ok
        )
    }
}
