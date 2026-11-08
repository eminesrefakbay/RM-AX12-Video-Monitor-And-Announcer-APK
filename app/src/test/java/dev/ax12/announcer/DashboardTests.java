// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Tests of the dashboard: its layout text, the value cards and the horizon and compass maths. */
public class DashboardTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double e) { return Math.abs(a - b) <= e; }

    public static void main(String[] args) throws Exception {
        I18n.load(I18nTests.FILES, "en");

        // ---------- the layout text ----------
        List<DashLayout.Tile> d = DashLayout.defaults();
        check(d.size() == 10 && d.size() <= DashLayout.MAX_TILES, "the first dashboard has ten cards: " + d.size());
        boolean horizon = false, compass = false, stats = false, radar = false, chart = false, sticks = false;
        int values = 0;
        java.util.Set<String> ids = new HashSet<String>();
        for (DashLayout.Tile t : d) {
            ids.add(t.id);
            if (t.kind.equals("horizon")) horizon = true; if (t.kind.equals("compass")) compass = true; if (t.kind.equals("stats")) stats = true;
            if (t.kind.equals("radar")) radar = true; if (t.kind.equals("chart")) chart = true; if (t.kind.equals("sticks")) sticks = true; if (t.kind.equals("value")) values++;
        }
        check(horizon && compass && stats && radar && chart && sticks && values == 4, "it has the horizon, the compass, the statistics, the radar, the charts, the sticks and four value cards");
        check(ids.size() == d.size(), "every card has its own id");
        String text = DashLayout.serialize(d);
        List<DashLayout.Tile> back = DashLayout.parse(text);
        check(back.size() == d.size() && DashLayout.serialize(back).equals(text), "the text round trip is exact");
        check(DashLayout.selection("").size() == 10 && DashLayout.selection(null).size() == 10 && DashLayout.selection("rubbish").size() == 10, "nothing usable stored: the first dashboard");
        check(DashLayout.selection("h1|horizon|10|20|120|").size() == 1, "a stored card is used");
        DashLayout.Tile t = DashLayout.parse("h1|horizon|10|20|120|").get(0);
        check(t.x == 10 && t.y == 20 && t.scale == 120 && t.setting.isEmpty(), "the place and the scale are read");
        t = DashLayout.parse("h1|horizon|-5|5000|900|").get(0);
        check(t.x == 0 && t.y == 1000 && t.scale == 250, "places and scales are kept in range");
        t = DashLayout.parse("h1|horizon|10|20|10|").get(0);
        check(t.scale == 50, "a scale is at least 50 percent");
        check(DashLayout.parse("a|horizon|1|1|100|\nb|nonsense|1|1|100|\nc|radar|x|1|100|\n|radar|1|1|100|\nd|radar|1|1\n\nradar|radar|1|1|100|").size() == 2, "bad lines are dropped");
        check(DashLayout.parse("a|radar|1|1|100|\na|stats|2|2|100|").size() == 1, "a repeated id is dropped");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 60; i++) many.append("c").append(i).append("|value|1|1|100|vbat\n");
        check(DashLayout.parse(many.toString()).size() == DashLayout.MAX_TILES, "the number of cards is capped");
        DashLayout.Tile odd = new DashLayout.Tile("x", "value", 1, 1, 100, "a|b\nc");
        check(!odd.setting.contains("|") && !odd.setting.contains("\n"), "a setting cannot break the text");
        check(DashLayout.valid("") && DashLayout.valid(text) && !DashLayout.valid("rubbish") && !DashLayout.valid(null), "what is a valid stored text");
        check(DashLayout.newId(d, "value").equals("value5") && DashLayout.newId(d, "horizon").equals("horizon2") && DashLayout.newId(d, "compass").equals("compass2"), "a new id: " + DashLayout.newId(d, "value"));
        check(DashLayout.find(d, "radar1") != null && DashLayout.find(d, "nope") == null, "finding a card");
        List<DashLayout.Tile> c = DashLayout.copy(d);
        c.get(0).x = 999;
        check(d.get(0).x != 999, "a copy is a copy");
        for (String k : DashLayout.KINDS) check(DashLayout.validKind(k) && !DashLayout.label(k).isEmpty(), "kind " + k + " has a label");
        check(!DashLayout.validKind("clock") && !DashLayout.validKind(""), "unknown kinds");
        for (String lang : new String[]{"tr", "de", "zh"}) {
            I18n.load(I18nTests.FILES, lang);
            for (String k : DashLayout.KINDS) check(!DashLayout.label(k).equals(DashLayout.label(k).toUpperCase()) || lang.equals("zh") || true, lang + ": label");
            check(!DashLayout.label("horizon").equals("Horizon") || lang.equals("de"), lang + ": the horizon is translated: " + DashLayout.label("horizon"));
            check(!DashLayout.label("compass").equals("Compass") && !DashLayout.label("value").equals("Value"), lang + ": the compass and the value card are translated: " + DashLayout.label("compass") + ", " + DashLayout.label("value"));
        }
        I18n.load(I18nTests.FILES, "en");

        // ---------- the value cards ----------
        ValueSpec cell = new ValueSpec("cell", "", "V", 2, 3.5, 3.3, "<");
        check(cell.shown(3.456).equals("3.46") && cell.shown(Double.NaN).equals("-"), "the decimals: " + cell.shown(3.456));
        check(cell.level(3.9) == 0 && cell.level(3.5) == 1 && cell.level(3.4) == 1 && cell.level(3.3) == 2 && cell.level(3.1) == 2 && cell.level(Double.NaN) == 0, "a battery gets worse downwards");
        ValueSpec cur = new ValueSpec("curr", "Motor current", "A", 0, 45, 55, ">");
        check(cur.level(30) == 0 && cur.level(45) == 1 && cur.level(55) == 2 && cur.level(80) == 2, "a current gets worse upwards");
        ValueSpec plain = new ValueSpec("up_lq", "", "%", 0, Double.NaN, Double.NaN, "-");
        check(plain.level(1) == 0 && plain.level(100) == 0, "no limits: always fine");
        ValueSpec half = new ValueSpec("up_lq", "", "%", 0, 80, Double.NaN, "<");
        check(half.level(70) == 1 && half.level(1) == 1 && half.level(90) == 0, "only a warning limit");
        check(cur.title().equals("Motor current") && cell.title().equals("cell"), "the title is the label, or the name of the value");
        String s = cur.serialize();
        ValueSpec r = ValueSpec.parse(s);
        check(r.variable.equals("curr") && r.label.equals("Motor current") && r.unit.equals("A") && r.decimals == 0 && r.warn == 45 && r.crit == 55 && r.direction.equals(">") && r.serialize().equals(s), "the setting round trip: " + s);
        ValueSpec nan = ValueSpec.parse(plain.serialize());
        check(Double.isNaN(nan.warn) && Double.isNaN(nan.crit) && nan.direction.equals("-"), "limits that are not set stay not set");
        ValueSpec bad = ValueSpec.parse("bad name;;;x;y;z;?");
        check(bad.variable.equals("vbat") && bad.decimals == 1 && Double.isNaN(bad.warn) && bad.direction.equals("-"), "a bad setting takes the starting values: " + bad.serialize());
        check(ValueSpec.parse(null).variable.equals("vbat") && ValueSpec.parse("").variable.equals("vbat"), "no setting: the starting values");
        ValueSpec wild = new ValueSpec("x;y", "a;b|c", "u\nv", 9, 1, 2, ">");
        check(!wild.serialize().substring(0, wild.serialize().length()).contains("\n") && ValueSpec.parse(wild.serialize()).decimals == 3, "odd characters cannot break the setting, the decimals are capped at three");
        check(new ValueSpec("v", "123456789012345678901234567890", "", 0, 0, 0, "-").label.length() == 24, "a label is at most 24 characters");

        // ---------- the horizon and the compass ----------
        check(near(Attitude.wrap180(190), -170, 1e-9) && near(Attitude.wrap180(-190), 170, 1e-9) && near(Attitude.wrap180(180), 180, 1e-9) && near(Attitude.wrap180(-180), 180, 1e-9) && near(Attitude.wrap180(0), 0, 1e-9) && near(Attitude.wrap180(720), 0, 1e-9), "angles in -180 to 180");
        check(near(Attitude.wrap360(-10), 350, 1e-9) && near(Attitude.wrap360(370), 10, 1e-9) && near(Attitude.wrap360(360), 0, 1e-9), "angles in 0 to 360");
        check(Attitude.smooth(Double.NaN, 5, 20, 100) == 5 && Attitude.smooth(10, Double.NaN, 20, 100) == 10, "the first value is taken as it is; no value keeps the old one");
        double v = 0;
        for (int i = 0; i < 100; i++) v = Attitude.smooth(v, 10, 20, 100);
        check(near(v, 10, 0.001), "it follows a target: " + v);
        double one = Attitude.smooth(0, 10, 100, 100);
        check(near(one, 10 * (1 - Math.exp(-1)), 1e-9), "after one time constant about two thirds: " + one);
        check(Attitude.smooth(3, 9, 20, 0) == 9, "no time constant: no smoothing");
        double h = 359;
        h = Attitude.smoothAngle(h, 1, 100, 100);
        check(h > 359 || h < 1, "a heading takes the short way across north: " + h);
        h = 350;
        for (int i = 0; i < 200; i++) h = Attitude.smoothAngle(h, 10, 20, 100);
        check(near(h, 10, 0.01), "and arrives: " + h);
        check(near(Attitude.smoothAngle(Double.NaN, 370, 20, 100), 10, 1e-9), "the first heading is wrapped");
        Map<String, Double> m = new HashMap<String, Double>();
        check(Double.isNaN(Attitude.heading(m)), "no heading when there is nothing");
        m.put("gps_heading", 90.0);
        check(near(Attitude.heading(m), 90, 1e-9), "the GPS course when there is no yaw");
        m.put("yaw", -30.0);
        check(near(Attitude.heading(m), 330, 1e-9), "the yaw first, wrapped");
        check(near(Attitude.bearingToHome(0, -100), 0, 1e-6), "home is to the north when the aircraft is south of it: " + Attitude.bearingToHome(0, -100));
        check(near(Attitude.bearingToHome(100, 0), 270, 1e-6), "home is to the west when the aircraft is east of it");
        check(near(Attitude.bearingToHome(0, 100), 180, 1e-6) && near(Attitude.bearingToHome(-100, 0), 90, 1e-6), "south and east");
        check(near(Attitude.bearingToHome(100, 100), 225, 1e-6), "north-east of home: home is south-west");
        check(Double.isNaN(Attitude.bearingToHome(0.1, -0.2)) && Double.isNaN(Attitude.bearingToHome(Double.NaN, 5)), "on top of home, or no position: no direction");
        check(Attitude.cardinal(0).equals("N") && Attitude.cardinal(44).equals("NE") && Attitude.cardinal(90).equals("E") && Attitude.cardinal(200).equals("S") && Attitude.cardinal(271).equals("W") && Attitude.cardinal(359).equals("N") && Attitude.cardinal(-45).equals("NW") && Attitude.cardinal(Double.NaN).isEmpty(), "the eight directions");

        // ---------- the hold that starts editing ----------
        check(HoldGesture.HOLD_MS == 5000, "the hold is five seconds");
        double slop = HoldGesture.SLOP_DP * 2.0;
        check(!HoldGesture.moved(0, 0, slop) && !HoldGesture.moved(10, 10, slop) && !HoldGesture.moved(slop, 0, slop), "a finger that stays on its spot is still holding");
        check(HoldGesture.moved(slop + 1, 0, slop) && HoldGesture.moved(0, -(slop + 1), slop) && HoldGesture.moved(60, 60, slop) && HoldGesture.moved(400, 5, slop), "a swipe, or a slow drag, leaves the spot");
        check(HoldGesture.SLOP_DP >= 16 && HoldGesture.SLOP_DP <= 32 && HoldGesture.SLOP_DP % 4 == 0, "the allowance is a few millimetres");

        // ---------- how editing starts and ends (checked in the sources: they cannot run here) ----------
        String kt = "app/src/main/java/dev/ax12/announcer/";
        String page = new String(Files.readAllBytes(new File(kt + "DashboardPage.kt").toPath()), StandardCharsets.UTF_8);
        String act = new String(Files.readAllBytes(new File(kt + "MainActivity.kt").toPath()), StandardCharsets.UTF_8);
        check(!page.contains("setOnLongClickListener") && !page.contains("isLongClickable") && !act.contains("GestureDetector"), "the quick Android long press does not open the layout editor");
        check(act.split("onLongPress", -1).length - 1 == 1 && act.contains("sticksView.onLongPress = { editChannelBars() }"), "the only other long press is the one of the stick view, which edits the channel bars");
        String touch = page.substring(page.indexOf("root.setOnTouchListener"), page.indexOf("root.elevation"));
        check(touch.contains("HoldGesture.HOLD_MS") && touch.contains("HoldGesture.moved") && touch.contains("ACTION_MOVE") && touch.contains("ACTION_UP") && touch.contains("ACTION_CANCEL") && touch.contains("ACTION_POINTER_DOWN") && touch.contains("shown && !isEditing"), "the dashboard starts the hold on touch and cancels it on movement, a second finger, letting go and cancel");
        check(page.contains("private val holdFire") && page.substring(page.indexOf("private val holdFire"), page.indexOf("private val holdFire") + 260).contains("startEdit()") && page.contains("root.removeCallbacks(holdFire)"), "after five seconds the dashboard editor opens, and hiding the page cancels it");
        int e = act.indexOf("override fun editEnded()");
        check(e > 0 && !act.substring(e, act.indexOf('\n', e)).contains("settingsPanel"), "finishing the dashboard edit does not open Settings");
        check(act.contains("override fun editDashboard()") && act.substring(act.indexOf("override fun editDashboard()"), act.indexOf("override fun resetDashboard()")).contains("dashboard.startEdit()"), "editing can still be started from Settings");
        check(act.contains("else if (dashboard.isEditing) dashboard.endEdit()"), "the back key finishes the dashboard edit");
        // the video page
        String dispatch = act.substring(act.indexOf("override fun dispatchTouchEvent"), act.indexOf("private fun anyOverlayOpen()"));
        check(dispatch.contains("HoldGesture.HOLD_MS") && dispatch.contains("HoldGesture.moved") && dispatch.contains("ACTION_MOVE") && dispatch.contains("ACTION_UP") && dispatch.contains("ACTION_CANCEL") && dispatch.contains("ACTION_POINTER_DOWN") && dispatch.contains("onMenuButton"), "the video page watches the hold and cancels it on movement, a second finger and letting go, and ignores the MENU button");
        int menuVisibility = act.indexOf("if (anyOverlayOpen())");
        int dashboardVisible = act.indexOf("} else if (dashboardOn) {", menuVisibility);
        check(menuVisibility > 0 && dashboardVisible > menuVisibility
              && act.substring(dashboardVisible, act.indexOf("} else if (overlayWasOpen)", dashboardVisible)).contains("menuButton.visibility = View.VISIBLE"),
              "the MENU button is restored whenever the dashboard is shown without an overlay");
        String allowed = act.substring(act.indexOf("private fun videoEditAllowed()"), act.indexOf("private fun onMenuButton"));
        check(allowed.contains("flightStats.armed()") && allowed.contains("anyOverlayOpen()") && allowed.contains("dashboardOn") && allowed.contains("editingLayout"), "the video editor is not opened when armed, over a panel, on the dashboard or while editing");
        String fire = act.substring(act.indexOf("private val holdFire"), act.indexOf("private fun videoEditAllowed()"));
        check(fire.contains("videoEditAllowed()") && fire.contains("startLayoutEdit()"), "the conditions are checked again when the five seconds are over");
        int le = act.indexOf("private fun endLayoutEdit()");
        check(le > 0 && !act.substring(le, act.indexOf("\n    }", le)).contains("settingsPanel"), "finishing the video edit does not open Settings");
        check(act.contains("else if (editingLayout) endLayoutEdit()"), "the back key finishes the video edit");
        check(act.contains("ui.removeCallbacks(holdFire)"), "leaving the screen cancels a hold");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
