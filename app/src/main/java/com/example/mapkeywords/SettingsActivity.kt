package com.example.mapkeywords

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/** 설정 화면: 카카오 REST API 키를 입력하고 저장한다. 키는 이 폰에만 저장된다. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var keyInput: EditText
    private lateinit var resultText: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.title_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        keyInput = findViewById(R.id.keyInput)
        resultText = findViewById(R.id.resultText)

        ApiKeys.load(this)
        keyInput.setText(ApiKeys.kakaoRestKey)

        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        findViewById<Button>(R.id.testButton).setOnClickListener { test() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun save() {
        val key = keyInput.text.toString().trim()
        Prefs.of(this).edit().putString(Prefs.KEY_KAKAO, key).apply()
        ApiKeys.kakaoRestKey = key
        Toast.makeText(
            this,
            if (key.isEmpty()) R.string.key_cleared else R.string.key_saved,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 입력한 키로 간단한 검색을 해 보고 정상 여부를 알려준다. */
    private fun test() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            resultText.setText(R.string.key_empty)
            return
        }
        resultText.setText(R.string.key_testing)
        executor.execute {
            val message = try {
                PlaceSearch.verifyKakaoKey(key)
                getString(R.string.key_test_ok)
            } catch (e: Exception) {
                getString(R.string.key_test_fail, e.message ?: "?")
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) resultText.text = message
            }
        }
    }
}
