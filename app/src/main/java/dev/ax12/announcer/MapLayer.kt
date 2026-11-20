// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The map under the 3D replay: the tiles that cover one flight, loaded in the background. A tile comes from the cache folder of the app when it is there
 * (so a flight that was opened once at home shows its map at the field without a network) and from the tile server when it is not. Two threads at most
 * ask the server, each tile is asked for once, the pictures are checked before they are kept, and the cache is trimmed when it grows. The tiles of the
 * flight are requested nearest the home point first. See [MapTiles] for the maths.
 *
 * The view asks [bitmap] for the tile it wants to draw and gets null until the picture is ready; it draws every frame anyway, so no callback is needed.
 */
class MapLayer(context: Context, path: FlightPath, custom: String, customCredit: String, private val userAgent: String) {
    val originLat: Double = path.originLat
    val originLon: Double = path.originLon
    val template: String = MapTiles.templateOrDefault(custom)
    val attribution: String = MapTiles.attributionFor(template, customCredit)
    val tiles: List<MapTiles.Tile> = MapTiles.forFlight(path)

    /** For every tile: a grid of (GRID + 1) x (GRID + 1) ground points as metres east and north, row by row from the north-west corner. */
    val grid: Array<DoubleArray> = Array(tiles.size) { i -> gridOf(tiles[i]) }
    /** The middle of every tile on the ground, and the side of a tile in metres. */
    val centerX = DoubleArray(tiles.size)
    val centerY = DoubleArray(tiles.size)
    val size: Double

    private val dir = File(context.applicationContext.cacheDir, "maptiles")
    private val memory = LruCache<Int, Bitmap>(MEMORY_TILES)
    private val pending = HashSet<Int>()
    private val failed = HashSet<Int>()
    private val wanted = HashSet<Int>()          // tiles the view asked for: they are decoded into memory, the others are only fetched to the cache
    private val pool: ExecutorService = Executors.newFixedThreadPool(2) { r -> Thread(r, "map-tiles").also { it.isDaemon = true } }
    @Volatile private var closed = false

    init {
        val mid = GRID / 2 * (GRID + 1) + GRID / 2
        for (i in tiles.indices) {
            centerX[i] = grid[i][mid * 2]
            centerY[i] = grid[i][mid * 2 + 1]
        }
        size = if (tiles.isEmpty()) 100.0 else Math.abs(grid[0][GRID * 2] - grid[0][0])
        if (tiles.isNotEmpty()) {
            for (i in tiles.indices) request(i, false)             // the whole area, nearest first: it is in the cache for the next time
            try { pool.execute { trimCache() } } catch (e: Exception) { }
        }
    }

    private fun gridOf(t: MapTiles.Tile): DoubleArray {
        val g = DoubleArray((GRID + 1) * (GRID + 1) * 2)
        val pt = DoubleArray(2)
        var k = 0
        for (j in 0..GRID) for (i in 0..GRID) {
            MapTiles.pointInTile(originLat, originLon, t, i.toDouble() / GRID, j.toDouble() / GRID, pt)
            g[k++] = pt[0]
            g[k++] = pt[1]
        }
        return g
    }

    /** The picture of tile i, or null while it is not ready (it is asked for, if it was not yet). */
    fun bitmap(i: Int): Bitmap? {
        val b = memory.get(i)
        if (b != null) return b
        request(i, true)
        return null
    }

    private fun request(i: Int, forView: Boolean) {
        synchronized(this) {
            if (closed || failed.contains(i)) return
            if (forView) wanted.add(i)
            if (pending.contains(i)) return
            pending.add(i)
        }
        try {
            pool.execute { load(i) }
        } catch (e: Exception) {
            synchronized(this) { pending.remove(i) }
        }
    }

    private fun load(i: Int) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            if (closed) return
            val t = tiles[i]
            val f = File(dir, MapTiles.cachePath(template, t))
            if (!f.exists() && (closed || !download(t, f))) { synchronized(this) { failed.add(i) }; return }
            val want = synchronized(this) { wanted.remove(i) }
            if (want) {
                val bmp = decode(f)
                if (bmp != null) memory.put(i, bmp) else synchronized(this) { failed.add(i) }
            }
        } catch (e: Throwable) {
            synchronized(this) { failed.add(i) }
        } finally {
            synchronized(this) { pending.remove(i) }
        }
    }

    private fun decode(f: File): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options()
        o.inPreferredConfig = Bitmap.Config.RGB_565          // half the memory of a full colour picture; a map does not need more
        val b = BitmapFactory.decodeFile(f.path, o)
        if (b == null) f.delete()                            // a broken file is never kept
        else f.setLastModified(System.currentTimeMillis())   // the cache trims the oldest first, so a tile that is in use stays
        return b
    }

    private fun download(t: MapTiles.Tile, dest: File): Boolean {
        var c: HttpURLConnection? = null
        val part = File(dest.path + ".part")
        try {
            c = URL(MapTiles.url(template, t)).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "image/png,image/jpeg,image/*")
            if (c.responseCode != 200) return false
            if ((c.contentType ?: "").startsWith("image/").not()) return false
            dest.parentFile?.mkdirs()
            var total = 0
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_TILE_BYTES) throw IOException("tile too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            return total > 0 && part.renameTo(dest)
        } catch (e: Exception) {
            return false
        } finally {
            c?.disconnect()
            if (part.exists()) part.delete()
        }
    }

    /** Deletes the oldest tiles when the cache is past its limit. */
    private fun trimCache() {
        try {
            val files = ArrayList<File>()
            fun walk(d: File) {
                val list = d.listFiles() ?: return
                for (f in list) {
                    if (f.isDirectory) walk(f)
                    else if (f.name.endsWith(".part")) f.delete()
                    else if (f.name.endsWith(".tile")) files.add(f)
                }
            }
            walk(dir)
            val del = MapTiles.trim(LongArray(files.size) { files[it].length() }, LongArray(files.size) { files[it].lastModified() }, MapTiles.LIMIT_BYTES, MapTiles.TARGET_BYTES)
            for (k in del) files[k].delete()
        } catch (e: Exception) { }
    }

    /** Stops the loading and lets go of the pictures. */
    fun close() {
        closed = true
        pool.shutdownNow()
        memory.evictAll()
    }

    companion object {
        /** Points per side of the grid laid on a tile: the picture is drawn as a mesh of GRID x GRID cells near the camera, half of that far away. */
        const val GRID = 8
        private const val MEMORY_TILES = 150
        private const val MAX_TILE_BYTES = 600 * 1024
    }
}
