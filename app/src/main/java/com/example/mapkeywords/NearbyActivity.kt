package com.example.mapkeywords

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** 주변 검색 화면: 반경을 고르고, 키워드를 하나 또는 여러 개 골라 지도에 함께 표시한다. */
class NearbyActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: KeywordAdapter
    private lateinit var input: EditText
    private lateinit var multiButton: Button
    private val keywords = mutableListOf<String>()
    private val selected = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby)
        title = getString(R.string.title_nearby)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = Prefs.of(this)
        keywords.addAll(Prefs.loadKeywords(prefs, Prefs.KEYWORDS_NEARBY))

        // 검색 반경: 선택값을 저장해 두고 다음 실행 때도 그대로 사용
        val radiusSpinner = findViewById<Spinner>(R.id.radiusSpinner)
        radiusSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, RADIUS_LABELS
        )
        radiusSpinner.setSelection(
            RADIUS_VALUES.indexOf(prefs.getInt(Prefs.KEY_RADIUS, DEFAULT_RADIUS)).coerceAtLeast(0)
        )
        radiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(Prefs.KEY_RADIUS, RADIUS_VALUES[position]).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        input = findViewById(R.id.keywordInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val list = findViewById<ListView>(R.id.keywordList)
        list.emptyView = findViewById<TextView>(R.id.emptyView)

        adapter = KeywordAdapter()
        list.adapter = adapter

        // 줄을 누르면 선택/해제, 길게 누르면 삭제, [주변 지도] 버튼은 그 키워드만 보기
        list.setOnItemClickListener { _, _, position, _ -> toggle(keywords[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(position)
            true
        }

        // 체크한 키워드를 한 지도에 함께 표시
        multiButton = findViewById(R.id.multiButton)
        multiButton.setOnClickListener { openSelectedMap() }
        updateMultiButton()

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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class KeywordAdapter : ArrayAdapter<String>(
        this@NearbyActivity, R.layout.item_keyword, R.id.keywordText, keywords
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val keyword = keywords[position]
            view.findViewById<View>(R.id.mapButton).setOnClickListener {
                openNearbyMap(arrayOf(keyword))
            }

            val check = view.findViewById<CheckBox>(R.id.selectCheck)
            check.setOnCheckedChangeListener(null)
            check.isChecked = selected.contains(keyword)
            check.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    if (selected.size >= MAX_SELECTED) {
                        buttonView.isChecked = false
                        Toast.makeText(this@NearbyActivity, R.string.max_selected, Toast.LENGTH_SHORT).show()
                        return@setOnCheckedChangeListener
                    }
                    selected.add(keyword)
                } else {
                    selected.remove(keyword)
                }
                updateMultiButton()
            }
            return view
        }
    }

    private fun toggle(keyword: String) {
        if (selected.contains(keyword)) {
            selected.remove(keyword)
        } else {
            if (selected.size >= MAX_SELECTED) {
                Toast.makeText(this, R.string.max_selected, Toast.LENGTH_SHORT).show()
                return
            }
            selected.add(keyword)
        }
        updateMultiButton()
        adapter.notifyDataSetChanged()
    }

    private fun openSelectedMap() {
        // 목록 순서대로 정렬해서 전달 (마커 색 순서가 항상 같도록)
        val chosen = keywords.filter { it in selected }.toTypedArray()
        if (chosen.isNotEmpty()) openNearbyMap(chosen)
    }

    private fun updateMultiButton() {
        multiButton.isEnabled = selected.isNotEmpty()
        multiButton.text = getString(R.string.select_button, selected.size)
    }

    private fun openNearbyMap(chosen: Array<String>) {
        val intent = Intent(this, MapActivity::class.java)
            .putExtra(MapActivity.EXTRA_KEYWORDS, chosen)
            .putExtra(MapActivity.EXTRA_RADIUS, prefs.getInt(Prefs.KEY_RADIUS, DEFAULT_RADIUS))
            .putExtra(MapActivity.EXTRA_MODE, Prefs.currentMode(this))
        startActivity(intent)
    }

    private fun addKeyword() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (keywords.contains(text)) {
            Toast.makeText(this, R.string.already_exists, Toast.LENGTH_SHORT).show()
            return
        }
        keywords.add(text)
        Prefs.saveKeywords(prefs, Prefs.KEYWORDS_NEARBY, keywords)
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
                selected.remove(target)
                updateMultiButton()
                Prefs.saveKeywords(prefs, Prefs.KEYWORDS_NEARBY, keywords)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val DEFAULT_RADIUS = 500
        private const val MAX_SELECTED = 6

        private val RADIUS_VALUES = intArrayOf(200, 300, 500, 1000, 2000, 3000)
        private val RADIUS_LABELS = arrayOf("200m", "300m", "500m", "1km", "2km", "3km")
    }
}
