// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The icons of the app, read from the text file assets/icons.txt: simple shapes on a 24 x 24 grid.
 * One file feeds the app and the preview pictures, so what is previewed is what is drawn. Plain Java, tested on a PC.
 */
public final class IconSet {

    public static final double GRID = 24.0;

    public static final class Op {
        public final String type;       // rect, circle, line, polyline, poly, arc
        public final double[] n;        // the numbers
        public final boolean fill;      // filled (otherwise an outline)

        Op(String type, double[] n, boolean fill) {
            this.type = type;
            this.n = n;
            this.fill = fill;
        }
    }

    private IconSet() {}

    private static int expected(String type) {
        switch (type) {
            case "rect": return 5;
            case "circle": return 3;
            case "line": return 4;
            case "arc": return 6;
            default: return -1;      // polyline and poly: any even count, set below
        }
    }

    /**
     * @param errors receives a message for every line that could not be used (may be null)
     * @return the icons by name, in file order; a line with a mistake is skipped, an icon without shapes is dropped
     */
    public static Map<String, List<Op>> parse(String text, List<String> errors) {
        Map<String, List<Op>> out = new LinkedHashMap<String, List<Op>>();
        List<Op> current = null;
        String currentName = null;
        String[] lines = text.replace("\r\n", "\n").split("\n");
        for (int ln = 0; ln < lines.length; ln++) {
            String line = lines[ln].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] w = line.split("\\s+");
            String where = "line " + (ln + 1) + ": ";
            if (w[0].equals("icon")) {
                if (w.length != 2) { err(errors, where + "an icon needs one name"); current = null; continue; }
                if (out.containsKey(w[1])) { err(errors, where + "the icon " + w[1] + " is already defined"); current = null; continue; }
                currentName = w[1];
                current = new ArrayList<Op>();
                out.put(currentName, current);
                continue;
            }
            if (current == null) { err(errors, where + "a shape outside an icon"); continue; }
            boolean fill = false, hasMode = false;
            int end = w.length;
            if (w[end - 1].equals("fill") || w[end - 1].equals("stroke")) {
                fill = w[end - 1].equals("fill");
                hasMode = true;
                end--;
            }
            String type = w[0];
            if (!(type.equals("rect") || type.equals("circle") || type.equals("line") || type.equals("polyline") || type.equals("poly") || type.equals("arc"))) {
                err(errors, where + "unknown shape " + type);
                continue;
            }
            if (hasMode && (type.equals("line") || type.equals("polyline") || type.equals("arc"))) { err(errors, where + type + " is always an outline"); continue; }
            int count = end - 1;
            int want = expected(type);
            boolean badCount = want >= 0 ? count != want : (count < (type.equals("poly") ? 6 : 4) || count % 2 != 0);
            if (badCount) { err(errors, where + "wrong number of values for " + type); continue; }
            double[] n = new double[count];
            boolean bad = false;
            for (int i = 0; i < count; i++) {
                try {
                    n[i] = Double.parseDouble(w[i + 1]);
                } catch (NumberFormatException e) {
                    bad = true;
                }
            }
            if (bad) { err(errors, where + "a value is not a number"); continue; }
            current.add(new Op(type, n, fill));
        }
        for (String k : new ArrayList<String>(out.keySet())) {
            if (out.get(k).isEmpty()) {
                err(errors, "the icon " + k + " has no shapes");
                out.remove(k);
            }
        }
        return out;
    }

    private static void err(List<String> errors, String m) {
        if (errors != null) errors.add(m);
    }

    /** Every coordinate of a shape lies on the grid (the radius and the angles are not coordinates). */
    public static boolean onGrid(Op op) {
        int coords;
        switch (op.type) {
            case "rect": coords = 4; break;
            case "circle": coords = 2; break;
            case "arc": coords = 4; break;
            default: coords = op.n.length;
        }
        for (int i = 0; i < coords; i++) if (op.n[i] < 0 || op.n[i] > GRID) return false;
        if (op.type.equals("circle")) {
            return op.n[2] > 0 && op.n[0] - op.n[2] >= 0 && op.n[0] + op.n[2] <= GRID && op.n[1] - op.n[2] >= 0 && op.n[1] + op.n[2] <= GRID;
        }
        return true;
    }
}
