package dev.ax12.announcer

import android.content.Context

/** Small wrapper around SharedPreferences. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("ax12announcer", Context.MODE_PRIVATE)

    fun rules(c: Context): String = p(c).getString("rules", null) ?: DefaultRules.TEXT
    fun setRules(c: Context, text: String) { p(c).edit().putString("rules", text).apply() }

    fun cameraId(c: Context): String? = p(c).getString("camera", null)
    fun setCameraId(c: Context, id: String) { p(c).edit().putString("camera", id).apply() }

    fun fill(c: Context): Boolean = p(c).getBoolean("fill", false)
    fun setFill(c: Context, on: Boolean) { p(c).edit().putBoolean("fill", on).apply() }

    fun rotation(c: Context): Int = p(c).getInt("rotation", 0)
    fun setRotation(c: Context, deg: Int) { p(c).edit().putInt("rotation", deg).apply() }
}
