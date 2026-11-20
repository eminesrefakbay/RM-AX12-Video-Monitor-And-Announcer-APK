// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.List;

/** Tests of the maths of the map under the 3D replay: tile numbers, the covered square, the zoom, addresses, cache files and trimming. */
public class MapTilesTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    public static void main(String[] args) throws Exception {
        // ---------- tile numbers: known values of the slippy map scheme
        check(near(MapTiles.lonToX(-180, 3), 0, 1e-9) && near(MapTiles.lonToX(180, 3), 8, 1e-9) && near(MapTiles.lonToX(0, 3), 4, 1e-9), "longitude maps to 0..2^z");
        check(near(MapTiles.latToY(0, 3), 4, 1e-9), "the equator is in the middle of the rows");
        check(near(MapTiles.latToY(MapTiles.MAX_LAT, 2), 0, 1e-6), "the top of the map is row 0");
        // Mersin (36.8, 34.64) at zoom 15 is tile 19537/12777 (worked out with the standard formula)
        double x15 = MapTiles.lonToX(34.64, 15), y15 = MapTiles.latToY(36.8, 15);
        check((int) Math.floor(x15) == 19537 && (int) Math.floor(y15) == 12777, "Mersin is tile " + (int) x15 + "/" + (int) y15);
        check(near(MapTiles.xToLon(MapTiles.lonToX(34.64, 15), 15), 34.64, 1e-9), "longitude round trip");
        check(near(MapTiles.yToLat(MapTiles.latToY(36.8, 15), 15), 36.8, 1e-9), "latitude round trip");
        check(near(MapTiles.yToLat(MapTiles.latToY(-60.3, 9), 9), -60.3, 1e-9), "latitude round trip in the south");
        check(MapTiles.latToY(89.9, 4) >= 0, "the poles are clamped");

        // ---------- points of a tile in metres around an origin
        double lat0 = 36.85, lon0 = 34.60;
        int z = 17;
        int tx = (int) Math.floor(MapTiles.lonToX(lon0, z)), ty = (int) Math.floor(MapTiles.latToY(lat0, z));
        List<MapTiles.Tile> one = MapTiles.cover(lat0, lon0, 10, z);
        check(!one.isEmpty() && one.get(0).x == tx && one.get(0).y == ty, "the first tile is the one that holds the origin");
        double[] a = new double[2], b = new double[2];
        MapTiles.pointInTile(lat0, lon0, one.get(0), 0, 0, a);          // the north-west corner: west and north of the origin
        MapTiles.pointInTile(lat0, lon0, one.get(0), 1, 1, b);          // the south-east corner: east and south
        check(a[0] < 0 && a[1] > 0 && b[0] > 0 && b[1] < 0, "the corners lie around the origin: " + a[0] + "," + a[1] + " / " + b[0] + "," + b[1]);
        double w = b[0] - a[0], h = a[1] - b[1];
        double expect = MapTiles.tileMeters(lat0, z);
        check(near(w, expect, expect * 0.01), "a tile is as wide on the ground as the formula says: " + w + " vs " + expect);
        check(near(h, w, w * 0.02), "and about as high (Mercator is square at a point): " + h);
        // an interior point is where interpolation says, in the east-west direction
        double[] c = new double[2];
        MapTiles.pointInTile(lat0, lon0, one.get(0), 0.5, 0.5, c);
        check(near(c[0], (a[0] + b[0]) / 2, 0.01) && near(c[1], (a[1] + b[1]) / 2, w * 0.002), "the middle of the tile is the middle of its corners");

        // ---------- the same flat-earth rule as FlightPath
        FlightPath fp = Flight3DTests.flightPath();
        check(near(MapTiles.eastM(fp.originLat, fp.originLon, fp.originLon + 0.001), fp.eastM(fp.originLon + 0.001), 1e-6), "east metres agree with FlightPath");
        check(near(MapTiles.northM(fp.originLat, fp.originLat + 0.001), fp.northM(fp.originLat + 0.001), 1e-6), "north metres agree with FlightPath");

