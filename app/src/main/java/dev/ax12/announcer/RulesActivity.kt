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
    private var checks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)
        findViewById<TextView>(R.id.rulesTitle).text = tr("Rules as text - one per line:   ch5 >= 1500 -> drone armed | drone disarmed")
        findViewById<TextView>(R.id.rulesHint).text = tr("And / or allowed.  ! = urgent.  @10 = repeat every 10 s.  Settings: hysteresis = 25, hold = 100, initial = on/off.  # = comment.")
        findViewById<Button>(R.id.btnCheck).text = tr("Check")
        findViewById<Button>(R.id.btnSave).text = tr("Save")
        findViewById<Button>(R.id.btnSample).text = tr("Sample")
        findViewById<Button>(R.id.btnClose).text = tr("Close")
        edit = findViewById(R.id.rulesEdit)
        info = findViewById(R.id.rulesInfo)
        warnings = intent.getBooleanExtra("warnings", false)
        checks = intent.getBooleanExtra("checks", false)
        edit.setText(if (checks) Prefs.checkRules(this) else if (warnings) Prefs.warningRules(this) else Prefs.rules(this))

        findViewById<Button>(R.id.btnCheck).setOnClickListener { check() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (check()) {
                val t = edit.text.toString()
                if (checks) Prefs.setCheckRules(this, t) else if (warnings) Prefs.setWarningRules(this, t) else Prefs.setRules(this, t)
                finish()
            }
        }
        findViewById<Button>(R.id.btnSample).setOnClickListener {
            edit.setText(if (checks) DefaultChecks.text() else if (warnings) DefaultWarnings.text() else DefaultRules.text(this))
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
            info.text = tr("OK: {0} rules", result.rules.size)
            true
        } else {
            info.setTextColor(0xFFFF8A80.toInt())
            info.text = result.errors.joinToString("\n")
            false
        }
    }
}
