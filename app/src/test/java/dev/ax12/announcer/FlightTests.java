package dev.ax12.announcer;

import java.util.HashMap;
import java.util.Map;

/** Tests for the flight statistics, the radar helpers and the pre-flight checklist. */
public class FlightTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    static Map<String, Double> m(Object... kv) {
        Map<String, Double> map = new HashMap<String, Double>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return map;
    }

    public static void main(String[] args) {
        // ---------- arming ----------
        FlightStats f = new FlightStats();
        long t = 0;
        f.update(m("ch5", 1000), t += 100);
        check(!f.armed() && !f.hasFlight(), "disarmed at start");
        f.update(m("ch5", 2000), t += 100);
        check(!f.armed(), "a single reading does not arm (hold 300 ms)");
        f.update(m("ch5", 1000), t += 100);
        f.update(m("ch5", 2000), t += 100); f.update(m("ch5", 2000), t += 100);
        check(!f.armed(), "a blip that ends cancels the hold");
        for (int i = 0; i < 5; i++) f.update(m("ch5", 2000), t += 100);
        check(f.armed() && f.hasFlight(), "armed after the switch stays up for 300 ms");
        for (int i = 0; i < 6; i++) f.update(m("ch5", 1700), t += 100);
        check(f.armed(), "1700 is inside the dead band: stays armed");
        for (int i = 0; i < 6; i++) f.update(m("ch5", 1600), t += 100);
        check(!f.armed() && f.hasFlight(), "disarms below 1650 and keeps the flight");
        FlightStats nosw = new FlightStats();
        nosw.update(m("vbat", 25), 100);
        check(!nosw.armed(), "no arm channel value: not armed");

        // ---------- a flight ----------
        FlightStats a = new FlightStats();
        a.configure(5, 1750, 1650, 6);
        long n = 0;
        double lat0 = 36.8000, lon0 = 34.6000;
        a.update(m("ch5", 1000, "gps_lat", lat0, "gps_lon", lon0, "gps_alt", 112, "sats", 18), n += 100);
        check(Double.isNaN(a.maxSpeed()) && Double.isNaN(a.peakCurrent()), "nothing is recorded while disarmed");
        for (int i = 0; i < 5; i++) a.update(m("ch5", 2000, "gps_lat", lat0, "gps_lon", lon0, "gps_alt", 112, "sats", 18, "curr", 3, "gps_speed", 0, "up_lq", 100, "vbat", 25.0, "yaw", 90), n += 100);
        check(a.armed() && a.hasHome(), "home is set when armed");
        check(near(a.distanceNow(), 0, 0.5) && near(a.heightNow(), 0, 0.01), "at home: no distance, no height");
        // fly 10 steps north (0.001 degree = 111.32 m each), climbing, speeding up, drawing current
        for (int i = 1; i <= 10; i++) {
            a.update(m("ch5", 2000, "gps_lat", lat0 + 0.001 * i, "gps_lon", lon0, "gps_alt", 112 + 10 * i, "sats", 18,
                    "curr", 10 + 5 * i, "gps_speed", 8 * i, "up_lq", 100 - 3 * i, "vbat", 25.0 - 0.3 * i, "yaw", 0), n += 100);
        }
        check(near(a.distanceNow(), 1113.2, 1.0), "1113 m north of home: " + a.distanceNow());
        check(near(a.maxDistance(), 1113.2, 1.0), "max distance");
        check(near(a.heightNow(), 100, 0.01) && near(a.maxHeight(), 100, 0.01), "height above the arming point");
        check(near(a.maxSpeed(), 80, 0.01) && near(a.peakCurrent(), 60, 0.01), "max speed and peak current");
        check(near(a.minLinkQuality(), 70, 0.01), "minimum link quality");
        check(near(a.minCellVolts(), (25.0 - 3.0) / 6, 0.001), "minimum cell voltage: " + a.minCellVolts());
        check(near(a.northM(), 1113.2, 1.0) && near(a.eastM(), 0, 0.5), "position north of home");
        check(near(a.headingDeg(), 0, 0.01), "heading from the attitude yaw");
        // coming back: now-values fall, maxima stay
        for (int i = 9; i >= 0; i--) {
            a.update(m("ch5", 2000, "gps_lat", lat0 + 0.001 * i, "gps_lon", lon0, "gps_alt", 112 + 10 * i, "sats", 18,
                    "curr", 8, "gps_speed", 40, "up_lq", 100, "vbat", 22.0, "yaw", 180), n += 100);
        }
        check(near(a.maxDistance(), 1113.2, 1.0) && near(a.maxHeight(), 100, 0.01) && near(a.peakCurrent(), 60, 0.01) && near(a.maxSpeed(), 80, 0.01), "maxima stay after the return");
        check(near(a.distanceNow(), 0, 0.5) && near(a.currentNow(), 8, 0.01), "now-values follow");
        check(a.trailSize() > 3 && a.trailSize() < 60, "a track was recorded: " + a.trailSize());
        check(near(a.trailEast(0), 0, 0.5) && near(a.trailNorth(0), 0, 0.5), "the track starts at home");
        // disarm: values are frozen and readable (the disarm takes 300 ms to confirm, with normal readings meanwhile)
        for (int i = 0; i < 6; i++) a.update(m("ch5", 1000, "gps_lat", lat0, "gps_lon", lon0, "gps_alt", 112, "curr", 3, "gps_speed", 2), n += 100);
        for (int i = 0; i < 6; i++) a.update(m("ch5", 1000, "gps_lat", lat0, "gps_lon", lon0, "gps_alt", 300, "curr", 99, "gps_speed", 200), n += 100);
        check(!a.armed() && near(a.maxSpeed(), 80, 0.01) && near(a.peakCurrent(), 60, 0.01) && near(a.maxHeight(), 100, 0.01), "after landing the numbers stay, later readings do not count");
        check(a.armedMs(n) > 2000 && a.armedMs(n) == a.armedMs(n + 50000), "the armed time stops at disarm: " + a.armedMs(n));
        // arm again: everything starts over
        for (int i = 0; i < 5; i++) a.update(m("ch5", 2000, "gps_lat", lat0 + 0.01, "gps_lon", lon0 + 0.01, "gps_alt", 150, "sats", 12, "curr", 4, "gps_speed", 1), n += 100);
        check(near(a.maxSpeed(), 1, 0.01) && near(a.peakCurrent(), 4, 0.01) && near(a.distanceNow(), 0, 0.5) && near(a.heightNow(), 0, 0.01), "a new arm resets the statistics and moves home");

        // ---------- bad or missing data ----------
        FlightStats b = new FlightStats();
        for (int i = 0; i < 6; i++) b.update(m("ch5", 2000, "curr", 12, "gps_lat", 36.8, "gps_lon", 34.6, "sats", 2, "gps_alt", 100), 100 * (i + 1));
        check(b.armed() && !b.hasHome() && Double.isNaN(b.maxDistance()) && Double.isNaN(b.maxHeight()), "no home and no height without a GPS fix (2 satellites)");
        check(near(b.peakCurrent(), 12, 0.01), "current still counts without GPS");
        FlightStats z = new FlightStats();
        for (int i = 0; i < 6; i++) z.update(m("ch5", 2000, "gps_lat", 0, "gps_lon", 0, "sats", 9), 100 * (i + 1));
        check(!z.hasHome(), "position 0,0 is not a fix");

        // ---------- the track ----------
        FlightStats tr = new FlightStats();
        for (int i = 0; i < 5; i++) tr.update(m("ch5", 2000, "gps_lat", 36.8, "gps_lon", 34.6, "sats", 9), 100 * (i + 1));
        for (int i = 1; i <= 5000; i++) tr.update(m("ch5", 2000, "gps_lat", 36.8 + i * 0.00005, "gps_lon", 34.6, "sats", 9), 600 + 100 * i);
        check(tr.trailSize() <= FlightStats.TRAIL_MAX && tr.trailSize() > FlightStats.TRAIL_MAX / 4, "the track is capped and keeps its length: " + tr.trailSize());
        check(near(tr.trailNorth(tr.trailSize() - 1), 5000 * 0.00005 * 111320, 12), "the track still reaches the far end: " + tr.trailNorth(tr.trailSize() - 1));
        check(near(tr.trailNorth(0), 0, 1), "and still starts at home");
        FlightStats still = new FlightStats();
        for (int i = 0; i < 100; i++) still.update(m("ch5", 2000, "gps_lat", 36.8, "gps_lon", 34.6, "sats", 9), 100 * (i + 1));
        check(still.trailSize() == 1, "standing still adds no track points: " + still.trailSize());

        // ---------- radar scale ----------
        check(FlightStats.ringStep(0) == 25 && FlightStats.ringStep(60) == 25, "small range: 25 m rings");
        check(FlightStats.ringStep(100) == 50 && FlightStats.ringStep(300) == 150 && FlightStats.ringStep(120) == 50, "100 m, 120 m and 300 m: " + FlightStats.ringStep(300));
        check(FlightStats.ringStep(1113) == 500, "1.1 km: 500 m rings");
        check(FlightStats.ringStep(1e7) == 10000, "huge range is capped");
        check(near(a.radarRadiusM(), FlightStats.ringStep(a.maxDistance()) * 3, 1e-9), "radius is three rings");
        double far = 0; boolean grows = true;
        for (double d = 0; d < 6000; d += 37) { double r = FlightStats.ringStep(d) * 3; if (r < far) grows = false; if (r < d) grows = false; far = r; }
        check(grows, "the radar never shrinks as the distance grows, and always contains the aircraft");

        // ---------- the checklist ----------
        String text = "hold = 0\n" +
            "vbat > 12 -> Battery connected\n" +
            "vbat / 6 >= 4.15 -> Battery full {vbat/6:2} V per cell\n" +
            "sats >= 10 -> GPS {sats} satellites\n" +
            "up_lq >= 95 -> Link {up_lq}%\n" +
            "ch9 <= 1100 -> Airmode switch on\n";
        RuleParser.Result cp = RuleParser.parse(text);
        CheckModel cm = new CheckModel(cp.rules);
        RuleEngine ce = new RuleEngine(cp, null, cm);
        check(cm.size() == 5 && cm.allUnknown() && !cm.ready(), "all unknown before anything is connected");
        Map<String, Double> cv = new HashMap<String, Double>(); Map<String, String> ct = new HashMap<String, String>();
        cv.put("ch9", 1000.0);
        ce.update(cv, ct, 100);
        check(cm.pass() == 1 && cm.unknown() == 4 && cm.fail() == 0, "only the switch is known: " + cm.pass() + "/" + cm.fail() + "/" + cm.unknown());
        cv.put("vbat", 24.0); cv.put("sats", 6.0); cv.put("up_lq", 100.0);
        ce.update(cv, ct, 200);
        check(cm.pass() == 3 && cm.fail() == 2 && cm.unknown() == 0 && !cm.ready(), "half charged pack and few satellites fail: " + cm.pass() + "/" + cm.fail());
        CheckModel.Item[] its = cm.items(cv, ct).toArray(new CheckModel.Item[0]);
        check(its[1].state == CheckModel.State.FAIL && its[1].label.equals("Battery full 4.00 V per cell"), "label with the live value: " + its[1].label);
        check(its[2].state == CheckModel.State.FAIL && its[2].label.equals("GPS 6 satellites"), "gps label: " + its[2].label);
        cv.put("vbat", 25.2); cv.put("sats", 14.0);
        ce.update(cv, ct, 300);
        check(cm.ready() && cm.pass() == 5, "all pass: ready");
        cv.put("ch9", 1500.0);
        ce.update(cv, ct, 400);
        check(!cm.ready() && cm.fail() == 1, "airmode switch off: not ready");
        cv.remove("vbat");
        ce.update(cv, ct, 500);
        check(cm.unknown() == 2 && !cm.ready(), "a value that disappears makes its checks unknown: " + cm.unknown());
        cv.clear();
        ce.update(cv, ct, 600);
        check(cm.allUnknown(), "everything gone: all unknown again");
        // a hold time delays a change of result
        CheckModel hm = new CheckModel(RuleParser.parse("hold = 300\nsats >= 10 -> GPS").rules);
        RuleEngine he = new RuleEngine(RuleParser.parse("hold = 300\nsats >= 10 -> GPS"), null, hm);
        Map<String, Double> hv = m("sats", 4);
        he.update(hv, ct, 0); check(hm.fail() == 1, "starts as fail");
        hv.put("sats", 12.0); he.update(hv, ct, 50); he.update(hv, ct, 100);
        check(hm.fail() == 1, "a change waits for the hold time");
        he.update(hv, ct, 400); check(hm.pass() == 1, "then passes");
        // describing a check
        RuleText.Spec cs = RuleText.fromLine("sats >= 10 -> GPS {sats} satellites");
        check(RuleText.describeCheck(cs).startsWith("passes when sats \u2265 10: \u2713 "), "check description: " + RuleText.describeCheck(cs));
        // the shipped defaults
        String dc = readFile("app/src/main/assets/i18n/checks.en.txt");
        RuleParser.Result dcp = RuleParser.parse(dc);
        check(!dc.isEmpty() && dcp.errors.isEmpty() && dcp.rules.size() == 6, "default checks parse: " + dcp.errors + " " + dcp.rules.size());
        int editable = 0;
        for (RuleText.Item it : RuleText.parse(dc)) if (it.kind == RuleText.Kind.RULE && it.spec != null) editable++;
        check(editable == 6, "all default checks are editable in the GUI");
        CheckModel dm = new CheckModel(dcp.rules);
        RuleEngine de = new RuleEngine(dcp, null, dm);
        Map<String, Double> good = m("vbat", 25.2, "sats", 16, "up_lq", 100, "ch9", 1000, "ch5", 1000); BatteryProfiles.addDerived(good, 6);
        for (int i = 0; i < 10; i++) de.update(good, ct, 100 * (i + 1));
        check(dm.ready(), "a healthy, disarmed aircraft is ready with the default checks: pass " + dm.pass() + " fail " + dm.fail() + " unknown " + dm.unknown());
        good.put("vbat", 23.0); BatteryProfiles.addDerived(good, 6);
        for (int i = 0; i < 10; i++) de.update(good, ct, 2000 + 100 * i);
        check(!dm.ready() && dm.fail() == 1, "a half empty pack is caught: fail " + dm.fail());

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    static String readFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
