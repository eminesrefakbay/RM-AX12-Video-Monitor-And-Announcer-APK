// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The About page: what the app is, its license, a link to the project, and every library and SDK inside it with its
 * license text. A license text opens in a reader inside the page, so nothing needs a network or a browser to be read.
 */
class AboutPanel(private val activity: Activity, private val versionText: () -> String) {
    val root = LinearLayout(activity)
    private val titleView = TextView(activity)
    private val backButton = Button(activity)
    private val page = ScrollView(activity)
    private val content = LinearLayout(activity)
    private val viewer = ScrollView(activity)
    private val viewerText = TextView(activity)
    private val density = activity.resources.displayMetrics.density

    private var shown = false
    val isShowing: Boolean get() = shown

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(CardStyle.SEE_THROUGH)         // the video stays visible behind the page
        root.setPadding(dp(16), dp(8), dp(16), dp(8))
        root.visibility = View.GONE
        root.isClickable = true
        root.elevation = dp(28).toFloat()          // above the Settings screen

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(CardStyle.BLOCK)
        bar.setPadding(dp(8), dp(0), dp(8), dp(0))
        titleView.textSize = 22f
        titleView.setTextColor(Color.WHITE)
        titleView.setTypeface(null, Typeface.BOLD)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backButton.setOnClickListener { back() }
        bar.addView(backButton)
        root.addView(bar)

        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(CardStyle.BLOCK)
        content.setPadding(dp(16), dp(8), dp(16), dp(24))
        page.addView(content)
        root.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        viewerText.typeface = Typeface.MONOSPACE
        viewerText.textSize = 12f
        viewerText.setTextColor(0xFFDDE3E8.toInt())
        viewerText.setBackgroundColor(CardStyle.BLOCK)
        viewerText.setPadding(dp(16), dp(8), dp(16), dp(24))
        viewer.addView(viewerText)
        viewer.visibility = View.GONE
        root.addView(viewer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show() {
        build()
        showPage()
        shown = true
        Anim.panelIn(root)
    }

    fun hide() {
        shown = false
        Anim.panelOut(root)
    }

    /** From a license text back to the page; from the page back to Settings. */
    fun back() {
        if (viewer.visibility == View.VISIBLE) showPage() else hide()
    }

    private fun showPage() {
        titleView.text = tr("About AX-HUD")
        backButton.text = tr("Close")
        viewer.visibility = View.GONE
        page.visibility = View.VISIBLE
    }

    private fun showText(title: String, asset: String) {
        val text = try {
            activity.assets.open(asset).use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: Exception) {
            tr("The license text could not be read.")
        }
        titleView.text = title
        backButton.text = tr("Back")
        viewerText.text = text
        page.visibility = View.GONE
        viewer.visibility = View.VISIBLE
        viewer.scrollTo(0, 0)
    }

    private fun openUrl(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, tr("No web browser found. The address is: {0}", url), Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ the page

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false, topDp: Int = 0): TextView {
        val t = TextView(activity)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, 0)
        return t
    }

    private fun heading(s: String) {
        content.addView(text(s, 14f, CardStyle.TITLE, bold = true, topDp = 24))
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(activity)
        b.text = label
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setOnClickListener { onClick() }
        return b
    }

    private fun build() {
        content.removeAllViews()

        // who we are
        val logo = ImageView(activity)
        logo.setImageResource(R.drawable.splash_logo)
        logo.adjustViewBounds = true
        content.addView(logo, LinearLayout.LayoutParams(dp(160), dp(160)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val name = text(Credits.PROJECT_NAME, 26f, Color.WHITE, bold = true, topDp = 6)
        name.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tag = text(tr("Video monitor, HUD and voice announcer for the RadioMaster AX12."), 14f, CardStyle.LABEL, topDp = 4)
        tag.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(tag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(text(versionText(), 12f, CardStyle.LABEL, topDp = 10))

        // the license of the app
        val app = Credits.entries()[0]
        heading(tr("License"))
        content.addView(text(tr("AX-HUD is free software under the GNU General Public License, version 3 or later, with an additional permission that allows it to be combined with the RadioMaster AX SDK. You may use, change and share it; if you share a changed version, you must share its source code under the same license. It comes without any warranty."), 14f, Color.WHITE, topDp = 2))
        for (t in app.texts) content.addView(button(tr("Read the {0}", t.label)) { showText(t.label, t.asset) })
        content.addView(text(tr("Project page and source code: {0}", app.url), 12f, CardStyle.LABEL, topDp = 6))
        content.addView(button(tr("Open the project page")) { openUrl(app.url) })

        // everything inside the app
        heading(tr("Open-source libraries and SDKs"))
        content.addView(text(tr("These parts are inside the app. Each keeps its own license; tap to read it."), 12f, CardStyle.LABEL, topDp = 2))
        for (e in Credits.entries().drop(1)) {
            val card = LinearLayout(activity)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(8), dp(16), dp(8))
            card.background = CardStyle.panelBackground(density, 0xE6232C36.toInt())
            card.addView(text(e.name, 16f, Color.WHITE, bold = true))
            card.addView(text(e.holder + "  \u00B7  " + e.license, 12f, CardStyle.ACCENT, topDp = 2))
            card.addView(text(tr(e.description), 13f, 0xFFDDE3E8.toInt(), topDp = 4))
            card.addView(text(e.url, 12f, CardStyle.LABEL, topDp = 4))
            for (t in e.texts) card.addView(button(tr("Read: {0}", t.label)) { showText(t.label, t.asset) })
            card.addView(button(tr("Open the page")) { openUrl(e.url) })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            content.addView(card, lp)
        }

        // thanks and independence
        heading(tr("Thanks"))
        content.addView(text(tr("Thank you, RadioMaster, for publishing the AX SDK that makes this app possible."), 14f, Color.WHITE, topDp = 2))
        content.addView(text(tr("AX-HUD is an independent project. It is not affiliated with, endorsed by or supported by RadioMaster. RadioMaster and AX12 are names of their owners."), 12f, CardStyle.LABEL, topDp = 6))
    }
}
