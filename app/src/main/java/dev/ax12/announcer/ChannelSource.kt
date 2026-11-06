package dev.ax12.announcer

/**
 * Where the 16 channel values come from. Values are in MICROSECONDS (1000..2000),
 * the same units as the INAV mode ranges, so a rule like  ch5 >= 1500  means what you expect.
 */
interface ChannelSource {
    /** @return 16 values, or null when no data is available. */
    fun read(): DoubleArray?
}

/** Sliders in the app's test panel stand in for the radio. */
class FakeChannelSource : ChannelSource {
    val values = DoubleArray(16) { 1500.0 }
    override fun read(): DoubleArray? = values.copyOf()
}

/**
 * The real source, to be connected to the RadioMaster AX SDK.
 *
 * How (from the SDK API guide):
 *  1. initialize(appDataPath) and setMessageCallback() once at start-up.
 *  2. On RC_MSGID_MIXOUT_UPDATE (about every 40 ms) call rcGetChOutList() and copy the
 *     channel values into [latest]. Do not call the getters in a tight loop.
 *  3. Convert to microseconds. If the SDK reports percent (-100..100): us = 1500 + 5 * percent.
 *     CHECK the real range with the print-out in the test app before trusting this.
 *  4. Call only getters (rcGet*). Never rcSet*, never the save/task functions.
 *
 * The SDK is C++, so this needs a small JNI bridge. Until then [available] stays false and the
 * app uses the test sliders.
 */
class AxSdkChannelSource : ChannelSource {
    val available: Boolean = false
    @Volatile private var latest: DoubleArray? = null

    /** Called from the SDK bridge (on any thread) with the newest 16 values in microseconds. */
    fun publish(valuesUs: DoubleArray) { latest = valuesUs.copyOf() }

    override fun read(): DoubleArray? = latest
}
