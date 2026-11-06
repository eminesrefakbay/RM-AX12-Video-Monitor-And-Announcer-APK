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
    private var warnings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)
        edit = findViewById(R.id.rulesEdit)
        info = findViewById(R.id.rulesInfo)
        warnings = intent.getBooleanExtra("warnings", false)
        edit.setText(if (warnings) Prefs.warningRules(this) else Prefs.rules(this))

        findViewById<Button>(R.id.btnCheck).setOnClickListener { check() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (check()) {
                if (warnings) Prefs.setWarningRules(this, edit.text.toString()) else Prefs.setRules(this, edit.text.toString())
                finish()
            }
        }
        findViewById<Button>(R.id.btnSample).setOnClickListener {
            edit.setText(if (warnings) DefaultWarnings.TEXT else DefaultRules.TEXT)
            check()
        }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(this)
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
