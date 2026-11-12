// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests of the fixes found in the first real flight log: the signal strength, the log rate, the approval window minutes and the descriptions. */
public class LogReadingTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File(f).toPath()), "UTF-8"); }
    static final String KT = "app/src/main/java/dev/ax12/announcer/";

    public static void main(String[] args) throws Exception {
        // ---------- the signal strength ----------
        check(CrsfParser.rssiDbm((byte) 201) == -55.0 && CrsfParser.rssiDbm((byte) 55) == -55.0, "-55 dBm is read from the signed byte 201 and from the magnitude 55");
        check(CrsfParser.rssiDbm((byte) 164) == -92.0 && CrsfParser.rssiDbm((byte) 240) == -16.0 && CrsfParser.rssiDbm((byte) 224) == -32.0, "the values of the real flight: 164 is -92 dBm, 240 is -16, 224 is -32");
        check(CrsfParser.rssiDbm((byte) 0) == 0.0 && CrsfParser.rssiDbm((byte) 128) == -128.0 && CrsfParser.rssiDbm((byte) 129) == -127.0 && CrsfParser.rssiDbm((byte) 255) == -1.0, "the edges: 0, 128, 129 and 255");
        boolean monotonic = true;
        for (int b = 1; b < 256; b++) if (b != 129 && b != 1 && b <= 128 && CrsfParser.rssiDbm((byte) b) != -b) monotonic = false;
        check(monotonic, "every magnitude up to 128 stays negative as it is");
        Map<String, Double> num = new HashMap<String, Double>();
        Map<String, String> txt = new HashMap<String, String>();
        CrsfParser p = new CrsfParser();
        check(p.feed(CrsfParser.makeFrame(0xC8, 0x14, new byte[]{(byte) 201, (byte) 176, 98, 9, 0, 4, 3, (byte) 221, 97, 8}), num, txt), "link statistics from the AX12's module are accepted");
        check(num.get("up_rssi1") == -55.0 && num.get("up_rssi2") == -80.0 && num.get("dn_rssi") == -35.0, "its signed bytes give -55, -80 and -35 dBm, not -201, -176 and -221");
        check(p.feed(CrsfParser.makeFrame(0xC8, 0x14, new byte[]{62, 70, 98, 9, 0, 4, 3, 65, 97, 8}), num, txt) && num.get("up_rssi1") == -62.0 && num.get("up_rssi2") == -70.0 && num.get("dn_rssi") == -65.0, "a receiver that sends magnitudes still gives -62, -70 and -65 dBm");

        // ---------- the log rate and the last line ----------
        int[] hz = FlightLogger.RATES_HZ;
        boolean up = true;
        for (int i = 1; i < hz.length; i++) if (hz[i] <= hz[i - 1]) up = false;
        check(up && hz[hz.length - 1] == 20 && hz[0] == 1, "the rates go from 1 to 20 rows per second (the radio is polled 20 times a second)");
        File d = FlightLoggerTests.tmp();
        FlightLogger lg = new FlightLogger(d, FlightLoggerTests.WALL, FlightLoggerTests.META);
        lg.configure(true, hz.length - 1, Arrays.asList("ch1"), 1);
        long t = FlightLoggerTests.run(lg, 0, 10000, 10, true);
        lg.update(t, false, FlightLoggerTests.nums(1000, 0, 0, 0), new HashMap<String, String>());
        List<File> files = FlightLoggerTests.logs(d);
        String log = files.isEmpty() ? "" : read(files.get(0).getPath());
        Matcher m = Pattern.compile("# end:.*actual_rate_hz=([0-9.]+)").matcher(log);
        check(m.find() && Math.abs(Double.parseDouble(m.group(1)) - 20.0) <= 1.0, "the last line of the log states the real rate (about 20 rows per second): " + (m.find(0) ? m.group(1) : "none"));
        check(log.contains("# rate_hz: 20") && log.contains("# telemetry: Values from the aircraft (battery, GPS, attitude) change only when the link sends them"), "the header names the rate and says how fresh the values are");
        FlightLoggerTests.rm(d);

        // ---------- the approval window ----------
        ApprovalWindow w = new ApprovalWindow();
        w.configure(true, 16 * 60, 17 * 60, "30,15,10,5,2,1");
        check(Math.abs(w.minutesLeftOrOver(16 * 3600 + 50 * 60) - 10.0) < 1e-9, "inside the window: 10 minutes left at 16:50");
        check(Math.abs(w.minutesLeftOrOver(17 * 3600)) < 1e-9, "zero at the end");
        check(Math.abs(w.minutesLeftOrOver(17 * 3600 + 4 * 60) + 4.0) < 1e-9, "negative after it: 4 minutes over at 17:04");
        check(Double.isNaN(w.minutesLeftOrOver(15 * 3600)), "nothing before it opens");
        ApprovalWindow off = new ApprovalWindow();
        off.configure(false, 16 * 60, 17 * 60, "30,15,10,5,2,1");
        check(Double.isNaN(off.minutesLeftOrOver(16 * 3600 + 30 * 60)), "nothing when the window is off");
        ApprovalWindow night = new ApprovalWindow();
        night.configure(true, 23 * 60, 1 * 60, "30,15,10,5,2,1");
        check(Math.abs(night.minutesLeftOrOver(23 * 3600 + 30 * 60) - 90.0) < 1e-9 && Math.abs(night.minutesLeftOrOver(1 * 3600 + 30 * 60) + 30.0) < 1e-9, "across midnight: 90 minutes left at 23:30, 30 over at 01:30");
        check(read(KT + "FlightExtras.kt").contains("window.minutesLeftOrOver(secOfDay(wallMs))") && !read(KT + "FlightExtras.kt").contains("numbers[\"win_left\"] = left / 60.0"), "the log and the cards use it");

        // ---------- the descriptions ----------
        String vc = read(KT + "VariableCatalog.kt");
        check(vc.contains("\"dbm\" to tn(\"Signal figure reported by the radio (not dBm: a small positive number, higher is stronger)\")") && vc.contains("\"dbm\" to tn(\"Signal figure\")"), "dbm is described as the radio's own signal figure");
        check(vc.contains("0 means the receiver is lost, a steady number such as 5 means the link is alive") && vc.contains("\"lost\" to tn(\"Link heartbeat\")"), "lost is described as a heartbeat");
        check(vc.contains("it changes by itself with dynamic power") && vc.contains("negative after it has ended (minutes over)"), "tx_power and win_left are described");
        String src = read("app/src/main/assets/i18n/source.txt");
        for (String k : new String[]{"Signal figure", "Link heartbeat", "Rows are written up to 20 times a second."}) {
            check(src.contains(k), "the text \"" + k + "\" is in the sources");
            for (String lang : new String[]{"tr", "de", "zh"}) check(read("app/src/main/assets/i18n/" + lang + ".tsv").contains(k), "and translated (" + lang + "): " + k);
        }
        check(!src.contains("\nLost frames\n") && !src.contains("\nSignal strength from the radio\n"), "the two wrong texts are gone");
        check(read(KT + "SettingsPanel.kt").contains("Rows are written up to 20 times a second."), "Settings > Flight logs has the note about how fresh the values are");

        // ---------- the README ----------
        String r = read("README.md");
        check(r.contains("### Reading a flight log") && r.contains("actual_rate_hz") && r.contains("`!ERR` means") && r.contains("minus 256") && r.contains("about once a second"), "the README explains how to read a log");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
