package com.example.mapkeywords

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var input: EditText
    private val keywords = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadKeywords()

        // 이동수단: 마지막으로 선택한 값을 저장해 두었다가 다음 실행 때도 그대로 사용
        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        modeGroup.check(idForMode(prefs.getString(KEY_MODE, MODE_DRIVING) ?: MODE_DRIVING))
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putString(KEY_MODE, modeForId(checkedId)).apply()
        }

        input = findViewById(R.id.keywordInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val list = findViewById<ListView>(R.id.keywordList)
        list.emptyView = findViewById<TextView>(R.id.emptyView)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, keywords)
        list.adapter = adapter

        // 짧게 누르면 길찾기 실행, 길게 누르면 삭제
        list.setOnItemClickListener { _, _, position, _ -> openDirections(keywords[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(position)
            true
        }

        addButton.setOnClickListener { addKeyword() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addKeyword()
                true
            } else {
                false
            }
        }
    }

    private fun addKeyword() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (keywords.contains(text)) {
            Toast.makeText(this, R.string.already_exists, Toast.LENGTH_SHORT).show()
            return
        }
        keywords.add(text)
        saveKeywords()
        adapter.notifyDataSetChanged()
        input.text.clear()
    }

    private fun confirmDelete(position: Int) {
        val target = keywords[position]
        AlertDialog.Builder(this)
            .setTitle(target)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                keywords.removeAt(position)
                saveKeywords()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 구글맵 길찾기 화면을 연다. 출발지는 생략하면 현재 위치가 사용된다. */
    private fun openDirections(keyword: String) {
        val mode = prefs.getString(KEY_MODE, MODE_DRIVING) ?: MODE_DRIVING
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", keyword)
            .appendQueryParameter("travelmode", mode)
            .build()

        try {
            // 구글맵 앱으로 바로 열기
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(MAPS_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            try {
                // 구글맵 앱이 없으면 브라우저 등 다른 앱으로 열기
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_maps_app, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadKeywords() {
        keywords.clear()
        val json = prefs.getString(KEY_KEYWORDS, null) ?: return
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                keywords.add(array.getString(i))
            }
        } catch (e: Exception) {
            // 저장된 데이터가 손상된 경우 빈 목록으로 시작
        }
    }

    private fun saveKeywords() {
        prefs.edit().putString(KEY_KEYWORDS, JSONArray(keywords).toString()).apply()
    }

    private fun idForMode(mode: String): Int = when (mode) {
        MODE_TRANSIT -> R.id.modeTransit
        MODE_WALKING -> R.id.modeWalking
        MODE_BICYCLING -> R.id.modeBicycling
        else -> R.id.modeDriving
    }

    private fun modeForId(id: Int): String = when (id) {
        R.id.modeTransit -> MODE_TRANSIT
        R.id.modeWalking -> MODE_WALKING
        R.id.modeBicycling -> MODE_BICYCLING
        else -> MODE_DRIVING
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_MODE = "travel_mode"
        private const val KEY_KEYWORDS = "keywords"
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"

        private const val MODE_DRIVING = "driving"
        private const val MODE_TRANSIT = "transit"
        private const val MODE_WALKING = "walking"
        private const val MODE_BICYCLING = "bicycling"
    }
}
