// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** A flight log in the formats other tools read: GPX and KML tracks, CSV, and the report text. Plain Java, tested on a PC. */
public final class LogExport {
    private LogExport() {}

    public static final int GPX = 0, KML = 1, CSV_ALL = 2, CSV_SMALL = 3, REPORT = 4;
    public static final int FORMATS = 5;

    public static final class Result {
        public final String name, mime, text;
        Result(String name, String mime, String text) { this.name = name; this.mime = mime; this.text = text; }
    }

    public static String extension(int format) {
        switch (format) {
            case GPX: return ".gpx";
            case KML: return ".kml";
            case REPORT: return ".txt";
            default: return ".csv";
        }
    }

    /**
     * @param baseName the log's file name without the extension
     * @param noGps    the track is moved so that the take-off is at 0, 0 (the shape stays, where it was does not); CSV and the report leave the positions out
     * @param zone     the zone the log's start time was written in (the radio's own)
     */
    public static Result export(FlightLog log, int format, String baseName, boolean noGps, double capacityMah, TimeZone zone) {
        String base = baseName == null || baseName.isEmpty() ? "AX-HUD" : baseName;
        switch (format) {
            case GPX: return new Result(base + ".gpx", "application/gpx+xml", gpx(log, noGps, zone));
            case KML: return new Result(base + ".kml", "application/vnd.google-earth.kml+xml", kml(log, noGps));
            case CSV_ALL: return new Result(base + "_all.csv", "text/csv", csv(log, null, noGps));
            case CSV_SMALL: return new Result(base + "_small.csv", "text/csv", csv(log, new String[]{"gps_lat", "gps_lon", "height", "gps_speed", "vbat", "curr", "capa"}, noGps));
            default: return new Result(base + ".txt", "text/plain", FlightSummary.build(log, capacityMah, 120).reportText());
        }
    }

    private static boolean valid(double lat, double lon) { return !Double.isNaN(lat) && !Double.isNaN(lon) && GeoMath.validFix(lat, lon); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private static String num(double v, int decimals) { return Double.isNaN(v) ? "" : String.format(Locale.US, "%." + decimals + "f", v); }

    /** The start of the log as a time in UTC, for the GPX points; the epoch of the file when it cannot be read. */
    private static long startUtcMs(FlightLog log, TimeZone zone) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setTimeZone(zone);
            return f.parse(log.meta("started", "")).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String gpx(FlightLog log, boolean shift, TimeZone zone) {
        int cLat = log.indexOf("gps_lat"), cLon = log.indexOf("gps_lon"), cAlt = log.indexOf("gps_alt");
        double[] h = Manoeuvres.heights(log);
        long t0 = startUtcMs(log, zone);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"AX-HUD\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<trk><name>").append(esc(log.meta("aircraft", "AX-HUD flight"))).append("</name><trkseg>\n");
        double lat0 = Double.NaN, lon0 = Double.NaN, lastLat = Double.NaN, lastLon = Double.NaN;
        if (cLat >= 0 && cLon >= 0) {
            for (int r = 0; r < log.rows; r++) {
                double lat = log.value(r, cLat), lon = log.value(r, cLon);
                if (!valid(lat, lon)) continue;
                if (Double.isNaN(lat0)) { lat0 = lat; lon0 = lon; }
                if (lat == lastLat && lon == lastLon) continue;
                lastLat = lat; lastLon = lon;
                double alt = cAlt >= 0 ? log.value(r, cAlt) : Double.NaN;
                if (Double.isNaN(alt)) alt = h[r];
                double outLat = shift ? lat - lat0 : lat, outLon = shift ? lon - lon0 : lon;
                sb.append("<trkpt lat=\"").append(num(outLat, 7)).append("\" lon=\"").append(num(outLon, 7)).append("\">");
                if (!Double.isNaN(alt)) sb.append("<ele>").append(num(alt, 1)).append("</ele>");
                if (t0 > 0) sb.append("<time>").append(iso.format(new Date(t0 + log.time[r]))).append("</time>");
                sb.append("</trkpt>\n");
            }
        }
        sb.append("</trkseg></trk>\n</gpx>\n");
        return sb.toString();
    }

    private static String kml(FlightLog log, boolean shift) {
        int cLat = log.indexOf("gps_lat"), cLon = log.indexOf("gps_lon");
        double[] h = Manoeuvres.heights(log);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n");
        sb.append("<Style id=\"t\"><LineStyle><color>fff7c34f</color><width>3</width></LineStyle><PolyStyle><color>554fc3f7</color></PolyStyle></Style>\n");
        sb.append("<Placemark><name>").append(esc(log.meta("aircraft", "AX-HUD flight"))).append("</name><styleUrl>#t</styleUrl>");
        sb.append("<LineString><extrude>1</extrude><tessellate>1</tessellate><altitudeMode>relativeToGround</altitudeMode><coordinates>\n");
        double lat0 = Double.NaN, lon0 = Double.NaN, lastLat = Double.NaN, lastLon = Double.NaN;
        if (cLat >= 0 && cLon >= 0) {
            for (int r = 0; r < log.rows; r++) {
                double lat = log.value(r, cLat), lon = log.value(r, cLon);
                if (!valid(lat, lon)) continue;
                if (Double.isNaN(lat0)) { lat0 = lat; lon0 = lon; }
                if (lat == lastLat && lon == lastLon) continue;
                lastLat = lat; lastLon = lon;
                double up = Double.isNaN(h[r]) ? 0 : h[r];
                sb.append(num(shift ? lon - lon0 : lon, 7)).append(',').append(num(shift ? lat - lat0 : lat, 7)).append(',').append(num(up, 1)).append('\n');
            }
        }
        sb.append("</coordinates></LineString></Placemark>\n</Document></kml>\n");
        return sb.toString();
    }

    /** @param only the columns to keep (null = all); a column the log does not have is left out */
    private static String csv(FlightLog log, String[] only, boolean noGps) {
        java.util.ArrayList<Integer> cols = new java.util.ArrayList<Integer>();
        if (only == null) {
            for (int c = 0; c < log.columnCount(); c++) cols.add(c);
        } else {
            for (String n : only) { int c = log.indexOf(n); if (c >= 0) cols.add(c); }
        }
        if (noGps) {
            java.util.ArrayList<Integer> keep = new java.util.ArrayList<Integer>();
            for (int c : cols) { String n = log.columns[c]; if (!n.equals("gps_lat") && !n.equals("gps_lon") && !n.equals("gps_alt")) keep.add(c); }
            cols = keep;
        }
        StringBuilder sb = new StringBuilder("t_s");
        for (int c : cols) sb.append(',').append(log.columns[c]);
        sb.append('\n');
        for (int r = 0; r < log.rows; r++) {
            sb.append(num(log.time[r] / 1000.0, 3));
            for (int c : cols) {
                sb.append(',');
                if (log.isText[c]) { String w = log.word(r, c); sb.append(w == null ? "" : w.replace(',', ' ')); }
                else sb.append(num(log.value(r, c), 3));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
