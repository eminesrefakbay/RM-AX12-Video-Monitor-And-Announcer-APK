package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** The newest values from the radio, for the editor's "now" read-out and switch detection. */
interface LiveValues {
    fun numbers(): Map<String, Double>
    fun texts(): Map<String, String>
}

/**
 * Sound rules as a list you can tap: switch rules on and off, edit, test, add, delete.
 * It edits the same rules text as the text editor (see RuleText), so both always agree.
 * It lives inside the main screen so the radio link keeps running and live values are available.
 */
class RulesPanel(
    private val activity: Activity,
    private val speaker: Speaker,
    private val live: LiveValues,
    private val onChanged: () -> Unit,
    private val openTextEditor: () -> Unit
) {
    val root = LinearLayout(activity)
    private val list = LinearLayout(activity)
    private var items: MutableList<RuleText.Item> = ArrayList()
    private val ui = Handler(Looper.getMainLooper())

    val isShowing: Boolean get() = root.visibility == View.VISIBLE

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xF01E1E1E.toInt())
        root.setPadding(dp(16), dp(10), dp(16), dp(10))
        root.visibility = View.GONE
        root.isClickable = true                       // swallow touches so the video behind is not used

        val title = TextView(activity)
        title.text = "Sound rules"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(button("+ Add rule") { editRule(null) }, weight())
        bar.addView(button("Settings") { editSettings() }, weight())
        bar.addView(button("Text editor") { openTextEditor() }, weight())
        bar.addView(button("Close") { hide() }, weight())
        root.addView(bar)

        val scroll = ScrollView(activity)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tip = TextView(activity)
        tip.text = "A rule speaks when its condition becomes true. The switch on the left turns a rule off without deleting it."
        tip.textSize = 12f
        tip.setTextColor(0xFFBDBDBD.toInt())
        root.addView(tip)
    }

    fun show() {
        items = RuleText.parse(Prefs.rules(activity)).toMutableList()
        refresh()
        root.visibility = View.VISIBLE
    }

    fun hide() {
        root.visibility = View.GONE
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(activity)
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun save() {
        Prefs.setRules(activity, RuleText.join(items))
        onChanged()
    }

    private fun testSpeak(text: String, urgent: Boolean) {
        if (speaker.muted) Toast.makeText(activity, "Speech is muted (MENU, Mute speech)", Toast.LENGTH_SHORT).show()
        else speaker.say(text, urgent)
    }

    // ------------------------------------------------------------------ the list

    private fun refresh() {
        list.removeAllViews()
        var count = 0
        for (item in items) {
            if (item.kind != RuleText.Kind.RULE && item.kind != RuleText.Kind.DISABLED_RULE) continue
            count++
            list.addView(row(item))
        }
        if (count == 0) {
            val empty = TextView(activity)
            empty.text = "No rules yet. Tap + Add rule."
            empty.setTextColor(Color.WHITE)
            empty.setPadding(0, dp(16), 0, 0)
            list.addView(empty)
        }
    }

    private fun row(item: RuleText.Item): View {
        val r = LinearLayout(activity)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(6), 0, dp(6))

        val summary = TextView(activity)
        val spec = item.spec
        summary.text = if (spec != null) RuleText.describe(spec)
        else "Advanced rule (open the text editor): " + item.raw.removePrefix("#off").trim()
        summary.textSize = 15f
        summary.setTextColor(Color.WHITE)
        summary.alpha = if (item.kind == RuleText.Kind.RULE) 1f else 0.4f

        val sw = Switch(activity)
        sw.isChecked = item.kind == RuleText.Kind.RULE
        sw.setOnCheckedChangeListener { _, checked ->
            RuleText.setEnabled(item, checked)
            summary.alpha = if (checked) 1f else 0.4f
            save()
        }

        r.addView(sw)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(10)
        lp.rightMargin = dp(10)
        r.addView(summary, lp)
        r.addView(button("Edit") { if (item.spec != null) editRule(item) else openTextEditor() })
        r.addView(button("Test") { if (item.spec != null) testSpeak(item.spec!!.onTrue, item.spec!!.urgentTrue) })
        r.addView(button("X") {
            AlertDialog.Builder(activity)
                .setMessage("Delete this rule?")
                .setPositiveButton("Delete") { _, _ ->
                    items.remove(item)
                    save()
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        return r
    }

    // ------------------------------------------------------------------ settings

    private fun label(text: String): TextView {
        val t = TextView(activity)
        t.text = text
        t.setPadding(0, dp(8), 0, 0)
        return t
    }

    private fun numberField(value: String): EditText {
        val e = EditText(activity)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        e.setText(value)
        return e
    }

    private fun editSettings() {
        val body = LinearLayout(activity)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))

        body.addView(label("Channel hysteresis, microseconds (stops chatter near a switch threshold)"))
        val hys = numberField(RuleText.getSetting(items, "hysteresis", "25"))
        body.addView(hys)
        body.addView(label("Voltage and link hysteresis, percent of the threshold"))
        val vhys = numberField(RuleText.getSetting(items, "var_hysteresis", "1"))
        body.addView(vhys)
        body.addView(label("Hold time, milliseconds (ignore changes shorter than this)"))
        val hold = numberField(RuleText.getSetting(items, "hold", "100"))
        body.addView(hold)
        val initial = CheckBox(activity)
        initial.text = "Speak rules that are already true at start-up"
        initial.isChecked = RuleText.getSetting(items, "initial", "off").equals("on", ignoreCase = true)
        body.addView(initial)
        body.addView(label("Unit of the radio's channel numbers (auto is fine)"))
        val scales = arrayOf("auto", "percent", "permille", "us")
        val scale = Spinner(activity)
        val ad = ArrayAdapter(activity, android.R.layout.simple_spinner_item, scales)
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scale.adapter = ad
        scale.setSelection(maxOf(0, scales.indexOf(RuleText.getSetting(items, "scale", "auto").lowercase())))
        body.addView(scale)

        val scroll = ScrollView(activity)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(activity)
            .setTitle("Rule settings")
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val h = hys.text.toString().trim().toDoubleOrNull()
            val v = vhys.text.toString().trim().toDoubleOrNull()
            val ms = hold.text.toString().trim().toLongOrNull()
            if (h == null || h < 0 || v == null || v < 0 || ms == null || ms < 0) {
                Toast.makeText(activity, "Enter numbers (0 or more)", Toast.LENGTH_SHORT).show()
            } else {
                RuleText.setSetting(items, "hysteresis", RuleText.fmt(h))
                RuleText.setSetting(items, "var_hysteresis", RuleText.fmt(v))
                RuleText.setSetting(items, "hold", ms.toString())
                RuleText.setSetting(items, "initial", if (initial.isChecked) "on" else "off")
                RuleText.setSetting(items, "scale", scales[scale.selectedItemPosition])
                save()
                dlg.dismiss()
            }
        }
    }

    // ------------------------------------------------------------------ the rule editor

    private fun editRule(existing: RuleText.Item?) {
        RuleEditor(existing).show()
    }

    private fun watcher(f: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { f() }
    }

    private fun spinnerWatcher(f: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { f() }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun isTextVariable(name: String): Boolean = name == "fm" || live.texts().containsKey(name)

    private inner class RuleEditor(private val existing: RuleText.Item?) {
        private val spec: RuleText.Spec = existing?.spec?.copy() ?: RuleText.Spec.blank()
        private val body = LinearLayout(activity)
        private val condBox = LinearLayout(activity)
        private val rows = ArrayList<CondRow>()
        private val opCodes = arrayOf(">=", "<=", ">", "<", "==", "!=", "~")
        private val opLabels = arrayOf("\u2265 at least", "\u2264 at most", "> above", "< below", "= equals", "\u2260 not equal", "contains (text)")

        private lateinit var connector: Spinner
        private lateinit var sayField: EditText
        private lateinit var urgent: CheckBox
        private lateinit var falseCheck: CheckBox
        private lateinit var falseField: EditText
        private lateinit var repeatCheck: CheckBox
        private lateinit var repeatField: EditText
        private lateinit var preview: TextView
        private lateinit var errorView: TextView
        private lateinit var detectInfo: TextView
        private var dialog: AlertDialog? = null
        private var building = true
        private var detectBaseline: Map<String, Double>? = null
        private var detectUntil = 0L

        private inner class CondRow(c: RuleText.Cond) {
            val view = LinearLayout(activity)
            val varField = AutoCompleteTextView(activity)
            val opSpinner = Spinner(activity)
            val valueField = EditText(activity)
            val divField = EditText(activity)
            val nowText = TextView(activity)
            private var numericKeyboard = true

            init {
                view.orientation = LinearLayout.VERTICAL
                view.setPadding(0, dp(6), 0, dp(6))

                val top = LinearLayout(activity)
                top.orientation = LinearLayout.HORIZONTAL
                top.gravity = Gravity.CENTER_VERTICAL

                val names = VariableCatalog.names(live.numbers().keys + live.texts().keys)
                varField.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, names))
                varField.threshold = 0
                varField.setSingleLine(true)
                varField.hint = "variable"
                varField.setText(c.variable)
                varField.setOnClickListener { varField.showDropDown() }
                varField.setOnFocusChangeListener { _, has -> if (has) varField.showDropDown() }
                top.addView(varField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val ops = ArrayAdapter(activity, android.R.layout.simple_spinner_item, opLabels)
                ops.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                opSpinner.adapter = ops
                opSpinner.setSelection(maxOf(0, opCodes.indexOf(c.op)))
                top.addView(opSpinner)

                valueField.setSingleLine(true)
                valueField.hint = "value"
                valueField.setText(c.value)
                top.addView(valueField, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))

                val remove = Button(activity)
                remove.text = "X"
                remove.setOnClickListener { removeRow(this@CondRow) }
                top.addView(remove)
                view.addView(top)

                val second = LinearLayout(activity)
                second.orientation = LinearLayout.HORIZONTAL
                second.gravity = Gravity.CENTER_VERTICAL
                val divLabel = TextView(activity)
                divLabel.text = "divide by"
                divLabel.textSize = 12f
                second.addView(divLabel)
                divField.setSingleLine(true)
                divField.hint = "1"
                divField.textSize = 12f
                divField.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (c.divisor != 1.0) divField.setText(RuleText.fmt(c.divisor))
                second.addView(divField, LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT))
                nowText.textSize = 12f
                nowText.setTextColor(0xFF9CDCFE.toInt())
                nowText.setPadding(dp(10), 0, 0, 0)
                second.addView(nowText)
                view.addView(second)

                updateKeyboard()
                varField.addTextChangedListener(watcher { updateKeyboard(); refreshPreview() })
                valueField.addTextChangedListener(watcher { refreshPreview() })
                divField.addTextChangedListener(watcher { refreshPreview() })
                opSpinner.onItemSelectedListener = spinnerWatcher { refreshPreview() }
            }

            private fun updateKeyboard() {
                val numeric = !isTextVariable(varField.text.toString().trim())
                if (numeric != numericKeyboard) {
                    numericKeyboard = numeric
                    valueField.inputType = if (numeric)
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    else InputType.TYPE_CLASS_TEXT
                }
            }

            fun toCond(): RuleText.Cond {
                val v = varField.text.toString().trim()
                val op = opCodes[opSpinner.selectedItemPosition]
                val textMode = isTextVariable(v) || op == "~"
                val div = divField.text.toString().trim().toDoubleOrNull() ?: 1.0
                return RuleText.Cond(v, op, valueField.text.toString().trim(), if (textMode) 1.0 else div, textMode)
            }

            fun updateNow(nums: Map<String, Double>, txts: Map<String, String>) {
                val v = varField.text.toString().trim()
                val help = VariableCatalog.help(v)
                val t = txts[v]
                val n = nums[v]
                val shown = if (t != null) "\u201C" + t + "\u201D"
                else if (n != null) {
                    val d = divField.text.toString().trim().toDoubleOrNull() ?: 1.0
                    RuleText.fmt(Math.round(n / (if (d == 0.0) 1.0 else d) * 100.0) / 100.0)
                } else "-"
                nowText.text = "now: " + shown + (if (help.isNotEmpty()) "   (" + help + ")" else "")
            }
        }

        private fun addRow(c: RuleText.Cond) {
            val row = CondRow(c)
            rows.add(row)
            condBox.addView(row.view)
            updateConnector()
        }

        private fun removeRow(row: CondRow) {
            if (rows.size <= 1) {
                row.varField.setText("")
                row.valueField.setText("")
                row.divField.setText("")
                return
            }
            rows.remove(row)
            condBox.removeView(row.view)
            updateConnector()
            refreshPreview()
        }

        private fun updateConnector() {
            if (::connector.isInitialized) connector.visibility = if (rows.size > 1) View.VISIBLE else View.GONE
        }

        private fun readSpec(): RuleText.Spec {
            val s = RuleText.Spec()
            for (r in rows) s.conds.add(r.toCond())
            s.any = connector.selectedItemPosition == 1
            s.onTrue = sayField.text.toString()
            s.urgentTrue = urgent.isChecked
            s.onFalse = if (falseCheck.isChecked) falseField.text.toString() else ""
            s.urgentFalse = spec.urgentFalse
            s.repeatSec = if (repeatCheck.isChecked) (repeatField.text.toString().trim().toIntOrNull() ?: 0) else 0
            return s
        }

        private fun refreshPreview() {
            if (building) return
            val s = readSpec()
            var err = RuleText.validate(s)
            if (err == null && repeatCheck.isChecked && s.repeatSec < 2) err = "Repeat needs a number of seconds, 2 or more."
            errorView.text = err ?: ""
            preview.text = if (err == null) RuleText.line(s) else "-"
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = (err == null)
        }

        private val tick = object : Runnable {
            override fun run() {
                if (dialog?.isShowing != true) return
                val nums = live.numbers()
                val txts = live.texts()
                for (r in rows) r.updateNow(nums, txts)
                detectStep(nums)
                ui.postDelayed(this, 250)
            }
        }

        private fun startDetect() {
            val base = HashMap<String, Double>()
            for (i in 1..16) live.numbers()["ch$i"]?.let { base["ch$i"] = it }
            if (base.isEmpty()) {
                detectInfo.text = "No channel values yet (is the radio connected? try MENU, Show variables)"
                return
            }
            detectBaseline = base
            detectUntil = SystemClock.elapsedRealtime() + 15000
            detectInfo.text = "Move the switch now..."
        }

        private fun detectStep(nums: Map<String, Double>) {
            val base = detectBaseline ?: return
            if (SystemClock.elapsedRealtime() > detectUntil) {
                detectBaseline = null
                detectInfo.text = "No switch moved."
                return
            }
            for (i in 1..16) {
                val key = "ch$i"
                val now = nums[key] ?: continue
                val was = base[key] ?: continue
                if (Math.abs(now - was) >= 300) {
                    detectBaseline = null
                    applyDetected(key, now)
                    detectInfo.text = "Detected $key at " + now.toInt()
                    return
                }
            }
        }

        private fun applyDetected(key: String, value: Double) {
            val r50 = (Math.round(value / 50.0) * 50).toInt()
            building = true
            rows.clear()
            condBox.removeAllViews()
            if (value >= 1700) {
                addRow(RuleText.Cond(key, ">=", (r50 - 50).toString(), 1.0, false))
            } else if (value <= 1300) {
                addRow(RuleText.Cond(key, "<=", (r50 + 50).toString(), 1.0, false))
            } else {
                addRow(RuleText.Cond(key, ">=", (r50 - 50).toString(), 1.0, false))
                addRow(RuleText.Cond(key, "<=", (r50 + 50).toString(), 1.0, false))
                connector.setSelection(0)
            }
            building = false
            refreshPreview()
        }

        fun show() {
            body.orientation = LinearLayout.VERTICAL
            body.setPadding(dp(16), dp(8), dp(16), dp(8))
            condBox.orientation = LinearLayout.VERTICAL

            // when ...
            val whenRow = LinearLayout(activity)
            whenRow.orientation = LinearLayout.HORIZONTAL
            whenRow.gravity = Gravity.CENTER_VERTICAL
            val whenLabel = TextView(activity)
            whenLabel.text = "When"
            whenLabel.textSize = 16f
            whenRow.addView(whenLabel)
            connector = Spinner(activity)
            val cad = ArrayAdapter(activity, android.R.layout.simple_spinner_item, arrayOf("all of these are true", "any of these is true"))
            cad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            connector.adapter = cad
            connector.setSelection(if (spec.any) 1 else 0)
            connector.onItemSelectedListener = spinnerWatcher { refreshPreview() }
            whenRow.addView(connector)
            body.addView(whenRow)
            body.addView(condBox)
            for (c in spec.conds) addRow(c)
            updateConnector()

            val tools = LinearLayout(activity)
            tools.orientation = LinearLayout.HORIZONTAL
            tools.gravity = Gravity.CENTER_VERTICAL
            val addCond = Button(activity)
            addCond.text = "+ condition"
            addCond.setOnClickListener { addRow(RuleText.Cond()); refreshPreview() }
            tools.addView(addCond)
            val detect = Button(activity)
            detect.text = "Detect switch"
            detect.setOnClickListener { startDetect() }
            tools.addView(detect)
            detectInfo = TextView(activity)
            detectInfo.textSize = 12f
            detectInfo.setTextColor(0xFF9CDCFE.toInt())
            tools.addView(detectInfo)
            body.addView(tools)

            // say ...
            body.addView(label("Say"))
            sayField = EditText(activity)
            sayField.setText(spec.onTrue)
            sayField.setSingleLine(true)
            sayField.hint = "words to speak"
            sayField.addTextChangedListener(watcher { refreshPreview() })
            body.addView(sayField)
            urgent = CheckBox(activity)
            urgent.text = "Urgent (interrupts what is being said)"
            urgent.isChecked = spec.urgentTrue
            urgent.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            body.addView(urgent)

            falseCheck = CheckBox(activity)
            falseCheck.text = "Also say something when it turns false"
            falseCheck.isChecked = spec.onFalse.isNotEmpty()
            falseCheck.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            body.addView(falseCheck)
            falseField = EditText(activity)
            falseField.setText(spec.onFalse)
            falseField.setSingleLine(true)
            falseField.hint = "words when it turns false"
            falseField.addTextChangedListener(watcher { if (falseField.text.isNotEmpty()) falseCheck.isChecked = true; refreshPreview() })
            body.addView(falseField)

            repeatCheck = CheckBox(activity)
            repeatCheck.text = "Repeat while it stays true, every (seconds)"
            repeatCheck.isChecked = spec.repeatSec > 0
            repeatCheck.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            body.addView(repeatCheck)
            repeatField = EditText(activity)
            repeatField.inputType = InputType.TYPE_CLASS_NUMBER
            repeatField.setText(if (spec.repeatSec > 0) spec.repeatSec.toString() else "")
            repeatField.hint = "10"
            repeatField.addTextChangedListener(watcher { if (repeatField.text.isNotEmpty()) repeatCheck.isChecked = true; refreshPreview() })
            body.addView(repeatField, LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT))

            body.addView(label("The rule as text"))
            preview = TextView(activity)
            preview.typeface = Typeface.MONOSPACE
            preview.textSize = 13f
            body.addView(preview)
            errorView = TextView(activity)
            errorView.setTextColor(0xFFFF8A80.toInt())
            body.addView(errorView)

            val scroll = ScrollView(activity)
            scroll.addView(body)
            val dlg = AlertDialog.Builder(activity)
                .setTitle(if (existing == null) "New rule" else "Edit rule")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Test", null)
                .create()
            dialog = dlg
            dlg.setOnDismissListener { ui.removeCallbacks(tick) }
            dlg.show()
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (sayField.text.isNotBlank()) testSpeak(sayField.text.toString().trim(), urgent.isChecked)
            }
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val s = readSpec()
                val err = RuleText.validate(s)
                if (err != null) {
                    errorView.text = err
                } else {
                    if (existing != null) RuleText.applySpec(existing, s) else items.add(RuleText.newRule(s))
                    save()
                    refresh()
                    dlg.dismiss()
                }
            }
            building = false
            refreshPreview()
            ui.post(tick)
        }
    }
}
