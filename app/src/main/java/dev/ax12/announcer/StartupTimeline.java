package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The start-up timeline: when each step of starting the app and the radio link happened, in milliseconds since the app's process
 * started. Native steps come from the bridge (relative to the start of the SDK) and are shifted onto the same clock.
 * Observe-only: nothing here changes what the app does. Plain Java, tested on a PC.
 */
public final class StartupTimeline {

    // The numbering of the native timeline: it must match bridge_core.h (a test compares them).
    public static final int N_INIT = 0, N_CALLBACK = 1, N_FIRST_MSG = 2, N_FIRST_MIXOUT = 3, N_FWINFO = 4, N_CFG_END = 5,
        N_CHANNELS = 6, N_DEVINFO = 7, N_REQ_COUNT = 8, N_REQ0 = 9, N_MAX_REQ = 8, N_SIZE = N_REQ0 + N_MAX_REQ;

    public static final String PROCESS = "app process started";
    public static final String APP_CREATED = "application created";
    public static final String SCREEN_CREATED = "screen created";
    public static final String SCREEN_RESUMED = "screen resumed";
    public static final String SDK_CALLED = "SDK start called";
    public static final String SDK_RETURNED = "SDK start returned";
    public static final String SDK_INIT = "SDK initialize() returned";
    public static final String CALLBACK = "message callback set";
    public static final String DEVINFO_REQ = "device info requested";
    public static final String CFG_REQ = "config requested";
    public static final String FIRST_MSG = "first SDK message";
    public static final String FIRST_MIXOUT = "first channel message (mixout)";
    public static final String FWINFO = "device info answered";
    public static final String CFG_LOADED = "config loaded (GETDEVCFG_END)";
    public static final String CHANNELS = "channel list available";
    public static final String FIRST_UI = "first sample with config in the app";

    private final Map<String, Double> events = new LinkedHashMap<String, Double>();

    public synchronized void clear() { events.clear(); }

    /** Records a step; the first time wins, so marking again later changes nothing. A negative time (= not yet) is ignored. */
    public synchronized void mark(String label, double ms) {
        if (ms < 0 || Double.isNaN(ms) || events.containsKey(label)) return;
        events.put(label, ms);
    }

    /** @return the time of a step, or NaN */
    public synchronized double get(String label) {
        Double v = events.get(label);
        return v == null ? Double.NaN : v;
    }

    /** Adds the native steps: their times count from the start of the SDK, which began at baseMs on this clock. */
    public synchronized void mergeNative(double[] tl, double baseMs) {
        if (tl == null || tl.length < N_SIZE) return;
        mark(SDK_INIT, shift(tl[N_INIT], baseMs));
        mark(CALLBACK, shift(tl[N_CALLBACK], baseMs));
        mark(DEVINFO_REQ, shift(tl[N_DEVINFO], baseMs));
        mark(FIRST_MSG, shift(tl[N_FIRST_MSG], baseMs));
        mark(FIRST_MIXOUT, shift(tl[N_FIRST_MIXOUT], baseMs));
        mark(FWINFO, shift(tl[N_FWINFO], baseMs));
        mark(CFG_LOADED, shift(tl[N_CFG_END], baseMs));
        mark(CHANNELS, shift(tl[N_CHANNELS], baseMs));
        int shown = Math.min((int) tl[N_REQ_COUNT], N_MAX_REQ);
        for (int i = 0; i < shown; i++) mark(i == 0 ? CFG_REQ : CFG_REQ + " again #" + i, shift(tl[N_REQ0 + i], baseMs));
    }

    private static double shift(double nativeMs, double baseMs) { return nativeMs < 0 ? -1 : baseMs + nativeMs; }

    /** The steps in the order they happened, one per line. */
    public synchronized List<String> lines() {
        List<Map.Entry<String, Double>> all = new ArrayList<Map.Entry<String, Double>>(events.entrySet());
        java.util.Collections.sort(all, new java.util.Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) { return Double.compare(a.getValue(), b.getValue()); }
        });
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Double> e : all) out.add(String.format(Locale.US, "%7.0f ms  %s", e.getValue(), e.getKey()));
        return out;
    }

    private String t(String label) {
        double v = get(label);
        return Double.isNaN(v) ? "-" : String.format(Locale.US, "%.0f", v);
    }

    private int requests() {
        int n = 0;
        for (String k : events.keySet()) if (k.startsWith(CFG_REQ)) n++;
        return n;
    }

    /** One line to keep in the log of launches. */
    public synchronized String summary(String method) {
        double called = get(SDK_CALLED), returned = get(SDK_RETURNED);
        String took = Double.isNaN(called) || Double.isNaN(returned) ? "-" : String.format(Locale.US, "%.0f", returned - called);
        return method + " | start " + t(SDK_CALLED) + " | call took " + took + " | init done " + t(SDK_INIT) + " | mixout " + t(FIRST_MIXOUT)
            + " | config " + t(CFG_LOADED) + " | requests " + requests() + " | graph " + t(FIRST_UI);
    }

    /** Adds a line to the end of a log and keeps only the last `keep` lines. */
    public static String appendLine(String log, String line, int keep) {
        List<String> all = new ArrayList<String>();
        if (log != null) for (String l : log.split("\\r?\\n")) if (!l.trim().isEmpty()) all.add(l);
        all.add(line);
        while (all.size() > Math.max(1, keep)) all.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String l : all) sb.append(l).append('\n');
        return sb.toString();
    }
}
