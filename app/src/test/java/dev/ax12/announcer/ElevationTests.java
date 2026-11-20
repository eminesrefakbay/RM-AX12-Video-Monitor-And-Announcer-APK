// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.List;

/** Tests of the elevation model behind the hills of the 3D replay: decoding the colours, smooth heights, relative to home, tiles side by side. */
public class ElevationTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    /** The pixels of a tile whose height is a function of the pixel position (metres), written the way Terrarium writes it. */
    interface H { double at(int px, int py); }
    static int[] tile(H h) {
        int[] p = new int[ElevationModel.TILE * ElevationModel.TILE];
        for (int y = 0; y < ElevationModel.TILE; y++) for (int x = 0; x < ElevationModel.TILE; x++) {
            double v = h.at(x, y) + 32768.0;
            int r = (int) Math.floor(v / 256.0);
            int g = (int) Math.floor(v - r * 256.0);
            int b = (int) Math.floor((v - Math.floor(v)) * 256.0);
            p[y * ElevationModel.TILE + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return p;
    }

    public static void main(String[] args) {
        // ---------- the colour code
        check(near(ElevationModel.decode(128, 0, 0), 0, 1e-9), "red 128 is sea level");
        check(near(ElevationModel.decode(128, 100, 0), 100, 1e-9), "green adds metres");
        check(near(ElevationModel.decode(128, 10, 128), 10.5, 1e-9), "blue adds fractions of a metre");
        check(near(ElevationModel.decode(129, 0, 0), 256, 1e-9) && near(ElevationModel.decode(127, 0, 0), -256, 1e-9), "red counts 256 metres");
        check(near(ElevationModel.decode(0, 0, 0), -32768, 1e-9), "the lowest value");

        // ---------- one tile: a plane that rises to the east, 1 m per pixel from 100 m
        ElevationModel m = new ElevationModel(13);
        int tx = 5000, ty = 3200;
        m.put(tx, ty, tile((x, y) -> 100 + x));
        check(m.has(tx, ty) && !m.has(tx + 1, ty), "the tile is in, its neighbour is not");
        double lat = MapTiles.yToLat(ty + 0.5, 13), lon = MapTiles.xToLon(tx + 0.5, 13);
        double h = m.heightAt(lat, lon);
        check(near(h, 100 + 127.5, 1.0), "the middle of the tile is at the middle height: " + h);
        double lon2 = MapTiles.xToLon(tx + 0.5 + 10.0 / 256, 13);
        check(near(m.heightAt(lat, lon2) - h, 10, 0.5), "10 pixels east is 10 m higher: " + (m.heightAt(lat, lon2) - h));
        check(Double.isNaN(m.heightAt(lat, MapTiles.xToLon(tx + 1.5, 13))), "a point in a tile that is not there has no height");
        check(near(m.reliefAt(lat, lon2, h, 1), m.heightAt(lat, lon2) - h, 1e-9), "relief is relative to the home height");
        check(near(m.reliefAt(lat, lon2, h, 2), 2 * (m.heightAt(lat, lon2) - h), 1e-9), "and is multiplied by the scale");
        check(m.reliefAt(lat, MapTiles.xToLon(tx + 1.5, 13), h, 1) == 0.0, "no data is flat");
        check(m.reliefAt(lat, lon2, Double.NaN, 1) == 0.0, "no home height is flat");

        // ---------- below the sea is flat
        ElevationModel sea = new ElevationModel(13);
        sea.put(tx, ty, tile((x, y) -> x < 128 ? -50 : 20));
        check(near(sea.heightAt(MapTiles.yToLat(ty + 0.5, 13), MapTiles.xToLon(tx + 0.1, 13)), 0, 1e-3), "the sea floor is drawn as sea level");
        check(near(sea.heightAt(MapTiles.yToLat(ty + 0.5, 13), MapTiles.xToLon(tx + 0.9, 13)), 20, 0.5), "land is as high as it is");

        // ---------- smooth across the edge between two tiles
        ElevationModel two = new ElevationModel(13);
        two.put(tx, ty, tile((x, y) -> 100 + x));                 // 100..355
        two.put(tx + 1, ty, tile((x, y) -> 356 + x));             // goes on at 356: one continuous slope
        double edgeLat = MapTiles.yToLat(ty + 0.5, 13);
        double before = two.heightAt(edgeLat, MapTiles.xToLon(tx + 1.0 - 0.5 / 256, 13));
        double after = two.heightAt(edgeLat, MapTiles.xToLon(tx + 1.0 + 0.5 / 256, 13));
        check(near(after - before, 1, 0.3) && before > 350 && after < 365, "no step at the edge of two tiles: " + before + " -> " + after);

        // ---------- the grid for a map tile
        MapTiles.Tile mt = null;
        double lat0 = MapTiles.yToLat(ty + 0.5, 13), lon0 = MapTiles.xToLon(tx + 0.5, 13);
        List<MapTiles.Tile> cov = MapTiles.cover(lat0, lon0, 50, 17);
        mt = cov.get(0);
        double home = m.heightAt(lat0, lon0);
        float[] g = m.reliefGrid(mt, 4, home, 1);
        check(g.length == 25, "a grid of 4 cells has 25 points");
        check(g[4] > g[0], "the grid rises to the east like the land: " + g[0] + " -> " + g[4]);
        check(near(g[0], g[20], 0.5), "and does not change to the south on this plane");
        float[] g2 = m.reliefGrid(mt, 4, home, 3);
        check(near(g2[4], 3 * g[4], 1e-3), "the scale multiplies the grid");

        // ---------- which tiles
        int z = ElevationModel.zoomFor(300, 36.85);
        check(z == ElevationModel.WANTED_ZOOM, "a small flight uses the wanted zoom: " + z);
        int zFar = ElevationModel.zoomFor(20000, 36.85);
        check(zFar < z, "a long flight uses a coarser zoom: " + zFar);
        List<MapTiles.Tile> need = MapTiles.cover(36.85, 34.6, 300, z);
        check(need.size() >= 1 && need.size() <= ElevationModel.MAX_SIDE * ElevationModel.MAX_SIDE, "few tiles are needed: " + need.size());
        check(!new ElevationModel(z).hasAll(need) && !new ElevationModel(z).hasAll(new java.util.ArrayList<MapTiles.Tile>()), "an empty model has none of them");
        check(near(ElevationModel.fitOffset(new double[]{0, 10, 5}, new double[]{0, 2, 9}, 1, 60), -5, 1e-9), "the ground is lowered until the flight is a metre above it: " + ElevationModel.fitOffset(new double[]{0, 10, 5}, new double[]{0, 2, 9}, 1, 60));
        check(ElevationModel.fitOffset(new double[]{20, 30}, new double[]{0, 0}, 1, 60) == 0, "the ground is never raised");
        check(ElevationModel.fitOffset(new double[]{0}, new double[]{500}, 1, 60) == -60, "the shift is limited");
        check(ElevationModel.fitOffset(new double[]{}, new double[]{}, 1, 60) == 0 && ElevationModel.fitOffset(new double[]{Double.NaN}, new double[]{3}, 1, 60) == 0, "no data, no shift");
        check(ElevationModel.scaleOf(0) == 0 && ElevationModel.scaleOf(1) == 1 && ElevationModel.scaleOf(3) == 3 && ElevationModel.scaleOf(9) == 3 && ElevationModel.scaleOf(-2) == 0, "the setting gives the scale");
        check(MapTiles.validTemplate(ElevationModel.URL) && ElevationModel.CREDIT.length() <= 80, "the address is valid and the credit is short");
        check(new ElevationModel(99).zoom == ElevationModel.MAX_ZOOM, "the zoom is limited to what the tiles have");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