        // ---------- zoom and cover
        check(MapTiles.chooseZoom(150, 36.85) >= 17, "a small flight gets a sharp zoom: " + MapTiles.chooseZoom(150, 36.85));
        int zFar = MapTiles.chooseZoom(5000, 36.85);
        check(zFar < MapTiles.chooseZoom(150, 36.85) && zFar >= MapTiles.MIN_ZOOM, "a long flight gets a coarser zoom: " + zFar);
        check(MapTiles.chooseZoom(1e7, 36.85) == MapTiles.MIN_ZOOM, "a huge radius stops at the lowest zoom");
        for (double r : new double[]{150, 300, 800, 2000, 6000, 20000}) {
            int zz = MapTiles.chooseZoom(r, 36.85);
            List<MapTiles.Tile> tiles = MapTiles.cover(36.85, 34.60, r, zz);
            check(tiles.size() <= MapTiles.MAX_SIDE * MapTiles.MAX_SIDE && tiles.size() >= 1, "radius " + r + " m: " + tiles.size() + " tiles at zoom " + zz);
            boolean holds = false;
            for (MapTiles.Tile t : tiles) if (t.x == (int) Math.floor(MapTiles.lonToX(34.60, zz)) && t.y == (int) Math.floor(MapTiles.latToY(36.85, zz))) holds = true;
            check(holds, "radius " + r + " m: the origin's tile is covered");
        }
        // the cover reaches the far point
        double rr = 800;
        int zr = MapTiles.chooseZoom(rr, 36.85);
        List<MapTiles.Tile> cov = MapTiles.cover(36.85, 34.60, rr, zr);
        double east = MapTiles.lonToX(34.60 + Math.toDegrees(rr / (MapTiles.EARTH_M * Math.cos(Math.toRadians(36.85)))) * 0.99, zr);
        boolean reach = false;
        for (MapTiles.Tile t : cov) if (t.x == (int) Math.floor(east)) reach = true;
        check(reach, "the cover reaches the east end of the radius");
        // nearest first
        double d0 = Math.hypot(cov.get(0).x + 0.5 - MapTiles.lonToX(34.60, zr), cov.get(0).y + 0.5 - MapTiles.latToY(36.85, zr));
        double dN = Math.hypot(cov.get(cov.size() - 1).x + 0.5 - MapTiles.lonToX(34.60, zr), cov.get(cov.size() - 1).y + 0.5 - MapTiles.latToY(36.85, zr));
        check(d0 <= dN, "the tiles are ordered from the origin outwards");
        // the edge of the world does not break it
        List<MapTiles.Tile> edge = MapTiles.cover(0.0, 179.999, 2000, 12);
        boolean inRange = true;
        for (MapTiles.Tile t : edge) if (t.x < 0 || t.x >= (1 << 12) || t.y < 0 || t.y >= (1 << 12)) inRange = false;
        check(inRange && !edge.isEmpty(), "tiles near the date line stay inside the grid");

        // ---------- a flight
        List<MapTiles.Tile> ft = MapTiles.forFlight(fp);
        check(!ft.isEmpty() && ft.size() <= 144, "a flight has a set of tiles: " + ft.size());
        check(MapTiles.forFlight(null).isEmpty() && MapTiles.forFlight(new FlightPath()).isEmpty(), "no path, no tiles");
        double rad = MapTiles.radiusOf(fp);
        check(rad >= MapTiles.MIN_RADIUS_M && rad <= MapTiles.MAX_RADIUS_M && rad >= fp.maxRangeM * 0.7, "the radius covers the flight with a margin: " + rad + " (range " + fp.maxRangeM + ")");

        // ---------- addresses
        check(MapTiles.validTemplate(MapTiles.DEFAULT_URL), "the default address is valid");
        check(!MapTiles.validTemplate("http://example.com/{z}/{x}/{y}.png"), "plain http is refused");
        check(!MapTiles.validTemplate("https://example.com/tiles.png"), "an address without the placeholders is refused");
        check(!MapTiles.validTemplate("https://example.com/{z}/{x}/{y}.png and more") && !MapTiles.validTemplate(null) && !MapTiles.validTemplate(""), "spaces, null and empty are refused");
        check(MapTiles.validTemplate("https://example.com/a/{z}/{y}/{x}.jpg?key=abc"), "other orders and a key are fine");
        check(MapTiles.validTemplate("http://127.0.0.1:8080/{z}/{x}/{y}.png"), "the local test server is fine");
        check(MapTiles.templateOrDefault("nonsense").equals(MapTiles.DEFAULT_URL) && MapTiles.templateOrDefault("  https://e.org/{z}/{x}/{y}.png ").equals("https://e.org/{z}/{x}/{y}.png"), "a bad address falls back to the default, a good one is trimmed");
        MapTiles.Tile t5 = MapTiles.cover(36.85, 34.60, 10, 17).get(0);
        String u = MapTiles.url("https://e.org/{z}/{x}/{y}.png", t5);
        check(u.equals("https://e.org/17/" + t5.x + "/" + t5.y + ".png"), "the address is filled in: " + u);
        check(MapTiles.url("https://e.org/{z}/{y}/{x}", t5).equals("https://e.org/17/" + t5.y + "/" + t5.x), "the placeholders can come in any order");

