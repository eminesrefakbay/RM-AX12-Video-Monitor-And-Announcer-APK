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
    fun int(c: Context, key: String, def: Int): Int = p(c).getInt(key, def)
    fun setInt(c: Context, key: String, v: Int) { p(c).edit().putInt(key, v).apply() }

    fun updateChannel(c: Context): String = if (p(c).getString("updateChannel", "nightly") == "stable") "stable" else "nightly"
    fun setUpdateChannel(c: Context, ch: String) { p(c).edit().putString("updateChannel", ch).apply() }

    /** "auto" (follow Android), or en / tr / de / zh */
    fun language(c: Context): String = p(c).getString("language", "auto") ?: "auto"
    fun setLanguage(c: Context, code: String) { p(c).edit().putString("language", code).apply() }
    /** "same" (as the app), or en / tr / de / zh */
    fun speechLanguage(c: Context): String = p(c).getString("speechLanguage", "same") ?: "same"
    fun setSpeechLanguage(c: Context, code: String) { p(c).edit().putString("speechLanguage", code).apply() }

    fun rotation(c: Context): Int = p(c).getInt("rotation", 0)
    fun setRotation(c: Context, deg: Int) { p(c).edit().putInt("rotation", deg).apply() }
}
