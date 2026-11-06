package dev.ax12.announcer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a release says about itself: the small "update.json" file the build publishes next to the APK.
 * Plain Java, tested on a PC. Everything read from the network is checked here before the app trusts it.
 *
 *   { "versionCode": 1042, "versionName": "0.7.0", "channel": "dev", "apk": "ax-hud.apk",
 *     "sha256": "<64 hex digits>", "size": 31457280, "notes": "what changed" }
 */
public final class UpdateInfo {

    public static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    public final int versionCode;
    public final String versionName;
    public final String channel;
    public final String apk;
    public final String sha256;
    public final long size;
    public final String notes;

    private UpdateInfo(int versionCode, String versionName, String channel, String apk, String sha256, long size, String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.channel = channel;
        this.apk = apk;
        this.sha256 = sha256;
        this.size = size;
        this.notes = notes;
    }

    /** @return null when the text is not a valid, safe update description */
    public static UpdateInfo parse(String json) {
        Map<String, Object> m = parseFlat(json);
        if (m == null) return null;
        Object vc = m.get("versionCode");
        Object vn = m.get("versionName");
        Object apk = m.get("apk");
        Object sha = m.get("sha256");
        Object size = m.get("size");
        if (!(vc instanceof Double) || !(vn instanceof String) || !(apk instanceof String) || !(sha instanceof String) || !(size instanceof Double)) return null;
        double code = (Double) vc, bytes = (Double) size;
        if (code < 1 || code > 2_000_000_000 || code != Math.rint(code)) return null;
        if (bytes < 1 || bytes > MAX_APK_BYTES || bytes != Math.rint(bytes)) return null;
        String name = ((String) vn).trim();
        if (name.isEmpty() || name.length() > 40) return null;
        String file = (String) apk;
        if (!FILE_NAME.matcher(file).matches() || !file.toLowerCase(Locale.ROOT).endsWith(".apk")) return null;   // a file name only, never a path or a URL
        String hash = ((String) sha).toLowerCase(Locale.ROOT);
        if (!HEX64.matcher(hash).matches()) return null;
        Object ch = m.get("channel");
        String channel = ch instanceof String ? ((String) ch).trim() : "";
        Object n = m.get("notes");
        String notes = n instanceof String ? ((String) n) : "";
        if (notes.length() > 4000) notes = notes.substring(0, 4000);
        return new UpdateInfo((int) code, name, channel.length() > 20 ? channel.substring(0, 20) : channel, file, hash, (long) bytes, notes);
    }

    public boolean isNewerThan(int installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    // ------------------------------------------------------------------ a small reader for flat JSON

    /** Reads one JSON object of strings, numbers, booleans and nulls. Nested values are skipped. null if malformed. */
    static Map<String, Object> parseFlat(String s) {
        if (s == null) return null;
        int[] p = {0};
        skipWs(s, p);
        if (p[0] >= s.length() || s.charAt(p[0]) != '{') return null;
        p[0]++;
        Map<String, Object> out = new HashMap<String, Object>();
        skipWs(s, p);
        if (p[0] < s.length() && s.charAt(p[0]) == '}') return out;
        while (true) {
            skipWs(s, p);
            String key = readString(s, p);
            if (key == null) return null;
            skipWs(s, p);
            if (p[0] >= s.length() || s.charAt(p[0]) != ':') return null;
            p[0]++;
            skipWs(s, p);
            if (p[0] >= s.length()) return null;
            char c = s.charAt(p[0]);
            Object value;
            if (c == '"') {
                value = readString(s, p);
                if (value == null) return null;
            } else if (c == '{' || c == '[') {
                if (!skipNested(s, p)) return null;
                value = null;
            } else {
                int start = p[0];
                while (p[0] < s.length() && ",}] \t\r\n".indexOf(s.charAt(p[0])) < 0) p[0]++;
                String tok = s.substring(start, p[0]);
                if (tok.equals("true")) value = Boolean.TRUE;
                else if (tok.equals("false")) value = Boolean.FALSE;
                else if (tok.equals("null")) value = null;
                else {
                    try {
                        value = Double.valueOf(tok);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
            out.put(key, value);
            skipWs(s, p);
            if (p[0] >= s.length()) return null;
            char d = s.charAt(p[0]++);
            if (d == '}') {
                skipWs(s, p);
                return p[0] == s.length() ? out : null;
            }
            if (d != ',') return null;
        }
    }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static String readString(String s, int[] p) {
        if (p[0] >= s.length() || s.charAt(p[0]) != '"') return null;
        p[0]++;
        StringBuilder sb = new StringBuilder();
        while (p[0] < s.length()) {
            char c = s.charAt(p[0]++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (p[0] >= s.length()) return null;
            char e = s.charAt(p[0]++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (p[0] + 4 > s.length()) return null;
                    try {
                        sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                    p[0] += 4;
                    break;
                default: return null;
            }
        }
        return null;
    }

    private static boolean skipNested(String s, int[] p) {
        int depth = 0;
        while (p[0] < s.length()) {
            char c = s.charAt(p[0]);
            if (c == '"') {
                if (readString(s, p) == null) return false;
                continue;
            }
            p[0]++;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) return true;
            }
        }
        return false;
    }
}
