// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writes a flight log from arming to disarming. The screen calls {@link #update} on every tick; this class takes one row at the
 * chosen rate and hands it to a writer thread, so a slow or failing storage can never hold up the radio link or the screen. If the
 * writer cannot keep up, rows are dropped and counted; if the storage fails, logging stops and says why. Plain Java, no Android.
 */
public final class FlightLogger {

    /** The facts written at the top of the file when it is opened. */
    public interface MetaSource { Map<String, String> get(); }

    /** Wall-clock time in milliseconds (a seam for tests). */
    public interface Wall { long now(); }

    public static final long MIN_FREE_BYTES = 20L * 1024 * 1024;
    public static final int[] RATES_HZ = {1, 2, 5, 10, 25};
    public static final int[] KEEP = {20, 50, 100, 200};

    private final File dir;
    private final Wall wall;
    private final MetaSource metaSource;
    private final int queueCapacity;
    private final boolean startWriter;

    // ---- settings (from Settings)
    private boolean enabled = false;
    private int rateHz = 10;
    private List<String> fields = LogFields.replaySet();
    private int keep = 50;

    // ---- the open log
    private Sink sink;
    private File file;
    private String[] cols;
    private boolean[] isText;
    private long startMs, lastRowMs;
    private int rows, dropped;
    private String error = "";
    private double maxHeight = Double.NaN, maxDist = Double.NaN, minCell = Double.NaN, maxSpeed = Double.NaN, peakCurrent = Double.NaN, mah = Double.NaN;

    public FlightLogger(File dir, Wall wall, MetaSource metaSource) { this(dir, wall, metaSource, 2048, true); }

    /** For tests: a small queue, and optionally no writer thread, to see what happens when the writer cannot keep up. */
    FlightLogger(File dir, Wall wall, MetaSource metaSource, int queueCapacity, boolean startWriter) {
        this.dir = dir;
        this.wall = wall;
        this.metaSource = metaSource;
        this.queueCapacity = queueCapacity;
        this.startWriter = startWriter;
    }

    public void configure(boolean enabled, int rateIndex, List<String> fields, int keepIndex) {
        this.enabled = enabled;
        this.rateHz = RATES_HZ[Math.max(0, Math.min(RATES_HZ.length - 1, rateIndex))];
        this.fields = fields == null || fields.isEmpty() ? LogFields.replaySet() : new ArrayList<String>(fields);
        this.keep = KEEP[Math.max(0, Math.min(KEEP.length - 1, keepIndex))];
        if (!enabled) error = "";
    }

    public boolean isLogging() { return sink != null; }
    public File currentFile() { return file; }
    public int rows() { return rows; }
    public int dropped() { return dropped; }

    /** A short problem text (storage almost full, the file could not be written), or an empty text. */
    public String problem() { return error; }

    private static double nz(Double d) { return d == null ? Double.NaN : d; }
    private static double mx(double a, double b) { return Double.isNaN(b) ? a : Double.isNaN(a) ? b : Math.max(a, b); }
    private static double mn(double a, double b) { return Double.isNaN(b) ? a : Double.isNaN(a) ? b : Math.min(a, b); }

