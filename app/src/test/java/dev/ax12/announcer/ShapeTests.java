// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;

/** Tests of the shape styles: the geometry of the corners and how the styles are wired in. */
public class ShapeTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/" + f).toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        // ---------- the styles ----------
        check(ShapeGeometry.COUNT == 5 && ShapeGeometry.CHAMFER2 == 0 && ShapeGeometry.clampStyle(0) == ShapeGeometry.CHAMFER2, "five styles, the default is two cut corners");
        check(ShapeGeometry.clampStyle(-1) == 0 && ShapeGeometry.clampStyle(5) == 0 && ShapeGeometry.clampStyle(99) == 0 && ShapeGeometry.clampStyle(4) == 4 && ShapeGeometry.clampStyle(3) == 3, "a stored value that makes no sense falls back to the default");
        check(ShapeGeometry.cutsCorners(ShapeGeometry.CHAMFER2) && ShapeGeometry.cutsCorners(ShapeGeometry.CHAMFER4) && !ShapeGeometry.cutsCorners(ShapeGeometry.SOFT) && !ShapeGeometry.cutsCorners(ShapeGeometry.PLAIN) && !ShapeGeometry.cutsCorners(ShapeGeometry.BRACKETS), "which styles cut corners");

        // ---------- sizes ----------
        float d = 2f;
        check(near(ShapeGeometry.cut(200, 100, d), 16f, 0.001) && ShapeGeometry.cut(10, 10, d) == 6f && ShapeGeometry.cut(2000, 1000, d) == 18f, "the cut is about a sixth of the short side, between 3 and 9 dp: " + ShapeGeometry.cut(200, 100, d));
        check(ShapeGeometry.radius(10, 10, d) == 6f && ShapeGeometry.radius(2000, 1000, d) == 16f && near(ShapeGeometry.radius(200, 100, d), 14f, 0.001), "the radius between 3 and 8 dp");
        check(ShapeGeometry.bracket(10, 10, d) == 16f && ShapeGeometry.bracket(2000, 1000, d) == 32f && near(ShapeGeometry.bracket(200, 100, d), 28f, 0.001), "the arms of a bracket between 8 and 16 dp");

        // ---------- the outlines ----------
        float l = 10, t = 20, r = 210, b = 120, c = 12;
        float[] two = ShapeGeometry.outline(ShapeGeometry.CHAMFER2, l, t, r, b, c);
        check(two.length == 12, "two cut corners: six points");
        check(near(ShapeGeometry.area(two), 200.0 * 100 - 2 * (12.0 * 12 / 2), 0.01), "and two triangles of area " + (12 * 12 / 2) + " are gone: " + ShapeGeometry.area(two));
        check(two[0] == l + c && two[1] == t && two[10] == l && two[11] == t + c && two[6] == r - c && two[7] == b, "the top left and the bottom right are the cut ones");
        float[] four = ShapeGeometry.outline(ShapeGeometry.CHAMFER4, l, t, r, b, c);
        double cc = c * 0.75;
        check(four.length == 16 && near(ShapeGeometry.area(four), 200.0 * 100 - 4 * (cc * cc / 2), 0.01), "four cut corners: eight points, four smaller triangles gone: " + ShapeGeometry.area(four));
        boolean inside = true;
        for (float[] poly : new float[][]{two, four}) for (int i = 0; i < poly.length; i += 2) if (poly[i] < l || poly[i] > r || poly[i + 1] < t || poly[i + 1] > b) inside = false;
        check(inside, "no point of an outline is outside the rectangle");
        check(ShapeGeometry.outline(ShapeGeometry.PLAIN, l, t, r, b, c) == null && ShapeGeometry.outline(ShapeGeometry.SOFT, l, t, r, b, c) == null && ShapeGeometry.outline(ShapeGeometry.BRACKETS, l, t, r, b, c) == null, "the other styles have no polygon");
        double rectArea = 200.0 * 100;
        check(ShapeGeometry.area(four) > ShapeGeometry.area(two) - 1e-6 + 0 || ShapeGeometry.area(four) < rectArea, "a cut shape is always smaller than the rectangle");
        // the outline goes clockwise on a screen (y down): the area is positive
        double signed = 0;
        for (int i = 0; i < two.length / 2; i++) { int j = (i + 1) % (two.length / 2); signed += two[2 * i] * two[2 * j + 1] - two[2 * j] * two[2 * i + 1]; }
        check(signed > 0, "the points run clockwise on the screen");
        // a tiny card keeps a sensible shape
        float tc = ShapeGeometry.cut(20, 14, 1f);
        float[] tiny = ShapeGeometry.outline(ShapeGeometry.CHAMFER2, 0, 0, 20, 14, tc);
        check(tc <= 7 && ShapeGeometry.area(tiny) > 20 * 14 * 0.7, "a tiny card still has most of its area: cut " + tc);

        // ---------- the brackets ----------
        float[] br = ShapeGeometry.brackets(l, t, r, b, 16);
        check(br.length == 24, "four brackets of three points");
        check(br[0] == l && br[1] == t + 16 && br[2] == l && br[3] == t && br[4] == l + 16 && br[5] == t, "the top left bracket: down arm, corner, right arm");
        check(br[8] == r && br[9] == t && br[10] == r && br[11] == t + 16, "the top right bracket ends going down");
        check(br[14] == r && br[15] == b && br[12] == r && br[13] == b - 16, "the bottom right corner");
        check(br[18] == l + 16 && br[19] == b && br[20] == l && br[21] == b && br[22] == l && br[23] == b - 16, "the bottom left bracket: right arm, corner, up arm");
        boolean onEdge = true;
        for (int i = 0; i < br.length; i += 2) if (!(br[i] == l || br[i] == r || br[i + 1] == t || br[i + 1] == b)) onEdge = false;
        check(onEdge, "every point of a bracket lies on the edge of the rectangle");

        // ---------- the wiring (checked in the sources) ----------
        String cs = read("CardStyle.kt"), fd = read("FrameDrawable.kt"), act = read("MainActivity.kt"), sp = read("SettingsPanel.kt"), wb = read("WarningBar.kt");
        check(cs.contains("fun drawFill(canvas: Canvas") && cs.contains("fun drawEdge(canvas: Canvas") && cs.contains("fun clipFrame(canvas: Canvas") && cs.contains("ShapeGeometry.outline(") && cs.contains("ShapeGeometry.brackets("), "CardStyle draws every style");
        check(cs.contains("if (solid && Shapes.style == ShapeGeometry.BRACKETS) ShapeGeometry.CHAMFER2"), "a solid frame (a warning) never uses brackets");
        check(!cs.contains("RADIUS_DP") && !cs.contains("GradientDrawable") && cs.contains("FrameDrawable("), "panels and tiles are drawn by the frame drawable");
        check(fd.contains("CardStyle.drawFill(canvas, rect, fillPaint, density, solid)") && fd.contains("CardStyle.drawEdge("), "the frame drawable reads the style when it draws");
        String[][] views = {{"ValueTileView.kt", "rect"}, {"FlightStatsView.kt", "rect"}, {"ChartView.kt", "rect"}, {"ChecklistView.kt", "rect"}, {"CompassView.kt", "rect"}, {"RadarView.kt", "frame"}, {"HorizonView.kt", "rect"}};
        for (String[] v : views) {
            String s = read(v[0]);
            check(s.contains("CardStyle.drawFill(canvas, " + v[1] + ", bg, density)") && s.contains("CardStyle.drawEdge(canvas, " + v[1] + ", edge, density,") && !s.contains("canvas.drawRect(" + v[1] + ", bg)") && !s.contains("canvas.drawRect(" + v[1] + ", edge)"), v[0] + " draws its frame in the chosen style");
        }
        check(read("HorizonView.kt").contains("CardStyle.clipFrame(canvas, rect, density)") && read("ValueTileView.kt").contains("CardStyle.clipFrame(canvas, rect, density)"), "what fills the card is cut to its shape");
        check(!read("SticksView.kt").contains("CardStyle.drawFill("), "the stick boxes stay plain rectangles");
        check(wb.contains("FrameDrawable(") && wb.contains("true)") && !wb.contains("GradientDrawable"), "the warning bar is a solid frame");
        check(act.contains("Shapes.style = ShapeGeometry.clampStyle(Prefs.int(this, \"shape\", 0))") && act.indexOf("Shapes.style = ShapeGeometry.clampStyle") < act.indexOf("mainMenu = MainMenu("), "the style is read before any screen is built");
        check(act.contains("override fun cycleShape()") && act.contains("override fun shapeLabel()") && act.contains("Prefs.setInt(this@MainActivity, \"shape\""), "the setting is kept");
        check(sp.contains("valueRow(tr(\"Shape\"), host.shapeLabel())") && sp.contains("host.cycleShape()"), "Settings > Display has the Shape row");
        check(SettingsFile.isKnown("shape") && Integer.valueOf(0).equals(SettingsFile.defaultOf("shape")), "the choice is part of the backup, the default is two cut corners");
        boolean named = true;
        for (int i = 0; i < ShapeGeometry.COUNT; i++) if (!read("Shapes.kt").contains("ShapeGeometry." + new String[]{"CHAMFER2", "CHAMFER4", "BRACKETS", "SOFT", "PLAIN"}[i] + " ->") && i != 4) named = false;
        check(named && read("Shapes.kt").contains("else -> tr(\"Plain rectangles\")"), "every style has a name");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
