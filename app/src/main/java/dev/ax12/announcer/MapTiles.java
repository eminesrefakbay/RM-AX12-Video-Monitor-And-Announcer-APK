// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The maths of the map under the 3D replay, in plain Java so that it can be tested on a PC: which map tiles (the usual Web Mercator "slippy map" tiles
 * of 256 pixels) cover a flight, which zoom level to use, where a point of a tile lies in metres east and north of the flight's origin (the same
 * axes as {@link FlightPath}), the address of a tile, the name of its file in the cache, and which cached files to delete when the cache is too big.
 * Nothing here touches the network or the screen.
 */
public final class MapTiles {
    private MapTiles() {}

    /** The street map of the OpenStreetMap project. Its tile usage policy asks for a real User-Agent, caching and a modest number of requests, all of which the app does. */
    public static final String DEFAULT_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    public static final String DEFAULT_ATTRIBUTION = "© OpenStreetMap contributors";

    /** The kinds of map. CUSTOM is the address the user gave in the settings. */
    public static final int STREET = 0, SATELLITE = 1, TERRAIN = 2, CUSTOM = 3, TYPES = 4;
    /** Satellite pictures of the world imagery service of Esri; the order of the placeholders is z, y, x. No key is in the address. */
    public static final String SATELLITE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
    /** The terrain map of OpenTopoMap (contour lines and shading, from OpenStreetMap and SRTM data). It has no tiles past zoom 17. */
    public static final String TERRAIN_URL = "https://tile.opentopomap.org/{z}/{x}/{y}.png";

    public static final int MIN_ZOOM = 10, MAX_ZOOM = 18;
    /** At most this many tiles along one side of the covered square, so one flight is at most 144 tiles (a few MB on the card). */
    public static final int MAX_SIDE = 12;
    /** The map reaches this far past the farthest point of the flight, and at least this far from home. */
    public static final double MARGIN_M = 80.0, MIN_RADIUS_M = 150.0, MAX_RADIUS_M = 30000.0;
    /** The cache is trimmed to TARGET_BYTES when it grows past LIMIT_BYTES. */
    public static final long LIMIT_BYTES = 120L * 1024 * 1024, TARGET_BYTES = 80L * 1024 * 1024;

    static final double EARTH_M = FlightPath.EARTH_M;
    static final double CIRCUMFERENCE_M = 2 * Math.PI * 6378137.0;
    /** The most latitude Web Mercator can show. */
    static final double MAX_LAT = 85.0511287798;

    /** One tile and where its corners are. */
    public static final class Tile {
        public final int z, x, y;
        Tile(int z, int x, int y) { this.z = z; this.x = x; this.y = y; }
        @Override public String toString() { return z + "/" + x + "/" + y; }
    }

    // ------------------------------------------------------------------ tile numbers

    /** The tile column of a longitude, as a fraction (the whole part is the tile). */
    public static double lonToX(double lon, int z) { return (lon + 180.0) / 360.0 * (1L << z); }

    /** The tile row of a latitude, as a fraction. */
    public static double latToY(double lat, int z) {
        double la = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
        double r = Math.toRadians(la);
        return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * (1L << z);
    }

    /** The longitude of the left edge of tile column x (a fraction works too). */
    public static double xToLon(double x, int z) { return x / (1L << z) * 360.0 - 180.0; }

    /** The latitude of the top edge of tile row y (a fraction works too). */
    public static double yToLat(double y, int z) {
        double n = Math.PI - 2.0 * Math.PI * y / (1L << z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /** Metres east of the origin, with the same flat-earth rule as {@link FlightPath#eastM}. */
    public static double eastM(double originLat, double originLon, double lon) {
        return Math.toRadians(lon - originLon) * Math.cos(Math.toRadians(originLat)) * EARTH_M;
    }

    /** Metres north of the origin. */
    public static double northM(double originLat, double lat) { return Math.toRadians(lat - originLat) * EARTH_M; }

    /**
     * A point inside a tile as metres east and north of the origin: u runs from 0 (left edge) to 1 (right edge), v from 0 (top, north) to 1 (bottom).
     * The rows follow the Mercator scale, so a picture laid on a grid of these points sits right on the ground.
     */
    public static void pointInTile(double originLat, double originLon, Tile t, double u, double v, double[] out) {
        out[0] = eastM(originLat, originLon, xToLon(t.x + u, t.z));
        out[1] = northM(originLat, yToLat(t.y + v, t.z));
    }

    // ------------------------------------------------------------------ which tiles

    /** How far the map has to reach: the farthest point of the flight from the origin plus a margin, kept between the limits. */
    public static double radiusOf(FlightPath p) {
        double r = 0;
        for (int i = 0; i < p.n; i++) r = Math.max(r, Math.max(Math.abs(p.x[i]), Math.abs(p.y[i])));
        return Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, r + MARGIN_M));
    }

