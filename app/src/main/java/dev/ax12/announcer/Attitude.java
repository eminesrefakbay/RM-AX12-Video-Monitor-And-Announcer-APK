// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/** The small maths of the horizon and the compass: angles that wrap, a smooth follow, the heading and the way home. */
public final class Attitude {
    private Attitude() {}

    /** An angle in -180 to 180. */
    public static double wrap180(double a) {
        double r = ((a + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return r == -180.0 ? 180.0 : r;
    }

    /** An angle in 0 to 360. */
    public static double wrap360(double a) { return ((a % 360.0) + 360.0) % 360.0; }

    /** Follows a target with a time constant: after tauMs it has covered about two thirds of the way. NaN until the first value. */
    public static double smooth(double current, double target, double dtMs, double tauMs) {
        if (Double.isNaN(target)) return current;
        if (Double.isNaN(current) || tauMs <= 0) return target;
        double k = 1.0 - Math.exp(-Math.max(0.0, dtMs) / tauMs);
        return current + (target - current) * k;
    }

    /** The same for a heading: it takes the short way round, 359 to 1 degree is a step of 2, not 358. */
    public static double smoothAngle(double current, double target, double dtMs, double tauMs) {
        if (Double.isNaN(target)) return current;
        if (Double.isNaN(current) || tauMs <= 0) return wrap360(target);
        double delta = wrap180(target - current);
        double k = 1.0 - Math.exp(-Math.max(0.0, dtMs) / tauMs);
        return wrap360(current + delta * k);
    }

    /** The heading of the aircraft: the flight controller's yaw, or the GPS course when there is no yaw. NaN when neither is there. */
    public static double heading(Map<String, Double> v) {
        Double yaw = v.get("yaw");
        if (yaw != null && !yaw.isNaN()) return wrap360(yaw);
        Double course = v.get("gps_heading");
        if (course != null && !course.isNaN()) return wrap360(course);
        return Double.NaN;
    }

    /** The direction from the aircraft to home, in degrees from north: the aircraft is eastM and northM metres from home. */
    public static double bearingToHome(double eastM, double northM) {
        if (Double.isNaN(eastM) || Double.isNaN(northM)) return Double.NaN;
        if (Math.abs(eastM) < 0.5 && Math.abs(northM) < 0.5) return Double.NaN;       // on top of home: no direction
        return wrap360(Math.toDegrees(Math.atan2(-eastM, -northM)));
    }

    /** N, NE, E ... for a heading. */
    public static String cardinal(double deg) {
        if (Double.isNaN(deg)) return "";
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.floor((wrap360(deg) + 22.5) / 45.0) % 8];
    }
}
