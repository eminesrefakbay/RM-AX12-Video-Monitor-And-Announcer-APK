// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/**
 * Statistics for one flight, started and stopped by the arm switch: maximum speed, height above the arming
 * point, peak current, farthest distance, lowest link quality and lowest cell voltage, plus the track for the
 * radar. After disarming the numbers stay until the next arm. Plain Java, tested on a PC.
 */
public final class FlightStats {

    public static final int TRAIL_MAX = 400;
    private static final double TRAIL_STEP_M = 4.0;
    private static final double[] RING_STEPS = {25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 5000, 10000};

    // ---- settings
    private int armChannel = 5;
    private double armHighUs = 1750;
    private double armLowUs = 1650;
    private long debounceMs = 300;
    private int cells = 6;

    // ---- arm state
    private boolean armed;
    private boolean pending;
    private boolean pendingState;
    private long pendingSince;
    private boolean hasFlight;
    private long armedAtMs;
    private long disarmedAtMs;

    // ---- home
    private boolean homeSet;
    private double homeLat, homeLon;
    private boolean homeAltSet;
    private double homeAlt;

    // ---- latest and extremes
    private double speedNow = Double.NaN, heightNow = Double.NaN, currentNow = Double.NaN, distNow = Double.NaN;
    private double maxSpeed = Double.NaN, maxHeight = Double.NaN, peakCurrent = Double.NaN, maxDist = Double.NaN;
    private double minLq = Double.NaN, minCell = Double.NaN;
    private double posX = Double.NaN, posY = Double.NaN;       // metres east and north of home
    private double heading = Double.NaN;

    // ---- track
    private final double[] trailX = new double[TRAIL_MAX];
    private final double[] trailY = new double[TRAIL_MAX];
    private int trailN;
    private final FlightTotals totals = new FlightTotals();

    public void configure(int armChannel, double armHighUs, double armLowUs, int cells) {
        this.armChannel = armChannel;
        this.armHighUs = armHighUs;
        this.armLowUs = Math.min(armLowUs, armHighUs);
        this.cells = Math.max(1, cells);
    }

    public boolean armed() { return armed; }
    public boolean hasFlight() { return hasFlight; }
    public boolean hasHome() { return homeSet; }
    public long armedMs(long nowMs) { return !hasFlight ? 0 : (armed ? nowMs - armedAtMs : disarmedAtMs - armedAtMs); }

    public double speedNow() { return speedNow; }
    public double heightNow() { return heightNow; }
    public double currentNow() { return currentNow; }
    public double distanceNow() { return distNow; }
    public double maxSpeed() { return maxSpeed; }
    public double maxHeight() { return maxHeight; }
    public double peakCurrent() { return peakCurrent; }
    public double maxDistance() { return maxDist; }
    public double minLinkQuality() { return minLq; }
    public double minCellVolts() { return minCell; }
    public double headingDeg() { return heading; }
    public double homeLat() { return homeLat; }
    public double homeLon() { return homeLon; }
    /** The distance, energy and capacity of this flight so far, or of the last one after landing. */
    public FlightTotals totals() { return totals; }
    public long disarmedAtMs() { return disarmedAtMs; }
    public double eastM() { return posX; }
    public double northM() { return posY; }
    public int trailSize() { return trailN; }
    public double trailEast(int i) { return trailX[i]; }
    public double trailNorth(int i) { return trailY[i]; }

    /** Radius of the radar in metres: three rings, sized to fit the farthest point reached so far. */
    public double radarRadiusM() {
        double far = Double.isNaN(maxDist) ? 0 : maxDist;
        return ringStep(far) * 3;
    }

    public double radarRingStepM() { return ringStep(Double.isNaN(maxDist) ? 0 : maxDist); }

    public static double ringStep(double farMetres) {
        for (double s : RING_STEPS) if (s * 3 >= farMetres * 1.1) return s;
        return RING_STEPS[RING_STEPS.length - 1];
    }

