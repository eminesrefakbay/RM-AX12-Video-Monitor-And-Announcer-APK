package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for the CRSF parser, the channel scaler, per-cell division and text rules. */
public class ExtraTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    static class Rec implements Announcer {
        final List<String> said = new ArrayList<String>();
        public void say(String text, boolean urgent) { said.add((urgent ? "!" : "") + text); }
        String joined() { return said.toString(); }
    }
    static byte[] be16(int v) { return new byte[]{(byte) (v >> 8), (byte) v}; }
    static byte[] cat(byte[]... parts) {
        int n = 0; for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n]; int o = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
        return out;
    }

    public static void main(String[] args) {
        // ---------- CRC and frames ----------
        byte[] f = CrsfParser.makeFrame(0xC8, 0x08, cat(be16(252), be16(123), new byte[]{0x00, 0x0B, (byte) 0xB8, 87}));
        CrsfParser p = new CrsfParser();
        Map<String, Double> num = new HashMap<String, Double>(); Map<String, String> txt = new HashMap<String, String>();
        check(p.feed(f, num, txt), "battery frame accepted");
        check(near(num.get("vbat"), 25.2, 1e-9), "vbat 25.2 V: " + num.get("vbat"));
        check(near(num.get("curr"), 12.3, 1e-9), "curr 12.3 A");
        check(near(num.get("capa"), 3000, 1e-9), "capacity 3000 mAh: " + num.get("capa"));
        check(near(num.get("batpct"), 87, 1e-9), "remaining 87 %");

        // a corrupted byte must be rejected
        byte[] bad = f.clone(); bad[4] ^= 0x10;
        Map<String, Double> n2 = new HashMap<String, Double>();
        check(!new CrsfParser().feed(bad, n2, txt) && n2.isEmpty(), "bad CRC rejected");
        CrsfParser p2 = new CrsfParser(); p2.feed(bad, n2, txt);
        check(p2.framesBadCrc == 1, "bad CRC counted");
        check(!p.feed(new byte[]{(byte) 0xC8, 0x02}, num, txt), "truncated frame ignored");
        check(!p.feed(CrsfParser.makeFrame(0xC8, 0x55, new byte[]{1, 2, 3}), num, txt), "unknown type ignored");

        // link statistics: rssi -62/-70, LQ 98, SNR 9, antenna 0, rf mode 4, power index 3 (100 mW), down rssi -65, LQ 97, SNR 8
        byte[] ls = CrsfParser.makeFrame(0xC8, 0x14, new byte[]{62, 70, 98, 9, 0, 4, 3, 65, 97, 8});
        check(p.feed(ls, num, txt), "link stats accepted");
        check(near(num.get("up_lq"), 98, 0) && near(num.get("up_rssi1"), -62, 0) && near(num.get("tx_power"), 100, 0), "link stats values");
        check(near(num.get("dn_lq"), 97, 0) && near(num.get("up_snr"), 9, 0), "downlink values");

        // flight mode string
        byte[] fm = CrsfParser.makeFrame(0xC8, 0x21, new byte[]{'R', 'T', 'H', 0});
        check(p.feed(fm, num, txt) && "RTH".equals(txt.get("fm")), "flight mode text: " + txt.get("fm"));

        // GPS: 36.8200N 34.6100E, 45.5 km/h, heading 182.50, altitude 1130 -> 130 m, 24 sats
        int latV = (int) Math.round(36.82 * 1e7), lonV = (int) Math.round(34.61 * 1e7);
        byte[] gps = CrsfParser.makeFrame(0xC8, 0x02, cat(
            new byte[]{(byte) (latV >> 24), (byte) (latV >> 16), (byte) (latV >> 8), (byte) latV},
            new byte[]{(byte) (lonV >> 24), (byte) (lonV >> 16), (byte) (lonV >> 8), (byte) lonV},
            be16(455), be16(18250), be16(1130), new byte[]{24}));
        check(p.feed(gps, num, txt), "GPS accepted");
        check(near(num.get("gps_lat"), 36.82, 1e-6) && near(num.get("gps_lon"), 34.61, 1e-6), "GPS position");
        check(near(num.get("gps_speed"), 45.5, 1e-9) && near(num.get("gps_heading"), 182.5, 1e-9), "GPS speed and heading");
        check(near(num.get("gps_alt"), 130, 0) && near(num.get("sats"), 24, 0), "GPS altitude and satellites");

        // attitude: pitch +0.5236 rad (30 deg), roll -0.2618 (-15 deg), yaw 1.5708 (90 deg)
        byte[] att = CrsfParser.makeFrame(0xC8, 0x1E, cat(be16(5236), be16(-2618), be16(15708)));
        check(p.feed(att, num, txt) && near(num.get("pitch"), 30, 0.01) && near(num.get("roll"), -15, 0.01) && near(num.get("yaw"), 90, 0.01), "attitude");
        check(p.framesOk == 5, "five frames counted: " + p.framesOk);

        // ---------- channel scaler ----------
        ChannelScaler sc = new ChannelScaler(true, ChannelScaler.Mode.PERCENT);
        double[] pct = new double[16]; pct[4] = 100; pct[5] = -100;
        check(!sc.observe(pct) && sc.mode() == ChannelScaler.Mode.PERCENT, "percent range keeps percent");
        check(near(sc.toMicros(100), 2000, 1e-9) && near(sc.toMicros(-100), 1000, 1e-9) && near(sc.toMicros(0), 1500, 1e-9), "percent mapping");
        double[] pm = new double[16]; pm[4] = 1024;
        check(sc.observe(pm) && sc.mode() == ChannelScaler.Mode.PERMILLE, "1024 switches to permille");
        check(near(sc.toMicros(1000), 2000, 1e-9) && near(sc.toMicros(0), 1500, 1e-9), "permille mapping");
        double[] us = new double[16]; us[4] = 1900;
        check(sc.observe(us) && sc.mode() == ChannelScaler.Mode.MICROS && near(sc.toMicros(1500), 1500, 0), "1900 switches to micros");
        check(!sc.observe(pct) && sc.mode() == ChannelScaler.Mode.MICROS, "never moves back");
        ChannelScaler fixed = new ChannelScaler(false, ChannelScaler.Mode.PERCENT);
        check(!fixed.observe(us) && fixed.mode() == ChannelScaler.Mode.PERCENT, "auto off keeps the chosen unit");

        // ---------- per-cell division, text rules, variable hysteresis ----------
        RuleParser.Result pr = RuleParser.parse("hold = 0\nvbat / 6 < 3.5 -> battery low | battery ok\nfm ~ \"RTH\" -> return to home | ");
        check(pr.errors.isEmpty(), "new syntax parses: " + pr.errors);
        check(pr.rules.get(0).orGroups.get(0).get(0).divisor == 6.0, "divisor parsed");
        check(pr.rules.get(1).orGroups.get(0).get(0).isText(), "text atom parsed");
        check(!RuleParser.parse("vbat / 0 < 3 -> x").errors.isEmpty(), "divide by zero rejected");
        check(RuleParser.parse("fm ~ RTH -> x").errors.size() == 1, "unquoted text rejected");

        Rec r = new Rec();
        RuleEngine e = new RuleEngine(pr, r);
        Map<String, Double> v = new HashMap<String, Double>(); Map<String, String> t = new HashMap<String, String>();
        long now = 0;
        v.put("vbat", 24.6); t.put("fm", "ANGL"); e.update(v, t, now += 100);   // 4.10 V per cell, silent start
        v.put("vbat", 21.5); e.update(v, t, now += 100);                          // 3.58 V per cell
        check(r.said.isEmpty(), "above the threshold stays silent");
        v.put("vbat", 20.9); e.update(v, t, now += 100);                          // 3.48 V per cell
        check(r.joined().equals("[battery low]"), "per-cell threshold fires: " + r.joined());
        v.put("vbat", 21.1); e.update(v, t, now += 100);                          // 3.517, inside the 1 % band (3.465..3.535)
        check(r.said.size() == 1, "no chatter inside the voltage hysteresis band");
        v.put("vbat", 21.4); e.update(v, t, now += 100);                          // 3.567, clear of the band
        check(r.joined().equals("[battery low, battery ok]"), "recovers above the band: " + r.joined());
        t.put("fm", "RTH"); e.update(v, t, now += 100);
        check(r.joined().equals("[battery low, battery ok, return to home]"), "text rule fires: " + r.joined());
        t.put("fm", "rth"); e.update(v, t, now += 100);
        check(r.said.size() == 3, "case-insensitive and no repeat");
        // missing text variable skips the rule instead of firing
        Rec r2 = new Rec();
        RuleEngine e2 = new RuleEngine(RuleParser.parse("hold = 0\nfm == \"ACRO\" -> acro"), r2);
        e2.update(v, 50); e2.update(v, 100);
        check(r2.said.isEmpty(), "no text variable -> rule skipped");

        RuleParser.Result sc2 = RuleParser.parse("scale = permille\nvar_hysteresis = 2");
        check(sc2.errors.isEmpty() && sc2.settings.scale.equals("permille") && sc2.settings.varHysteresisPct == 2.0, "scale and var_hysteresis settings");
        check(!RuleParser.parse("scale = banana").errors.isEmpty(), "bad scale rejected");
        check(RuleParser.parse("").settings.scale.equals("auto"), "scale defaults to auto");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
