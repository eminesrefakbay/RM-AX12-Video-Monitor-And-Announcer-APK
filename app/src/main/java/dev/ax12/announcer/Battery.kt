package dev.ax12.announcer

import android.content.Context

/**
 * The battery profiles (the limits of a kind of pack) and the packs (a named pack with its profile and capacity).
 * With packs, the menu chooses a pack and the pack chooses the profile; without packs it chooses a profile.
 */
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

    // ---- packs ----
    fun packs(c: Context): List<Packs.Pack> = Packs.decode(Prefs.packsText(c))

    fun setPacks(c: Context, list: List<Packs.Pack>) {
        Prefs.setPacksText(c, Packs.encode(list))
        if (Prefs.int(c, "packActive", 0) >= list.size) Prefs.setInt(c, "packActive", 0)
    }

    fun activePack(c: Context): Packs.Pack? {
        val p = packs(c)
        return if (p.isEmpty()) null else p[Prefs.int(c, "packActive", 0).coerceIn(0, p.size - 1)]
    }

    /** Makes the active pack's profile the active profile (when it still exists). */
    fun applyActivePack(c: Context) {
        val pk = activePack(c) ?: return
        val idx = Packs.profileIndex(pk, profiles(c))
        if (idx >= 0) Prefs.setInt(c, "batActive", idx)
    }

    /** What the menu and the checklist show: "Pack 1 (Li-ion 6S, 3000 mAh)", or the profile's name when there are no packs. */
    fun label(c: Context): String {
        val pk = activePack(c) ?: return active(c).name
        return Packs.describe(Packs.Pack(pk.name, active(c).name, pk.capacityMah))
    }

    /** What the voice says: the pack's name, or the profile's. */
    fun spokenName(c: Context): String = activePack(c)?.name ?: active(c).name

    /** The next pack (or, with no packs, the next profile) becomes the active one. */
    fun cycle(c: Context) {
        val p = packs(c)
        if (p.isEmpty()) {
            Prefs.setInt(c, "batActive", (activeIndex(c) + 1) % profiles(c).size)
        } else {
            Prefs.setInt(c, "packActive", (Prefs.int(c, "packActive", 0).coerceIn(0, p.size - 1) + 1) % p.size)
            applyActivePack(c)
        }
    }

    /** The numbers $low, $crit and $full stand for in rules. */
    fun macros(c: Context): Map<String, Double> = BatteryProfiles.macros(active(c))
}
