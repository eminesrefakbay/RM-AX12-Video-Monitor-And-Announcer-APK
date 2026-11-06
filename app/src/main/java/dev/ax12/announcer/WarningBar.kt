package dev.ax12.announcer

import android.view.View
import android.widget.TextView

/**
 * The warning status bar: shows every active warning (see WarningModel) in one line, coloured by the
 * worst one. Critical warnings blink. The warning rules are edited in the same way as the sound rules.
 */
class WarningBar(private val bar: TextView) {

    private var engine: RuleEngine? = null
    private var model: WarningModel? = null
    var enabled: Boolean = true
    private var testText: String? = null
    private var testLevel = 1
    private var testUntil = 0L
    private var lastShown = ""
    private var lastLevel = -1

    /** (Re)load the warning rules. The bar starts with the current state of every rule. */
    fun load(rulesText: String) {
        val parsed = RuleParser.parse(rulesText)
        val m = WarningModel(parsed.rules)
        model = m
        engine = RuleEngine(parsed, null, m)
        lastShown = ""
        lastLevel = -1
    }

    /** Show a sample line for a few seconds (the Test button and the preview). */
    fun showTest(text: String, level: Int, nowMs: Long, forMs: Long = 4000) {
        testText = text
        testLevel = level
        testUntil = nowMs + forMs
    }

    fun update(numbers: Map<String, Double>, texts: Map<String, String>, nowMs: Long) {
        val m = model
        val e = engine
        if (!enabled || m == null || e == null) {
            bar.visibility = View.GONE
            return
        }
        e.update(numbers, texts, nowMs)
        val items = m.items(numbers, texts)
        val parts = ArrayList<String>()
        var worst = -1
        for (it in items) {
            parts.add(it.text)
            if (it.level.ordinal > worst) worst = it.level.ordinal
        }
        val t = testText
        if (t != null) {
            if (nowMs < testUntil) {
                parts.add(t)
                if (testLevel > worst) worst = testLevel
            } else {
                testText = null
            }
        }
        if (parts.isEmpty()) {
            bar.visibility = View.GONE
            lastShown = ""
            return
        }
        val joined = parts.joinToString("   \u00B7   ")
        if (joined != lastShown) {
            bar.text = joined
            lastShown = joined
        }
        if (worst != lastLevel) {
            lastLevel = worst
            when (worst) {
                2 -> { bar.setBackgroundColor(0xFFC62828.toInt()); bar.setTextColor(0xFFFFFFFF.toInt()) }
                1 -> { bar.setBackgroundColor(0xFFFFA000.toInt()); bar.setTextColor(0xFF000000.toInt()) }
                else -> { bar.setBackgroundColor(0xFF1565C0.toInt()); bar.setTextColor(0xFFFFFFFF.toInt()) }
            }
        }
        // critical warnings blink slowly
        bar.alpha = if (worst == 2 && (nowMs / 500) % 2L == 1L) 0.45f else 1f
        bar.visibility = View.VISIBLE
    }
}
