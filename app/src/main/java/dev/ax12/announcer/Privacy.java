// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Takes out of a text what must not leave the radio in a bug report: GPS positions, network addresses, e-mail addresses and long numbers
 * such as device or SIM identifiers. A report is built from facts that hold none of these, and this is the second lock on the door.
 * Plain Java, so that it can be tested on a PC.
 */
public final class Privacy {
    private Privacy() {}

    private static final String DEC = "-?\\d{1,3}\\.\\d{4,}";
    private static final Pattern POSITION = Pattern.compile(DEC + "\\s*[,;/ ]\\s*" + DEC);
    private static final Pattern LONG_DECIMAL = Pattern.compile("(?<![\\d.])-?\\d{1,3}\\.\\d{5,}(?![\\d.])");
    private static final Pattern MAC = Pattern.compile("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");
    private static final Pattern IPV6_SHORT = Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){1,7}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,5})?");
    private static final Pattern IPV6 = Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{1,4}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<!\\d)\\d{13,}(?!\\d)");

    /** Replaces what must not leave the radio by a short note saying what was there. */
    public static String redact(String text) {
        if (text == null) return "";
        String t = POSITION.matcher(text).replaceAll("<position removed>");
        t = LONG_DECIMAL.matcher(t).replaceAll("<number removed>");
        t = MAC.matcher(t).replaceAll("<address removed>");
        t = IPV4.matcher(t).replaceAll("<address removed>");
        t = IPV6_SHORT.matcher(t).replaceAll("<address removed>");
        t = IPV6.matcher(t).replaceAll("<address removed>");
        t = EMAIL.matcher(t).replaceAll("<address removed>");
        t = LONG_DIGITS.matcher(t).replaceAll("<number removed>");
        return t;
    }

    /** What the text still holds that redaction would take out (empty when it is clean). */
    public static List<String> findings(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        if (POSITION.matcher(text).find() || LONG_DECIMAL.matcher(text).find()) out.add("position");
        if (MAC.matcher(text).find() || IPV4.matcher(text).find() || IPV6.matcher(text).find() || IPV6_SHORT.matcher(text).find()) out.add("address");
        if (EMAIL.matcher(text).find()) out.add("e-mail");
        if (LONG_DIGITS.matcher(text).find()) out.add("long number");
        return out;
    }
}
