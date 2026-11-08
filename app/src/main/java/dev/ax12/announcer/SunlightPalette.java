// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.HashMap;
import java.util.Map;

/**
 * The sunlight style: dark text on light backgrounds, because in direct sun the glare lifts the black of an LCD and dark-on-light stays
 * readable where light-on-dark washes out. Every colour of the dark style that a panel or a dashboard card uses has its light counterpart
 * here; a colour that is not in the table is turned the other way round (light becomes dark, dark becomes light). Plain Java, so that the
 * contrast can be tested on a PC.
 */
public final class SunlightPalette {
    private SunlightPalette() {}

    private static final Map<Integer, Integer> T = new HashMap<Integer, Integer>();

    private static void p(long dark, long light) { T.put((int) dark, (int) light); }

    static {
        // backdrops and blocks: nearly solid light, so that the video behind cannot spoil the text
        p(0x8C1A222CL, 0xF2EEF2F5L);       // the backdrop of Settings, About and the rules
        p(0xCC1A222CL, 0xFFFFFFFFL);       // a block of text or rows on it
        p(0xBA1A222CL, 0xFAFFFFFFL);       // the MENU box
        p(0xF21A222CL, 0xFFFFFFFFL);       // panels that are read closely
        p(0xD91A222CL, 0xF2FFFFFFL);       // cards
        p(0xE6232C36L, 0xFFE6ECF0L);       // rows and tiles
        p(0xF24F6272L, 0xFFB7C4CEL);       // a pressed tile
        p(0xF22C3844L, 0xFFE4EAEFL);       // an open group header
        p(0xFF11161CL, 0xFFF4F7F9L);
        p(0xFF0B1016L, 0xFFF4F7F9L);       // the page behind the dashboard cards
        // text
        p(0xFFFFFFFFL, 0xFF0B141BL);       // the main text
        p(0xFFDDE3E8L, 0xFF1B262EL);       // text of cards
        p(0xFFB0BEC5L, 0xFF3C4B55L);       // labels
        p(0xFFBDBDBDL, 0xFF47525BL);       // notes
        p(0xFFCFD8DCL, 0xFF37474FL);
        p(0xFFE2E8EEL, 0xFF1B262EL);       // icons
        p(0xFF9CDCFEL, 0xFF014F86L);       // titles
        // signal colours, dark enough to read on white
        p(0xFF4FC3F7L, 0xFF01579BL);       // accent
        p(0xFF66BB6AL, 0xFF1B5E20L);       // good
        p(0xFFFFB300L, 0xFF9A5200L);       // warning
        p(0xFFFFC107L, 0xFFBF4F00L);       // the nose and other amber marks
        p(0xFFEF5350L, 0xFFB71C1CL);       // bad
        p(0xFFFF8A80L, 0xFFB71C1CL);       // error text
        // lines and edges
        p(0xFF90A4AEL, 0xFF546E7AL);
        p(0xFF78909CL, 0xFF607D8BL);
        p(0xFF546E7AL, 0xFF78909CL);
        p(0xFF455A64L, 0xFF90A4AEL);
        p(0xFF37474FL, 0xFFCFD8DCL);
        p(0xFF607D8BL, 0xFF90A4AEL);
        p(0xAA90A4AEL, 0xAA546E7AL);
        p(0x44FFFFFFL, 0x44000000L);
        p(0xE0000000L, 0xE0FFFFFFL);
        // switches and dots
        p(0x884FC3F7L, 0x880277BDL);
        p(0x66263238L, 0x66B0BEC5L);
        p(0x66B0BEC5L, 0x66455A64L);
    }

    // the warning bar in sunlight: fully saturated, with a thick outline and thick marks
    public static final int BAR_CRITICAL = 0xFFD50000, BAR_CRITICAL_TEXT = 0xFFFFFFFF;
    public static final int BAR_WARNING = 0xFFFFD600, BAR_WARNING_TEXT = 0xFF000000;
    public static final int BAR_INFO = 0xFF0D47A1, BAR_INFO_TEXT = 0xFFFFFFFF;
    public static final float BAR_TEXT_SP = 20f;
    public static final int BAR_OUTLINE_DP = 3;
    public static final int BAR_ICON_DP = 36;
    public static final float BAR_ICON_STROKE = 3.6f;

    private static int alpha(int c) { return (c >>> 24) & 0xFF; }

    private static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6; else if (max == g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static int fromHsv(float h, float s, float v, int alpha) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return (alpha << 24) | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** The sunlight colour for a colour of the dark style. */
    public static int map(int c) {
        Integer known = T.get(c);
        if (known != null) return known;
        float[] h = hsv(c);                                  // not in the table: the other way round, with the same hue and alpha
        return fromHsv(h[0], Math.min(1f, h[1] * 1.1f), 1f - h[2] * 0.85f, alpha(c));
    }

    public static boolean known(int c) { return T.containsKey(c); }

    // ------------------------------------------------------------------ contrast, for the tests

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** A colour laid over a background (the alpha of the colour decides how much of the background shows). */
    public static int over(int fg, int bg) {
        double a = alpha(fg) / 255.0;
        int r = (int) Math.round(((fg >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((fg >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((fg & 0xFF) * a + (bg & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static double luminance(int c) { return 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF); }

    /** The WCAG contrast ratio of text on a background (1 to 21); 4.5 is the usual minimum, 7 is comfortable. */
    public static double contrast(int fg, int bg) {
        int solidBg = over(bg, 0xFFFFFFFF);
        double a = luminance(over(fg, solidBg)), b = luminance(solidBg);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
