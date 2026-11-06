package dev.ax12.announcer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How a live chart is scaled and written. Values with a natural range (a percentage, a channel) get a fixed scale, so a small
 * wobble does not look like a big one; the others get a scale that never gets tighter than a sensible span.
 * Plain Java, tested on a PC.
 */
public final class ChartScale {

    private static final Set<String> PERCENT = new HashSet<String>();
    private static final Set<String> NON_NEGATIVE = new HashSet<String>();
    private static final Map<String, Double> MIN_SPAN = new HashMap<String, Double>();
    private static final Set<String> WHOLE = new HashSet<String>();
    private static final Set<String> ZERO_BASED = new HashSet<String>();      // the height of the line means something: start at zero

    static {
        for (String s : new String[]{"up_lq", "dn_lq", "lq", "batpct"}) PERCENT.add(s);
        for (String s : new String[]{"vbat", "cell", "curr", "capa", "gps_speed", "sats", "tx_power", "dist", "txv", "rxv", "lost", "rf_mode"}) NON_NEGATIVE.add(s);
        for (String s : new String[]{"up_lq", "dn_lq", "lq", "batpct", "sats", "tx_power", "rf_mode", "lost", "capa", "tlm"}) WHOLE.add(s);
        for (String s : new String[]{"curr", "gps_speed", "dist", "capa", "tx_power", "lost", "sats", "rf_mode"}) ZERO_BASED.add(s);
        MIN_SPAN.put("cell", 1.0); MIN_SPAN.put("vbat", 6.0); MIN_SPAN.put("curr", 20.0); MIN_SPAN.put("capa", 500.0);
        MIN_SPAN.put("gps_speed", 30.0); MIN_SPAN.put("gps_alt", 20.0); MIN_SPAN.put("height", 20.0); MIN_SPAN.put("dist", 100.0);
        MIN_SPAN.put("up_rssi1", 40.0); MIN_SPAN.put("up_rssi2", 40.0); MIN_SPAN.put("dn_rssi", 40.0); MIN_SPAN.put("dbm", 40.0);
        MIN_SPAN.put("up_snr", 20.0); MIN_SPAN.put("dn_snr", 20.0); MIN_SPAN.put("vspeed", 10.0);
        MIN_SPAN.put("pitch", 40.0); MIN_SPAN.put("roll", 40.0); MIN_SPAN.put("yaw", 90.0); MIN_SPAN.put("sats", 10.0);
        MIN_SPAN.put("txv", 2.0); MIN_SPAN.put("rxv", 2.0); MIN_SPAN.put("tx_power", 100.0); MIN_SPAN.put("lost", 5.0);
    }

    private ChartScale() {}

    private static boolean isChannel(String name) {
        if (!name.startsWith("ch") || name.length() < 3) return false;
        try {
            int n = Integer.parseInt(name.substring(2));
            return n >= 1 && n <= 16;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** @return {low, high} of the vertical axis for the values seen (min, max) */
    public static double[] range(String name, double min, double max) {
        if (PERCENT.contains(name)) return new double[]{0.0, 100.0};
        if (isChannel(name)) return new double[]{1000.0, 2000.0};
        double minSpan = MIN_SPAN.containsKey(name) ? MIN_SPAN.get(name) : Math.max(1.0, 0.1 * Math.max(Math.abs(min), Math.abs(max)));
        if (ZERO_BASED.contains(name) && min >= 0) return new double[]{0.0, Math.max(max, minSpan) * 1.08};
        double span = max - min;
        double lo = min, hi = max;
        if (span < minSpan) {
            double mid = (min + max) / 2.0;
            lo = mid - minSpan / 2.0;
            hi = mid + minSpan / 2.0;
        }
        if (NON_NEGATIVE.contains(name) && lo < 0) {
            hi -= lo;
            lo = 0;
        }
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;
        if (NON_NEGATIVE.contains(name) && lo < 0 && min >= 0) lo = 0;        // never below zero for something that cannot be
        return new double[]{lo, hi};
    }

    /** A value for the legend: whole numbers for percentages and counts, fewer decimals for bigger numbers. */
    public static String format(String name, double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
        if (WHOLE.contains(name) || isChannel(name)) return String.format(Locale.US, "%d", Math.round(v));
        double a = Math.abs(v);
        if (a >= 100) return String.format(Locale.US, "%.0f", v);
        if (a >= 10) return String.format(Locale.US, "%.1f", v);
        return String.format(Locale.US, "%.2f", v);
    }
}
