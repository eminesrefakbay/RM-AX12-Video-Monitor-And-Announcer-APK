package dev.ax12.announcer;

/**
 * Converts the radio's channel output numbers to microseconds (1000..2000).
 * The SDK documents the output as "real-time channel output" without naming the unit, so the
 * scaler guesses it from the numbers seen so far and only ever moves to a larger unit:
 *   PERCENT  (about -100..100)    ->  1500 + 5 * v
 *   PERMILLE (about -1000..1000)  ->  1500 + 0.5 * v      (also fits -1024..1024)
 *   MICROS   (about 900..2100)    ->  v
 */
public final class ChannelScaler {

    public enum Mode { PERCENT, PERMILLE, MICROS }

    private Mode mode;
    private final boolean auto;
    private double maxAbs = 0;

    public ChannelScaler(boolean auto, Mode initial) {
        this.auto = auto;
        this.mode = initial;
    }

    public Mode mode() { return mode; }

    /** Feed the newest raw values. @return true if the unit changed (the rule engine should restart). */
    public boolean observe(double[] raw) {
        if (!auto) return false;
        for (double v : raw) maxAbs = Math.max(maxAbs, Math.abs(v));
        Mode next = mode;
        if (maxAbs > 1100) next = Mode.MICROS;
        else if (maxAbs > 110 && mode == Mode.PERCENT) next = Mode.PERMILLE;
        if (next != mode) {
            mode = next;
            return true;
        }
        return false;
    }

    public double toMicros(double v) {
        switch (mode) {
            case PERCENT:  return 1500.0 + 5.0 * v;
            case PERMILLE: return 1500.0 + 0.5 * v;
            default:       return v;
        }
    }
}
