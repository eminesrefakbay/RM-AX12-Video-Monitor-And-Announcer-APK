// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The camera of the 3D replay: three modes (chase, orbit, pilot) and the projection of a world point to the screen.
 * Every movement of the camera is smoothed with a half-life in seconds, so it looks the same at 30 and at 60 frames a second,
 * and a seek (reset) puts it straight at the new place instead of flying there. World axes: x east, y north, z up.
 */
public final class Cam3D {
    public static final int CHASE = 0, ORBIT = 1, PILOT = 2;
    public static final double NEAR = 0.2;

    public int mode = CHASE;
    /** Orbit: set by the finger. Azimuth in radians, elevation in radians, distance in metres. */
    public double userAzimuth = 0.6, userElevation = 0.45, userDistance = 40;
    public boolean autoOrbit = true;
    /** Where the pilot stands and the height of his eyes. */
    public double homeX = 0, homeY = 0, eyeHeight = 1.7;

    // result
    public double ex, ey, ez, tx, ty, tz, fovDeg = 70;
    // smoothed state
    private double fx, fy, fz;                 // the aircraft as the camera sees it (smoothed)
    private double az;                         // chase heading, radians
    private double orbitAuto;
    private double fov = 70;
    private boolean fresh = true;
    // the view basis and the viewport
    private double rx, ry, rz, ux, uy, uz, dx, dy, dz;
    private double w = 1280, h = 720, focalPx = 700;

    public void reset() { fresh = true; }

    public void setViewport(double width, double height) { w = width; h = height; }

    /** The fraction of the way to the target after dt seconds, for a given half-life: the same however the time is cut up. */
    public static double alpha(double dtSec, double halfLifeSec) {
        if (halfLifeSec <= 0) return 1;
        return 1 - Math.pow(0.5, Math.max(0, dtSec) / halfLifeSec);
    }

    public static double damp(double cur, double target, double dtSec, double halfLifeSec) { return cur + (target - cur) * alpha(dtSec, halfLifeSec); }

    /** The same, round the circle: takes the short way from one angle to the other. */
    public static double dampAngle(double cur, double target, double dtSec, double halfLifeSec) {
        double d = target - cur;
        d -= 2 * Math.PI * Math.floor((d + Math.PI) / (2 * Math.PI));
        return cur + d * alpha(dtSec, halfLifeSec);
    }

    public void update(FlightPath.Pose p, double dtSec) {
        if (dtSec > 0.5 || dtSec < 0) fresh = true;
        double heading = Math.toRadians(p.headingDeg);
        if (fresh) { fx = p.x; fy = p.y; fz = p.z; az = heading; fov = 70; }
        else {
            double hl = mode == PILOT ? 0.10 : 0.12;
            fx = damp(fx, p.x, dtSec, hl); fy = damp(fy, p.y, dtSec, hl); fz = damp(fz, p.z, dtSec, hl);
            az = dampAngle(az, heading, dtSec, 0.7);
        }
        if (autoOrbit && mode == ORBIT) orbitAuto += dtSec * 0.06;
        double nex, ney, nez;
        switch (mode) {
            case ORBIT: {
                double a = userAzimuth + orbitAuto, ce = Math.cos(userElevation);
                nex = fx + userDistance * ce * Math.sin(a); ney = fy + userDistance * ce * Math.cos(a); nez = fz + userDistance * Math.sin(userElevation);
                tx = fx; ty = fy; tz = fz;
                setFov(damp(fov, 55, dtSec, 0.3), fresh);
                break;
            }
            case PILOT: {
                nex = homeX; ney = homeY; nez = homeY * 0 + eyeHeight;
                tx = fx; ty = fy; tz = fz;
                double dist = Math.max(3, Math.sqrt((fx - homeX) * (fx - homeX) + (fy - homeY) * (fy - homeY) + (fz - nez) * (fz - nez)));
                double want = Math.max(12, Math.min(70, Math.toDegrees(2 * Math.atan(7.0 / dist)) * 1.6));   // the farther it is, the closer the lens
                fov = fresh ? want : damp(fov, want, dtSec, 0.5);
                break;
            }
            default: {                                           // chase: behind and above, looking a little ahead
                double dist = 8, up = 4.5;
                double bx = Math.sin(az), by = Math.cos(az);
                nex = fx - bx * dist; ney = fy - by * dist; nez = fz + up;
                tx = fx + bx * 6; ty = fy + by * 6; tz = fz + 0.4;
                setFov(damp(fov, 70, dtSec, 0.3), fresh);
            }
        }
        if (fresh || mode == PILOT) { ex = nex; ey = ney; ez = nez; }
        else {
            double hl = mode == ORBIT ? 0.18 : 0.30;
            ex = damp(ex, nex, dtSec, hl); ey = damp(ey, ney, dtSec, hl); ez = damp(ez, nez, dtSec, hl);
        }
        if (ez < tz - 400) ez = tz - 400;
        fovDeg = fov;
        fresh = false;
        basis();
    }

