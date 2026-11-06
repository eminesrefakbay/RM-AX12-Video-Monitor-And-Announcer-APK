package dev.ax12.announcer;

import java.util.List;

/** Tests for the channel bars configuration. */
public class AuxBarsTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) {
        List<AuxBars.Bar> d = AuxBars.defaults();
        check(d.size() == 8, "eight slots");
        check(d.get(0).channel == 5 && d.get(7).channel == 12, "channels 5 to 12");
        check(d.get(0).name.equals("ARM") && d.get(2).name.equals("RTH") && d.get(5).name.equals("HOLD"), "default names follow the INAV modes");
        boolean allOn = true; for (AuxBars.Bar b : d) allOn &= b.enabled;
        check(allOn, "all enabled by default");
        check(AuxBars.side(d, true).size() == 4 && AuxBars.side(d, false).size() == 4, "four on each side");
        check(AuxBars.side(d, true).get(0).channel == 5 && AuxBars.side(d, true).get(3).channel == 8, "left side is channels 5 to 8");
        check(AuxBars.side(d, false).get(0).channel == 9 && AuxBars.side(d, false).get(3).channel == 12, "right side is channels 9 to 12");

        // round trip
        d.get(1).name = "Flight mode"; d.get(2).enabled = false; d.get(6).channel = 14;
        String text = AuxBars.encode(d);
        List<AuxBars.Bar> back = AuxBars.decode(text);
        check(back.get(1).name.equals("Flight mode") && !back.get(2).enabled && back.get(6).channel == 14, "settings survive encode and decode");
        check(AuxBars.encode(back).equals(text), "encode(decode(x)) is stable");
        check(AuxBars.side(back, true).size() == 3, "a disabled bar leaves its side");

        // names
        check(AuxBars.cleanName("  arm  switch ", 5).equals("arm switch"), "spaces tidied");
        check(AuxBars.cleanName("a|b\nc", 5).equals("a b c"), "pipe and newline removed");
        check(AuxBars.cleanName("", 7).equals("CH7") && AuxBars.cleanName(null, 7).equals("CH7") && AuxBars.cleanName("   ", 9).equals("CH9"), "empty names fall back to CHn");
        check(AuxBars.cleanName("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 5).length() == AuxBars.MAX_NAME, "names are cut to the maximum length");
        check(AuxBars.cleanName("\u00C7\u0131rp\u0131", 5).equals("\u00C7\u0131rp\u0131"), "non-ASCII names are kept");

        // damaged storage
        List<AuxBars.Bar> bad = AuxBars.decode("garbage\n1|99|x\n0|abc|y\n1|6|OK\n");
        check(bad.get(0).channel == 5 && bad.get(1).channel == 6 && bad.get(2).channel == 7, "damaged lines fall back to defaults");
        check(bad.get(3).channel == 6 && bad.get(3).name.equals("OK"), "a good line after damaged ones is still read");
        check(bad.get(4).channel == 9 && bad.get(4).name.equals("AIRMODE"), "missing lines fall back to defaults");
        check(AuxBars.decode(null).size() == 8 && AuxBars.decode("").size() == 8, "null and empty storage give defaults");
        check(AuxBars.decode("0|6|only one").get(0).channel == 6 && !AuxBars.decode("0|6|only one").get(0).enabled, "a partial file still loads its first line");
        check(AuxBars.decode("1|7").get(0).name.equals("CH7"), "a line without a name gets CHn");

        // validation
        List<AuxBars.Bar> v = AuxBars.defaults();
        check(AuxBars.validate(v) == null, "defaults are valid");
        v.get(3).channel = 0;
        check(AuxBars.validate(v).contains("Bar 4"), "a bad channel is named: " + AuxBars.validate(v));
        v.get(3).channel = 17;
        check(AuxBars.validate(v) != null, "17 is refused");

        // values
        check(AuxBars.fraction(1000) == 0.0 && AuxBars.fraction(2000) == 1.0 && Math.abs(AuxBars.fraction(1500) - 0.5) < 1e-9, "fractions");
        check(AuxBars.fraction(900) == 0.0 && AuxBars.fraction(2400) == 1.0, "fractions are clamped");
        check(AuxBars.level(0.0) == 0 && AuxBars.level(0.5) == 1 && AuxBars.level(1.0) == 2, "low, middle, high");
        check(AuxBars.level(AuxBars.fraction(1150)) == 0 && AuxBars.level(AuxBars.fraction(1850)) == 2 && AuxBars.level(AuxBars.fraction(1500)) == 1, "a three position switch reads low, middle, high");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
