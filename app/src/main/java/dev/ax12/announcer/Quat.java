// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * Unit quaternions as plain arrays {w, x, y, z}, for the attitude of the 3D replay. The log holds the attitude about once a second;
 * squad() makes a smooth turn between those samples (it passes through every sample and has no corner at them, which slerp alone has).
 * World axes: x east, y north, z up. Body axes: x right, y forward, z up.
 */
public final class Quat {
    private Quat() {}

    public static double[] identity() { return new double[]{1, 0, 0, 0}; }

    public static double[] mul(double[] a, double[] b) {
        return new double[]{
            a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
            a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
            a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
            a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    public static double[] conj(double[] q) { return new double[]{q[0], -q[1], -q[2], -q[3]}; }

    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]; }

    public static double[] normalize(double[] q) {
        double n = Math.sqrt(dot(q, q));
        if (n < 1e-12) return identity();
        return new double[]{q[0] / n, q[1] / n, q[2] / n, q[3] / n};
    }

    /** A rotation of an angle (radians) about a unit axis. */
    public static double[] axisAngle(double ax, double ay, double az, double rad) {
        double s = Math.sin(rad / 2);
        return new double[]{Math.cos(rad / 2), ax * s, ay * s, az * s};
    }

    /**
     * The attitude of the aircraft from its three angles in degrees: heading clockwise from north, pitch nose up, roll right side down.
     * The rotation is heading, then pitch about the right axis, then roll about the forward axis.
     */
    public static double[] fromHeadingPitchRoll(double headingDeg, double pitchDeg, double rollDeg) {
        double[] qz = axisAngle(0, 0, 1, -Math.toRadians(headingDeg));
        double[] qx = axisAngle(1, 0, 0, Math.toRadians(pitchDeg));
        double[] qy = axisAngle(0, 1, 0, Math.toRadians(rollDeg));
        return normalize(mul(mul(qz, qx), qy));
    }

    /** Turns a vector by the rotation. */
    public static double[] rotate(double[] q, double x, double y, double z) {
        double[] p = mul(mul(q, new double[]{0, x, y, z}), conj(q));
        return new double[]{p[1], p[2], p[3]};
    }

    /** The same rotation with the sign that is on the near side of the other one (q and -q are the same turn). */
    public static double[] nearSide(double[] q, double[] other) {
        return dot(q, other) < 0 ? new double[]{-q[0], -q[1], -q[2], -q[3]} : q;
    }

    /** The shortest turn from a to b at a fraction u (0 to 1). */
    public static double[] slerp(double[] a, double[] b0, double u) {
        double[] b = nearSide(b0, a);
        double d = Math.min(1.0, dot(a, b));
        if (d > 0.9995) {                                   // nearly the same: a straight blend is exact enough, and safe
            return normalize(new double[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u});
        }
        double th = Math.acos(d), s = Math.sin(th);
        double wa = Math.sin((1 - u) * th) / s, wb = Math.sin(u * th) / s;
        return new double[]{wa * a[0] + wb * b[0], wa * a[1] + wb * b[1], wa * a[2] + wb * b[2], wa * a[3] + wb * b[3]};
    }

    /** Logarithm of a unit quaternion: a vector (x, y, z) of half the rotation angle times the axis. */
    public static double[] log(double[] q) {
        double vn = Math.sqrt(q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (vn < 1e-12) return new double[]{0, 0, 0};
        double k = Math.atan2(vn, q[0]) / vn;
        return new double[]{q[1] * k, q[2] * k, q[3] * k};
    }

    public static double[] exp(double x, double y, double z) {
        double vn = Math.sqrt(x * x + y * y + z * z);
        if (vn < 1e-12) return identity();
        double s = Math.sin(vn) / vn;
        return new double[]{Math.cos(vn), x * s, y * s, z * s};
    }

    /** The inner control point of squad at q1, between its neighbours q0 and q2. */
    public static double[] control(double[] q0, double[] q1, double[] q2) {
        double[] ci = conj(q1);
        double[] a = log(mul(ci, nearSide(q2, q1)));
        double[] b = log(mul(ci, nearSide(q0, q1)));
        double[] e = exp(-(a[0] + b[0]) / 4, -(a[1] + b[1]) / 4, -(a[2] + b[2]) / 4);
        return normalize(mul(q1, e));
    }

    /** Smooth interpolation between q1 and q2 (u 0 to 1) with the control points s1 and s2 of those two. */
    public static double[] squad(double[] q1, double[] q2, double[] s1, double[] s2, double u) {
        return slerp(slerp(q1, q2, u), slerp(s1, s2, u), 2 * u * (1 - u));
    }

    public static double headingDeg(double[] q) {
        double[] f = rotate(q, 0, 1, 0);
        return (Math.toDegrees(Math.atan2(f[0], f[1])) + 360) % 360;
    }

    public static double pitchDeg(double[] q) {
        double[] f = rotate(q, 0, 1, 0);
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, f[2]))));
    }

    public static double rollDeg(double[] q) {
        double[] r = rotate(q, 1, 0, 0);          // the right side
        double[] f = rotate(q, 0, 1, 0);          // the nose
        // the right side and the up side of the same attitude without roll: the right one is level, the up one is square to the nose
        double hx = f[1], hy = -f[0], hn = Math.sqrt(hx * hx + hy * hy);
        if (hn < 1e-9) return 0;                  // nose straight up or down: roll has no meaning
        hx /= hn; hy /= hn;
        double ux = hy * f[2] - 0 * f[1], uy = 0 * f[0] - hx * f[2], uz = hx * f[1] - hy * f[0];
        double along = r[0] * hx + r[1] * hy;
        double below = -(r[0] * ux + r[1] * uy + r[2] * uz);     // how far the right side is under the level one: positive roll
        return Math.toDegrees(Math.atan2(below, along));
    }
}