        // ---------- cache files
        String p1 = MapTiles.cachePath(MapTiles.DEFAULT_URL, t5);
        String p2 = MapTiles.cachePath("https://e.org/{z}/{x}/{y}.png", t5);
        check(p1.endsWith("/17/" + t5.x + "/" + t5.y + ".tile") && !p1.contains("..") && !p1.startsWith("/"), "the cache path is relative and clean: " + p1);
        check(!p1.substring(0, 8).equals(p2.substring(0, 8)), "another source has another folder");
        check(p1.substring(0, 8).equals(MapTiles.cachePath(MapTiles.DEFAULT_URL, t5).substring(0, 8)), "the folder of a source is stable");

        // ---------- attribution and names
        check(MapTiles.attributionFor(MapTiles.DEFAULT_URL, "").contains("OpenStreetMap"), "the default source credits OpenStreetMap");
        check(MapTiles.attributionFor("https://e.org/{z}/{x}/{y}.png", "(c) Example").equals("(c) Example"), "another source shows the user's text");
        check(MapTiles.attributionFor("https://e.org/{z}/{x}/{y}.png", null).isEmpty(), "and nothing when there is none");
        check(MapTiles.sourceName(MapTiles.DEFAULT_URL).equals("OpenStreetMap") && MapTiles.sourceName("https://tiles.example.org/a/{z}/{x}/{y}.png").equals("tiles.example.org"), "source names");

        // ---------- the camera: a ground point behind it is still put on the screen, one in front is projected as before
        Cam3D cam = new Cam3D();
        cam.setViewport(1280, 720);
        FlightPath.Pose pose = new FlightPath.Pose();
        pose.x = 0; pose.y = 0; pose.z = 30;
        cam.mode = Cam3D.TOP;
        cam.reset();
        cam.update(pose, 0.016);
        double[] pr = new double[3], pc = new double[3];
        boolean seen = cam.project(5, 5, 0, pr);
        cam.projectClamped(5, 5, 0, 0.8, pc);
        check(seen && near(pr[0], pc[0], 1e-9) && near(pr[1], pc[1], 1e-9) && near(pr[2], pc[2], 1e-9), "in front of the camera the clamped projection is the normal one");
        cam.mode = Cam3D.CHASE; cam.reset(); pose.x = 0; pose.y = 0; pose.z = 5;
        cam.update(pose, 0.016);
        cam.projectClamped(cam.ex * 2 - cam.tx, cam.ey * 2 - cam.ty, 0, 0.8, pc);          // a ground point on the far side of the camera from where it looks
        check(pc[2] < Cam3D.NEAR && !Double.isNaN(pc[0]) && !Double.isInfinite(pc[0]) && !Double.isNaN(pc[1]) && !Double.isInfinite(pc[1]), "a point behind the camera has a finite screen position and a depth that says it is behind: " + pc[0] + ", " + pc[1] + ", " + pc[2]);
        // the ground in front of a chase camera is on the lower half of the screen
        cam.projectClamped(cam.tx, cam.ty, 0, 0.8, pc);
        check(pc[2] > 0 && pc[1] > 360 - 5, "the ground under the aircraft is on the lower half of the screen: y " + pc[1]);

