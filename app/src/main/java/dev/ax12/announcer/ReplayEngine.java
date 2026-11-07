// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/**
 * The clock of a replay: a position in a flight log that moves while it plays, at 1, 2, 4 or 8 times real time, can be set anywhere,
 * and gives the values of the row at that position. The screen feeds them to the same cards that show the live flight.
 */
public final class ReplayEngine {
    public static final int[] SPEEDS = {1, 2, 4, 8};

    /** Receives rows in order, with a time that only grows. */
    public interface Sink { void accept(long replayNowMs, Map<String, Double> numbers, Map<String, String> texts); }

    /** The replay's own clock starts here, so it never looks like time zero to code that treats zero as "not set". */
    public static final long CLOCK_BASE = 1_000_000L;

    private final FlightLog log;
    private double positionMs;
    private boolean playing;
    private int speedIndex;

    public ReplayEngine(FlightLog log) { this.log = log; }

    public FlightLog log() { return log; }
    public long durationMs() { return log.durationMs(); }
    public long positionMs() { return (long) positionMs; }
    public boolean playing() { return playing; }
    public boolean atEnd() { return positionMs >= durationMs(); }
    public int speed() { return SPEEDS[speedIndex]; }

    public void play() {
        if (atEnd()) positionMs = 0;                  // play again from the start
        playing = true;
    }

    public void pause() { playing = false; }

    public void toggle() { if (playing) pause(); else play(); }

    public void cycleSpeed() { speedIndex = (speedIndex + 1) % SPEEDS.length; }

    /** Moves the position by what the real clock moved. Stops at the end. */
    public void advance(long realDeltaMs) {
        if (!playing || realDeltaMs <= 0) return;
        positionMs += realDeltaMs * (double) speed();
        if (positionMs >= durationMs()) {
            positionMs = durationMs();
            playing = false;
        }
    }

    public void seek(long ms) {
        positionMs = Math.max(0, Math.min(durationMs(), ms));
    }

    /** The row at or just before the position; -1 when the log has no rows. */
    public int rowAt(long ms) {
        int n = log.rows;
        if (n == 0) return -1;
        int lo = 0, hi = n - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (log.time[mid] <= ms) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    public int currentRow() { return rowAt((long) positionMs); }

    /** The values of one row: only what the log holds (a value that was not there is left out, like a value that was not published). */
    public void fill(int row, Map<String, Double> numbers, Map<String, String> texts) {
        numbers.clear();
        texts.clear();
        if (row < 0 || row >= log.rows) return;
        for (int c = 0; c < log.columns.length; c++) {
            if (log.isText[c]) {
                String w = log.word(row, c);
                if (w != null) texts.put(log.columns[c], w);
            } else {
                double v = log.value(row, c);
                if (!Double.isNaN(v)) numbers.put(log.columns[c], v);
            }
        }
    }

    /** The clock the cards see for a position in the flight. */
    public static long clock(long positionMs) { return CLOCK_BASE + positionMs; }

    /** Feeds the rows from..to (both included) to the sink, in order: how the statistics and the charts are built up to a position. */
    public void feed(int from, int to, Sink sink) {
        java.util.HashMap<String, Double> nums = new java.util.HashMap<String, Double>();
        java.util.HashMap<String, String> txts = new java.util.HashMap<String, String>();
        for (int r = Math.max(0, from); r <= to && r < log.rows; r++) {
            fill(r, nums, txts);
            sink.accept(clock(log.time[r]), nums, txts);
        }
    }

    /** The first row whose time is not before the position minus the window: where a rolling chart starts when it is rebuilt. */
    public int rowForWindow(long positionMs, long windowMs) { return rowAt(Math.max(0, positionMs - windowMs)); }
}
