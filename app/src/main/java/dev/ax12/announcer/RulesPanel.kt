// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

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
import android.widget.BaseAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
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
    private val openTextEditor: () -> Unit,
    private val loadText: () -> String,
    private val saveText: (String) -> Unit,
    private val warning: Boolean = false,
    private val titleText: String = tr("Sound rules"),
    private val testWarning: ((String, Int) -> Unit)? = null,
    private val check: Boolean = false
) {
    val root = LinearLayout(activity)
    private val list = LinearLayout(activity)
    private var items: MutableList<RuleText.Item> = ArrayList()
    private val ui = Handler(Looper.getMainLooper())

    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    private var titleLabel: TextView? = null
    private var tipView: TextView? = null

    /** The colours of the frame of the screen, set again each time it opens, because the theme may have changed. */
    private fun skin() {
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))
        titleLabel?.setTextColor(Sunlight.c(Color.WHITE))
        titleLabel?.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        list.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        tipView?.setTextColor(Sunlight.c(0xFFBDBDBD.toInt()))
        tipView?.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Sunlight.c(CardStyle.SEE_THROUGH))         // the video stays visible behind the rules
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true                       // swallow touches so the video behind is not used
        root.elevation = dp(24).toFloat()             // above the MENU button and every other view

        val title = TextView(activity)
        titleLabel = title
        title.text = titleText
        title.textSize = 22f
        title.setTextColor(Sunlight.c(Color.WHITE))
        title.setTypeface(null, Typeface.BOLD)
        title.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        title.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.addView(title)

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(button(tr("+ Add rule")) { editRule(null) }, weight())
        bar.addView(button(tr("Settings")) { editSettings() }, weight())
        bar.addView(button(tr("Text editor")) { openTextEditor() }, weight())
        bar.addView(button(tr("Close")) { hide() }, weight())
        root.addView(bar)

        val scroll = ScrollView(activity)
        list.orientation = LinearLayout.VERTICAL
        list.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tip = TextView(activity)
        tipView = tip
        tip.text = if (check)
            tr("Each check shows a tick when its condition is true and a cross when not, before you arm. The switch on the left turns a check off without deleting it.")
        else if (warning)
            tr("A warning shows in the status bar while its condition is true. Critical = red and blinking, warning = amber, info = blue. The switch on the left turns a rule off without deleting it.")
        else
            tr("A rule speaks when its condition becomes true. The switch on the left turns a rule off without deleting it.")
        tip.textSize = 12f
        tip.setTextColor(Sunlight.c(0xFFBDBDBD.toInt()))
        tip.setBackgroundColor(Sunlight.c(CardStyle.BLOCK))
        tip.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.addView(tip)
    }

    fun show() {
        skin()
        items = RuleText.parse(loadText()).toMutableList()
        refresh()
        shown = true
        Anim.panelIn(root)
    }

    fun hide() {
        shown = false
        Anim.panelOut(root)
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(activity)
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun save() {
        saveText(RuleText.join(items))
        onChanged()
    }

    private fun testSpeak(text: String, urgent: Boolean) {
        if (speaker.muted) Toasts.make(activity, tr("Speech is muted (MENU, Speech)"), Toast.LENGTH_SHORT).show()
        else speaker.say(text, urgent)
    }

    /** The limit names low, crit and full (written with a dollar sign): the limits of the active battery profile, to use in a condition instead of a number. */
    private fun pickLimit(onPicked: (String) -> Unit) {
        val names = arrayOf("\$low", "\$crit", "\$full")
        val labels = arrayOf(
            tr("\$low  - warning limit of the active battery profile"),
            tr("\$crit  - critical limit"),
            tr("\$full  - limit for a full pack")
        )
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Battery limit"))
            .setItems(labels) { _, which -> onPicked(names[which]) }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }

    private fun testWarningFor(sp: RuleText.Spec) {
        val level = if (sp.urgentTrue) 2 else if (sp.onTrue.startsWith("*")) 0 else 1
        val text = TextTemplate.expand(sp.onTrue.removePrefix("*").trim(), live.numbers(), live.texts())
        testWarning?.invoke(text, level)
    }

    // ------------------------------------------------------------------ the list

    private fun refresh() {
        refreshRows()
        Look.apply(root)
    }

    private fun refreshRows() {
        list.removeAllViews()
        var count = 0
        for (item in items) {
            if (item.kind != RuleText.Kind.RULE && item.kind != RuleText.Kind.DISABLED_RULE) continue
            count++
            list.addView(row(item))
        }
        if (count == 0) {
            val empty = TextView(activity)
            empty.text = tr("No rules yet. Tap + Add rule.")
            empty.setTextColor(Sunlight.c(Color.WHITE))
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
        summary.text = if (spec != null) (if (check) RuleText.describeCheck(spec) else if (warning) RuleText.describeWarning(spec) else RuleText.describe(spec))
        else tr("Advanced rule (open the text editor): {0}", item.raw.removePrefix("#off").trim())
        summary.textSize = 15f
        summary.setTextColor(Sunlight.c(Color.WHITE))
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
        r.addView(button(tr("Edit")) { if (item.spec != null) editRule(item) else openTextEditor() })
        if (!check) r.addView(button(tr("Test")) {
            val sp = item.spec
            if (sp != null) {
                if (warning) testWarningFor(sp) else testSpeak(sp.onTrue, sp.urgentTrue)
            }
        })
        r.addView(button("X") {
            Immersive.show(
                Sunlight.builder(activity)
                    .setMessage(tr("Delete this rule?"))
                    .setPositiveButton(tr("Delete")) { _, _ ->
                        items.remove(item)
                        save()
                        refresh()
                    }
                    .setNegativeButton(tr("Cancel"), null)
                    .create()
            )
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

        body.addView(label(tr("Channel hysteresis, microseconds (stops chatter near a switch threshold)")))
        val hys = numberField(RuleText.getSetting(items, "hysteresis", "25"))
        body.addView(hys)
        body.addView(label(tr("Voltage and link hysteresis, percent of the threshold")))
        val vhys = numberField(RuleText.getSetting(items, "var_hysteresis", "1"))
        body.addView(vhys)
        body.addView(label(tr("Hold time, milliseconds (ignore changes shorter than this)")))
        val hold = numberField(RuleText.getSetting(items, "hold", "100"))
        body.addView(hold)
        val initial = CheckBox(activity)
        initial.text = tr("Speak rules that are already true at start-up")
        initial.isChecked = RuleText.getSetting(items, "initial", "off").equals("on", ignoreCase = true)
        if (!warning && !check) body.addView(initial)
        body.addView(label(tr("Unit of the radio's channel numbers (auto is fine)")))
        val scales = arrayOf("auto", "percent", "permille", "us")
        val scale = Spinner(activity)
        val ad = ArrayAdapter(activity, android.R.layout.simple_spinner_item, scales)
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scale.adapter = ad
        scale.setSelection(maxOf(0, scales.indexOf(RuleText.getSetting(items, "scale", "auto").lowercase())))
        body.addView(scale)

        val scroll = ScrollView(activity)
        scroll.addView(body)
        val dlg = Sunlight.builder(activity)
            .setTitle(tr("Rule settings"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val h = hys.text.toString().trim().toDoubleOrNull()
            val v = vhys.text.toString().trim().toDoubleOrNull()
            val ms = hold.text.toString().trim().toLongOrNull()
            if (h == null || h < 0 || v == null || v < 0 || ms == null || ms < 0) {
                Toasts.make(activity, tr("Enter numbers (0 or more)"), Toast.LENGTH_SHORT).show()
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

    // ------------------------------------------------------------------ variable picker

    private fun isTextVariable(name: String): Boolean = name == "fm" || live.texts().containsKey(name)

    private inner class RuleEditor(private val existing: RuleText.Item?) {
        private val spec: RuleText.Spec = existing?.spec?.copy() ?: RuleText.Spec.blank()
        private val body = LinearLayout(activity)
        private val condBox = LinearLayout(activity)
        private val rows = ArrayList<CondRow>()
        private val opCodes = arrayOf(">=", "<=", ">", "<", "==", "!=", "~")
        private val opLabels = arrayOf(tr("\u2265 at least"), tr("\u2264 at most"), tr("> above"), tr("< below"), tr("= equals"), tr("\u2260 not equal"), tr("Contains (text)"))

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
        private var severityButton: Button? = null
        private var severity: Int = if (spec.urgentTrue) 2 else if (spec.onTrue.startsWith("*")) 0 else 1
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

                val lineA = LinearLayout(activity)
                lineA.orientation = LinearLayout.HORIZONTAL
                lineA.gravity = Gravity.CENTER_VERTICAL

                val names = VariableCatalog.names(live.numbers().keys + live.texts().keys)
                varField.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, names))
                varField.threshold = 0
                varField.setSingleLine(true)
                varField.hint = tr("Variable (type, or tap List)")
                varField.setText(c.variable)
                varField.setOnClickListener { varField.showDropDown() }
                lineA.addView(varField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val pick = Button(activity)
                pick.text = tr("List")
                pick.setOnClickListener {
                    VariablePicker.show(activity, live, false) { chosen ->
                        varField.setText(chosen)
                        varField.setSelection(chosen.length)
                    }
                }
                lineA.addView(pick)

                val remove = Button(activity)
                remove.text = "X"
                remove.setOnClickListener { removeRow(this@CondRow) }
                lineA.addView(remove)
                view.addView(lineA)

                val lineB = LinearLayout(activity)
                lineB.orientation = LinearLayout.HORIZONTAL
                lineB.gravity = Gravity.CENTER_VERTICAL

                val ops = ArrayAdapter(activity, android.R.layout.simple_spinner_item, opLabels)
                ops.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                opSpinner.adapter = ops
                opSpinner.setSelection(maxOf(0, opCodes.indexOf(c.op)))
                lineB.addView(opSpinner)

                valueField.setSingleLine(true)
                valueField.hint = tr("Value")
                valueField.setText(c.value)
                lineB.addView(valueField, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
                if (BatteryProfiles.isMacro(c.value)) valueField.inputType = InputType.TYPE_CLASS_TEXT
                val limits = Button(activity)
                limits.text = "\$"
                limits.isAllCaps = false
                limits.setOnClickListener { pickLimit { name ->
                    valueField.inputType = InputType.TYPE_CLASS_TEXT          // a name needs the text keyboard
                    valueField.setText(name)
                    valueField.setSelection(name.length)
                } }
                lineB.addView(limits)

                val divLabel = TextView(activity)
                divLabel.text = tr("  Divide by")
                divLabel.textSize = 12f
                lineB.addView(divLabel)
                divField.setSingleLine(true)
                divField.hint = "1"
                divField.textSize = 12f
                divField.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (c.divisor != 1.0) divField.setText(RuleText.fmt(c.divisor))
                lineB.addView(divField, LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT))
                view.addView(lineB)

                nowText.textSize = 12f
                nowText.setTextColor(Sunlight.c(0xFF9CDCFE.toInt()))
                view.addView(nowText)

                updateKeyboard()
                varField.addTextChangedListener(watcher { updateKeyboard(); refreshPreview() })
                valueField.addTextChangedListener(watcher { refreshPreview() })
                divField.addTextChangedListener(watcher { refreshPreview() })
                opSpinner.onItemSelectedListener = spinnerWatcher { refreshPreview() }
            }

            private fun updateKeyboard() {
                val numeric = !isTextVariable(varField.text.toString().trim()) && !BatteryProfiles.isMacro(valueField.text.toString().trim())
                if (numeric != numericKeyboard) {
                    numericKeyboard = numeric
                    val tilde = opCodes.indexOf("~")
                    if (!numeric && opSpinner.selectedItemPosition < 4) opSpinner.setSelection(tilde)
                    if (numeric && opSpinner.selectedItemPosition == tilde) opSpinner.setSelection(0)
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
                nowText.text = tr("Now: {0}{1}", shown, (if (help.isNotEmpty()) "   (" + help + ")" else ""))
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
            if (warning) {
                s.onTrue = (if (severity == 0) "*" else "") + sayField.text.toString()
                s.urgentTrue = severity == 2
                s.onFalse = ""
                s.urgentFalse = false
                s.repeatSec = if (repeatCheck.isChecked) (repeatField.text.toString().trim().toIntOrNull() ?: 0) else 0      // the timeout
            } else if (check) {
                s.onTrue = sayField.text.toString()
                s.urgentTrue = false
                s.onFalse = ""
                s.urgentFalse = false
                s.repeatSec = 0
            } else {
                s.onTrue = sayField.text.toString()
                s.urgentTrue = urgent.isChecked
                s.onFalse = if (falseCheck.isChecked) falseField.text.toString() else ""
                s.urgentFalse = spec.urgentFalse
                s.repeatSec = if (repeatCheck.isChecked) (repeatField.text.toString().trim().toIntOrNull() ?: 0) else 0
            }
            return s
        }

        private fun severityLabel(): String = when (severity) {
            0 -> tr("Level: Info (blue)  - tap to change")
            2 -> tr("Level: Critical (red, blinking)  - tap to change")
            else -> tr("Level: Warning (amber)  - tap to change")
        }

        private fun refreshPreview() {
            if (building) return
            val s = readSpec()
            var err = RuleText.validate(s)
            if (err == null && repeatCheck.isChecked && s.repeatSec < 2) err = if (warning) tr("The timeout needs a number of seconds, 2 or more.") else tr("Repeat needs a number of seconds, 2 or more.")
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
                detectInfo.text = tr("No channel values yet (is the radio connected? try Settings, Diagnostics, Show variables)")
                return
            }
            detectBaseline = base
            detectUntil = SystemClock.elapsedRealtime() + 15000
            detectInfo.text = tr("Move the switch now...")
        }

        private fun detectStep(nums: Map<String, Double>) {
            val base = detectBaseline ?: return
            if (SystemClock.elapsedRealtime() > detectUntil) {
                detectBaseline = null
                detectInfo.text = tr("No switch moved.")
                return
            }
            for (i in 1..16) {
                val key = "ch$i"
                val now = nums[key] ?: continue
                val was = base[key] ?: continue
                if (Math.abs(now - was) >= 300) {
                    detectBaseline = null
                    applyDetected(key, now)
                    detectInfo.text = tr("Detected {0} at {1}", key, now.toInt())
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
            whenLabel.text = tr("When")
            whenLabel.textSize = 16f
            whenRow.addView(whenLabel)
            connector = Spinner(activity)
            val cad = ArrayAdapter(activity, android.R.layout.simple_spinner_item, arrayOf(tr("All of these are true"), tr("Any of these is true")))
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
            addCond.text = tr("+ condition")
            addCond.setOnClickListener { addRow(RuleText.Cond()); refreshPreview() }
            tools.addView(addCond)
            val detect = Button(activity)
            detect.text = tr("Detect switch")
            detect.setOnClickListener { startDetect() }
            tools.addView(detect)
            detectInfo = TextView(activity)
            detectInfo.textSize = 12f
            detectInfo.setTextColor(Sunlight.c(0xFF9CDCFE.toInt()))
            tools.addView(detectInfo)
            body.addView(tools)

            // say ...  (or: show in the status bar)
            body.addView(label(if (check) tr("Check label (shown with a tick or a cross)") else if (warning) tr("Show in the status bar") else tr("Say")))
            sayField = EditText(activity)
            sayField.setText(if (warning) spec.onTrue.removePrefix("*").trim() else spec.onTrue)
            sayField.setSingleLine(true)
            sayField.hint = if (check) tr("Label, for example GPS {sats} satellites") else if (warning) tr("Text, for example BATTERY {vbat/6:2} V") else tr("Words to speak")
            sayField.addTextChangedListener(watcher { refreshPreview() })
            body.addView(sayField)
            val tipLive = TextView(activity)
            tipLive.text = tr("Live values: {vbat/6:2} = volts per cell with 2 decimals, {up_lq}, {sats}, {fm}")
            tipLive.textSize = 11f
            tipLive.setTextColor(Sunlight.c(0xFFBDBDBD.toInt()))
            body.addView(tipLive)
            urgent = CheckBox(activity)
            urgent.text = tr("Urgent (interrupts what is being said)")
            urgent.isChecked = spec.urgentTrue
            urgent.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            if (warning) {
                val sb = Button(activity)
                severityButton = sb
                sb.isAllCaps = false
                sb.setOnClickListener {
                    severity = (severity + 1) % 3
                    sb.text = severityLabel()
                    refreshPreview()
                }
                sb.text = severityLabel()
                body.addView(sb)
            } else if (!check) {
                body.addView(urgent)
            }

            falseCheck = CheckBox(activity)
            falseCheck.text = tr("Also say something when it turns false")
            falseCheck.isChecked = spec.onFalse.isNotEmpty()
            falseCheck.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            if (!warning && !check) body.addView(falseCheck)
            falseField = EditText(activity)
            falseField.setText(spec.onFalse)
            falseField.setSingleLine(true)
            falseField.hint = tr("Words when it turns false")
            falseField.addTextChangedListener(watcher { if (falseField.text.isNotEmpty()) falseCheck.isChecked = true; refreshPreview() })
            if (!warning && !check) body.addView(falseField)

            repeatCheck = CheckBox(activity)
            repeatCheck.text = if (warning) tr("Hide after (seconds), even if the condition stays true") else tr("Repeat while it stays true, every (seconds)")
            repeatCheck.isChecked = spec.repeatSec > 0
            repeatCheck.setOnCheckedChangeListener { _, _ -> refreshPreview() }
            if (!check) body.addView(repeatCheck)
            repeatField = EditText(activity)
            repeatField.inputType = InputType.TYPE_CLASS_NUMBER
            repeatField.setText(if (spec.repeatSec > 0) spec.repeatSec.toString() else "")
            repeatField.hint = "10"
            repeatField.addTextChangedListener(watcher { if (repeatField.text.isNotEmpty()) repeatCheck.isChecked = true; refreshPreview() })
            if (!check) body.addView(repeatField, LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT))

            body.addView(label(tr("The rule as text")))
            preview = TextView(activity)
            preview.typeface = Typeface.MONOSPACE
            preview.textSize = 13f
            body.addView(preview)
            errorView = TextView(activity)
            errorView.setTextColor(Sunlight.c(0xFFFF8A80.toInt()))
            body.addView(errorView)

            val scroll = ScrollView(activity)
            scroll.addView(body)
            val dlg = Sunlight.builder(activity)
                .setTitle(if (check) (if (existing == null) tr("New check") else tr("Edit check")) else if (warning) (if (existing == null) tr("New warning") else tr("Edit warning")) else (if (existing == null) tr("New rule") else tr("Edit rule")))
                .setView(scroll)
                .setPositiveButton(tr("Save"), null)
                .setNegativeButton(tr("Cancel"), null)
                .setNeutralButton(tr("Test"), null)
                .create()
            dialog = dlg
            dlg.setOnDismissListener { ui.removeCallbacks(tick) }
            Immersive.show(dlg)
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (sayField.text.isNotBlank()) {
                    if (check) {
                        val shown = TextTemplate.expand(sayField.text.toString().trim(), live.numbers(), live.texts())
                        Toasts.make(activity, tr("Label now: {0}", shown), Toast.LENGTH_SHORT).show()
                    } else if (warning) {
                        val shown = TextTemplate.expand(sayField.text.toString().trim(), live.numbers(), live.texts())
                        testWarning?.invoke(shown, severity)
                    } else {
                        testSpeak(sayField.text.toString().trim(), urgent.isChecked)
                    }
                }
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
