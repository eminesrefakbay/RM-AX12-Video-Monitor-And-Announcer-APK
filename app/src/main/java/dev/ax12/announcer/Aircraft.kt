package dev.ax12.announcer

import android.content.Context

/**
 * The aircraft profiles. The settings that belong to an aircraft live in the normal settings while it is in use;
 * switching saves them into its slot and loads the next one's (see AircraftStore).
 */
object Aircraft {
    fun store(c: Context): AircraftStore = AircraftStore.decode(Prefs.aircraftStoreText(c), Prefs.aircraftActive(c))

    fun save(c: Context, s: AircraftStore) {
        Prefs.setAircraftStoreText(c, s.encode())
        Prefs.setAircraftActive(c, s.activeId())
    }

    fun activeName(c: Context): String = store(c).active().name

    /** The settings of the aircraft in use, as they are now. */
    fun live(c: Context): Map<String, Any> = Prefs.exportMap(c).filterKeys { AircraftStore.isAircraftKey(it) }

    /** What a new aircraft starts with when it is not a copy: the starting rules, warnings, checks, profiles and no packs. */
    fun startingValues(c: Context): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        for (k in AircraftStore.keys()) SettingsFile.defaultOf(k)?.let { m[k] = it }
        m["rules"] = DefaultRules.text(c)
        m["warnRules"] = DefaultWarnings.text()
        m["checkRules"] = DefaultChecks.text()
        m["barsConfig"] = AuxBars.encode(AuxBars.defaults())
        m["batProfiles"] = BatteryProfiles.encode(BatteryProfiles.defaults())
        m["packs"] = ""
        return m
    }

    /** Saves the aircraft in use and loads another one's settings. @return false if there is no such aircraft */
    fun switchTo(c: Context, id: String): Boolean {
        val s = store(c)
        val values = s.switchTo(id, live(c)) ?: return false
        Prefs.applyAll(c, values)
        save(c, s)
        return true
    }
}
