// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The approval window of a flight (for example the two hours the aviation authority approved): from when to when it is lawful to fly, how
 * much of it is left, and when to warn. The window repeats every day; it may cross midnight. Plain Java, so that it can be tested on a PC.
 */
public final class ApprovalWindow {
    public static final String DEFAULT_WARN = "30,15,10,5,2,1";
    public static final int MAX_WARNINGS = 8;
    public static final int MAX_WARN_MINUTES = 240;
    private static final int DAY_S = 86400;

    public enum State { OFF, BEFORE, OPEN, ENDED }

    private boolean enabled;
    private int startMin = 16 * 60, endMin = 18 * 60;
    private int[] warn = parseWarn(DEFAULT_WARN);

    public void configure(boolean on, int startMinute, int endMinute, String warnText) {
        enabled = on;
        startMin = Math.max(0, Math.min(1439, startMinute));
        endMin = Math.max(0, Math.min(1439, endMinute));
        int[] w = parseWarn(warnText);
        warn = w == null ? parseWarn(DEFAULT_WARN) : w;
    }

    public boolean enabled() { return enabled; }
    public int startMinute() { return startMin; }
    public int endMinute() { return endMin; }
    public int[] warnings() { return warn.clone(); }

    private int startS() { return startMin * 60; }

    private int endS() {
        int e = endMin * 60;
        return e <= startS() ? e + DAY_S : e;               // an end at or before the start is the next day's
    }

    private static int mod(int a) { return ((a % DAY_S) + DAY_S) % DAY_S; }

    /** Seconds of the day (0 to 86399) to where the window stands. */
    public State state(int secOfDay) {
        if (!enabled) return State.OFF;
        int s = startS(), e = endS();
        if ((secOfDay >= s && secOfDay < e) || (secOfDay + DAY_S >= s && secOfDay + DAY_S < e)) return State.OPEN;
        return untilStart(secOfDay) <= sinceEnd(secOfDay) ? State.BEFORE : State.ENDED;
    }

    private int untilStart(int sec) { return mod(startS() - sec); }
    private int sinceEnd(int sec) { return mod(sec - endS()); }

    /** Seconds left in the window; NaN unless it is open. */
    public double leftSeconds(int secOfDay) {
        if (state(secOfDay) != State.OPEN) return Double.NaN;
        int n = secOfDay >= startS() && secOfDay < endS() ? secOfDay : secOfDay + DAY_S;
        return endS() - n;
    }

    /** Seconds until the window opens; NaN unless it is still to come. */
    public double startsInSeconds(int secOfDay) { return state(secOfDay) == State.BEFORE ? untilStart(secOfDay) : Double.NaN; }

    /** Seconds since the window closed; NaN unless it has closed. */
    public double endedSecondsAgo(int secOfDay) { return state(secOfDay) == State.ENDED ? sinceEnd(secOfDay) : Double.NaN; }

    /** The minute marks (30, 15 ...) that the time left passed between two looks, the bigger first. */
    public int[] crossed(double prevLeftSec, double leftSec) {
        if (Double.isNaN(prevLeftSec) || Double.isNaN(leftSec)) return new int[0];
        List<Integer> out = new ArrayList<Integer>();
        for (int m : warn) if (prevLeftSec > m * 60.0 && leftSec <= m * 60.0) out.add(m);
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    // ------------------------------------------------------------------ text

    /** "09:05" from minutes of the day; anything else gives "--:--". */
    public static String hm(int minutes) {
        if (minutes < 0 || minutes > 1439) return "--:--";
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** "16:30" or "9:05" to minutes of the day, or -1. */
    public static int parseHm(String s) {
        if (s == null) return -1;
        String t = s.trim();
        int c = t.indexOf(':');
        if (c < 1 || c > 2 || t.length() - c != 3) return -1;
        try {
            int h = Integer.parseInt(t.substring(0, c)), m = Integer.parseInt(t.substring(c + 1));
            return h >= 0 && h <= 23 && m >= 0 && m <= 59 ? h * 60 + m : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "1:12:05" for an hour or more, "12:05" below; never negative. */
    public static String clock(double seconds) {
        long s = Double.isNaN(seconds) ? 0 : Math.max(0, (long) Math.floor(seconds));
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** Minute marks from text such as "30,15,10,5,2,1": whole minutes 1 to 240, the bigger first, no repeats; null for anything else. Empty means none. */
    public static int[] parseWarn(String text) {
        if (text == null) return null;
        List<Integer> out = new ArrayList<Integer>();
        for (String part : text.split("[,; ]+")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int v;
            try { v = Integer.parseInt(p); } catch (NumberFormatException e) { return null; }
            if (v < 1 || v > MAX_WARN_MINUTES) return null;
            if (!out.contains(v)) out.add(v);
        }
        if (out.size() > MAX_WARNINGS) return null;
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        Arrays.sort(r);
        for (int i = 0; i < r.length / 2; i++) { int t = r[i]; r[i] = r[r.length - 1 - i]; r[r.length - 1 - i] = t; }
        return r;
    }

    public static boolean validWarn(String text) { return parseWarn(text) != null; }

    public static String joinWarn(int[] marks) {
        StringBuilder sb = new StringBuilder();
        for (int m : marks) { if (sb.length() > 0) sb.append(','); sb.append(m); }
        return sb.toString();
    }

    // ------------------------------------------------------------------ what the voice says

    public static String minutesLeftSpeech(String language, int minutes) {
        return minutes == 1 ? I18n.tIn(language, "One minute left in the approval window.") : I18n.tIn(language, "{0} minutes left in the approval window.", minutes);
    }

    public static String endedSpeech(String language) { return I18n.tIn(language, "The approval window has ended. Land now."); }

    public static String outsideSpeech(String language) { return I18n.tIn(language, "Warning: you are outside the approval window."); }

    /** The line of the chip on the screen. */
    public String chipText(int secOfDay) {
        State st = state(secOfDay);
        if (st == State.OPEN) return I18n.t("Window {0} left", clock(leftSeconds(secOfDay)));
        if (st == State.BEFORE) return I18n.t("Window opens in {0}", clock(startsInSeconds(secOfDay)));
        if (st == State.ENDED) return I18n.t("Window ended {0} ago", clock(endedSecondsAgo(secOfDay)));
        return "";
    }
}