        // ---------- the kinds of map
        check(MapTiles.validTemplate(MapTiles.SATELLITE_URL) && MapTiles.validTemplate(MapTiles.TERRAIN_URL), "the satellite and the terrain addresses are valid");
        check(MapTiles.templateFor(MapTiles.STREET, "").equals(MapTiles.DEFAULT_URL) && MapTiles.templateFor(MapTiles.SATELLITE, "").equals(MapTiles.SATELLITE_URL) && MapTiles.templateFor(MapTiles.TERRAIN, "x").equals(MapTiles.TERRAIN_URL), "every kind has its address");
        check(MapTiles.templateFor(MapTiles.CUSTOM, " https://e.org/{z}/{x}/{y}.png ").equals("https://e.org/{z}/{x}/{y}.png"), "the custom kind uses the user's address");
        check(MapTiles.templateFor(MapTiles.CUSTOM, "").equals(MapTiles.DEFAULT_URL) && MapTiles.effectiveType(MapTiles.CUSTOM, "nonsense") == MapTiles.STREET && MapTiles.effectiveType(MapTiles.CUSTOM, "https://e.org/{z}/{x}/{y}") == MapTiles.CUSTOM, "custom without a good address is the street map");
        check(MapTiles.typeOf(-3) == 0 && MapTiles.typeOf(9) == 3 && MapTiles.typeOf(2) == 2, "the kind is kept in range");
        check(MapTiles.SATELLITE_URL.indexOf("{z}") < MapTiles.SATELLITE_URL.indexOf("{y}") && MapTiles.SATELLITE_URL.indexOf("{y}") < MapTiles.SATELLITE_URL.indexOf("{x}"), "the satellite address has its placeholders as z, y, x");
        check(MapTiles.maxZoomOf(MapTiles.TERRAIN) == 17 && MapTiles.maxZoomOf(MapTiles.SATELLITE) <= 17 && MapTiles.maxZoomOf(MapTiles.STREET) == MapTiles.MAX_ZOOM, "terrain stops at zoom 17");
        check(MapTiles.chooseZoom(150, 36.85, 17) == 17 && MapTiles.chooseZoom(150, 36.85) == 18, "a small flight is held to the zoom limit of its source");
        List<MapTiles.Tile> tt = MapTiles.forFlight(fp, 15);
        check(!tt.isEmpty() && tt.get(0).z <= 15, "the tiles of a flight respect the limit: zoom " + tt.get(0).z);
        check(MapTiles.creditFor(MapTiles.TERRAIN, "", "").contains("OpenTopoMap") && MapTiles.creditFor(MapTiles.SATELLITE, "", "").contains("Esri") && MapTiles.creditFor(MapTiles.STREET, "", "").contains("OpenStreetMap"), "the preset kinds have their credits");
        check(MapTiles.creditFor(MapTiles.CUSTOM, "https://e.org/{z}/{x}/{y}.png", "(c) Me").equals("(c) Me") && MapTiles.creditFor(MapTiles.CUSTOM, "https://e.org/{z}/{x}/{y}.png", "").isEmpty(), "the custom kind shows the user's credit, or none");
        check(MapTiles.creditFor(MapTiles.CUSTOM, "nonsense", "(c) Me").contains("OpenStreetMap"), "custom without an address is the street map, with its credit");
        check(MapTiles.creditFor(MapTiles.TERRAIN, "", "").length() <= 80 && MapTiles.creditFor(MapTiles.SATELLITE, "", "").length() <= 80, "the credits are short");
        check(!MapTiles.cachePath(MapTiles.SATELLITE_URL, t5).substring(0, 8).equals(MapTiles.cachePath(MapTiles.TERRAIN_URL, t5).substring(0, 8)), "each kind has its own cache folder");

        // ---------- the settings file only takes a good address and a short credit
        java.util.Map<String, Object> st = new java.util.HashMap<String, Object>();
        st.put("mapOn", Boolean.TRUE); st.put("mapUrl", "https://e.org/{z}/{x}/{y}.png"); st.put("mapCredit", "(c) Example");
        SettingsFile.Summary ok = SettingsFile.inspect(SettingsFile.export(st, "x", "y"));
        check(ok.usable && ok.entries == 3 && ok.ignored == 0, "a good map address and credit are accepted: " + ok.entries + " entries, " + ok.ignored + " ignored");
        java.util.Map<String, Object> bad = new java.util.HashMap<String, Object>();
        bad.put("mapUrl", "http://evil.example/{z}/{x}/{y}.png");
        check(SettingsFile.inspect(SettingsFile.export(bad, "x", "y")).entries == 0, "a plain http address is not taken from a backup");
        bad.put("mapUrl", "https://e.org/tiles.png");
        check(SettingsFile.inspect(SettingsFile.export(bad, "x", "y")).entries == 0, "an address without the placeholders is not taken");

        // ---------- trimming
        check(MapTiles.trim(new long[]{10, 20, 30}, new long[]{3, 1, 2}, 100, 50).length == 0, "nothing is deleted below the limit");
        int[] del = MapTiles.trim(new long[]{40, 40, 40, 40}, new long[]{4, 1, 3, 2}, 100, 80);
        check(del.length == 2 && del[0] == 1 && del[1] == 3, "the oldest files go first, until the target is reached: " + java.util.Arrays.toString(del));
        check(MapTiles.trim(new long[0], new long[0], 10, 5).length == 0, "an empty cache is fine");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
