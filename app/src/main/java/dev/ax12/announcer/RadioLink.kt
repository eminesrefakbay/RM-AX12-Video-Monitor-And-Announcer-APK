package dev.ax12.announcer

import android.content.Context
import android.os.SystemClock

/** One reading of the radio. Channel numbers are exactly what the radio reports (no unit guessed here). */
class RadioSample(
    val raw: DoubleArray,                 // 16 channel outputs
    val logic: DoubleArray,               // 16 logic values
    val numbers: Map<String, Double>,     // lq, dbm, txv, rxv, tlm, vbat, up_lq ...
    val texts: Map<String, String>,       // fm
    val info: String,                     // one line for the status text
    val cfgReady: Boolean = true          // the radio's configuration is loaded (always true for the test sliders)
)

interface RadioLink {
    val name: String
    /** @return null when started, otherwise a message to show */
    fun start(): String?
    fun stop()
    fun poll(): RadioSample?
}

/** Sliders in the test panel stand in for the radio. Values are microseconds. */
class FakeRadioLink : RadioLink {
    val values = DoubleArray(16) { 1500.0 }
    override val name = tr("test sliders")
    override fun start(): String? = null
    override fun stop() {}
    override fun poll(): RadioSample? =
        RadioSample(values.copyOf(), DoubleArray(16), HashMap(), HashMap(), tr("test sliders (no radio)"))
}

/**
 * The real radio, through the native bridge. Read-only: the bridge only calls getters.
 *  - channel outputs and radio status come from the SDK;
 *  - battery, link quality, flight mode and GPS come from the CRSF telemetry frames.
 * Each CRSF telemetry group (battery, link, flight mode, GPS ...) is dropped 5 s after its own frames stop,
 * so a rule never acts on a stale battery voltage or a stale flight mode.
 */
class AxSdkRadioLink(private val context: Context) : RadioLink {
    override val name = tr("RadioMaster SDK")

    private val crsf = CrsfState()
    @Volatile private var started = false          // the SDK may be started on a background thread
    private var requestDeviceInfo = false
    private var configRetryMs = 3000

    /** How the SDK is started: the standard way, or like RadioMaster's demo (device information first, a longer wait before asking again). */
    fun configure(demoLike: Boolean) {
        requestDeviceInfo = demoLike
        configRetryMs = if (demoLike) 6000 else 3000
    }

    /** When each step of the start happened (see bridge_core.h), or null without the SDK. */
    fun timeline(): DoubleArray? = if (AxSdkJni.loaded) AxSdkJni.nativeTimeline() else null

    override fun start(): String? {
        if (!AxSdkJni.loaded) return tr("This build has no RadioMaster SDK files (see the README)")
        StartupState.nativeBaseMs = SystemClock.elapsedRealtime()      // the bridge's own clock starts now
        val code = AxSdkJni.nativeStart(context.filesDir.absolutePath, requestDeviceInfo, configRetryMs)
        started = (code == 0 || code == 1)
        return when (code) {
            0, 1 -> null
            2 -> tr("SDK worker thread failed to start")
            3 -> tr("The SDK could not open the radio's serial link (see README: serial lock)")
            else -> tr("SDK start failed (code {0})", code)
        }
    }

    override fun stop() {
        if (started) {
            AxSdkJni.nativeStop()
            started = false
        }
    }

    override fun poll(): RadioSample? {
        if (!started) return null
        val s = AxSdkJni.nativeSnapshot()
        if (s.size < 44) return null
        val now = SystemClock.elapsedRealtime()

        var count = 0
        while (count < 60) {
            val frame = AxSdkJni.nativePopCrsf() ?: break
            crsf.feed(frame, now)
            count++
        }
        crsf.expire(now, 5000)          // each telemetry group expires on its own clock

        val numbers = HashMap<String, Double>()
        numbers["lq"] = s[32]
        numbers["dbm"] = s[33]
        numbers["lost"] = s[34]
        numbers["txv"] = s[35]
        numbers["rxv"] = s[36]
        numbers["tlm"] = s[37]
        numbers.putAll(crsf.numbers())

        val cfgReady = s[40] == 1.0
        val sb = StringBuilder()
        sb.append("SDK refreshes ").append(s[38].toLong())
            .append("   mixout msgs ").append(s[41].toLong())
            .append("   channels listed ").append(s[42].toLong())
            .append("\nradio config: ").append(if (cfgReady) "loaded" else "NOT loaded yet")
        if (s[43] == 1.0) sb.append("\n!! radio firmware does not match this SDK")
        if (!cfgReady) sb.append("\nwaiting for the radio; if this stays, the SDK cannot reach it (serial lock, see README)")
        sb.append("\nCRSF frames ok ").append(crsf.framesOk()).append(" / bad crc ").append(crsf.framesBadCrc())
            .append(" / other ").append(crsf.framesIgnored())
        val info = sb.toString()
        return RadioSample(s.copyOfRange(0, 16), s.copyOfRange(16, 32), numbers, HashMap(crsf.texts()), info, cfgReady)
    }
}

/** Builds the variable lists the rules see. */
object Variables {
    fun numbers(sample: RadioSample, scaler: ChannelScaler, cells: Int): HashMap<String, Double> {
        val m = HashMap<String, Double>()
        for (i in 0 until 16) {
            m["ch" + (i + 1)] = scaler.toMicros(sample.raw[i])
            m["raw" + (i + 1)] = sample.raw[i]
            m["lv" + (i + 1)] = sample.logic[i]
        }
        m.putAll(sample.numbers)
        BatteryProfiles.addDerived(m, cells)          // "cell": volts per cell, while a pack is connected
        return m
    }
}
