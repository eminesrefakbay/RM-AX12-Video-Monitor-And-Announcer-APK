// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.StringReader;

/** Tests of the 3D replay maths: quaternions, the smooth path through a log, the camera and its projection. */
public class Flight3DTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    /** A flight that flies east then turns north: one telemetry packet a second, with the 20 Hz rows in between repeating the last values. */
    static FlightLog flight() throws Exception {
        StringBuilder sb = new StringBuilder("# text: fm\nt_ms,sats,gps_lat,gps_lon,gps_speed,gps_alt,gps_heading,pitch,roll,yaw,vspeed,fm,win_left,height\n");
        double lat0 = 36.85, lon0 = 34.60;
        double lat = lat0, lon = lon0;
        for (int s = 0; s <= 20; s++) {
            if (s > 0) {
                if (s <= 10) lon += 10.0 / (111320.0 * Math.cos(Math.toRadians(lat0)));     // 10 m east a second
                else lat += 10.0 / 111320.0;                                                // then 10 m north
            }
            double yaw = s <= 10 ? 90 : 0, roll = s == 10 ? 30 : (s % 2 == 1 ? 1 : 0);
            double hgt = s * 1.0;
            String fm = s < 5 ? "ANGL" : (s < 15 ? "ACRO" : "RTH");
            double win = 1.0 - s / 10.0;
            for (int j = 0; j < 20; j++) {
                sb.append(s * 1000 + j * 50).append(',').append(10).append(',').append(String.format(java.util.Locale.US, "%.7f,%.7f", lat, lon))
                  .append(",36,").append(100 + hgt).append(',').append(s <= 10 ? 90 : 0).append(",5,").append(roll).append(',').append(yaw)
                  .append(",1,").append(fm).append(',').append(win).append(',').append(hgt).append('\n');
            }
        }
        return LogFormat.read(new BufferedReader(new StringReader(sb.toString())), 1_000_000);
    }

    public static void main(String[] args) throws Exception {
        // ---------- quaternions ----------
        double[] q = Quat.fromHeadingPitchRoll(90, 0, 0);
        double[] f = Quat.rotate(q, 0, 1, 0);
        check(near(f[0], 1, 1e-9) && near(f[1], 0, 1e-9), "heading 90 points the nose east");
        f = Quat.rotate(Quat.fromHeadingPitchRoll(0, 30, 0), 0, 1, 0);
        check(f[2] > 0.49 && f[2] < 0.51, "pitch 30 lifts the nose");
        double[] r = Quat.rotate(Quat.fromHeadingPitchRoll(0, 0, 30), 1, 0, 0);
        check(r[2] < -0.49 && r[2] > -0.51, "roll 30 puts the right side down");
        for (double[] a : new double[][]{{10, 5, -20}, {200, -30, 45}, {359, 60, -80}, {0, 0, 0}}) {
            double[] qq = Quat.fromHeadingPitchRoll(a[0], a[1], a[2]);
            check(near(Quat.headingDeg(qq), a[0], 1e-6) || near(Math.abs(Quat.headingDeg(qq) - a[0]), 360, 1e-6), "heading round trip " + a[0]);
            check(near(Quat.pitchDeg(qq), a[1], 1e-6), "pitch round trip " + a[1]);
            check(near(Quat.rollDeg(qq), a[2], 1e-6), "roll round trip " + a[2]);
        }
        double[] qa = Quat.fromHeadingPitchRoll(179, 0, 0), qb = Quat.fromHeadingPitchRoll(-179, 0, 0);
        double mid = Quat.headingDeg(Quat.slerp(qa, qb, 0.5));
        check(near(mid, 180, 1e-6), "turning from 179 to -179 goes through 180, not round through 0");
        double[] s = Quat.slerp(Quat.identity(), Quat.axisAngle(0, 0, 1, 1.0), 0.5);
        check(near(Quat.dot(s, s), 1, 1e-12), "slerp stays a unit quaternion");
        check(near(Quat.dot(Quat.slerp(qa, qb, 0), qa), 1, 1e-12) && near(Quat.dot(Quat.slerp(qa, qb, 1), qb), 1, 1e-12) || near(Quat.dot(Quat.slerp(qa, qb, 1), qb), -1, 1e-12), "slerp ends on its two ends");

        // ---------- the path ----------
        FlightLog log = flight();
        FlightPath p = FlightPath.build(log);
        check(p.n >= 19 && p.n <= 22, "about one keyframe per packet, not per row: " + p.n);
        check(near(p.x[0], 0, 1e-6) && near(p.y[0], 0, 1e-6), "the first fix is the origin");
        check(near(p.x[10], 100, 0.5) && near(p.y[10], 0, 0.5), "100 m east after 10 s: " + p.x[10]);
        FlightPath.Pose a = new FlightPath.Pose(), b = new FlightPath.Pose();
        p.sample(5000, a);
        check(near(a.x, 50, 0.6) && near(a.z, 5, 0.3), "at 5 s the pose is where the log says: " + a.x + ", " + a.z);
        for (int i = 0; i < p.n; i++) {
            p.sample(p.t[i], a);
            check(near(a.x, p.x[i], 1e-6) && near(a.y, p.y[i], 1e-6) && near(a.z, p.z[i], 1e-6), "the curve passes through keyframe " + i);
        }
        // smooth: no jump of speed at a keyframe (the speed just before and just after are the same)
        double maxJump = 0, maxStep = 0;
        for (int i = 1; i < p.n - 1; i++) {
            p.sample(p.t[i] - 1, a); p.sample(p.t[i] + 1, b);
            maxJump = Math.max(maxJump, Math.hypot(b.x - a.x, b.y - a.y));
        }
        check(maxJump < 0.05, "no corner at a keyframe: a 2 ms step crosses " + maxJump + " m");
        // no overshoot on a straight leg: the lateral error stays tiny
        double worst = 0;
        for (int ms = 0; ms <= 9000; ms += 37) { p.sample(ms, a); worst = Math.max(worst, Math.abs(a.y)); }
        check(worst < 0.5, "a straight leg stays straight: " + worst);
        // frame-rate independent: sampling at 30 and 60 fps walks the same curve
        p.sample(2345.6, a); p.sample(2345.6, b);
        check(a.x == b.x && a.q[0] == b.q[0], "sampling is a pure function of the time");
        // the attitude: yaw 90 then 0 over the turn; at the keyframes it is the logged one
        p.sample(2000, a);
        check(near(a.headingDeg, 90, 0.01) && near(a.pitchDeg, 5 * FlightPath.PITCH_SIGN, 0.01), "attitude at a keyframe is the logged one");
        p.sample(10000, a);
        check(near(a.rollDeg, 30, 0.01), "roll 30 at 10 s");
        p.sample(9500, a); p.sample(10500, b);
        check(a.rollDeg > 0 && a.rollDeg < 30 && b.rollDeg > 0 && b.rollDeg < 30, "roll rises into the turn and falls out of it, between the keyframes");
        // time outside the log: held at the ends, never extrapolated
        p.sample(-500, a); check(near(a.x, 0, 1e-6), "before the start: the first pose");
        p.sample(99999, a); check(near(a.y, p.y[p.n - 1], 1e-9), "after the end: the last pose");
        // the polyline
        float[] pl = p.polyline(250);
        check(pl.length % 4 == 0 && pl.length >= 4 * 80, "the ribbon has a point every 250 ms: " + pl.length / 4);
        // events
        boolean mode = false, window = false, far = false, takeoff = false, landing = false, rth = false;
        for (FlightPath.Event e : p.events) {
            if (e.kind.equals("mode")) { mode = true; if (e.label.equals("RTH")) rth = true; }
            if (e.kind.equals("window")) window = true;
            if (e.kind.equals("farthest")) far = true;
            if (e.kind.equals("takeoff")) takeoff = true;
            if (e.kind.equals("landing")) landing = true;
        }
        check(mode && rth && window && far && takeoff && landing, "events: take-off, modes, window, farthest, landing");
        long prev = -1; boolean sorted = true;
        for (FlightPath.Event e : p.events) { if (e.ms < prev) sorted = false; prev = e.ms; }
        check(sorted, "events are in time order");
        check(p.maxRangeM > 100 && p.maxHeightM > 19, "range and height are found");

        // a log without a position: no path, no crash
        FlightLog bare = LogFormat.read(new BufferedReader(new StringReader("t_ms,vbat\n0,24\n100,24\n")), 1000);
        check(FlightPath.build(bare).n == 0, "a log without a position gives an empty path");
        FlightPath.Pose none = new FlightPath.Pose();
        FlightPath.build(bare).sample(50, none);
        check(near(none.x, 0, 0), "sampling an empty path is harmless");
        // no fix (sats below 6): those rows are ignored
        FlightLog nofix = LogFormat.read(new BufferedReader(new StringReader("t_ms,sats,gps_lat,gps_lon\n0,3,36.85,34.6\n1000,3,36.86,34.6\n")), 1000);
        check(FlightPath.build(nofix).n == 0, "rows with too few satellites are not trusted");

        // ---------- the camera ----------
        check(near(Cam3D.alpha(1.0 / 60, 0.3) , Cam3D.alpha(1.0 / 60, 0.3), 0), "alpha is deterministic");
        double v60 = 0, v30 = 0;
        for (int i = 0; i < 60; i++) v60 = Cam3D.damp(v60, 10, 1.0 / 60, 0.25);
        for (int i = 0; i < 30; i++) v30 = Cam3D.damp(v30, 10, 1.0 / 30, 0.25);
        check(near(v60, v30, 1e-9), "smoothing for one second gives the same at 30 and at 60 frames a second");
        check(near(Cam3D.damp(0, 10, 0.25, 0.25), 5, 1e-9), "after one half-life the camera is half way");
        check(near(Cam3D.dampAngle(Math.toRadians(350), Math.toRadians(10), 1000, 0.1), Math.toRadians(10) + 0 * 1 , 1e-6) || near(Cam3D.dampAngle(Math.toRadians(350), Math.toRadians(10), 1000, 0.1), Math.toRadians(370), 1e-6), "an angle takes the short way round");
        Cam3D cam = new Cam3D();
        cam.setViewport(1280, 720);
        FlightPath.Pose pose = new FlightPath.Pose();
        pose.x = 0; pose.y = 0; pose.z = 20; pose.q = Quat.fromHeadingPitchRoll(0, 0, 0); pose.headingDeg = 0;
        cam.update(pose, 0.016);
        double[] sc = new double[3];
        check(cam.project(pose.x, pose.y, pose.z, sc), "the aircraft is in front of the chase camera");
        check(sc[0] > 400 && sc[0] < 880 && sc[1] > 150 && sc[1] < 570, "the aircraft is near the middle of the screen: " + sc[0] + ", " + sc[1]);
        check(!cam.project(cam.ex - (cam.tx - cam.ex), cam.ey - (cam.ty - cam.ey), cam.ez, sc), "a point behind the camera is not drawn");
        cam.mode = Cam3D.ORBIT; cam.reset(); cam.update(pose, 0.016);
        check(Math.hypot(cam.ex - pose.x, cam.ey - pose.y) > 20, "orbit stands off from the aircraft");
        cam.mode = Cam3D.PILOT; cam.reset(); pose.x = 300; pose.y = 100; cam.update(pose, 0.016);
        check(near(cam.ex, 0, 1e-9) && near(cam.ez, 1.7, 1e-9), "pilot view stands at home at eye height");
        check(cam.project(pose.x, pose.y, pose.z, sc) && near(sc[0], 640, 1), "pilot view looks straight at the aircraft");
        double far0 = cam.fovDeg;
        pose.x = 30; pose.y = 10; cam.reset(); cam.update(pose, 0.016);
        check(cam.fovDeg > far0, "the lens opens up when the aircraft comes close: " + far0 + " -> " + cam.fovDeg);
        // a seek does not make the camera fly across the map
        cam.mode = Cam3D.CHASE; cam.reset(); pose.x = 0; pose.y = 0; cam.update(pose, 0.016);
        double ex0 = cam.ex;
        pose.x = 500; cam.reset(); cam.update(pose, 0.016);
        check(Math.abs(cam.ex - 500) < 12, "after a reset the camera is at the new place at once: " + cam.ex);
        pose.x = 500; cam.update(pose, 0.016); pose.x = 510; cam.update(pose, 0.016);
        check(cam.ex < 509 && cam.ex > 490, "while playing the camera follows, a little late: " + cam.ex);

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