    private void setFov(double v, boolean snap) { fov = snap ? 70 : v; if (mode == ORBIT && snap) fov = 55; }

    private void basis() {
        dx = tx - ex; dy = ty - ey; dz = tz - ez;
        double dn = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dn < 1e-6) { dx = 0; dy = 1; dz = 0; dn = 1; }
        dx /= dn; dy /= dn; dz /= dn;
        // right = forward x world up
        rx = dy; ry = -dx; rz = 0;
        double rn = Math.sqrt(rx * rx + ry * ry);
        if (rn < 1e-6) { rx = 1; ry = 0; rn = 1; }
        rx /= rn; ry /= rn;
        ux = ry * dz - rz * dy; uy = rz * dx - rx * dz; uz = rx * dy - ry * dx;
        focalPx = 0.5 * h / Math.tan(Math.toRadians(fovDeg) / 2);
    }

    /** A world point on the screen: out = {x, y, depth}. False when it is behind the camera. */
    public boolean project(double wx, double wy, double wz, double[] out) {
        double px = wx - ex, py = wy - ey, pz = wz - ez;
        double zc = px * dx + py * dy + pz * dz;
        out[2] = zc;
        if (zc < NEAR) return false;
        double xc = px * rx + py * ry + pz * rz, yc = px * ux + py * uy + pz * uz;
        out[0] = w / 2 + focalPx * xc / zc;
        out[1] = h / 2 - focalPx * yc / zc;
        return true;
    }

    /** A line from one world point to another on the screen: out = {x1, y1, x2, y2}. The part behind the camera is cut off; false when nothing is left. */
    public boolean projectSegment(double ax, double ay, double az, double bx, double by, double bz, double[] out) {
        double za = (ax - ex) * dx + (ay - ey) * dy + (az - ez) * dz;
        double zb = (bx - ex) * dx + (by - ey) * dy + (bz - ez) * dz;
        if (za < NEAR && zb < NEAR) return false;
        if (za < NEAR) {
            double u = (NEAR - za) / (zb - za);
            ax += (bx - ax) * u; ay += (by - ay) * u; az += (bz - az) * u;
        } else if (zb < NEAR) {
            double u = (NEAR - zb) / (za - zb);
            bx += (ax - bx) * u; by += (ay - by) * u; bz += (az - bz) * u;
        }
        double[] a = new double[3];
        if (!project(ax, ay, az, a)) return false;
        double sx = a[0], sy = a[1];
        if (!project(bx, by, bz, a)) return false;
        out[0] = sx; out[1] = sy; out[2] = a[0]; out[3] = a[1];
        return true;
    }

    /** How far the camera looks up (negative: down), in radians. The horizon on the screen follows from it. */
    public double pitchRad() { return Math.asin(Math.max(-1, Math.min(1, dz))); }

    public double focal() { return focalPx; }

    /** Pixels per metre at a depth: how big something of a known size is. */
    public double scaleAt(double depth) { return focalPx / Math.max(NEAR, depth); }
}
