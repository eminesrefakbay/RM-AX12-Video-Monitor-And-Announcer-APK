// AX-HUD antenna tracker firmware. Copyright (C) 2026 eminesrefakbay. SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file of the AX-HUD app.
// Where to point: the geometry of the tracker. Plain C++, no Arduino headers, tested on a PC (tracker/test/test_core.cpp).
//
// Mechanical convention (document it on your build): the pan servo at 90 degrees looks straight ahead of the tracker's base (its "forward"
// axis, the way the compass is mounted); the tilt servo at 0 degrees looks at the horizon ahead, at 90 degrees straight up, and at
// 180 degrees at the horizon behind. Two ordinary 180-degree servos then cover every direction: when the aircraft is behind the base, the
// pan servo turns to the opposite side and the tilt servo goes past vertical (the "flip").
#pragma once
#include <math.h>

namespace trk {

static const double PI_ = 3.14159265358979323846;
static const double EARTH_R = 6371000.0;

inline double rad(double d) { return d * PI_ / 180.0; }
inline double deg(double r) { return r * 180.0 / PI_; }
inline double wrap360(double a) { a = fmod(a, 360.0); return a < 0 ? a + 360.0 : a; }
inline double wrap180(double a) { a = wrap360(a); return a > 180.0 ? a - 360.0 : a; }
inline double clampd(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

struct Aim {
    double azimuthDeg = 0;      // 0 = north, clockwise, true
    double elevationDeg = 0;    // 0 = horizon, 90 = straight up
    double distanceM = 0;
};

// Great-circle distance and initial bearing from point 1 to point 2.
inline void distanceBearing(double lat1, double lon1, double lat2, double lon2, double& distM, double& bearingDeg) {
    double p1 = rad(lat1), p2 = rad(lat2), dl = rad(lon2 - lon1);
    double a = sin((p2 - p1) / 2) * sin((p2 - p1) / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2);
    distM = 2 * EARTH_R * asin(sqrt(a < 1.0 ? a : 1.0));
    double y = sin(dl) * cos(p2);
    double x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl);
    bearingDeg = wrap360(deg(atan2(y, x)));
}

// The point reached from (lat, lon) after distM metres on the given bearing.
inline void destination(double lat, double lon, double bearingDeg, double distM, double& outLat, double& outLon) {
    double d = distM / EARTH_R, b = rad(bearingDeg), p1 = rad(lat), l1 = rad(lon);
    double p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b));
    double l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2));
    outLat = deg(p2);
    outLon = deg(l2);
}

// The aircraft's position after "seconds" more flight at its ground speed and course (the position is old by the time it arrives).
// Nothing is guessed for a slow or a very old position.
inline void deadReckon(double lat, double lon, double speedKmh, double courseDeg, double seconds, double& outLat, double& outLon) {
    if (speedKmh < 3.0 || seconds <= 0.0) { outLat = lat; outLon = lon; return; }
    if (seconds > 10.0) seconds = 10.0;
    destination(lat, lon, courseDeg, speedKmh / 3.6 * seconds, outLat, outLon);
}

// heightM = the aircraft's height above the tracker's antennas.
inline Aim aim(double trackerLat, double trackerLon, double aircraftLat, double aircraftLon, double heightM) {
    Aim a;
    distanceBearing(trackerLat, trackerLon, aircraftLat, aircraftLon, a.distanceM, a.azimuthDeg);
    if (a.distanceM < 0.5) a.elevationDeg = heightM > 0.5 ? 90.0 : 0.0;       // right above (or at) the tracker
    else a.elevationDeg = clampd(deg(atan2(heightM, a.distanceM)), 0.0, 90.0);
    return a;
}

struct ServoAngles {
    double pan = 90, tilt = 0;  // degrees, 0..180
    bool flipped = false;
};

// relBearingDeg = the bearing of the aircraft relative to the base's forward axis, -180..180. A little hysteresis keeps the tracker from
// flipping back and forth when the aircraft is flying across the line at 90 degrees. The pan stays within the servo's 0..180.
inline ServoAngles servoAngles(double relBearingDeg, double elevationDeg, bool wasFlipped) {
    double rel = wrap180(relBearingDeg);
    ServoAngles s;
    s.flipped = wasFlipped ? (fabs(rel) > 85.0) : (fabs(rel) > 95.0);
    double r = s.flipped ? (rel > 0 ? rel - 180.0 : rel + 180.0) : rel;
    s.pan = clampd(90.0 + r, 0.0, 180.0);
    s.tilt = clampd(s.flipped ? 180.0 - elevationDeg : elevationDeg, 0.0, 180.0);
    return s;
}

// A servo that may not move faster than maxRate degrees a second: no jerks, no cable pulling.
struct Slew {
    double value = 90;
    double maxRate = 90;
    bool started = false;
    double step(double target, double dtSeconds) {
        if (!started) { value = target; started = true; return value; }
        double d = target - value, m = maxRate * dtSeconds;
        value += clampd(d, -m, m);
        return value;
    }
};

// A heading that is smoothed without a jump at 359 -> 0.
inline double smoothAngle(double current, double target, double factor) {
    return wrap360(current + wrap180(target - current) * clampd(factor, 0.0, 1.0));
}

// Servo angle (0..180) to a pulse width in microseconds, with trims and end points.
inline int pulseUs(double angleDeg, int minUs, int maxUs) {
    return (int)(minUs + (maxUs - minUs) * clampd(angleDeg, 0.0, 180.0) / 180.0 + 0.5);
}

}  // namespace trk