    private void reset() {
        homeSet = false;
        homeAltSet = false;
        speedNow = heightNow = currentNow = distNow = Double.NaN;
        maxSpeed = maxHeight = peakCurrent = maxDist = minLq = minCell = Double.NaN;
        posX = posY = Double.NaN;
        trailN = 0;
        totals.reset();
    }

    private static double max(double a, double b) { return Double.isNaN(a) ? b : Math.max(a, b); }
    private static double min(double a, double b) { return Double.isNaN(a) ? b : Math.min(a, b); }

    public void update(Map<String, Double> v, long nowMs) {
        // ---- arm switch, with a dead band and a short hold so a bump of the switch does nothing
        Double sw = v.get("ch" + armChannel);
        if (sw != null) {
            boolean want = armed ? sw >= armLowUs : sw >= armHighUs;
            if (want == armed) {
                pending = false;
            } else if (!pending || pendingState != want) {
                pending = true;
                pendingState = want;
                pendingSince = nowMs;
            } else if (nowMs - pendingSince >= debounceMs) {
                pending = false;
                armed = want;
                if (armed) {
                    reset();
                    hasFlight = true;
                    armedAtMs = nowMs;
                } else {
                    disarmedAtMs = nowMs;
                }
            }
        }
        if (!armed) return;

        // ---- readings
        Double lat = v.get("gps_lat"), lon = v.get("gps_lon"), alt = v.get("gps_alt"), sats = v.get("sats");
        Double spd = v.get("gps_speed"), cur = v.get("curr"), lq = v.get("up_lq"), vbat = v.get("vbat"), capa = v.get("capa");
        Double yaw = v.get("yaw"), course = v.get("gps_heading");

        if (!Double.isNaN(heading) || yaw != null || course != null) {
            if (yaw != null) heading = ((yaw % 360) + 360) % 360;
            else if (course != null) heading = course;
        }
        boolean fix = lat != null && lon != null && (sats == null || sats >= 5) && !(lat == 0.0 && lon == 0.0);
        if (fix && !homeSet) {
            homeSet = true;
            homeLat = lat;
            homeLon = lon;
        }
        if (alt != null && !homeAltSet && (sats == null || sats >= 5)) {
            homeAltSet = true;
            homeAlt = alt;
        }
        if (fix && homeSet) {
            posY = (lat - homeLat) * 111320.0;
            posX = (lon - homeLon) * 111320.0 * Math.cos(Math.toRadians(homeLat));
            distNow = Math.hypot(posX, posY);
            maxDist = max(maxDist, distNow);
            addTrailPoint(posX, posY);
        }
        if (alt != null && homeAltSet) {
            heightNow = alt - homeAlt;
            maxHeight = max(maxHeight, heightNow);
        }
        if (spd != null) {
            speedNow = spd;
            maxSpeed = max(maxSpeed, spd);
        }
        if (cur != null) {
            currentNow = cur;
            peakCurrent = max(peakCurrent, cur);
        }
        totals.update(nowMs, spd == null ? Double.NaN : spd, vbat == null ? Double.NaN : vbat, cur == null ? Double.NaN : cur, capa == null ? Double.NaN : capa);
        if (lq != null) minLq = min(minLq, lq);
        if (vbat != null && vbat > 6.0) minCell = min(minCell, vbat / cells);
    }

    private void addTrailPoint(double x, double y) {
        if (trailN > 0 && Math.hypot(x - trailX[trailN - 1], y - trailY[trailN - 1]) < TRAIL_STEP_M) return;
        if (trailN == TRAIL_MAX) {                              // full: keep every second point, so the whole flight stays
            int k = 0;
            for (int i = 0; i < trailN; i += 2) {
                trailX[k] = trailX[i];
                trailY[k] = trailY[i];
                k++;
            }
            trailN = k;
        }
        trailX[trailN] = x;
        trailY[trailN] = y;
        trailN++;
    }
}
