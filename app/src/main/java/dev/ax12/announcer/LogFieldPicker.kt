// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * The choice of the values that go into a flight log: every channel and telemetry value the radio publishes, grouped like in the rule
 * editor, with a tick box each. A line at the top says what a replay would miss with the present choice.
 */
object LogFieldPicker {

    private class PickRow(val header: String?, val name: String?)

    fun show(activity: Activity, live: LiveValues, current: List<String>, armChannel: Int, onDone: (List<String>) -> Unit) {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density + 0.5f).toInt() }
        val selected = LinkedHashSet<String>(current)
        val known = LinkedHashSet<String>()
        val rows = ArrayList<PickRow>()
        for (g in VariableCatalog.groups(live.numbers().keys + live.texts().keys + current)) {
            if (g.names.isEmpty()) continue
            rows.add(PickRow(g.title, null))
            for (n in g.names) { rows.add(PickRow(null, n)); known.add(n) }
        }

        val body = LinearLayout(activity)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(0))

        val intro = TextView(activity)
        intro.text = tr("Tick the values to write into each flight log.")
        intro.textSize = 14f
        intro.setTextColor(0xFFDDE3E8.toInt())
        body.addView(intro)

        val missing = TextView(activity)
        missing.textSize = 12f
        missing.setTextColor(CardStyle.WARN)
        missing.setPadding(0, dp(4), 0, dp(4))
        body.addView(missing)

        val listView = ListView(activity)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = rows.size
            override fun getItem(position: Int): Any = rows[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun isEnabled(position: Int): Boolean = rows[position].name != null
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = rows[position]
                if (row.header != null) {
                    val h = TextView(activity)
                    h.text = row.header
                    h.textSize = 13f
                    h.setTypeface(null, Typeface.BOLD)
                    h.setTextColor(CardStyle.TITLE)
                    h.setPadding(dp(12), dp(14), dp(12), dp(4))
                    return h
                }
                val name = row.name ?: ""
                val box = LinearLayout(activity)
                box.orientation = LinearLayout.HORIZONTAL
                box.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)
                box.setPadding(dp(8), dp(4), dp(8), dp(4))
                val check = CheckBox(activity)
                check.isChecked = selected.contains(name)
                check.isClickable = false                      // the row takes the tap
                check.isFocusable = false
                box.addView(check)
                val texts = LinearLayout(activity)
                texts.orientation = LinearLayout.VERTICAL
                val line1 = TextView(activity)
                line1.text = name
                line1.textSize = 16f
                line1.setTextColor(Color.WHITE)
                texts.addView(line1)
                val help = VariableCatalog.help(name)
                if (help.isNotEmpty()) {
                    val line2 = TextView(activity)
                    line2.text = help
                    line2.textSize = 12f
                    line2.setTextColor(0xFFBDBDBD.toInt())
                    texts.addView(line2)
                }
                box.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                return box
            }
        }

        fun refreshNote() {
            val m = LogFields.missingForReplay(selected, armChannel)
            missing.text = if (m.isEmpty()) "" else tr("A replay needs these values and cannot show everything without them: {0}", m.joinToString(", "))
            missing.visibility = if (m.isEmpty()) View.GONE else View.VISIBLE
        }

        val presets = LinearLayout(activity)
        presets.orientation = LinearLayout.HORIZONTAL
        fun preset(label: String, apply: () -> Unit) {
            val b = Button(activity)
            b.text = label
            b.setOnClickListener { apply(); adapter.notifyDataSetChanged(); refreshNote() }
            presets.addView(b, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f))
        }
        preset(tr("Replay set")) { selected.clear(); selected.addAll(LogFields.replaySet()) }
        preset(tr("Everything")) { selected.clear(); selected.addAll(known) }
        preset(tr("None")) { selected.clear() }
        body.addView(presets)

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val n = rows[position].name
            if (n != null) {
                if (!selected.remove(n)) selected.add(n)
                adapter.notifyDataSetChanged()
                refreshNote()
            }
        }
        body.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        refreshNote()

        val dlg = AlertDialog.Builder(activity)
            .setTitle(tr("Values in the log"))
            .setView(body)
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Done"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (selected.isEmpty()) {
                Toast.makeText(activity, tr("Tick at least one value."), Toast.LENGTH_SHORT).show()
            } else {
                onDone(ArrayList(selected))
                dlg.dismiss()
            }
        }
    }
}
