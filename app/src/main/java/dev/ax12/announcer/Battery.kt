package dev.ax12.announcer

import android.content.Context

/** The battery profiles stored in the settings, and which one is active. */
object Battery {
    fun profiles(c: Context): List<BatteryProfiles.Profile> = BatteryProfiles.decode(Prefs.batProfilesText(c))

    fun setProfiles(c: Context, list: List<BatteryProfiles.Profile>) {
        Prefs.setBatProfilesText(c, BatteryProfiles.encode(list))
        // keep the active one inside the list
        if (Prefs.int(c, "batActive", 0) >= list.size) Prefs.setInt(c, "batActive", 0)
    }

    fun activeIndex(c: Context): Int {
        val n = profiles(c).size
        return Prefs.int(c, "batActive", 0).coerceIn(0, n - 1)
    }

    fun active(c: Context): BatteryProfiles.Profile = profiles(c)[activeIndex(c)]

    /** The next profile becomes the active one. */
    fun cycle(c: Context): BatteryProfiles.Profile {
        val n = profiles(c).size
        Prefs.setInt(c, "batActive", (activeIndex(c) + 1) % n)
        return active(c)
    }

    /** The numbers $low, $crit and $full stand for in rules. */
    fun macros(c: Context): Map<String, Double> = BatteryProfiles.macros(active(c))
}
