package dev.ax12.announcer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live values inside rule texts:  {vbat}  {vbat/6}  {vbat/6:2}  {fm}
 * name, optional "/divisor", optional ":decimals". Used by spoken rules and by warning rules.
 * Plain Java, tested on a PC.
 */
public final class TextTemplate {

    /** One placeholder. Group 1 = name, 2 = divisor, 3 = decimals. */
    public static final Pattern PLACEHOLDER =
            Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)(?:/(\\d+(?:\\.\\d+)?))?(?::(\\d))?\\}");

    private TextTemplate() {}

    public static boolean hasPlaceholders(String text) {
        return text != null && text.indexOf('{') >= 0;
    }

    /** @return null if the braces in the text are fine, otherwise a short message */
    public static String check(String text) {
        if (text == null) return null;
        String rest = PLACEHOLDER.matcher(text).replaceAll("");
        if (rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0) {
            return "Use {variable} for a live value, for example {vbat/6:2}.";
        }
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (m.group(2) != null && Double.parseDouble(m.group(2)) == 0.0) return "A divisor in { } cannot be 0.";
        }
        return null;
    }

    public static String expand(String text, Map<String, Double> numbers, Map<String, String> texts) {
        if (!hasPlaceholders(text)) return text;
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String value;
            String t = texts == null ? null : texts.get(name);
            Double n = numbers == null ? null : numbers.get(name);
            if (t != null) {
                value = t;
            } else if (n != null) {
                double v = n.doubleValue();
                if (m.group(2) != null) {
                    double d = Double.parseDouble(m.group(2));
                    v = d == 0.0 ? v : v / d;
                }
                if (m.group(3) != null) value = String.format(Locale.US, "%." + m.group(3) + "f", v);
                else value = shortNumber(v);
            } else {
                value = "?";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String shortNumber(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        String s = String.format(Locale.US, "%.2f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }
}
