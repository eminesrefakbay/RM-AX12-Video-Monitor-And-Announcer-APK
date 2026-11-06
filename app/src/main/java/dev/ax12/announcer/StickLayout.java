package dev.ax12.announcer;

/**
 * Which channel moves which way on each stick, for the stick overlay.
 * Mode 1: left = pitch + yaw,      right = throttle + roll
 * Mode 2: left = throttle + yaw,   right = pitch + roll       (the usual one)
 * Mode 3: left = throttle + roll,  right = pitch + yaw
 * Mode 4: left = pitch + roll,     right = throttle + yaw
 * Plain Java, tested on a PC.
 */
public final class StickLayout {

    /** Channel numbers (1 to 16) for the four axes of the two sticks. */
    public final int leftVertical, leftHorizontal, rightVertical, rightHorizontal;
    /** A bit per axis, set when that axis is drawn the other way round. */
    public static final int INV_LEFT_V = 1, INV_LEFT_H = 2, INV_RIGHT_V = 4, INV_RIGHT_H = 8;

    private StickLayout(int lv, int lh, int rv, int rh) {
        this.leftVertical = lv;
        this.leftHorizontal = lh;
        this.rightVertical = rv;
        this.rightHorizontal = rh;
    }

    public static StickLayout forMode(int mode, int roll, int pitch, int throttle, int yaw) {
        switch (mode) {
            case 1: return new StickLayout(pitch, yaw, throttle, roll);
            case 3: return new StickLayout(throttle, roll, pitch, yaw);
            case 4: return new StickLayout(pitch, roll, throttle, yaw);
            default: return new StickLayout(throttle, yaw, pitch, roll);
        }
    }

    /** -1 (low) .. 0 (centre) .. +1 (high) for a value in microseconds. */
    public static double norm(double us) {
        double v = (us - 1500.0) / 500.0;
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /** Position of one axis for drawing: normalised, and flipped if asked. */
    public static double axis(double us, boolean invert) {
        double v = norm(us);
        return invert ? -v : v;
    }

    public static String axisName(StickLayout l, int mode, boolean leftStick, boolean vertical, int roll, int pitch, int throttle, int yaw) {
        int ch = leftStick ? (vertical ? l.leftVertical : l.leftHorizontal) : (vertical ? l.rightVertical : l.rightHorizontal);
        if (ch == roll) return "R";
        if (ch == pitch) return "P";
        if (ch == throttle) return "T";
        if (ch == yaw) return "Y";
        return "?";
    }
}
