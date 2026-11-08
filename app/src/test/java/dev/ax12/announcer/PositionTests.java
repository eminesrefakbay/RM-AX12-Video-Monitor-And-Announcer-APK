// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.HashMap;
import java.util.Map;

/** Tests of the geometry, the link-loss tracker and the last-known-position texts. */
public class PositionTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    static Map<String, Double> flying(double lat, double lon, double tlm, double lq) {
        Map<String, Double> v = new HashMap<String, Double>();
        v.put("gps_lat", lat); v.put("gps_lon", lon); v.put("sats", 14.0); v.put("gps_alt", 112.0); v.put("height", 80.0);
        v.put("gps_speed", 64.0); v.put("gps_heading", 240.0);
        if (!Double.isNaN(tlm)) v.put("tlm", tlm);
        if (!Double.isNaN(lq)) v.put("up_lq", lq);
        return v;
    }

    public static void main(String[] args) throws Exception {
        I18n.load(I18nTests.FILES, "en");

        // ---------- geometry ----------
        check(GeoMath.validFix(36.8, 34.6) && !GeoMath.validFix(0, 0) && !GeoMath.validFix(Double.NaN, 1) && !GeoMath.validFix(91, 1) && !GeoMath.validFix(1, 181), "what a fix is");
        double d = GeoMath.distanceM(36.8, 34.6, 36.8, 34.61);
        check(near(d, 0.01 * 111195 * Math.cos(Math.toRadians(36.8)), 3), "ten thousandths of a degree east: " + d);
        check(near(GeoMath.distanceM(36.8, 34.6, 36.81, 34.6), 1111.95, 2), "and north");
        check(GeoMath.distanceM(36.8, 34.6, 36.8, 34.6) == 0, "no distance to itself");
        check(near(GeoMath.bearingDeg(36.8, 34.6, 36.8, 34.7), 90, 0.5) && near(GeoMath.bearingDeg(36.8, 34.6, 36.9, 34.6), 0, 0.01)
            && near(GeoMath.bearingDeg(36.8, 34.6, 36.7, 34.6), 180, 0.01) && near(GeoMath.bearingDeg(36.8, 34.6, 36.8, 34.5), 270, 0.5), "the four bearings");
        check(GeoMath.coords(36.8123456, 34.6123456).equals("36.812346, 34.612346") && GeoMath.coords(-1.5, 0.25).equals("-1.500000, 0.250000"), "coordinates as text");
        check(GeoMath.cardinalWord("en", 0).equals("north") && GeoMath.cardinalWord("en", 45).equals("north east") && GeoMath.cardinalWord("en", 224).equals("south west")
            && GeoMath.cardinalWord("en", 359).equals("north") && GeoMath.cardinalWord("en", -90).equals("west") && GeoMath.cardinalWord("en", Double.NaN).isEmpty(), "words for the voice");

        // ---------- the tracker: a normal loss and recovery ----------
        LinkLoss ll = new LinkLoss();
        long t = 0;
        LinkLoss.Event ev = LinkLoss.Event.NONE;
        for (; t <= 3000; t += 100) {                               // good link, moving north
            ev = ll.update(t, true, flying(36.8 + t * 1e-7, 34.6, 1, 100));
            check(ev == LinkLoss.Event.NONE, "no event while the link is good");
        }
        check(ll.everUp() && !ll.isLost() && ll.lastFix() != null && near(ll.lastFix().lat, 36.8 + 3000 * 1e-7, 1e-9), "settled and following the aircraft");
        LinkLoss.Fix lastGood = ll.lastFix();
        boolean lostEvent = false;
        long lostAt = -1;
        for (long u = t; u <= t + 1400; u += 100) {                  // telemetry gone for 1.4 s: not yet a loss
            ev = ll.update(u, true, flying(36.8 + u * 1e-7, 34.6, 0, 0));
            if (ev != LinkLoss.Event.NONE) lostEvent = true;
        }
        check(!lostEvent && !ll.isLost(), "a drop shorter than the delay is not a loss");
        for (long u = t + 1500; u <= t + 1700; u += 100) {
            ev = ll.update(u, true, flying(36.8 + u * 1e-7, 34.6, 0, 0));
            if (ev == LinkLoss.Event.LOST) { lostEvent = true; lostAt = u; }
        }
        check(lostEvent && ll.isLost() && ll.isPending(), "lost after the delay");
        check(ll.fixAtLoss() == lastGood || near(ll.fixAtLoss().lat, lastGood.lat, 1e-9), "the position kept is the last good one, not the stale one");
        check(ll.lostForMs(lostAt + 5000) >= 5000 && ll.lostForMs(lostAt) >= 0, "how long it has been lost");
        long u0 = t + 1800;
        ev = ll.update(u0, true, flying(36.9, 34.6, 0, 0));
        check(ev == LinkLoss.Event.NONE && near(ll.fixAtLoss().lat, lastGood.lat, 1e-9), "no second loss event, and the kept position does not move");
        // the arm switch goes low while the link is still lost: the position stays pending
        ev = ll.update(u0 + 100, false, flying(36.9, 34.6, 0, 0));
        check(ll.isLost() && ll.isPending(), "disarming does not forget the loss");
        // recovery
        long r0 = u0 + 5000;
        check(ll.update(r0, false, flying(36.85, 34.6, 1, 90)) == LinkLoss.Event.NONE, "back for a moment");
        check(ll.update(r0 + 500, false, flying(36.85, 34.6, 1, 90)) == LinkLoss.Event.NONE, "still not long enough");
        check(ll.update(r0 + 1000, false, flying(36.85, 34.6, 1, 90)) == LinkLoss.Event.RECOVERED && !ll.isLost() && !ll.isPending() && ll.recoveredAtMs() == r0 + 1000, "recovered after a steady second");
        // a flicker during loss restarts the recovery count
        LinkLoss fl = new LinkLoss();
        for (long k = 0; k <= 2000; k += 100) fl.update(k, true, flying(36.8, 34.6, 1, 100));
        for (long k = 2100; k <= 4000; k += 100) fl.update(k, true, flying(36.8, 34.6, 0, 0));
        check(fl.isLost(), "lost");
        fl.update(4100, true, flying(36.8, 34.6, 1, 80)); fl.update(4600, true, flying(36.8, 34.6, 0, 0)); fl.update(4700, true, flying(36.8, 34.6, 1, 80));
        check(fl.update(5500, true, flying(36.8, 34.6, 1, 80)) == LinkLoss.Event.NONE && fl.isLost(), "a flicker does not count as recovered");
        check(fl.update(5700, true, flying(36.8, 34.6, 1, 80)) == LinkLoss.Event.RECOVERED, "a steady second does");
        // dismissing the card
        LinkLoss dm = new LinkLoss();
        for (long k = 0; k <= 2000; k += 100) dm.update(k, true, flying(36.8, 34.6, 1, 100));
        for (long k = 2100; k <= 4000; k += 100) dm.update(k, true, flying(36.8, 34.6, 0, 0));
        check(dm.isPending(), "pending");
        dm.dismiss();
        check(!dm.isPending() && dm.isLost() && dm.fixAtLoss() != null, "dismissed: the card goes, the fact stays");
        // a new flight starts clean
        dm.update(10000, false, flying(36.8, 34.6, 1, 100));
        dm.update(10100, true, flying(36.8, 34.6, 1, 100));
        check(!dm.isLost() && !dm.isPending() && dm.fixAtLoss() == null && !dm.everUp(), "arming again forgets the old loss");

        // ---------- what is not a loss ----------
        LinkLoss never = new LinkLoss();
        boolean any = false;
        for (long k = 0; k <= 10000; k += 100) if (never.update(k, true, flying(36.8, 34.6, 0, 0)) != LinkLoss.Event.NONE) any = true;
        check(!any && !never.isLost(), "a link that was never up is not a lost link");
        LinkLoss ground = new LinkLoss();
        for (long k = 0; k <= 10000; k += 100) if (ground.update(k, false, flying(36.8, 34.6, 0, 0)) != LinkLoss.Event.NONE) any = true;
        check(!any && !ground.isLost(), "nothing is tracked on the ground");
        LinkLoss weak = new LinkLoss();
        Map<String, Double> poor = flying(36.8, 34.6, 1, 100);
        poor.put("sats", 3.0);
        for (long k = 0; k <= 5000; k += 100) weak.update(k, true, poor);
        check(weak.lastFix() == null && !weak.everUp(), "a position with too few satellites is not trusted");
        Map<String, Double> zero = flying(0, 0, 1, 100);
        LinkLoss nofix = new LinkLoss();
        for (long k = 0; k <= 5000; k += 100) nofix.update(k, true, zero);
        check(nofix.lastFix() == null, "0,0 is no position");
        LinkLoss nolink = new LinkLoss();
        Map<String, Double> unknown = flying(36.8, 34.6, Double.NaN, Double.NaN);
        for (long k = 0; k <= 8000; k += 100) nolink.update(k, true, unknown);
        check(!nolink.isLost() && nolink.everUp(), "with neither telemetry nor link quality known the link is taken as good");
        LinkLoss lqOnly = new LinkLoss();
        for (long k = 0; k <= 2000; k += 100) lqOnly.update(k, true, flying(36.8, 34.6, Double.NaN, 100));
        for (long k = 2100; k <= 4000; k += 100) lqOnly.update(k, true, flying(36.8, 34.6, Double.NaN, 0));
        check(lqOnly.isLost(), "uplink quality zero alone counts");
        check(!LinkLoss.linkGood(flying(1, 1, 0, 100)) && !LinkLoss.linkGood(flying(1, 1, 1, 0)) && LinkLoss.linkGood(flying(1, 1, 1, 100)) && LinkLoss.linkGood(flying(1, 1, Double.NaN, Double.NaN)), "the link rule");

        // ---------- from home ----------
        LinkLoss.Fix f = new LinkLoss.Fix(36.8, 34.605, 112, 80, 64, 240, 14, 0);
        double[] h = LinkLoss.fromHome(f, true, 36.8, 34.6);
        check(near(h[0], 0.005 * 111195 * Math.cos(Math.toRadians(36.8)), 2) && near(h[1], 90, 0.5), "distance and bearing from home: " + h[0] + " " + h[1]);
        double[] none = LinkLoss.fromHome(f, false, 0, 0);
        check(Double.isNaN(none[0]) && Double.isNaN(none[1]) && Double.isNaN(LinkLoss.fromHome(null, true, 1, 1)[0]), "no home, no distance");

        // ---------- the texts ----------
        String note = PositionNote.build("link lost", "2026-11-08 17:42:10", "Akb7custom001", f, true, 36.8, 34.6);
        Map<String, String> m = PositionNote.parse(note);
        check(m.get("lat").equals("36.800000") && m.get("lon").equals("34.605000") && m.get("coords").equals("36.800000, 34.605000"), "the file holds the position");
        check(m.get("reason").equals("link lost") && m.get("aircraft").equals("Akb7custom001") && m.get("height_m").equals("80") && m.get("speed_kmh").equals("64") && m.get("sats").equals("14"), "and the facts of the moment");
        check(m.get("from_home_m").equals("445") && m.get("bearing_from_home_deg").equals("90") && m.get("home").equals("36.800000, 34.600000"), "and where home is");
        check(note.startsWith("# AX-HUD last known position") && note.contains("GPS position"), "the file says what it is");
        check(PositionNote.coordsOf(note).equals("36.800000, 34.605000"), "the coordinates to copy");
        check(PositionNote.coordsOf("junk") == null && PositionNote.coordsOf(null) == null && PositionNote.coordsOf("coords: 0.000000, 0.000000") == null && PositionNote.coordsOf("coords: abc, def") == null, "no coordinates in anything else");
        String empty = PositionNote.build("in flight", "2026-11-08 17:42:10", "", null, false, 0, 0);
        check(empty.contains("position: none") && PositionNote.coordsOf(empty) == null && !empty.contains("aircraft"), "a file without a position says so");
        String lines = PositionNote.lines(f, true, 36.8, 34.6);
        check(lines.equals("36.800000, 34.605000\n445 m from home, bearing 90 deg E\nHeight 80 m  \u00B7  Speed 64 km/h  \u00B7  14 satellites"), "the lines of the card: " + lines.replace("\n", "|"));
        check(PositionNote.lines(f, false, 0, 0).equals("36.800000, 34.605000\nHeight 80 m  \u00B7  Speed 64 km/h  \u00B7  14 satellites"), "without home");
        check(PositionNote.lines(null, false, 0, 0).equals("No position was received in this flight."), "without a position");
        check(PositionNote.speech("en", f, true, 36.8, 34.6).equals("Link lost. Last position 445 meters east of home, height 80 meters."), "the voice: " + PositionNote.speech("en", f, true, 36.8, 34.6));
        LinkLoss.Fix noHeight = new LinkLoss.Fix(36.8, 34.605, 112, Double.NaN, 64, 240, 14, 0);
        check(PositionNote.speech("en", noHeight, true, 36.8, 34.6).equals("Link lost. Last position 445 meters east of home."), "the voice without a height");
        check(PositionNote.speech("en", f, false, 0, 0).equals("Link lost. The last position is saved.") && PositionNote.speech("en", null, false, 0, 0).equals("Link lost."), "the voice without home or position");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
