package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** A list of every channel and telemetry value, grouped, with its live value. Used by the rule editor and the chart settings. */
object VariablePicker {

    private class PickRow(val header: String?, val name: String?)

    private fun liveText(name: String, nums: Map<String, Double>, txts: Map<String, String>): String? {
        val t = txts[name]
        if (t != null) return "\u201C" + t + "\u201D"
        val n = nums[name] ?: return null
        val base = RuleText.fmt(Math.round(n * 100.0) / 100.0)
        if (name.startsWith("ch") && name.substring(2).toIntOrNull() != null) {
            val pos = if (n < 1300) "low" else if (n > 1700) "high" else "middle"
            return base + " (" + pos + ")"
        }
        return base
    }

    /** @param numericOnly leave out the text values (the flight mode): for charts */
    fun show(activity: Activity, live: LiveValues, numericOnly: Boolean, onPicked: (String) -> Unit) {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density + 0.5f).toInt() }
        val nums = live.numbers()
        val txts = live.texts()
        val rows = ArrayList<PickRow>()
        for (g in VariableCatalog.groups(nums.keys + txts.keys)) {
            val names = g.names.filter { !numericOnly || (it != "fm" && !txts.containsKey(it)) }
            if (names.isEmpty()) continue
            rows.add(PickRow(g.title, null))
            for (n in names) rows.add(PickRow(null, n))
        }
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
                    h.setTextColor(0xFF9CDCFE.toInt())
                    h.setPadding(dp(12), dp(14), dp(12), dp(4))
                    return h
                }
                val name = row.name ?: ""
                val box = LinearLayout(activity)
                box.orientation = LinearLayout.VERTICAL
                box.setPadding(dp(12), dp(6), dp(12), dp(6))
                val shown = liveText(name, nums, txts)
                val line1 = TextView(activity)
                line1.text = name + "      " + (if (shown != null) tr("now: {0}", shown) else tr("no value now"))
                line1.textSize = 16f
                line1.setTextColor(Color.WHITE)
                box.addView(line1)
                val help = VariableCatalog.help(name)
                if (help.isNotEmpty()) {
                    val line2 = TextView(activity)
                    line2.text = help
                    line2.textSize = 12f
                    line2.setTextColor(0xFFBDBDBD.toInt())
                    box.addView(line2)
                }
                box.alpha = if (shown != null) 1f else 0.55f
                return box
            }
        }
        listView.adapter = adapter
        val dlg = AlertDialog.Builder(activity)
            .setTitle(tr("Pick a channel or value"))
            .setView(listView)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            val n = rows[position].name
            if (n != null) {
                onPicked(n)
                dlg.dismiss()
            }
        }
        Immersive.show(dlg)
    }
}
