package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for text templates, level-mode rule engine, the warning model and the stick layout. */
public class OverlayTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static class Rec implements Announcer {
        final List<String> said = new ArrayList<String>();
        public void say(String text, boolean urgent) { said.add((urgent ? "!" : "") + text); }
    }

    public static void main(String[] args) {
        // ---------- templates ----------
        Map<String, Double> n = new HashMap<String, Double>(); Map<String, String> t = new HashMap<String, String>();
        n.put("vbat", 21.18); n.put("up_lq", 87.0); n.put("sats", 14.0); t.put("fm", "ANGL");
        check(TextTemplate.expand("BATTERY {vbat/6:2} V", n, t).equals("BATTERY 3.53 V"), "per cell with 2 decimals: " + TextTemplate.expand("BATTERY {vbat/6:2} V", n, t));
        check(TextTemplate.expand("LINK {up_lq}%", n, t).equals("LINK 87%"), "integer value has no decimals");
        check(TextTemplate.expand("{vbat}", n, t).equals("21.18"), "plain number trimmed to two decimals");
        check(TextTemplate.expand("mode {fm}", n, t).equals("mode ANGL"), "text variable");
        check(TextTemplate.expand("{nothing}", n, t).equals("?"), "unknown variable shows ?");
        check(TextTemplate.expand("no braces", n, t).equals("no braces"), "text without braces is untouched");
        check(TextTemplate.expand("{SATS} sats", n, t).equals("14 sats"), "names are case-insensitive");
        check(TextTemplate.expand("$1 {sats} \\", n, t).equals("$1 14 \\"), "dollar and backslash survive");
        check(TextTemplate.check("battery {vbat/6:2}") == null && TextTemplate.check("plain") == null, "good templates pass");
        check(TextTemplate.check("battery {vbat") != null && TextTemplate.check("a } b") != null && TextTemplate.check("{ x y }") != null, "bad braces are refused");
        check(TextTemplate.check("{vbat/0}") != null, "divisor 0 refused");
        check(RuleText.validate(spec("ch5 >= 1500 -> battery {vbat/6:2}")) == null, "rule with a placeholder validates");
        check(RuleText.validate(spec("ch5 >= 1500 -> battery {vbat")) != null, "rule with a broken placeholder is refused");

        // ---------- spoken values ----------
        Rec r = new Rec();
        RuleEngine e = new RuleEngine(RuleParser.parse("hold = 0\nvbat / 6 < 3.6 -> battery {vbat/6:2} per cell"), r);
        n.put("vbat", 24.0); e.update(n, t, 100);
        n.put("vbat", 21.2); e.update(n, t, 200);
        check(r.said.size() == 1 && r.said.get(0).equals("battery 3.53 per cell"), "spoken text carries the live value: " + r.said);

        // ---------- level mode: warnings ----------
        String warnText = "hold = 0\n" +
            "vbat / 6 < 3.70 and vbat / 6 >= 3.55 -> BATTERY LOW {vbat/6:2}\n" +
            "vbat / 6 < 3.55 and vbat > 12 -> !BATTERY CRITICAL {vbat/6:2}\n" +
            "up_lq < 70 -> LINK {up_lq}%\n" +
            "tlm == 0 -> !TELEMETRY LOST\n" +
            "ch5 >= 1950 -> *ARMED\n";
        RuleParser.Result wp = RuleParser.parse(warnText);
        check(wp.errors.isEmpty() && wp.rules.size() == 5, "warning rules parse: " + wp.errors);
        WarningModel wm = new WarningModel(wp.rules);
        RuleEngine we = new RuleEngine(wp, null, wm);
        Map<String, Double> v = new HashMap<String, Double>(); Map<String, String> vt = new HashMap<String, String>();
        v.put("vbat", 25.0); v.put("up_lq", 100.0); v.put("tlm", 1.0); v.put("ch5", 1000.0);
        we.update(v, vt, 100);
        check(wm.items(v, vt).isEmpty(), "healthy start: no warnings");
        v.put("vbat", 21.8); v.put("ch5", 2000.0);                       // 3.63 V per cell, armed
        we.update(v, vt, 200);
        List<WarningModel.Item> it = wm.items(v, vt);
        check(it.size() == 2 && it.get(0).text.equals("BATTERY LOW 3.63") && it.get(0).level == WarningModel.Level.WARNING, "battery warning shows with its value: " + show(it));
        check(it.get(1).text.equals("ARMED") && it.get(1).level == WarningModel.Level.INFO, "info line after the warning: " + show(it));
        v.put("vbat", 21.0); we.update(v, vt, 300);                       // 3.50 V per cell
        it = wm.items(v, vt);
        check(it.get(0).level == WarningModel.Level.CRITICAL && it.get(0).text.equals("BATTERY CRITICAL 3.50") && it.size() == 2, "critical replaces low, worst first: " + show(it));
        check(WarningModel.worst(it) == WarningModel.Level.CRITICAL, "worst level");
        v.put("up_lq", 45.0); v.put("tlm", 0.0); we.update(v, vt, 400);
        it = wm.items(v, vt);
        check(it.size() == 4 && it.get(0).level == WarningModel.Level.CRITICAL && it.get(1).level == WarningModel.Level.CRITICAL, "several warnings, criticals first: " + show(it));
        check(it.get(2).text.equals("LINK 45%"), "values update live: " + show(it));
        v.put("up_lq", 99.0); v.put("tlm", 1.0); v.put("vbat", 25.0); we.update(v, vt, 500);
        it = wm.items(v, vt);
        check(it.size() == 1 && it.get(0).text.equals("ARMED"), "warnings clear when the condition ends: " + show(it));
        // an input that disappears must clear its warning (a stale 'battery low' is worse than none)
        v.put("vbat", 21.0); we.update(v, vt, 600);
        check(wm.items(v, vt).size() == 2, "critical battery on again");
        v.remove("vbat"); we.update(v, vt, 700);
        it = wm.items(v, vt);
        check(it.size() == 1 && it.get(0).text.equals("ARMED"), "missing variable clears its warning: " + show(it));
        v.put("vbat", 21.0); we.update(v, vt, 800);
        check(wm.items(v, vt).size() == 2, "and it comes back with the value");
        // a warning that is already true at start shows at once, with hold respected for changes
        WarningModel m3 = new WarningModel(RuleParser.parse("hold = 200\nup_lq < 70 -> LINK").rules);
        RuleEngine e3 = new RuleEngine(RuleParser.parse("hold = 200\nup_lq < 70 -> LINK"), null, m3);
        Map<String, Double> low = new HashMap<String, Double>(); low.put("up_lq", 10.0);
        e3.update(low, vt, 0);
        check(m3.activeCount() == 1, "a warning that is true at start shows at once");
        Map<String, Double> good = new HashMap<String, Double>(); good.put("up_lq", 99.0);
        e3.update(good, vt, 50); e3.update(good, vt, 100);
        check(m3.activeCount() == 1, "turning off waits for the hold time");
        e3.update(good, vt, 300);
        check(m3.activeCount() == 0, "and then turns off");
        // sound rules do not change: still silent for a missing variable, no listener calls needed
        Rec sr = new Rec();
        RuleEngine se = new RuleEngine(RuleParser.parse("hold = 0\nvbat < 20 -> low"), sr);
        Map<String, Double> sv = new HashMap<String, Double>(); sv.put("vbat", 25.0);
        se.update(sv, vt, 0); sv.remove("vbat"); se.update(sv, vt, 100); sv.put("vbat", 19.0); se.update(sv, vt, 200);
        check(sr.said.size() == 1, "sound rules keep their old behaviour: " + sr.said);

        // ---------- describing warnings ----------
        RuleText.Spec ws = RuleText.fromLine("vbat / 6 < 3.55 -> !BATTERY CRITICAL {vbat/6:2}");
        check(RuleText.describeWarning(ws).startsWith("[CRITICAL] When vbat \u00F7 6 < 3.55: show"), "critical description: " + RuleText.describeWarning(ws));
        check(RuleText.describeWarning(RuleText.fromLine("ch5 >= 1950 -> *ARMED")).equals("[INFO] When ch5 \u2265 1950: show \u201CARMED\u201D"), "info description: " + RuleText.describeWarning(RuleText.fromLine("ch5 >= 1950 -> *ARMED")));
        check(RuleText.describeWarning(RuleText.fromLine("up_lq < 70 -> LINK")).startsWith("[WARNING]"), "warning description");

        // ---------- sticks ----------
        StickLayout k2 = StickLayout.forMode(2, 1, 2, 3, 4);
        check(k2.leftVertical == 3 && k2.leftHorizontal == 4 && k2.rightVertical == 2 && k2.rightHorizontal == 1, "mode 2: throttle+yaw left, pitch+roll right");
        StickLayout k1 = StickLayout.forMode(1, 1, 2, 3, 4);
        check(k1.leftVertical == 2 && k1.leftHorizontal == 4 && k1.rightVertical == 3 && k1.rightHorizontal == 1, "mode 1: pitch+yaw left, throttle+roll right");
        StickLayout k3 = StickLayout.forMode(3, 1, 2, 3, 4);
        check(k3.leftVertical == 3 && k3.leftHorizontal == 1 && k3.rightVertical == 2 && k3.rightHorizontal == 4, "mode 3");
        StickLayout k4 = StickLayout.forMode(4, 1, 2, 3, 4);
        check(k4.leftVertical == 2 && k4.leftHorizontal == 1 && k4.rightVertical == 3 && k4.rightHorizontal == 4, "mode 4");
        check(StickLayout.forMode(99, 1, 2, 3, 4).leftVertical == 3, "unknown mode falls back to mode 2");
        check(StickLayout.norm(1500) == 0.0 && StickLayout.norm(2000) == 1.0 && StickLayout.norm(1000) == -1.0, "normalising microseconds");
        check(StickLayout.norm(900) == -1.0 && StickLayout.norm(2300) == 1.0, "values are clamped");
        check(Math.abs(StickLayout.norm(1750) - 0.5) < 1e-9, "quarter travel is 0.5");
        check(StickLayout.axis(1750, true) == -0.5 && StickLayout.axis(1750, false) == 0.5, "invert flips the axis");
        check(StickLayout.axisName(k2, 2, true, true, 1, 2, 3, 4).equals("T") && StickLayout.axisName(k2, 2, false, false, 1, 2, 3, 4).equals("R"), "axis names");

        // ---------- the shipped default warning rules ----------
        String dw = readFile("app/src/main/assets/i18n/warnings.en.txt");
        check(!dw.isEmpty(), "default warning rules found");
        RuleParser.Result dwp = RuleParser.parse(dw);
        check(dwp.errors.isEmpty() && dwp.rules.size() == 8, "default warnings parse: " + dwp.errors + " " + dwp.rules.size());
        int editable = 0;
        for (RuleText.Item itm : RuleText.parse(dw)) if (itm.kind == RuleText.Kind.RULE && itm.spec != null) editable++;
        check(editable == 8, "all default warnings are editable in the GUI: " + editable);
        for (Rule rr : dwp.rules) check(TextTemplate.check(rr.onTrue) == null, "template ok: " + rr.onTrue);

        WarningModel dm = new WarningModel(dwp.rules);
        RuleEngine de = new RuleEngine(dwp, null, dm);
        Map<String, Double> dv = new HashMap<String, Double>(); Map<String, String> dt = new HashMap<String, String>();
        dv.put("vbat", 25.2); dv.put("up_lq", 100.0); dv.put("tlm", 1.0); dv.put("ch5", 1000.0); dv.put("sats", 16.0); dt.put("fm", "ANGL"); BatteryProfiles.addDerived(dv, 6);
        long dn = 0;
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(dm.items(dv, dt).isEmpty(), "bench, healthy, disarmed: nothing shown: " + show(dm.items(dv, dt)));
        dv.put("ch5", 2000.0);
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(show(dm.items(dv, dt)).equals("[INFO:ARMED; ]"), "armed shows the info line only: " + show(dm.items(dv, dt)));
        dv.put("vbat", 21.5); BatteryProfiles.addDerived(dv, 6);                                   // 3.58 V per cell
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(show(dm.items(dv, dt)).equals("[WARNING:BATTERY LOW 3.58 V per cell; INFO:ARMED; ]"), "low battery with the live value: " + show(dm.items(dv, dt)));
        dv.put("vbat", 20.9); BatteryProfiles.addDerived(dv, 6);                                   // 3.48 V per cell
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(show(dm.items(dv, dt)).startsWith("[CRITICAL:BATTERY CRITICAL 3.48 V per cell"), "critical battery: " + show(dm.items(dv, dt)));
        dv.put("up_lq", 55.0); dv.put("sats", 6.0); dt.put("fm", "RTH");
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        String all = show(dm.items(dv, dt));
        check(all.contains("WARNING:LINK QUALITY 55%") && all.contains("WARNING:GPS 6 SATELLITES") && all.contains("CRITICAL:RETURN TO HOME"), "link, GPS and RTH warnings: " + all);
        dv.put("up_lq", 20.0);
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        all = show(dm.items(dv, dt));
        check(all.contains("CRITICAL:LINK CRITICAL 20%") && !all.contains("LINK QUALITY"), "critical link replaces the plain link warning: " + all);
        dv.put("ch5", 1000.0); dv.put("up_lq", 0.0); dv.put("tlm", 0.0);                  // landed, disarmed, aircraft off
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(!show(dm.items(dv, dt)).contains("LINK") && !show(dm.items(dv, dt)).contains("TELEMETRY"), "disarmed: no link warnings after landing: " + show(dm.items(dv, dt)));
        dv.remove("vbat"); dv.remove("cell"); dv.remove("sats"); dt.remove("fm");                                 // aircraft powered off: battery, GPS and mode all expire
        for (int i = 0; i < 10; i++) de.update(dv, dt, dn += 100);
        check(dm.items(dv, dt).isEmpty(), "aircraft off: no stale battery or flight-mode warning remains: " + show(dm.items(dv, dt)));

        // ---------- warning timeout ----------
        RuleParser.Result tp = RuleParser.parse("hold = 0\nch5 >= 1950 -> *ARMED @5\nvbat / 6 < 3.55 and vbat > 12 -> !BATTERY CRITICAL {vbat/6:2}");
        check(tp.errors.isEmpty() && tp.rules.get(0).repeatSec == 5 && tp.rules.get(0).onTrue.equals("*ARMED") && tp.rules.get(1).repeatSec == 0, "@5 is read as a 5 second timeout: " + tp.errors);
        WarningModel tm = new WarningModel(tp.rules);
        RuleEngine te = new RuleEngine(tp, null, tm);
        Map<String, Double> tv = new HashMap<String, Double>(); Map<String, String> tt = new HashMap<String, String>();
        tv.put("ch5", 1000.0); tv.put("vbat", 25.0);
        long tn = 0;
        tm.setNow(tn += 100); te.update(tv, tt, tn);
        check(tm.items(tv, tt).isEmpty(), "disarmed: nothing shown");
        tv.put("ch5", 2000.0);                                   // arm at t = 200 ms
        tm.setNow(tn += 100); te.update(tv, tt, tn);
        check(show(tm.items(tv, tt)).equals("[INFO:ARMED; ]"), "armed label appears: " + show(tm.items(tv, tt)));
        tm.setNow(tn + 4000); te.update(tv, tt, tn + 4000);
        check(show(tm.items(tv, tt)).equals("[INFO:ARMED; ]"), "still shown after 4 seconds");
        tm.setNow(tn + 5100); te.update(tv, tt, tn + 5100);
        check(tm.items(tv, tt).isEmpty(), "gone after the 5 second timeout");
        tm.setNow(tn + 60000); te.update(tv, tt, tn + 60000);
        check(tm.items(tv, tt).isEmpty(), "stays gone while the condition stays true");
        tv.put("vbat", 20.9);                                    // 3.48 V per cell: a warning without a timeout is not affected
        tm.setNow(tn + 61000); te.update(tv, tt, tn + 61000);
        check(show(tm.items(tv, tt)).startsWith("[CRITICAL:BATTERY CRITICAL 3.48"), "warnings without a timeout stay: " + show(tm.items(tv, tt)));
        tm.setNow(tn + 400000); te.update(tv, tt, tn + 400000);
        check(show(tm.items(tv, tt)).startsWith("[CRITICAL:BATTERY CRITICAL"), "even after a long time");
        tv.put("ch5", 1000.0); tm.setNow(tn + 401000); te.update(tv, tt, tn + 401000);
        check(show(tm.items(tv, tt)).startsWith("[CRITICAL") && tm.items(tv, tt).size() == 1, "disarmed: the armed label is not there");
        tv.put("ch5", 2000.0); tm.setNow(tn + 402000); te.update(tv, tt, tn + 402000);
        check(tm.items(tv, tt).size() == 2, "arming again shows the label again");
        tm.setNow(tn + 402000 + 5200); te.update(tv, tt, tn + 402000 + 5200);
        check(tm.items(tv, tt).size() == 1, "and it times out again");
        // already true when the app starts: the timeout counts from the start
        WarningModel sm = new WarningModel(tp.rules);
        RuleEngine se2 = new RuleEngine(tp, null, sm);
        Map<String, Double> sv2 = new HashMap<String, Double>(); sv2.put("ch5", 2000.0); sv2.put("vbat", 25.0);
        sm.setNow(10000); se2.update(sv2, tt, 10000);
        check(sm.items(sv2, tt).size() == 1, "armed when the app starts: shown");
        sm.setNow(15500); se2.update(sv2, tt, 15500);
        check(sm.items(sv2, tt).isEmpty(), "and hidden 5 seconds later");
        // describing and editing a warning with a timeout
        RuleText.Spec ts = RuleText.fromLine("ch5 >= 1950 -> *ARMED @5");
        check(ts != null && ts.repeatSec == 5 && ts.onTrue.equals("*ARMED"), "the editor reads the timeout");
        check(RuleText.describeWarning(ts).equals("[INFO] When ch5 \u2265 1950: show \u201CARMED\u201D, hides after 5 s"), "description: " + RuleText.describeWarning(ts));
        check(RuleText.line(ts).equals("ch5 >= 1950 -> *ARMED @5") && RuleText.validate(ts) == null, "the editor writes the same line back: " + RuleText.line(ts));
        ts.repeatSec = 0;
        check(RuleText.line(ts).equals("ch5 >= 1950 -> *ARMED"), "no timeout: no @");
        check(!RuleText.describeWarning(ts).contains("hides"), "no timeout: nothing in the description");
        // the shipped defaults
        RuleParser.Result dwt = RuleParser.parse(readFile("app/src/main/assets/i18n/warnings.en.txt"));
        int timed = 0;
        for (Rule rr : dwt.rules) if (rr.repeatSec > 0) { timed++; check(rr.onTrue.equals("*ARMED") && rr.repeatSec == 5, "the ARMED label is the timed one"); }
        check(timed == 1 && dwt.errors.isEmpty(), "the default warnings have exactly one timed rule: " + timed);

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

    static RuleText.Spec spec(String line) { return RuleText.fromLine(line); }

    static String show(List<WarningModel.Item> items) {
        StringBuilder sb = new StringBuilder("[");
        for (WarningModel.Item i : items) sb.append(i.level).append(':').append(i.text).append("; ");
        return sb.append("]").toString();
    }
}
