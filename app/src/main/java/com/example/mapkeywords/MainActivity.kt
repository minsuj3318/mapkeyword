package com.example.mapkeywords

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: KeywordAdapter
    private lateinit var input: EditText
    private val keywords = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadKeywords()

        // 이동수단: 마지막으로 선택한 값을 저장해 두었다가 다음 실행 때도 그대로 사용
        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        modeGroup.check(idForMode(currentMode()))
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putString(KEY_MODE, modeForId(checkedId)).apply()
        }

        // 주변 지도 검색 반경: 선택값을 저장해 두고 다음 실행 때도 그대로 사용
        val radiusSpinner = findViewById<Spinner>(R.id.radiusSpinner)
        radiusSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, RADIUS_LABELS
        )
        radiusSpinner.setSelection(
            RADIUS_VALUES.indexOf(prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS)).coerceAtLeast(0)
        )
        radiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(KEY_RADIUS, RADIUS_VALUES[position]).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        input = findViewById(R.id.keywordInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val list = findViewById<ListView>(R.id.keywordList)
        list.emptyView = findViewById<TextView>(R.id.emptyView)

        adapter = KeywordAdapter()
        list.adapter = adapter

        // 짧게 누르면 길찾기 실행, 길게 누르면 삭제, [주변 지도] 버튼은 반경 내 검색
        list.setOnItemClickListener { _, _, position, _ ->
            Directions.open(this, keywords[position], currentMode())
        }
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

    private inner class KeywordAdapter : ArrayAdapter<String>(
        this@MainActivity, R.layout.item_keyword, R.id.keywordText, keywords
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            view.findViewById<View>(R.id.mapButton).setOnClickListener {
                openNearbyMap(keywords[position])
            }
            return view
        }
    }

    private fun openNearbyMap(keyword: String) {
        val intent = Intent(this, MapActivity::class.java)
            .putExtra(MapActivity.EXTRA_KEYWORD, keyword)
            .putExtra(MapActivity.EXTRA_RADIUS, prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS))
            .putExtra(MapActivity.EXTRA_MODE, currentMode())
        startActivity(intent)
    }

    private fun currentMode(): String = prefs.getString(KEY_MODE, MODE_DRIVING) ?: MODE_DRIVING

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
        private const val KEY_RADIUS = "radius"
        private const val DEFAULT_RADIUS = 500

        private const val MODE_DRIVING = "driving"
        private const val MODE_TRANSIT = "transit"
        private const val MODE_WALKING = "walking"
        private const val MODE_BICYCLING = "bicycling"

        private val RADIUS_VALUES = intArrayOf(200, 300, 500, 1000, 2000, 3000)
        private val RADIUS_LABELS = arrayOf("200m", "300m", "500m", "1km", "2km", "3km")
    }
}
