// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Everything the app does around a flight beyond the screens: the last known position when the link is lost, the approval window, the
 * pack book and the voice debrief after landing. It gets the numbers of every tick, adds a few derived ones (distance flown, energy,
 * efficiency, minutes left in the window), and owns the card, the chip and the two panels.
 */
class FlightExtras(
    private val activity: Activity,
    frame: FrameLayout,
    private val speaker: Speaker,
    private val stats: FlightStats,
    private val aircraft: () -> String
) {
    val loss = LinkLoss()
    val window = ApprovalWindow()
    private var book = PackBook.decode(Prefs.packBookText(activity))
    private val card = LinkLossCard(activity)
    private val chip = WindowChip(activity)
    private val io = Executors.newSingleThreadExecutor()
    private val density = activity.resources.displayMetrics.density

    private var wasArmed = false
    private var lastNoteAt = 0L
    private var lastCardAt = 0L
    private var backUntil = 0L
    private var lastWinAt = 0L
    private var prevLeft = Double.NaN
    private var prevState = ApprovalWindow.State.OFF
    private var lastEndedSpoken = 0L
    private var pending: PackBook.Flight? = null
    private var landedAt = 0L

    val packPanel = InfoPanel(activity, { tr("Packs") }) { buildPackBook(it) }
    val positionPanel = InfoPanel(activity, { tr("Last known position") }) { buildPosition(it) }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    private fun code(): String = Lang.speechCode(activity)

    init {
        frame.addView(card.view, FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        frame.addView(chip.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).also {
            it.topMargin = dp(52)
            it.rightMargin = dp(8)
        })
        card.closeButton.setOnClickListener {
            loss.dismiss()
            backUntil = 0
            card.hide()
        }
        card.copyButton.setOnClickListener {
            val fix = loss.fixAtLoss() ?: loss.lastFix()
            if (fix != null) copy(GeoMath.coords(fix.lat, fix.lon))
        }
        reload()
    }

    /** Called when the screen opens and after a change in Settings. */
    fun reload() {
        window.configure(
            Prefs.bool(activity, "winOn", false), Prefs.int(activity, "winStart", 960), Prefs.int(activity, "winEnd", 1080),
            Prefs.string(activity, "winWarn", ApprovalWindow.DEFAULT_WARN)
        )
        if (!window.enabled()) chip.hide()
        prevLeft = Double.NaN
        prevState = ApprovalWindow.State.OFF
    }

    fun noteFile(): File = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "last_position.txt")

    // ------------------------------------------------------------------ every tick

    private fun secOfDay(wallMs: Long): Int {
        val local = wallMs + TimeZone.getDefault().getOffset(wallMs)
        return (((local / 1000) % 86400 + 86400) % 86400).toInt()
    }

    /** With the numbers of the radio (the flight statistics are already updated): derived values, link loss, landing. */
    fun tick(now: Long, numbers: MutableMap<String, Double>) {
        val armed = stats.armed()
        val wallMs = System.currentTimeMillis()
        if (stats.hasFlight()) {
            val t = stats.totals()
            numbers["flown_km"] = t.distanceKm()
            numbers["energy_wh"] = t.energyWh()
            val wk = t.whPerKm()
            if (!wk.isNaN()) numbers["wh_km"] = wk
            val mk = t.mahPerKm()
            if (!mk.isNaN()) numbers["mah_km"] = mk
        }
        if (window.enabled()) {
            val left = window.minutesLeftOrOver(secOfDay(wallMs))      // minutes left; negative once the window has ended
            if (!left.isNaN()) numbers["win_left"] = left
        }
        if (armed != wasArmed) Diagnostics.event("flight", if (armed) "armed" else "disarmed after " + ApprovalWindow.clock(stats.armedMs(now) / 1000.0))
        when (loss.update(now, armed, numbers)) {
            LinkLoss.Event.LOST -> {
                Diagnostics.event("link", "lost in flight", "warn")
                onLost(wallMs)
            }
            LinkLoss.Event.RECOVERED -> {
                Diagnostics.event("link", "back after " + ApprovalWindow.clock(loss.lostForMs(now) / 1000.0))
                backUntil = now + 6000
            }
            else -> {}
        }
        val fix = loss.lastFix()
        if (armed && !loss.isLost() && fix != null && now - lastNoteAt >= 5000) {
            lastNoteAt = now
            saveNote("in flight", fix, wallMs)
        }
        updateCard(now)
        landing(now, wallMs, armed, numbers)
        if (armed && !wasArmed) armedEdge = true
        wasArmed = armed
    }

    private var armedEdge = false

    private fun onLost(wallMs: Long) {
        val fix = loss.fixAtLoss()
        saveNote("link lost", fix, wallMs)
        if (Prefs.bool(activity, "lossSpeech", true)) {
            speaker.say(PositionNote.speech(code(), fix, stats.hasHome(), stats.homeLat(), stats.homeLon()), true)
        }
    }

    private fun updateCard(now: Long) {
        if (now - lastCardAt < 500) return
        lastCardAt = now
        if (loss.isPending()) {
            val secs = loss.lostForMs(now) / 1000.0
            card.show(tr("LINK LOST {0}", ApprovalWindow.clock(secs)), PositionNote.lines(loss.fixAtLoss(), stats.hasHome(), stats.homeLat(), stats.homeLon()), true)
        } else if (now < backUntil) {
            card.show(tr("The link is back"), PositionNote.lines(loss.lastFix(), stats.hasHome(), stats.homeLat(), stats.homeLon()), false)
        } else if (card.isShown) {
            card.hide()
        }
    }

    private fun saveNote(reason: String, fix: LinkLoss.Fix?, wallMs: Long) {
        val text = PositionNote.build(
            reason, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(wallMs)), aircraft(), fix,
            stats.hasHome(), stats.homeLat(), stats.homeLon()
        )
        val file = noteFile()
        io.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text, Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(text, Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (e: Exception) { /* the note is a help; the flight does not depend on it */ }
        }
    }

    // ------------------------------------------------------------------ landing: the pack book and the debrief

    private fun packName(): String = Battery.spokenName(activity)

    private fun landing(now: Long, wallMs: Long, armed: Boolean, numbers: Map<String, Double>) {
        if (wasArmed && !armed) {                                           // the arm switch went low: landed
            finish(numbers, false)
            val seconds = (stats.armedMs(now) / 1000).toInt()
            if (seconds >= MIN_FLIGHT_S) {
                val t = stats.totals()
                val f = PackBook.Flight()
                f.wallMs = wallMs
                f.seconds = seconds
                f.mah = t.mahUsed()
                f.wh = if (t.energyWh() > 0) t.energyWh() else Double.NaN
                f.km = if (t.distanceKm() > 0) t.distanceKm() else Double.NaN
                f.minCell = stats.minCellVolts()
                f.aircraft = aircraft()
                pending = f
                landedAt = now
            }
        }
        if (armed && pending != null) finish(numbers, false)               // armed again at once: no time to wait for the rest voltage
        if (pending != null && now - landedAt >= REST_DELAY_MS) finish(numbers, true)
    }

    /** Puts the pending flight in the book (with the voltage the pack rested at, when the link still brings it) and speaks the debrief. */
    private fun finish(numbers: Map<String, Double>, speak: Boolean) {
        val f = pending ?: return
        pending = null
        val rest = numbers["cell"]
        f.restCell = if (speak && rest != null && rest > 1.0 && LinkLoss.linkGood(numbers)) rest else Double.NaN
        val name = packName()
        val n = book.add(name, f)
        Prefs.setPackBookText(activity, book.encode())
        if (speak && Prefs.bool(activity, "debriefOn", true)) speaker.say(Debrief.text(code(), f, name, n), false)
    }

    /** The screen is going away: a flight that waits for its rest voltage is counted now. */
    fun onPause() {
        finish(emptyMap(), false)
        io.execute { }
    }

    // ------------------------------------------------------------------ the approval window

    /** Every tick, also while the radio link is still coming up: the chip and the spoken warnings. */
    fun clock(now: Long) {
        if (!window.enabled()) return
        if (now - lastWinAt < 500) return
        lastWinAt = now
        val sec = secOfDay(System.currentTimeMillis())
        val st = window.state(sec)
        val left = window.leftSeconds(sec)
        val armed = stats.armed()
        val level = when {
            st == ApprovalWindow.State.ENDED -> 2
            st == ApprovalWindow.State.BEFORE -> 1
            st == ApprovalWindow.State.OPEN && left <= 5 * 60 -> 2
            st == ApprovalWindow.State.OPEN && left <= 15 * 60 -> 1
            else -> 0
        }
        chip.show(window.chipText(sec), level)
        val speech = Prefs.bool(activity, "winSpeech", true)
        if (armed && speech) {
            if (armedEdge && st != ApprovalWindow.State.OPEN) speaker.say(ApprovalWindow.outsideSpeech(code()), true)
            val marks = window.crossed(prevLeft, left)
            if (marks.isNotEmpty()) speaker.say(ApprovalWindow.minutesLeftSpeech(code(), marks[marks.size - 1]), false)
            if (prevState == ApprovalWindow.State.OPEN && st == ApprovalWindow.State.ENDED) {
                speaker.say(ApprovalWindow.endedSpeech(code()), true)
                lastEndedSpoken = now
            } else if (st == ApprovalWindow.State.ENDED && now - lastEndedSpoken >= 60000) {
                speaker.say(ApprovalWindow.endedSpeech(code()), true)
                lastEndedSpoken = now
            }
        }
        armedEdge = false
        prevLeft = left
        prevState = st
    }

    // ------------------------------------------------------------------ the panels

    private fun copy(text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("position", text))
        Toasts.make(activity, tr("Copied: {0}", text), Toast.LENGTH_SHORT).show()
    }

    private fun f1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)
    private val sep = "  \u00B7  "

    private fun buildPackBook(p: InfoPanel) {
        val entries = book.entries()
        if (entries.isEmpty()) {
            p.text(tr("No flights counted yet. Every flight of 20 seconds or more is added to the pack that is selected, when you land."), 14f, Color.WHITE, topDp = 8)
            return
        }
        val packs = Battery.packs(activity)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        for (e in entries) {
            val c = p.card()
            p.line(c, e.name, 17f, Color.WHITE, bold = true)
            val totals = ArrayList<String>()
            totals.add(tr("Flights {0}", e.flights))
            totals.add(PackBook.durationText(e.seconds))
            if (e.mah > 0) totals.add(tr("{0} mAh", Math.round(e.mah)))
            if (e.wh > 0) totals.add(tr("{0} Wh", f1(e.wh)))
            if (e.km > 0) totals.add(tr("{0} km", f1(e.km)))
            p.line(c, totals.joinToString(sep), 13f, CardStyle.ACCENT, topDp = 2)
            val more = ArrayList<String>()
            if (!e.whPerKm().isNaN()) more.add(tr("{0} Wh/km", f1(e.whPerKm())))
            if (!e.mahPerKm().isNaN()) more.add(tr("{0} mAh/km", Math.round(e.mahPerKm())))
            if (!e.avgMinutes().isNaN()) more.add(tr("Average flight {0} min", f1(e.avgMinutes())))
            val cap = packs.firstOrNull { it.name.equals(e.name, ignoreCase = true) }?.capacityMah ?: 0
            if (cap > 0 && !e.cycles(cap).isNaN()) more.add(tr("{0} full charges of {1} mAh", f1(e.cycles(cap)), cap))
            if (!e.minCell.isNaN()) more.add(tr("Lowest cell {0} V", f2(e.minCell)))
            if (more.isNotEmpty()) p.line(c, more.joinToString(sep), 13f, 0xFFDDE3E8.toInt(), topDp = 4)
            for (f in e.recent.asReversed().take(5)) {
                val parts = ArrayList<String>()
                parts.add(dateFmt.format(Date(f.wallMs)))
                parts.add(LogText.duration(f.seconds * 1000L))
                if (!f.mah.isNaN() && f.mah > 0) parts.add(tr("{0} mAh", Math.round(f.mah)))
                if (!f.km.isNaN() && f.km > 0) parts.add(tr("{0} km", f1(f.km)))
                if (!f.minCell.isNaN()) parts.add(tr("Lowest cell {0} V", f2(f.minCell)))
                if (!f.restCell.isNaN()) parts.add(tr("Rest {0} V", f2(f.restCell)))
                p.line(c, parts.joinToString(sep), 11f, CardStyle.LABEL, topDp = 6)
            }
            p.button(c, tr("Remove this pack from the book...")) {
                p.confirm(tr("Remove {0} and its flights from the book? It cannot be brought back.", e.name), tr("Remove")) {
                    book.remove(e.name)
                    Prefs.setPackBookText(activity, book.encode())
                    p.refresh()
                }
            }
        }
        p.text(tr("The book counts every flight of 20 seconds or more when you land, for the pack that is selected. It is part of the settings backup, and it does not depend on the flight logs."), 12f, CardStyle.LABEL, topDp = 12)
        p.button(p.content, tr("Clear the whole book..."), true) {
            p.confirm(tr("Clear the book of all {0} packs? It cannot be brought back.", entries.size), tr("Clear")) {
                book.clear()
                Prefs.setPackBookText(activity, book.encode())
                p.refresh()
            }
        }
    }

    private fun buildPosition(p: InfoPanel) {
        val f = noteFile()
        val text = try { if (f.exists()) f.readText(Charsets.UTF_8) else null } catch (e: Exception) { null }
        if (text == null) {
            p.text(tr("No position is saved yet. The radio saves one every 5 seconds in flight, and at once when the link is lost."), 14f, Color.WHITE, topDp = 8)
            return
        }
        val m = PositionNote.parse(text)
        val c = p.card()
        val reason = if (m["reason"] == "link lost") tr("Link lost") else tr("In flight, the latest position")
        p.line(c, reason, 17f, if (m["reason"] == "link lost") CardStyle.BAD else Color.WHITE, bold = true)
        p.line(c, tr("Saved {0}", m["saved"] ?: "?") + (if ((m["aircraft"] ?: "").isNotEmpty()) sep + m["aircraft"] else ""), 12f, CardStyle.LABEL, topDp = 2)
        val coords = PositionNote.coordsOf(text)
        if (coords == null) {
            p.line(c, tr("No position was received in this flight."), 15f, Color.WHITE, topDp = 8)
        } else {
            p.line(c, coords, 22f, Color.WHITE, bold = true, topDp = 8)
            val dist = m["from_home_m"]
            val brg = m["bearing_from_home_deg"]
            if (dist != null && brg != null) {
                p.line(c, tr("{0} m from home, bearing {1} deg {2}", dist, brg, Attitude.cardinal(brg.toDoubleOrNull() ?: Double.NaN)), 15f, Color.WHITE, topDp = 4)
            }
            val facts = ArrayList<String>()
            m["height_m"]?.let { facts.add(tr("Height {0} m", it)) }
            m["speed_kmh"]?.let { facts.add(tr("Speed {0} km/h", it)) }
            m["sats"]?.let { facts.add(tr("{0} satellites", it)) }
            if (facts.isNotEmpty()) p.line(c, facts.joinToString(sep), 13f, 0xFFDDE3E8.toInt(), topDp = 4)
            p.button(c, tr("Copy position")) { copy(coords) }
            p.button(c, tr("Open in a maps app")) {
                try {
                    val q = coords.replace(" ", "")
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + q + "?q=" + q + "(AX-HUD)")))
                } catch (e: Exception) {
                    Toasts.make(activity, tr("No maps app on this radio. Copy the position instead."), Toast.LENGTH_LONG).show()
                }
            }
        }
        p.button(c, tr("Delete this note...")) {
            p.confirm(tr("Delete the saved position? It cannot be brought back."), tr("Delete")) {
                f.delete()
                p.refresh()
            }
        }
        p.text(tr("The file is {0}", f.absolutePath), 11f, CardStyle.LABEL, topDp = 12)
    }

    companion object {
        const val MIN_FLIGHT_S = 20
        const val REST_DELAY_MS = 4000L
    }
}
