package com.example.mapkeywords

import android.content.SharedPreferences
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

/** 길찾기 화면: 이동수단을 고르고, 등록한 키워드를 누르면 구글맵 길찾기를 실행한다. */
class DirectionsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var input: EditText
    private val keywords = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_directions)
        title = getString(R.string.title_directions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = Prefs.of(this)
        keywords.addAll(Prefs.loadKeywords(prefs, Prefs.KEYWORDS_DIRECTIONS))

        // 이동수단: 마지막으로 선택한 값을 저장해 두었다가 다음 실행 때도 그대로 사용
        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        modeGroup.check(idForMode(Prefs.currentMode(this)))
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putString(Prefs.KEY_MODE, modeForId(checkedId)).apply()
        }

        input = findViewById(R.id.keywordInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val list = findViewById<ListView>(R.id.keywordList)
        list.emptyView = findViewById<TextView>(R.id.emptyView)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, keywords)
        list.adapter = adapter

        // 짧게 누르면 길찾기 실행, 길게 누르면 삭제
        list.setOnItemClickListener { _, _, position, _ ->
            Directions.open(this, keywords[position], Prefs.currentMode(this))
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun addKeyword() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (keywords.contains(text)) {
            Toast.makeText(this, R.string.already_exists, Toast.LENGTH_SHORT).show()
            return
        }
        keywords.add(text)
        Prefs.saveKeywords(prefs, Prefs.KEYWORDS_DIRECTIONS, keywords)
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
                Prefs.saveKeywords(prefs, Prefs.KEYWORDS_DIRECTIONS, keywords)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun idForMode(mode: String): Int = when (mode) {
        Prefs.MODE_TRANSIT -> R.id.modeTransit
        Prefs.MODE_WALKING -> R.id.modeWalking
        Prefs.MODE_BICYCLING -> R.id.modeBicycling
        else -> R.id.modeDriving
    }

    private fun modeForId(id: Int): String = when (id) {
        R.id.modeTransit -> Prefs.MODE_TRANSIT
        R.id.modeWalking -> Prefs.MODE_WALKING
        R.id.modeBicycling -> Prefs.MODE_BICYCLING
        else -> Prefs.MODE_DRIVING
    }
}
