// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context

/** Small wrapper around SharedPreferences. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("ax12announcer", Context.MODE_PRIVATE)

    fun rules(c: Context): String = p(c).getString("rules", null) ?: DefaultRules.text(c)
    fun setRules(c: Context, text: String) { p(c).edit().putString("rules", text).apply() }

    fun cameraId(c: Context): String? = p(c).getString("camera", null)
    fun setCameraId(c: Context, id: String) { p(c).edit().putString("camera", id).apply() }

    fun fill(c: Context): Boolean = p(c).getBoolean("fill", false)
    fun setFill(c: Context, on: Boolean) { p(c).edit().putBoolean("fill", on).apply() }

    fun menuAlways(c: Context): Boolean = p(c).getBoolean("menuAlways", false)
    fun setMenuAlways(c: Context, on: Boolean) { p(c).edit().putBoolean("menuAlways", on).apply() }

    /** The rules as they were before the last import, reset or restore. */
    fun rulesBackup(c: Context): String? = p(c).getString("rulesBackup", null)
    fun setRulesBackup(c: Context, text: String) { p(c).edit().putString("rulesBackup", text).apply() }

    // ---- warning rules and overlays ----
    fun warningRules(c: Context): String = p(c).getString("warnRules", null) ?: DefaultWarnings.text()
    fun setWarningRules(c: Context, text: String) { p(c).edit().putString("warnRules", text).apply() }

    fun checkRules(c: Context): String = p(c).getString("checkRules", null) ?: DefaultChecks.text()
    fun setCheckRules(c: Context, text: String) { p(c).edit().putString("checkRules", text).apply() }

    fun barsConfig(c: Context): String = p(c).getString("barsConfig", null) ?: AuxBars.encode(AuxBars.defaults())
    fun setBarsConfig(c: Context, text: String) { p(c).edit().putString("barsConfig", text).apply() }

    fun bool(c: Context, key: String, def: Boolean): Boolean = p(c).getBoolean(key, def)
    fun setBool(c: Context, key: String, v: Boolean) { p(c).edit().putBoolean(key, v).apply() }
    fun string(c: Context, key: String, def: String): String = p(c).getString(key, def) ?: def
    fun setString(c: Context, key: String, v: String) { p(c).edit().putString(key, v).apply() }
    fun int(c: Context, key: String, def: Int): Int = p(c).getInt(key, def)
    fun setInt(c: Context, key: String, v: Int) { p(c).edit().putInt(key, v).apply() }

    // ---- dashboard
    /** The cards of the dashboard page, one per line; empty means the first dashboard. */
    fun dashTiles(c: Context): String = p(c).getString("dashTiles", null) ?: ""
    fun setDashTiles(c: Context, text: String) { p(c).edit().putString("dashTiles", text).apply() }

    // ---- flight logs
    fun logOn(c: Context): Boolean = p(c).getBoolean("logOn", false)
    fun setLogOn(c: Context, on: Boolean) = setBool(c, "logOn", on)
    fun logRate(c: Context): Int = int(c, "logRate", 3).coerceIn(0, 4)
    fun logKeep(c: Context): Int = int(c, "logKeep", 1).coerceIn(0, 3)
    /** The names of the values written to a log, as one text; empty means the replay set. */
    fun logFields(c: Context): String = p(c).getString("logFields", null) ?: ""
    fun setLogFields(c: Context, text: String) { p(c).edit().putString("logFields", text).apply() }

    /** The newest version of the welcome guide that this person has seen (0 = never). */
    fun guideSeen(c: Context): Int = p(c).getInt("guideSeen", 0)
    fun setGuideSeen(c: Context, v: Int) { p(c).edit().putInt("guideSeen", v).apply() }

    /** Stable (the tested releases) unless the person chose Nightly on purpose. */
    fun updateChannel(c: Context): String = if (p(c).getString("updateChannel", "stable") == "nightly") "nightly" else "stable"
    fun setUpdateChannel(c: Context, ch: String) { p(c).edit().putString("updateChannel", ch).apply() }

    /** "auto" (follow Android), or en / tr / de / zh */
    fun language(c: Context): String = p(c).getString("language", "auto") ?: "auto"
    fun setLanguage(c: Context, code: String) { p(c).edit().putString("language", code).apply() }
    /** "same" (as the app), or en / tr / de / zh */
    fun speechLanguage(c: Context): String = p(c).getString("speechLanguage", "same") ?: "same"
    fun setSpeechLanguage(c: Context, code: String) { p(c).edit().putString("speechLanguage", code).apply() }

    // ---- battery profiles ----
    fun batProfilesText(c: Context): String = p(c).getString("batProfiles", null) ?: BatteryProfiles.encode(BatteryProfiles.defaults())
    fun setBatProfilesText(c: Context, text: String) { p(c).edit().putString("batProfiles", text).apply() }

    // ---- live charts ----
    fun chartSeries(c: Context): String = p(c).getString("chartSeries", null) ?: ChartConfig.DEFAULT
    fun setChartSeries(c: Context, text: String) { p(c).edit().putString("chartSeries", text).apply() }

    // ---- packs and aircraft ----
    fun packsText(c: Context): String = p(c).getString("packs", null) ?: ""
    fun setPacksText(c: Context, text: String) { p(c).edit().putString("packs", text).apply() }
    fun packBookText(c: Context): String = p(c).getString("packBook", null) ?: ""
    fun setPackBookText(c: Context, text: String) { p(c).edit().putString("packBook", text).apply() }
    fun aircraftStoreText(c: Context): String? = p(c).getString("aircraftStore", null)
    fun setAircraftStoreText(c: Context, text: String) { p(c).edit().putString("aircraftStore", text).apply() }
    fun aircraftActive(c: Context): String? = p(c).getString("aircraftActive", null)
    fun setAircraftActive(c: Context, id: String) { p(c).edit().putString("aircraftActive", id).apply() }

    // ---- backup of all settings ----
    /** The settings as they were before the last import or restore (the text of a settings file). */
    fun settingsBackup(c: Context): String? = p(c).getString("settingsBackup", null)
    fun setSettingsBackup(c: Context, text: String) { p(c).edit().putString("settingsBackup", text).apply() }

    /** Every setting with its value now, or its default when it was never changed: a complete snapshot. */
    fun exportMap(c: Context): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val stored = p(c).all
        for (k in SettingsFile.keys()) {
            val v = stored[k] ?: SettingsFile.defaultOf(k)
            if (v != null) out[k] = v
        }
        out["rules"] = rules(c)
        out["warnRules"] = warningRules(c)
        out["checkRules"] = checkRules(c)
        out["barsConfig"] = barsConfig(c)
        out["batProfiles"] = batProfilesText(c)
        out["packs"] = packsText(c)
        // all the aircraft: the one in use is saved from the settings as they are now
        val store = Aircraft.store(c)
        store.syncActive(out)
        out["aircraftStore"] = store.encode()
        out["aircraftActive"] = store.activeId()
        return out
    }

    /** Writes checked values from a settings file. */
    fun applyAll(c: Context, values: Map<String, Any>) {
        val e = p(c).edit()
        for ((k, v) in values) {
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
            }
        }
        e.apply()
    }

    fun rotation(c: Context): Int = p(c).getInt("rotation", 0)
    fun setRotation(c: Context, deg: Int) { p(c).edit().putInt("rotation", deg).apply() }
}
