package dev.ax12.announcer

import android.view.View
import android.widget.TextView

/**
 * The warning status bar: shows every active warning (see WarningModel) in one line, coloured by the worst one. It does not
 * rely on colour alone: each level has its own shape in front of the text (a triangle for critical, a diamond for warning, a circle
 * for info). Critical warnings breathe slowly (the bar fades between 70 % and full) instead of blinking. The warning rules are
 * edited in the same way as the sound rules.
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
    fun load(rulesText: String, macros: Map<String, Double>) {
        val parsed = RuleParser.parse(rulesText, macros)
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
        m.setNow(nowMs)
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
            val background: Int
            val foreground: Int
            val shape: String
            when (worst) {
                2 -> { background = 0xFFC62828.toInt(); foreground = 0xFFFFFFFF.toInt(); shape = "warn_critical" }
                1 -> { background = 0xFFFFA000.toInt(); foreground = 0xFF000000.toInt(); shape = "warn_warning" }
                else -> { background = 0xFF1565C0.toInt(); foreground = 0xFFFFFFFF.toInt(); shape = "about" }
            }
            bar.setBackgroundColor(background)
            bar.setTextColor(foreground)
            bar.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.drawable(bar.context, shape, foreground, 28), null, null, null)
            bar.compoundDrawablePadding = (12 * bar.resources.displayMetrics.density).toInt()
        }
        // a critical warning breathes slowly: 70 % to full and back every 1.8 seconds
        bar.alpha = if (worst == 2) (0.85 + 0.15 * Math.cos(2 * Math.PI * (nowMs % 1800L) / 1800.0)).toFloat() else 1f
        bar.visibility = View.VISIBLE
    }
}