    /** The side of one tile on the ground, in metres, at a latitude. */
    public static double tileMeters(double lat, int z) { return CIRCUMFERENCE_M * Math.cos(Math.toRadians(lat)) / (1L << z); }

    /** The sharpest zoom for which the square of side 2 x radius needs at most MAX_SIDE tiles along a side. */
    public static int chooseZoom(double radiusM, double lat) { return chooseZoom(radiusM, lat, MAX_ZOOM); }

    /** The same, but not past maxZoom (a source may have no tiles at the sharpest zoom). */
    public static int chooseZoom(double radiusM, double lat, int maxZoom) { return chooseZoom(radiusM, lat, maxZoom, MAX_SIDE); }

    /** The same, with at most maxSide tiles along a side (the elevation tiles use fewer and bigger ones). */
    public static int chooseZoom(double radiusM, double lat, int maxZoom, int maxSide) {
        for (int z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, maxZoom)); z > MIN_ZOOM; z--) {
            double side = Math.ceil(2 * radiusM / tileMeters(lat, z)) + 1;
            if (side <= maxSide) return z;
        }
        return MIN_ZOOM;
    }

    /** The tiles that cover the square around the origin, the ones nearest to the origin first (they are the ones that matter first). */
    public static List<Tile> cover(double originLat, double originLon, double radiusM, int z) {
        double dLat = Math.toDegrees(radiusM / EARTH_M);
        double dLon = Math.toDegrees(radiusM / (EARTH_M * Math.max(0.05, Math.cos(Math.toRadians(originLat)))));
        int n = 1 << z;
        int x0 = clamp((int) Math.floor(lonToX(originLon - dLon, z)), n), x1 = clamp((int) Math.floor(lonToX(originLon + dLon, z)), n);
        int y0 = clamp((int) Math.floor(latToY(originLat + dLat, z)), n), y1 = clamp((int) Math.floor(latToY(originLat - dLat, z)), n);
        final double cx = lonToX(originLon, z), cy = latToY(originLat, z);
        List<Tile> out = new ArrayList<Tile>();
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) out.add(new Tile(z, x, y));
        java.util.Collections.sort(out, new java.util.Comparator<Tile>() {
            @Override public int compare(Tile a, Tile b) {
                double da = Math.hypot(a.x + 0.5 - cx, a.y + 0.5 - cy), db = Math.hypot(b.x + 0.5 - cx, b.y + 0.5 - cy);
                return Double.compare(da, db);
            }
        });
        return out;
    }

    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    /** The tiles for a flight: the radius, the zoom and the cover in one step. Empty when the flight has no position. */
    public static List<Tile> forFlight(FlightPath p) { return forFlight(p, MAX_ZOOM); }

    /** The tiles for a flight, at a zoom of at most maxZoom. */
    public static List<Tile> forFlight(FlightPath p, int maxZoom) {
        if (p == null || p.n < 2 || Double.isNaN(p.originLat) || Double.isNaN(p.originLon)) return new ArrayList<Tile>();
        double r = radiusOf(p);
        return cover(p.originLat, p.originLon, r, chooseZoom(r, p.originLat, maxZoom));
    }

    // ------------------------------------------------------------------ the kinds of map

    /** The kind of map, kept to 0..3. */
    public static int typeOf(int v) { return Math.max(0, Math.min(TYPES - 1, v)); }

    /** The tile address of a kind of map. CUSTOM uses the user's address when it is valid, and falls back to the street map when it is not. */
    public static String templateFor(int type, String custom) {
        if (type == SATELLITE) return SATELLITE_URL;
        if (type == TERRAIN) return TERRAIN_URL;
        if (type == CUSTOM && validTemplate(custom)) return custom.trim();
        return DEFAULT_URL;
    }

    /** The kind of map that is really shown: CUSTOM without a valid address is the street map. */
    public static int effectiveType(int type, String custom) {
        return type == CUSTOM && !validTemplate(custom) ? STREET : typeOf(type);
    }

    /** The sharpest zoom a kind of map has tiles for. */
    public static int maxZoomOf(int type) {
        if (type == SATELLITE || type == TERRAIN) return 17;     // the imagery has no picture of many places at the sharpest zooms; OpenTopoMap ends at 17
        return MAX_ZOOM;
    }

    /** The credit to show for a kind of map. */
    public static String creditFor(int type, String custom, String customCredit) {
        if (type == SATELLITE) return "Esri, Maxar, Earthstar Geographics, GIS User Community";
        if (type == TERRAIN) return "\u00A9 OpenStreetMap contributors, SRTM | \u00A9 OpenTopoMap (CC-BY-SA)";
        if (type == CUSTOM && validTemplate(custom)) return attributionFor(custom.trim(), customCredit);
        return DEFAULT_ATTRIBUTION;
    }

    // ------------------------------------------------------------------ addresses and files

    /** A tile address template must be https (plain http only for the PC tests) and carry {z}, {x} and {y}. */
    public static boolean validTemplate(String t) {
        if (t == null) return false;
        String s = t.trim();
        if (s.length() < 12 || s.length() > 300) return false;
        boolean scheme = s.startsWith("https://") || s.startsWith("http://127.0.0.1:") || s.startsWith("http://localhost:");
        if (!scheme) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) <= ' ' || s.charAt(i) > '~') return false;
        return s.contains("{z}") && s.contains("{x}") && s.contains("{y}");
    }

    /** The template to use: the user's own when it is valid, otherwise the OpenStreetMap one. */
    public static String templateOrDefault(String custom) {
        return validTemplate(custom) ? custom.trim() : DEFAULT_URL;
    }

    public static String url(String template, Tile t) {
        return template.replace("{z}", Integer.toString(t.z)).replace("{x}", Integer.toString(t.x)).replace("{y}", Integer.toString(t.y));
    }

    /** The folder of one tile source inside the cache: a short hash of its template, so changing the source never mixes pictures. */
    public static String sourceFolder(String template) {
        int h = 0x811C9DC5;
        for (int i = 0; i < template.length(); i++) { h ^= template.charAt(i); h *= 0x01000193; }
        return String.format(Locale.US, "%08x", h);
    }

    /** The file of a tile inside the cache folder, as a relative path: source/z/x/y.tile */
    public static String cachePath(String template, Tile t) {
        return sourceFolder(template) + "/" + t.z + "/" + t.x + "/" + t.y + ".tile";
    }

    /** Where the attribution comes from: the default one for the default source, the user's text for another. */
    public static String attributionFor(String template, String custom) {
        if (DEFAULT_URL.equals(template)) return DEFAULT_ATTRIBUTION;
        String c = custom == null ? "" : custom.trim();
        if (c.length() > 80) c = c.substring(0, 80);
        return c.isEmpty() ? "" : c;
    }

    /** The short name of a source for the settings: OpenStreetMap, or the host of the user's address. */
    public static String sourceName(String template) {
        if (DEFAULT_URL.equals(template)) return "OpenStreetMap";
        int a = template.indexOf("://") + 3;
        int b = template.indexOf('/', a);
        return b < 0 ? template.substring(a) : template.substring(a, b);
    }

    // ------------------------------------------------------------------ the cache

    /**
     * Which files to delete when the cache has grown past the limit: the oldest ones, until what is left is at most the target. The arrays are
     * the sizes and the modification times of the cached files; the answer is the indexes to delete. Nothing is deleted below the limit.
     */
    public static int[] trim(long[] sizes, long[] modified, long limit, long target) {
        long total = 0;
        for (long s : sizes) total += s;
        if (total <= limit) return new int[0];
        Integer[] order = new Integer[sizes.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final long[] m = modified;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return Long.compare(m[a], m[b]); }
        });
        List<Integer> del = new ArrayList<Integer>();
        for (int k = 0; k < order.length && total > target; k++) { del.add(order[k]); total -= sizes[order[k]]; }
        int[] out = new int[del.size()];
        for (int i = 0; i < out.length; i++) out[i] = del.get(i);
        return out;
    }
}
