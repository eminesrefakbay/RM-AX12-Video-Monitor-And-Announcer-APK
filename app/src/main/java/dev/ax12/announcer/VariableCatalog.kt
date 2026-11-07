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
        "vbat" to tn("Battery volts (flight controller); divide by the cell count for volts per cell"),
        "cell" to tn("Volts per cell: battery volts divided by the cells of the active battery profile"),
        "curr" to tn("Battery current, amps"),
        "capa" to tn("Capacity used, mAh"),
        "batpct" to tn("Battery remaining, percent"),
        "up_lq" to tn("Uplink link quality, percent (0 = no link)"),
        "up_rssi1" to tn("Uplink signal strength, antenna 1, dBm (negative)"),
        "up_rssi2" to tn("Uplink signal strength, antenna 2, dBm (negative)"),
        "up_snr" to tn("Uplink signal to noise, dB"),
        "dn_lq" to tn("Downlink link quality, percent"),
        "dn_rssi" to tn("Downlink signal strength, dBm"),
        "dn_snr" to tn("Downlink signal to noise, dB"),
        "rf_mode" to tn("ExpressLRS packet rate mode number"),
        "tx_power" to tn("Transmitter power, mW"),
        "lq" to tn("Link quality from the radio, percent"),
        "dbm" to tn("Signal strength from the radio"),
        "lost" to tn("Radio's lost-frame counter"),
        "txv" to tn("Radio battery volts"),
        "rxv" to tn("Receiver volts"),
        "tlm" to tn("Telemetry present: 1 yes, 0 lost"),
        "sats" to tn("GPS satellites"),
        "gps_speed" to tn("Ground speed, km/h"),
        "gps_alt" to tn("GPS altitude, meters"),
        "gps_heading" to tn("GPS heading, degrees"),
        "height" to tn("Height above the arming point, meters (while armed)"),
        "dist" to tn("Distance from home, meters (while armed)"),
        "gps_lat" to tn("Latitude"),
        "gps_lon" to tn("Longitude"),
        "pitch" to tn("Pitch angle, degrees"),
        "roll" to tn("Roll angle, degrees"),
        "yaw" to tn("Yaw / heading, degrees"),
        "vspeed" to tn("Climb rate, m/s"),
        "fm" to tn("Flight mode name from the flight controller (text)")
    )

    class Group(val title: String, val names: List<String>)

    /** The picker's sections. Every name appears once; live names the catalog does not know go to "Other". */
    fun groups(live: Collection<String>): List<Group> {
        val used = HashSet<String>()
        fun take(names: List<String>): List<String> = names.filter { used.add(it) }
        val out = ArrayList<Group>()
        out.add(Group(tr("Switches and sticks (channels, 1000 to 2000)"), take((1..16).map { "ch$it" })))
        out.add(Group(tr("Battery (from the flight controller)"), take(listOf("vbat", "cell", "curr", "capa", "batpct"))))
        out.add(Group(tr("Link quality and signal"), take(listOf("up_lq", "up_rssi1", "up_rssi2", "up_snr", "dn_lq", "dn_rssi", "dn_snr", "rf_mode", "tx_power"))))
        out.add(Group(tr("Radio status"), take(listOf("lq", "dbm", "lost", "txv", "rxv", "tlm"))))
        out.add(Group(tr("GPS and attitude"), take(listOf("sats", "gps_speed", "gps_alt", "height", "dist", "gps_heading", "gps_lat", "gps_lon", "pitch", "roll", "yaw", "vspeed"))))
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
        if (name.startsWith("ch") && name.substring(2).toIntOrNull() != null) return tr("Channel {0}, 1000 to 2000 microseconds", name.substring(2))
        if (name.startsWith("raw")) return tr("The radio's own channel number")
        if (name.startsWith("lv")) return tr("The radio's logic value")
        return ""
    }

    // a short name with its unit, for the legend of a chart (translated when shown)
    private val chartLabels = mapOf(
        "up_lq" to tn("Link quality %"), "lq" to tn("Link quality %"), "dn_lq" to tn("Downlink quality %"),
        "cell" to tn("Cell volts"), "vbat" to tn("Battery volts"), "curr" to tn("Current A"), "capa" to tn("Capacity used mAh"), "batpct" to tn("Battery %"),
        "gps_speed" to tn("Speed km/h"), "gps_alt" to tn("GPS altitude m"), "height" to tn("Height m"), "dist" to tn("Distance m"),
        "sats" to tn("Satellites"), "gps_heading" to tn("Heading deg"), "vspeed" to tn("Climb m/s"),
        "pitch" to tn("Pitch deg"), "roll" to tn("Roll deg"), "yaw" to tn("Yaw deg"),
        "up_rssi1" to tn("Signal dBm"), "up_rssi2" to tn("Signal dBm"), "dn_rssi" to tn("Signal dBm"), "dbm" to tn("Signal dBm"),
        "up_snr" to tn("Signal to noise dB"), "dn_snr" to tn("Signal to noise dB"),
        "tx_power" to tn("Power mW"), "txv" to tn("Radio volts"), "rxv" to tn("Receiver volts"),
        "lost" to tn("Lost frames"), "rf_mode" to tn("Packet rate mode"), "tlm" to tn("Telemetry")
    )

    fun chartLabel(name: String): String {
        val l = chartLabels[name]
        if (l != null) return tr(l)
        if (name.startsWith("ch") && name.substring(2).toIntOrNull() != null) return tr("Channel {0}", name.substring(2))
        return name
    }
}
