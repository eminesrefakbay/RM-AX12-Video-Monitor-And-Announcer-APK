package dev.ax12.announcer

import android.content.Context
import android.os.SystemClock

/** One reading of the radio. Channel numbers are exactly what the radio reports (no unit guessed here). */
class RadioSample(
    val raw: DoubleArray,                 // 16 channel outputs
    val logic: DoubleArray,               // 16 logic values
    val numbers: Map<String, Double>,     // lq, dbm, txv, rxv, tlm, vbat, up_lq ...
    val texts: Map<String, String>,       // fm
    val info: String                      // one line for the status text
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
    override val name = "test sliders"
    override fun start(): String? = null
    override fun stop() {}
    override fun poll(): RadioSample? =
        RadioSample(values.copyOf(), DoubleArray(16), HashMap(), HashMap(), "test sliders (no radio)")
}

/**
 * The real radio, through the native bridge. Read-only: the bridge only calls getters.
 *  - channel outputs and radio status come from the SDK;
 *  - battery, link quality, flight mode and GPS come from the CRSF telemetry frames.
 * CRSF values older than 5 s are dropped, so a rule never acts on a stale battery voltage.
 */
class AxSdkRadioLink(private val context: Context) : RadioLink {
    override val name = "RadioMaster SDK"

    private val crsf = CrsfParser()
    private val crsfNums = HashMap<String, Double>()
    private val crsfTexts = HashMap<String, String>()
    private var lastCrsfMs = 0L
    private var started = false

    override fun start(): String? {
        if (!AxSdkJni.loaded) return "This build has no RadioMaster SDK files (see the README)"
        val code = AxSdkJni.nativeStart(context.filesDir.absolutePath)
        started = (code == 0 || code == 1)
        return when (code) {
            0, 1 -> null
            2 -> "SDK worker thread failed to start"
            3 -> "The SDK could not open the radio's serial link (see README: serial lock)"
            else -> "SDK start failed (code $code)"
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
        if (s.size < 40) return null
        val now = SystemClock.elapsedRealtime()

        var count = 0
        while (count < 60) {
            val frame = AxSdkJni.nativePopCrsf() ?: break
            if (crsf.feed(frame, crsfNums, crsfTexts)) lastCrsfMs = now
            count++
        }
        if (lastCrsfMs != 0L && now - lastCrsfMs > 5000) {
            crsfNums.clear()
            crsfTexts.clear()
        }

        val numbers = HashMap<String, Double>()
        numbers["lq"] = s[32]
        numbers["dbm"] = s[33]
        numbers["lost"] = s[34]
        numbers["txv"] = s[35]
        numbers["rxv"] = s[36]
        numbers["tlm"] = s[37]
        numbers.putAll(crsfNums)

        val info = "SDK refreshes " + s[38].toLong() + "   CRSF frames ok " + crsf.framesOk +
                " / bad crc " + crsf.framesBadCrc + " / other " + crsf.framesIgnored
        return RadioSample(s.copyOfRange(0, 16), s.copyOfRange(16, 32), numbers, HashMap(crsfTexts), info)
    }
}

/** Builds the variable lists the rules see. */
object Variables {
    fun numbers(sample: RadioSample, scaler: ChannelScaler): Map<String, Double> {
        val m = HashMap<String, Double>()
        for (i in 0 until 16) {
            m["ch" + (i + 1)] = scaler.toMicros(sample.raw[i])
            m["raw" + (i + 1)] = sample.raw[i]
            m["lv" + (i + 1)] = sample.logic[i]
        }
        m.putAll(sample.numbers)
        return m
    }
}
