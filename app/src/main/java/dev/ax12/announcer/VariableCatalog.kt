package dev.ax12.announcer

/** The variable names the rule editor offers, with a one-line explanation. Live names are added on top. */
object VariableCatalog {

    private val fixed: List<String> = run {
        val l = ArrayList<String>()
        for (i in 1..16) l.add("ch$i")
        l.addAll(listOf(
            "vbat", "curr", "capa", "batpct",
            "up_lq", "up_rssi1", "up_rssi2", "up_snr", "dn_lq", "dn_rssi", "dn_snr", "rf_mode", "tx_power",
            "lq", "dbm", "lost", "txv", "rxv", "tlm",
            "sats", "gps_speed", "gps_alt", "gps_heading", "gps_lat", "gps_lon",
            "pitch", "roll", "yaw", "vspeed", "fm"
        ))
        for (i in 1..16) l.add("raw$i")
        for (i in 1..16) l.add("lv$i")
        l
    }

    private val help: Map<String, String> = mapOf(
        "vbat" to "battery volts (flight controller); divide by the cell count for volts per cell",
        "curr" to "battery current, amps",
        "capa" to "capacity used, mAh",
        "batpct" to "battery remaining, percent",
        "up_lq" to "uplink link quality, percent (0 = no link)",
        "up_rssi1" to "uplink signal strength, antenna 1, dBm (negative)",
        "up_rssi2" to "uplink signal strength, antenna 2, dBm (negative)",
        "up_snr" to "uplink signal to noise, dB",
        "dn_lq" to "downlink link quality, percent",
        "dn_rssi" to "downlink signal strength, dBm",
        "dn_snr" to "downlink signal to noise, dB",
        "rf_mode" to "ExpressLRS packet rate mode number",
        "tx_power" to "transmitter power, mW",
        "lq" to "link quality from the radio, percent",
        "dbm" to "signal strength from the radio",
        "lost" to "radio's lost-frame counter",
        "txv" to "radio battery volts",
        "rxv" to "receiver volts",
        "tlm" to "telemetry present: 1 yes, 0 lost",
        "sats" to "GPS satellites",
        "gps_speed" to "ground speed, km/h",
        "gps_alt" to "GPS altitude, metres",
        "gps_heading" to "GPS heading, degrees",
        "gps_lat" to "latitude",
        "gps_lon" to "longitude",
        "pitch" to "pitch angle, degrees",
        "roll" to "roll angle, degrees",
        "yaw" to "yaw / heading, degrees",
        "vspeed" to "climb rate, m/s",
        "fm" to "flight mode name from the flight controller (text)"
    )

    class Group(val title: String, val names: List<String>)

    /** The picker's sections. Every name appears once; live names the catalog does not know go to "Other". */
    fun groups(live: Collection<String>): List<Group> {
        val used = HashSet<String>()
        fun take(names: List<String>): List<String> = names.filter { used.add(it) }
        val out = ArrayList<Group>()
        out.add(Group("Switches and sticks (channels, 1000 to 2000)", take((1..16).map { "ch$it" })))
        out.add(Group("Battery (from the flight controller)", take(listOf("vbat", "curr", "capa", "batpct"))))
        out.add(Group("Link quality and signal", take(listOf("up_lq", "up_rssi1", "up_rssi2", "up_snr", "dn_lq", "dn_rssi", "dn_snr", "rf_mode", "tx_power"))))
        out.add(Group("Radio status", take(listOf("lq", "dbm", "lost", "txv", "rxv", "tlm"))))
        out.add(Group("GPS and attitude", take(listOf("sats", "gps_speed", "gps_alt", "gps_heading", "gps_lat", "gps_lon", "pitch", "roll", "yaw", "vspeed"))))
        out.add(Group("Flight mode (text)", take(listOf("fm"))))
        val other = take(live.sorted())
        if (other.isNotEmpty()) out.add(Group("Other values from the radio", other))
        out.add(Group("Advanced: the radio's own channel numbers", take((1..16).map { "raw$it" })))
        out.add(Group("Advanced: the radio's logic values", take((1..16).map { "lv$it" })))
        return out
    }

    fun names(live: Collection<String>): List<String> {
        val out = ArrayList<String>(fixed)
        for (n in live.sorted()) if (!out.contains(n)) out.add(n)
        return out
    }

    fun help(name: String): String {
        val h = help[name]
        if (h != null) return h
        if (name.startsWith("ch") && name.substring(2).toIntOrNull() != null) return "channel " + name.substring(2) + ", 1000 to 2000 microseconds"
        if (name.startsWith("raw")) return "the radio's own channel number"
        if (name.startsWith("lv")) return "the radio's logic value"
        return ""
    }
}
