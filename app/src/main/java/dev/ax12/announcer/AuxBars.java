package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/**
 * The channel bars beside the sticks: eight bars (four on each side), each with a channel number, a name
 * an on/off switch and a "reversed" switch. Stored as eight text lines "on|channel|name|reversed"
 * (older settings without the last field still load). Plain Java, tested on a PC.
 */
public final class AuxBars {

    public static final int SLOTS = 8;
    public static final int MAX_NAME = 12;

    public static final class Bar {
        public boolean enabled;
        public int channel;
        public String name;
        /** The switch is reversed in the radio: the bar shows it the other way round. */
        public boolean invert;

        public Bar(boolean enabled, int channel, String name) {
            this(enabled, channel, name, false);
        }

        public Bar(boolean enabled, int channel, String name, boolean invert) {
            this.enabled = enabled;
            this.channel = channel;
            this.name = name;
            this.invert = invert;
        }

        public Bar copy() { return new Bar(enabled, channel, name, invert); }
    }

    private static final String[] DEFAULT_NAMES = {"ARM", "MODE", "RTH", "PREARM", "AIRMODE", "HOLD", "OSD", "BEEP"};

    private AuxBars() {}

    /** Channels 5 to 12, named for the AKB7's INAV modes. Rename them in the editor. */
    public static List<Bar> defaults() {
        List<Bar> out = new ArrayList<Bar>();
        for (int i = 0; i < SLOTS; i++) out.add(new Bar(true, 5 + i, DEFAULT_NAMES[i]));
        return out;
    }

    /** A name that is safe to store and short enough to draw. Empty names fall back to CHn. */
    public static String cleanName(String raw, int channel) {
        String s = raw == null ? "" : raw.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).trim();
        return s.isEmpty() ? "CH" + channel : s;
    }

    public static String encode(List<Bar> bars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            Bar b = i < bars.size() ? bars.get(i) : defaults().get(i);
            sb.append(b.enabled ? 1 : 0).append('|').append(b.channel).append('|').append(cleanName(b.name, b.channel))
              .append('|').append(b.invert ? 1 : 0).append('\n');
        }
        return sb.toString();
    }

    /** Reads the stored text. A missing or damaged line falls back to that slot's default. */
    public static List<Bar> decode(String text) {
        List<Bar> out = defaults();
        if (text == null) return out;
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < SLOTS && i < lines.length; i++) {
            String[] p = lines[i].split("\\|", 4);
            if (p.length < 2) continue;
            int ch;
            try {
                ch = Integer.parseInt(p[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (ch < 1 || ch > 16) continue;
            boolean on = !p[0].trim().equals("0");
            String name = p.length >= 3 ? p[2] : "";
            boolean inv = p.length == 4 && p[3].trim().equals("1");
            out.set(i, new Bar(on, ch, cleanName(name, ch), inv));
        }
        return out;
    }

    /** @return null if fine, otherwise a message */
    public static String validate(List<Bar> bars) {
        for (int i = 0; i < bars.size(); i++) {
            int ch = bars.get(i).channel;
            if (ch < 1 || ch > 16) return "Bar " + (i + 1) + ": the channel must be 1 to 16.";
        }
        return null;
    }

    /** The enabled bars of one side, in order. Slots 1 to 4 are the left side, 5 to 8 the right side. */
    public static List<Bar> side(List<Bar> all, boolean left) {
        List<Bar> out = new ArrayList<Bar>();
        int from = left ? 0 : 4;
        for (int i = from; i < from + 4 && i < all.size(); i++) if (all.get(i).enabled) out.add(all.get(i));
        return out;
    }

    /** 0 (1000 microseconds) .. 1 (2000 microseconds). */
    public static double fraction(double us) {
        return Math.max(0.0, Math.min(1.0, (us - 1000.0) / 1000.0));
    }

    /** The same, for a reversed switch: 1000 microseconds reads as full and 2000 as empty. */
    public static double fraction(double us, boolean invert) {
        double f = fraction(us);
        return invert ? 1.0 - f : f;
    }

    /** 0 = low, 1 = middle, 2 = high: what a two or three position switch is showing. */
    public static int level(double fraction) {
        if (fraction < 0.33) return 0;
        if (fraction > 0.67) return 2;
        return 1;
    }
}
