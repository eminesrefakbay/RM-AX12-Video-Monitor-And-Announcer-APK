// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * The list of flight logs: when, how long and a short summary of each, with Replay and Delete. A full-screen page like About and
 * Settings. The files are read on a background thread, so a long list never holds the screen up.
 */
class LogViewerPanel(
    private val activity: Activity,
    private val folder: () -> File,
    private val activeFile: () -> File?,
    private val onReplay: (File) -> Unit
) {
    val root = LinearLayout(activity)
    private val titleView = TextView(activity)
    private val closeButton = Button(activity)
    private val page = ScrollView(activity)
    private val content = LinearLayout(activity)
    private val density = activity.resources.displayMetrics.density

    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(CardStyle.SEE_THROUGH)
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(28).toFloat()

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(CardStyle.BLOCK)
        bar.setPadding(dp(8), 0, dp(8), 0)
        titleView.textSize = 22f
        titleView.setTextColor(Color.WHITE)
        titleView.setTypeface(null, Typeface.BOLD)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        closeButton.setOnClickListener { hide() }
        bar.addView(closeButton)
        root.addView(bar)

        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(CardStyle.BLOCK)
        content.setPadding(dp(16), dp(8), dp(16), dp(24))
        page.addView(content)
        root.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show() {
        titleView.text = tr("Flight logs")
        closeButton.text = tr("Close")
        content.removeAllViews()
        content.addView(text(tr("Loading the log..."), 14f, CardStyle.LABEL))
        shown = true
        Anim.panelIn(root)
        load()
    }

    fun hide() {
        shown = false
        Anim.panelOut(root)
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false, topDp: Int = 0): TextView {
        val t = TextView(activity)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, 0)
        return t
    }

    private fun load() {
        val dir = folder()
        Thread {
            val list = LogStore.list(dir)
            val bytes = LogStore.totalBytes(dir)
            activity.runOnUiThread { if (shown && !activity.isFinishing) build(list, bytes) }
        }.start()
    }

    private fun confirm(message: String, positive: String, onYes: () -> Unit) {
        val dlg = AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
    }

    private fun build(list: List<LogStore.Entry>, bytes: Long) {
        content.removeAllViews()
        if (list.isEmpty()) {
            content.addView(text(tr("No flight logs yet. Switch on logging in Settings, then arm to start one."), 14f, Color.WHITE, topDp = 8))
        }
        val active = activeFile()
        for (e in list) content.addView(card(e, active != null && active == e.file), cardParams())
        if (list.isNotEmpty()) {
            content.addView(text(LogText.footer(list.size, bytes), 12f, CardStyle.LABEL, topDp = 16))
            val all = Button(activity)
            all.text = tr("Delete all the logs...")
            all.setOnClickListener {
                confirm(tr("Delete all {0} logs? They cannot be brought back.", list.size), tr("Delete")) {
                    Thread {
                        LogStore.deleteAll(folder())
                        activity.runOnUiThread { if (shown) load() }
                    }.start()
                }
            }
            content.addView(all)
        }
        content.addView(text(tr("The logs hold GPS positions and stay on this radio, in the folder {0}", folder().absolutePath), 12f, CardStyle.LABEL, topDp = 12))
    }

    private fun cardParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        return lp
    }

    private fun card(e: LogStore.Entry, recording: Boolean): View {
        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(8), dp(16), dp(8))
        card.background = CardStyle.panelBackground(density, 0xE6232C36.toInt())
        card.addView(text(LogText.title(e), 16f, Color.WHITE, bold = true))
        card.addView(text(LogText.lengthAndPack(e), 13f, CardStyle.ACCENT, topDp = 2))
        val summary = LogText.summary(e)
        if (summary.isNotEmpty()) card.addView(text(summary, 13f, 0xFFDDE3E8.toInt(), topDp = 4))
        card.addView(text(e.name + "  \u00B7  " + LogText.size(e.size), 11f, CardStyle.LABEL, topDp = 4))
        if (recording) {
            card.addView(text(tr("Recording now"), 13f, CardStyle.WARN, bold = true, topDp = 6))
        } else {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            val replay = Button(activity)
            replay.text = tr("Replay")
            replay.isEnabled = e.rows >= 2
            replay.setOnClickListener { onReplay(e.file) }
            row.addView(replay, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f).also { it.rightMargin = dp(8) })
            val del = Button(activity)
            del.text = tr("Delete")
            del.setOnClickListener {
                confirm(tr("Delete this log? It cannot be brought back."), tr("Delete")) {
                    Thread {
                        LogStore.delete(e.file)
                        activity.runOnUiThread { if (shown) load() }
                    }.start()
                }
            }
            row.addView(del, LinearLayout.LayoutParams(0, dp(CardStyle.MIN_TOUCH_DP), 1f))
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        }
        return card
    }
}
