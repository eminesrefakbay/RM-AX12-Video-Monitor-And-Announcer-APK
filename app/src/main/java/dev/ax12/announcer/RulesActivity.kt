package dev.ax12.announcer

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Plain-text editor for the sound rules. */
class RulesActivity : Activity() {

    private lateinit var edit: EditText
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)
        edit = findViewById(R.id.rulesEdit)
        info = findViewById(R.id.rulesInfo)
        edit.setText(Prefs.rules(this))

        findViewById<Button>(R.id.btnCheck).setOnClickListener { check() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (check()) {
                Prefs.setRules(this, edit.text.toString())
                finish()
            }
        }
        findViewById<Button>(R.id.btnSample).setOnClickListener {
            edit.setText(DefaultRules.TEXT)
            check()
        }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }

    private fun check(): Boolean {
        val result = RuleParser.parse(edit.text.toString())
        return if (result.errors.isEmpty()) {
            info.setTextColor(0xFF9CDCFE.toInt())
            info.text = "OK: " + result.rules.size + " rules"
            true
        } else {
            info.setTextColor(0xFFFF8A80.toInt())
            info.text = result.errors.joinToString("\n")
            false
        }
    }
}
