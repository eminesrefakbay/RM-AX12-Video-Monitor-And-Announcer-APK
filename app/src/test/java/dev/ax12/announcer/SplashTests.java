// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;

/** Tests of the splash screen: its timing and how it is wired in. */
public class SplashTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/" + f).toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        SplashTimeline T = null;
        // ---------- the helper curves ----------
        check(SplashTimeline.seg(0, 100, 200) == 0f && SplashTimeline.seg(100, 100, 200) == 0f && SplashTimeline.seg(150, 100, 200) == 0.5f && SplashTimeline.seg(200, 100, 200) == 1f && SplashTimeline.seg(900, 100, 200) == 1f, "a part that runs from 100 to 200 ms");
        check(SplashTimeline.easeOut(0) == 0f && SplashTimeline.easeOut(1) == 1f && SplashTimeline.easeOut(0.5f) > 0.5f && SplashTimeline.easeOut(-1) == 0f && SplashTimeline.easeOut(5) == 1f, "ease out: starts fast, stays inside 0 to 1");
        check(SplashTimeline.easeInOut(0) == 0f && SplashTimeline.easeInOut(1) == 1f && Math.abs(SplashTimeline.easeInOut(0.5f) - 0.5f) < 1e-6 && SplashTimeline.easeInOut(0.25f) < 0.25f && SplashTimeline.easeInOut(0.75f) > 0.75f, "ease in and out: slow, fast, slow");
        float prevOut = 0, prevIO = 0;
        boolean monotonic = true;
        for (int i = 1; i <= 100; i++) {
            float x = i / 100f, a = SplashTimeline.easeOut(x), b = SplashTimeline.easeInOut(x);
            if (a < prevOut || b < prevIO) monotonic = false;
            prevOut = a; prevIO = b;
        }
        check(monotonic, "both curves only ever go forward");
        check(SplashTimeline.bell(0) < 1e-6f && Math.abs(SplashTimeline.bell(0.5f) - 1f) < 1e-6f && SplashTimeline.bell(1) < 1e-6f && SplashTimeline.bell(-1) < 1e-6f, "a bell goes 0, 1, 0");
        boolean breathes = true;
        for (long ms = 0; ms < 6000; ms += 37) { float b = SplashTimeline.breathe(ms); if (b < 0f || b > 1f) breathes = false; }
        check(breathes && Math.abs(SplashTimeline.breathe(0) - 0.5f) < 1e-6f, "the glow breathes between 0 and 1");

        // ---------- the order of the parts ----------
        check(SplashTimeline.FRAME_START < SplashTimeline.GLYPH_START && SplashTimeline.GLYPH_START < SplashTimeline.CHIP_START && SplashTimeline.CHIP_START <= SplashTimeline.LETTER_FIRST, "the frame, then the logo, then the tag, then the title");
        check(SplashTimeline.LETTER_FIRST < SplashTimeline.RULE_START && SplashTimeline.RULE_START < SplashTimeline.TAG1_START && SplashTimeline.TAG1_START < SplashTimeline.TAG2_START && SplashTimeline.TAG2_START < SplashTimeline.VERSION_START, "then the line, the tagline and the version chip");
        check(SplashTimeline.BRACKET_START >= SplashTimeline.FRAME_START && SplashTimeline.BRACKET_END <= SplashTimeline.GLYPH_END + 1, "the brackets come while the frame closes");
        check(SplashTimeline.SHINE_START >= SplashTimeline.letterStart(SplashTimeline.LETTERS - 1) && SplashTimeline.SHINE_END <= SplashTimeline.TOTAL_MS, "the light crosses the title once the last letter is there");
        long lastLetterEnd = SplashTimeline.letterStart(SplashTimeline.LETTERS - 1) + SplashTimeline.LETTER_DUR;
        long[] ends = {SplashTimeline.FRAME_END, SplashTimeline.BRACKET_END, SplashTimeline.GLYPH_END, SplashTimeline.SCAN_END, SplashTimeline.CHIP_END, lastLetterEnd, SplashTimeline.RULE_END, SplashTimeline.TAG1_END, SplashTimeline.TAG2_END, SplashTimeline.SHINE_END, SplashTimeline.VERSION_END};
        long latest = 0;
        for (long e : ends) latest = Math.max(latest, e);
        check(latest <= SplashTimeline.TOTAL_MS && latest >= SplashTimeline.TOTAL_MS - 400, "everything is done before the end, with a short rest: last part ends at " + latest + " of " + SplashTimeline.TOTAL_MS);
        check(SplashTimeline.TOTAL_MS <= 3000, "the splash is not longer than three seconds");

        // ---------- the letters ----------
        boolean staggered = true;
        for (int i = 1; i < SplashTimeline.LETTERS; i++) if (SplashTimeline.letterStart(i) <= SplashTimeline.letterStart(i - 1)) staggered = false;
        check(staggered && SplashTimeline.LETTERS == 6, "six letters, one after another");
        check(SplashTimeline.letter(0, 0) == 0f && SplashTimeline.letter(5, SplashTimeline.TOTAL_MS) == 1f && SplashTimeline.letter(0, SplashTimeline.letterStart(0) + SplashTimeline.LETTER_DUR) == 1f, "none at the start, all at the end");
        long mid = SplashTimeline.letterStart(2) + 10;
        check(SplashTimeline.letter(0, mid) > SplashTimeline.letter(2, mid) && SplashTimeline.letter(2, mid) > SplashTimeline.letter(5, mid), "at any moment the earlier letters are further along");

        // ---------- everything is within 0 to 1, and only moves forward ----------
        boolean inside = true, forward = true;
        float[] last = new float[8];
        for (long ms = 0; ms <= SplashTimeline.TOTAL_MS; ms += 10) {
            float[] now = {SplashTimeline.seg(ms, SplashTimeline.FRAME_START, SplashTimeline.FRAME_END), SplashTimeline.seg(ms, SplashTimeline.GLYPH_START, SplashTimeline.GLYPH_END), SplashTimeline.seg(ms, SplashTimeline.CHIP_START, SplashTimeline.CHIP_END), SplashTimeline.letter(0, ms), SplashTimeline.letter(5, ms), SplashTimeline.seg(ms, SplashTimeline.RULE_START, SplashTimeline.RULE_END), SplashTimeline.seg(ms, SplashTimeline.TAG2_START, SplashTimeline.TAG2_END), SplashTimeline.seg(ms, SplashTimeline.VERSION_START, SplashTimeline.VERSION_END)};
            for (int i = 0; i < now.length; i++) { if (now[i] < 0f || now[i] > 1f) inside = false; if (now[i] < last[i]) forward = false; last[i] = now[i]; }
        }
        check(inside && forward, "every part stays between 0 and 1 and never goes back");
        float[] atEnd = last;
        boolean allDone = true;
        for (float v : atEnd) if (v != 1f) allDone = false;
        check(allDone, "at the end every part is complete");

        // ---------- the wiring (checked in the sources) ----------
        String view = read("SplashView.kt"), over = read("SplashOverlay.kt"), act = read("MainActivity.kt");
        check(view.contains("ValueAnimator.ofInt(0, SplashTimeline.TOTAL_MS)") && view.contains("override fun onAttachedToWindow()") && view.contains("animator.cancel()"), "the animation starts when the view is shown and stops when it goes");
        check(view.contains("ShapeGeometry.outline(ShapeGeometry.CHAMFER2, 90f, 120f, 570f, 600f, 46f)") && view.contains("PathMeasure(") && view.contains("getSegment("), "the frame is the cut-corner shape and draws itself along its outline");
        check(view.contains("clipPath(wordPath)") && view.contains("getTextPath("), "the light on the title is clipped to the letters");
        check(view.contains("splash/glyph.png") && new File("app/src/main/assets/splash/glyph.png").length() > 20000, "the logo comes from an asset");
        check(view.contains("Fonts.numbers(") && view.contains("Fonts.text(weight"), "the title is Teko and the texts are Manrope (or the Chinese font)");
        check(view.contains("CardStyle.upper(tr(\"Video monitor\"))") && view.contains("tr(\"HUD  \\u00B7  voice announcer\")") && view.contains("tr(\"Version {0}  \\u00B7  Build {1}\"") && view.contains("\"RADIOMASTER AX12\""), "the texts are translated, except the brand");
        check(view.contains("const val BG = 0xFF212D3D.toInt()") && over.contains("root.setBackgroundColor(SplashView.BG)"), "the navy is the one of the window background");
        check(over.contains("SplashView(activity, name, build)") && over.contains("root.setOnClickListener { dismiss() }") && !over.contains("R.drawable.splash_logo"), "a tap skips the splash; the old still image is not used");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("const val SHOW_MS = (\\d+)L").matcher(over);
        check(m.find() && Long.parseLong(m.group(1)) >= SplashTimeline.TOTAL_MS && Long.parseLong(m.group(1)) <= SplashTimeline.TOTAL_MS + 500, "the splash stays a little longer than its animation");
        check(act.contains("SplashOverlay.SHOW_MS") && act.contains("Prefs.bool(this, \"splashOn\", true)"), "it is still shown at the start, and can be switched off");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
