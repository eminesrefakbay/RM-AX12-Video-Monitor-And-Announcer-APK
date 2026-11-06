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
        "vbat" to tn("battery volts (flight controller); divide by the cell count for volts per cell"),
        "curr" to tn("battery current, amps"),
        "capa" to tn("capacity used, mAh"),
        "batpct" to tn("battery remaining, percent"),
        "up_lq" to tn("uplink link quality, percent (0 = no link)"),
        "up_rssi1" to tn("uplink signal strength, antenna 1, dBm (negative)"),
        "up_rssi2" to tn("uplink signal strength, antenna 2, dBm (negative)"),
        "up_snr" to tn("uplink signal to noise, dB"),
        "dn_lq" to tn("downlink link quality, percent"),
        "dn_rssi" to tn("downlink signal strength, dBm"),
        "dn_snr" to tn("downlink signal to noise, dB"),
        "rf_mode" to tn("ExpressLRS packet rate mode number"),
        "tx_power" to tn("transmitter power, mW"),
        "lq" to tn("link quality from the radio, percent"),
        "dbm" to tn("signal strength from the radio"),
        "lost" to tn("radio's lost-frame counter"),
        "txv" to tn("radio battery volts"),
        "rxv" to tn("receiver volts"),
        "tlm" to tn("telemetry present: 1 yes, 0 lost"),
        "sats" to tn("GPS satellites"),
        "gps_speed" to tn("ground speed, km/h"),
        "gps_alt" to tn("GPS altitude, metres"),
        "gps_heading" to tn("GPS heading, degrees"),
        "gps_lat" to tn("latitude"),
        "gps_lon" to tn("longitude"),
        "pitch" to tn("pitch angle, degrees"),
        "roll" to tn("roll angle, degrees"),
        "yaw" to tn("yaw / heading, degrees"),
        "vspeed" to tn("climb rate, m/s"),
        "fm" to tn("flight mode name from the flight controller (text)")
    )

    class Group(val title: String, val names: List<String>)

    /** The picker's sections. Every name appears once; live names the catalog does not know go to "Other". */
    fun groups(live: Collection<String>): List<Group> {
        val used = HashSet<String>()
        fun take(names: List<String>): List<String> = names.filter { used.add(it) }
        val out = ArrayList<Group>()
        out.add(Group(tr("Switches and sticks (channels, 1000 to 2000)"), take((1..16).map { "ch$it" })))
        out.add(Group(tr("Battery (from the flight controller)"), take(listOf("vbat", "curr", "capa", "batpct"))))
        out.add(Group(tr("Link quality and signal"), take(listOf("up_lq", "up_rssi1", "up_rssi2", "up_snr", "dn_lq", "dn_rssi", "dn_snr", "rf_mode", "tx_power"))))
        out.add(Group(tr("Radio status"), take(listOf("lq", "dbm", "lost", "txv", "rxv", "tlm"))))
        out.add(Group(tr("GPS and attitude"), take(listOf("sats", "gps_speed", "gps_alt", "gps_heading", "gps_lat", "gps_lon", "pitch", "roll", "yaw", "vspeed"))))
        out.add(Group(tr("Flight mode (text)"), take(listOf("fm"))))
        val other = take(live.sorted())
        if (other.isNotEmpty()) out.add(Group(tr("Other values from the radio"), other))
        out.add(Group(tr("Advanced: the radio's own channel numbers"), take((1..16).map { "raw$it" })))
        out.add(Group(tr("Advanced: the radio's logic values"), take((1..16).map { "lv$it" })))
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
        if (name.startsWith("ch") && name.substring(2).toIntOrNull() != null) return tr("channel {0}, 1000 to 2000 microseconds", name.substring(2))
        if (name.startsWith("raw")) return tr("the radio's own channel number")
        if (name.startsWith("lv")) return tr("the radio's logic value")
        return ""
    }
}