    /**
     * Called on every tick with the newest values. Opens a log when the aircraft arms (and logging is on), writes rows while it
     * is armed, and closes the log when it disarms or logging is switched off. Never throws.
     */
    public void update(long nowMs, boolean armed, Map<String, Double> numbers, Map<String, String> texts) {
        try {
            if (sink != null && (!armed || !enabled)) { close(); return; }
            if (sink == null) {
                if (!(armed && enabled)) return;
                if (!open(nowMs, numbers, texts)) return;
            }
            // the summary comes from every value, not only the chosen ones
            maxHeight = mx(maxHeight, nz(numbers.get("height")));
            maxDist = mx(maxDist, nz(numbers.get("dist")));
            minCell = mn(minCell, nz(numbers.get("cell")));
            maxSpeed = mx(maxSpeed, nz(numbers.get("gps_speed")));
            peakCurrent = mx(peakCurrent, nz(numbers.get("curr")));
            double capa = nz(numbers.get("capa"));
            if (!Double.isNaN(capa)) mah = capa;

            int interval = 1000 / rateHz;
            if (rows > 0 && nowMs - lastRowMs < interval - 4) return;
            lastRowMs = nowMs;
            double[] nv = new double[cols.length];
            String[] tv = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (isText[i]) tv[i] = texts.get(cols[i]);
                else nv[i] = nz(numbers.get(cols[i]));
            }
            if (sink.offer(LogFormat.row(nowMs - startMs, cols, isText, nv, tv))) rows++;
            else dropped++;
            if (sink.failed()) { error = sink.failure(); closeQuietly(); }
        } catch (RuntimeException e) {
            error = I18n.t("Logging stopped: {0}", e.getClass().getSimpleName());
            closeQuietly();
        }
    }

    private boolean open(long nowMs, Map<String, Double> numbers, Map<String, String> texts) {
        if (!dir.isDirectory() && !dir.mkdirs()) { error = I18n.t("The log folder cannot be made"); return false; }
        if (dir.getUsableSpace() < MIN_FREE_BYTES) { error = I18n.t("Storage is almost full"); return false; }
        long wallMs = wall.now();
        Map<String, String> meta = new LinkedHashMap<String, String>();
        try { meta.putAll(metaSource.get()); } catch (RuntimeException e) { /* the facts are a bonus */ }
        String aircraft = meta.containsKey("aircraft") ? meta.get("aircraft") : "";
        String safe = aircraft.replaceAll("[^A-Za-z0-9_-]", "").trim();
        if (safe.length() > 24) safe = safe.substring(0, 24);
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date(wallMs));
        String base = "AX-HUD_" + stamp + (safe.isEmpty() ? "" : "_" + safe);
        File f = new File(dir, base + ".csv");
        for (int n = 2; f.exists() && n < 100; n++) f = new File(dir, base + "_" + n + ".csv");

        cols = fields.toArray(new String[0]);
        isText = new boolean[cols.length];
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            isText[i] = cols[i].equals("fm") || texts.containsKey(cols[i]);
            if (isText[i]) { if (text.length() > 0) text.append(','); text.append(cols[i]); }
        }
        try {
            sink = new Sink(f, queueCapacity, startWriter);
        } catch (IOException e) {
            error = I18n.t("The log file cannot be written");
            sink = null;
            return false;
        }
        file = f;
        startMs = nowMs;
        lastRowMs = nowMs;
        rows = 0;
        dropped = 0;
        error = "";
        maxHeight = maxDist = minCell = maxSpeed = peakCurrent = mah = Double.NaN;
        sink.offer("# AX-HUD flight log");
        sink.offer(LogFormat.metaLine("format", Integer.toString(LogFormat.VERSION)));
        sink.offer(LogFormat.metaLine("started", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(wallMs))));
        for (Map.Entry<String, String> e : meta.entrySet()) sink.offer(LogFormat.metaLine(e.getKey(), e.getValue()));
        sink.offer(LogFormat.metaLine("rate_hz", Integer.toString(rateHz)));
        sink.offer(LogFormat.metaLine("text", text.toString()));
        sink.offer(LogFormat.metaLine("note", "This file contains GPS positions."));
        sink.offer(LogFormat.headerRow(cols));
        return true;
    }

    /** Closes the log (disarmed, logging switched off, the screen paused). Safe to call at any time. */
    public void close() {
        if (sink == null) return;
        Map<String, String> end = new LinkedHashMap<String, String>();
        end.put("duration_ms", Long.toString(rows == 0 ? 0 : lastRowMs - startMs));
        end.put("rows", Integer.toString(rows));
        end.put("dropped", Integer.toString(dropped));
        end.put("max_height", LogFormat.fmt("", maxHeight));
        end.put("max_dist", LogFormat.fmt("", maxDist));
        end.put("min_cell", LogFormat.fmt("", minCell));
        end.put("max_speed", LogFormat.fmt("", maxSpeed));
        end.put("peak_current", LogFormat.fmt("", peakCurrent));
        end.put("mah", LogFormat.fmt("", mah));
        Sink s = sink;
        sink = null;
        s.close(LogFormat.endLine(end));
        if (s.failed() && error.isEmpty()) error = s.failure();
        LogStore.prune(dir, keep);
    }

    private void closeQuietly() {
        try { close(); } catch (RuntimeException e) { sink = null; }
    }

    // ------------------------------------------------------------------ the writer thread

    /** Lines go into a queue; one thread writes them, and flushes about once a second, so a crash costs a second at most. */
    static final class Sink implements Runnable {
        private static final String END = new String("end-of-log");
        private final BlockingQueue<String> queue;
        private final BufferedWriter out;
        private final Thread thread;
        private volatile String failure;

        Sink(File f, int capacity, boolean start) throws IOException {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"), 32768);
            queue = new ArrayBlockingQueue<String>(capacity);
            thread = new Thread(this, "ax-log-writer");
            thread.setDaemon(true);
            if (start) thread.start();
        }

        boolean offer(String line) { return queue.offer(line); }
        boolean failed() { return failure != null; }
        String failure() { return failure == null ? "" : failure; }

        void close(String footer) {
            try {
                queue.offer(footer, 100, TimeUnit.MILLISECONDS);
                queue.offer(END, 100, TimeUnit.MILLISECONDS);
                if (thread.isAlive()) thread.join(600);
                else drainHere();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try { out.close(); } catch (IOException e) { if (failure == null) failure = I18n.t("The log file cannot be written"); }
        }

        /** With no writer thread (a test): write what is queued, on this thread. */
        private void drainHere() {
            String s;
            while ((s = queue.poll()) != null) {
                if (s == END) break;
                try { out.write(s); out.write('\n'); } catch (IOException e) { failure = I18n.t("The log file cannot be written"); break; }
            }
        }

        @Override public void run() {
            long lastFlush = System.currentTimeMillis();
            try {
                while (true) {
                    String s = queue.poll(300, TimeUnit.MILLISECONDS);
                    if (s == END) break;
                    if (s != null) { out.write(s); out.write('\n'); }
                    long now = System.currentTimeMillis();
                    if (now - lastFlush >= 1000) { out.flush(); lastFlush = now; }
                }
                out.flush();
            } catch (IOException e) {
                failure = I18n.t("The log file cannot be written");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
