package dev.ax12.announcer

/**
 * The native bridge (libax12bridge.so). It only exists when the app was built with the RadioMaster
 * SDK files; without them [loaded] is false and the app runs on the test sliders.
 */
object AxSdkJni {
    val loaded: Boolean = try {
        System.loadLibrary("ax12bridge")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic external fun nativeVersion(): String
    /**
     * 0 = started, 1 = already started, 2 = worker thread failed, 3 = could not open the radio's serial link.
     * @param requestDeviceInfo ask the radio for its device information before the config (RadioMaster's demo does)
     * @param configRetryMs how long to wait before asking for the config again
     */
    @JvmStatic external fun nativeStart(appDataPath: String, requestDeviceInfo: Boolean, configRetryMs: Int): Int
    /** when each step of the start happened, in ms since the start (layout in bridge_core.h; -1 = not yet) */
    @JvmStatic external fun nativeTimeline(): DoubleArray
    @JvmStatic external fun nativeStop()
    /** 40 values, layout in bridge_core.h */
    @JvmStatic external fun nativeSnapshot(): DoubleArray
    /** oldest queued CRSF frame, or null */
    @JvmStatic external fun nativePopCrsf(): ByteArray?
}
