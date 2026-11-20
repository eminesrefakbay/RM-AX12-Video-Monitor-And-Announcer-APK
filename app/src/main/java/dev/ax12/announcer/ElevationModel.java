// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shape of the land around a flight, for the hills of the 3D replay. It holds elevation tiles in the "Terrarium" format (a picture of 256 by 256
 * pixels in which the height is written in the colour: metres = red x 256 + green + blue / 256 - 32768) and answers the question "how high is the
 * ground at this latitude and longitude", smoothly (bilinear between four pixels, also across the edge of a tile). The 3D view only wants the
 * shape, not the true heights, so everything is given relative to the home point, below sea level is flat (the sea is not drawn as a hole), and
 * a place without data counts as flat. Plain Java: the pictures are decoded by the screen code and handed in as pixel arrays.
 */
public final class ElevationModel {
    /** Elevation tiles of the Terrain Tiles on AWS (open data, no key). The bucket is meant for use from the cloud; check its terms. */
    public static final String URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";
    public static final String CREDIT = "Relief: USGS SRTM, GMTED2010, Copernicus EU-DEM (AWS Terrain Tiles)";
    public static final int TILE = 256, MAX_ZOOM = 15;
    /** The zoom used for the land: about 15 m a pixel; the tiles that cover a flight are at most MAX_SIDE along a side. */
    public static final int WANTED_ZOOM = 13, MAX_SIDE = 4;

    public final int zoom;
    private final ConcurrentHashMap<Long, float[]> tiles = new ConcurrentHashMap<Long, float[]>();

    public ElevationModel(int zoom) { this.zoom = Math.max(0, Math.min(MAX_ZOOM, zoom)); }

    /** The height in metres that a pixel of a Terrarium tile stands for. */
    public static double decode(int r, int g, int b) { return r * 256.0 + g + b / 256.0 - 32768.0; }

    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xFFFFFFFFL); }

    /** Adds a tile from its 65 536 pixels as 0xAARRGGBB (what Bitmap.getPixels gives). Heights below the sea (negative) are kept at 0. */
    public void put(int x, int y, int[] argb) {
        if (argb == null || argb.length != TILE * TILE) return;
        float[] h = new float[TILE * TILE];
        for (int i = 0; i < h.length; i++) {
            int p = argb[i];
            h[i] = (float) Math.max(0.0, decode((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
        }
        tiles.put(key(x, y), h);
    }

    public boolean has(int x, int y) { return tiles.containsKey(key(x, y)); }

    /** True when every tile of the list is in. */
    public boolean hasAll(List<MapTiles.Tile> list) {
        for (MapTiles.Tile t : list) if (!has(t.x, t.y)) return false;
        return !list.isEmpty();
    }

    private double pixel(int px, int py) {
        int n = TILE << zoom;
        if (px < 0) px = 0;
        if (py < 0) py = 0;
        if (px >= n) px = n - 1;
        if (py >= n) py = n - 1;
        float[] t = tiles.get(key(px >> 8, py >> 8));
        return t == null ? Double.NaN : t[(py & 255) * TILE + (px & 255)];
    }

    /** The height of the ground at a point in metres above the sea (not below 0); NaN where there is no tile. */
    public double heightAt(double lat, double lon) {
        double fx = MapTiles.lonToX(lon, zoom) * TILE - 0.5, fy = MapTiles.latToY(lat, zoom) * TILE - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double a = pixel(x0, y0), b = pixel(x0 + 1, y0), c = pixel(x0, y0 + 1), d = pixel(x0 + 1, y0 + 1);
        if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c) || Double.isNaN(d)) return Double.NaN;
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    /** The height of the ground at a point above the home point, times scale (1 true size, 2 twice as high...); 0 where the data is missing. */
    public double reliefAt(double lat, double lon, double homeHeight, double scale) {
        double h = heightAt(lat, lon);
        if (Double.isNaN(h) || Double.isNaN(homeHeight)) return 0.0;
        return (h - homeHeight) * scale;
    }

    /**
     * The relief at the points of the picture grid of a map tile: (grid + 1) x (grid + 1) values, row by row from the north-west corner, in the same
     * order as the ground points the 3D view lays the picture on.
     */
    public float[] reliefGrid(MapTiles.Tile t, int grid, double homeHeight, double scale) {
        float[] out = new float[(grid + 1) * (grid + 1)];
        int k = 0;
        for (int j = 0; j <= grid; j++) {
            double lat = MapTiles.yToLat(t.y + (double) j / grid, t.z);
            for (int i = 0; i <= grid; i++) {
                double lon = MapTiles.xToLon(t.x + (double) i / grid, t.z);
                out[k++] = (float) reliefAt(lat, lon, homeHeight, scale);
            }
        }
        return out;
    }

    /** The zoom for the land of a flight with this radius at this latitude. */
    public static int zoomFor(double radiusM, double lat) { return MapTiles.chooseZoom(radiusM, lat, WANTED_ZOOM, MAX_SIDE); }

    /** The scale for a setting: 0 off, 1 true size, 2 twice as high, 3 three times. */
    /**
     * How far to move the ground so that the aircraft is never under it: the log height of every point of the flight against the height of the hill under it.
     * The flight can only be above the ground, so the lowest gap says how much the hills stand too high, and they are lowered by that, leaving clearance metres
     * under the lowest point. The ground is only ever lowered (never above 0) and by at most maxShift, so a bad log cannot throw the hills away.
     * Returns the number to add to the ground heights (0 or negative).
     */
    public static double fitOffset(double[] logZ, double[] ground, double clearance, double maxShift) {
        double lowest = Double.MAX_VALUE;
        int n = Math.min(logZ.length, ground.length);
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(logZ[i]) || Double.isNaN(ground[i])) continue;
            double gap = logZ[i] - ground[i];
            if (gap < lowest) lowest = gap;
        }
        if (lowest == Double.MAX_VALUE) return 0;
        double d = lowest - clearance;
        return Math.max(-maxShift, Math.min(0, d));
    }

    public static double scaleOf(int setting) { return setting <= 0 ? 0.0 : Math.min(3, setting); }
}
