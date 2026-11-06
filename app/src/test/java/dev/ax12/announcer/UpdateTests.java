package dev.ax12.announcer;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Tests for the update description and the downloader, against a local web server. */
public class UpdateTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static String good(String extra) {
        return "{\"versionCode\":1042,\"versionName\":\"0.7.0\",\"channel\":\"dev\",\"apk\":\"ax-hud.apk\","
             + "\"sha256\":\"" + "ab".repeat(32) + "\",\"size\":31457280,\"notes\":\"line one\\nline \\\"two\\\"\"" + extra + "}";
    }

    public static void main(String[] args) throws Exception {
        // ---------- update.json ----------
        UpdateInfo u = UpdateInfo.parse(good(""));
        check(u != null && u.versionCode == 1042 && u.versionName.equals("0.7.0") && u.channel.equals("dev"), "a good description is read");
        check(u != null && u.apk.equals("ax-hud.apk") && u.size == 31457280L && u.sha256.equals("ab".repeat(32)), "file, size and checksum are read");
        check(u != null && u.notes.equals("line one\nline \"two\""), "notes with escapes: " + (u == null ? null : u.notes));
        check(u != null && u.isNewerThan(1041) && !u.isNewerThan(1042) && !u.isNewerThan(2000), "newer means a higher version code");
        check(UpdateInfo.parse(good(",\"extra\":{\"a\":[1,2,{\"b\":\"}\"}]},\"flag\":true,\"n\":null")) != null, "unknown and nested fields are skipped");
        check(UpdateInfo.parse("  " + good("") + "\n") != null, "whitespace around is fine");
        check(UpdateInfo.parse(good("").replace("\"sha256\":\"" + "ab".repeat(32) + "\"", "\"sha256\":\"" + "AB".repeat(32) + "\"")).sha256.equals("ab".repeat(32)), "an upper-case checksum is accepted and normalised");
        String[] bad = {
            null, "", "[]", "{", "{\"versionCode\":1}", "not json",
            good("").replace("1042", "0"), good("").replace("1042", "-5"), good("").replace("1042", "12.5"), good("").replace("1042", "\"1042\""),
            good("").replace("ax-hud.apk", "../ax-hud.apk"), good("").replace("ax-hud.apk", "https://evil.example/x.apk"), good("").replace("ax-hud.apk", "a/b.apk"),
            good("").replace("ax-hud.apk", "ax-hud.exe"), good("").replace("ab".repeat(32), "zz".repeat(32)), good("").replace("ab".repeat(32), "abcd"),
            good("").replace("31457280", "0"), good("").replace("31457280", "999999999999"), good("").replace("\"0.7.0\"", "\"\""),
            good("").replace("\"0.7.0\"", "\"" + "x".repeat(60) + "\""), good("") + " trailing", good("").replace("}", ""),
        };
        int accepted = 0; StringBuilder which = new StringBuilder();
        for (int i = 0; i < bad.length; i++) if (UpdateInfo.parse(bad[i]) != null) { accepted++; which.append(i).append(' '); }
        check(accepted == 0, "every malformed or unsafe description is refused (wrongly accepted: " + which + ")");

        // ---------- the downloader against a local server ----------
        byte[] apk = new byte[3 * 1024 * 1024 + 17];
        new Random(42).nextBytes(apk);
        String sha = UpdateClient.hex(java.security.MessageDigest.getInstance("SHA-256").digest(apk));
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicLong served = new AtomicLong();
        srv.createContext("/update.json", ex -> { byte[] b = good("").getBytes("UTF-8"); ex.sendResponseHeaders(200, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
        srv.createContext("/huge.txt", ex -> { ex.sendResponseHeaders(200, 0); try (OutputStream o = ex.getResponseBody()) { byte[] c = new byte[8192]; for (int i = 0; i < 100; i++) o.write(c); } catch (IOException e) { } });
        srv.createContext("/latest/download/ax-hud.apk", ex -> { ex.getResponseHeaders().add("Location", "/assets/blob/ax-hud.apk"); ex.sendResponseHeaders(302, -1); ex.close(); });
        srv.createContext("/assets/blob/ax-hud.apk", ex -> { ex.sendResponseHeaders(200, apk.length); try (OutputStream o = ex.getResponseBody()) { int off = 0; while (off < apk.length) { int n = Math.min(65536, apk.length - off); o.write(apk, off, n); off += n; served.addAndGet(n); } } catch (IOException e) { } });
        srv.createContext("/missing", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        srv.createContext("/short.apk", ex -> { byte[] b = new byte[1000]; ex.sendResponseHeaders(200, b.length); try (OutputStream o = ex.getResponseBody()) { o.write(b); } });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        File dir = Files.createTempDirectory("axupd").toFile();

        String text = UpdateClient.fetchText(base + "/update.json", 5000, 100000);
        check(UpdateInfo.parse(text) != null && UpdateInfo.parse(text).versionCode == 1042, "update.json is fetched and read");
        try { UpdateClient.fetchText(base + "/missing", 5000, 1000); check(false, "a 404 must fail"); } catch (IOException e) { check(e.getMessage().contains("404"), "a 404 is reported: " + e.getMessage()); }
        try { UpdateClient.fetchText(base + "/huge.txt", 5000, 1000); check(false, "a huge answer must fail"); } catch (IOException e) { check(e.getMessage().contains("too large"), "a huge answer is stopped: " + e.getMessage()); }
        try { UpdateClient.fetchText("http://example.com/update.json", 2000, 1000); check(false, "plain http must be refused"); } catch (IOException e) { check(e.getMessage().contains("https"), "plain http is refused"); }
        try { UpdateClient.fetchText("ftp://example.com/x", 2000, 1000); check(false, "ftp must be refused"); } catch (IOException e) { check(e.getMessage().contains("https"), "other schemes are refused"); }
        try { UpdateClient.fetchText("http://127.0.0.1.evil.example:80/x", 2000, 1000); check(false, "lookalike host must be refused"); } catch (IOException e) { check(e.getMessage().contains("https"), "a lookalike host is refused"); }

        File out = new File(dir, "ax-hud.apk");
        final long[] last = {0}; final boolean[] mono = {true}; final long[] totalSeen = {0};
        UpdateClient.download(base + "/latest/download/ax-hud.apk", out, apk.length, sha, 5000, new UpdateClient.Progress() {
            public void onProgress(long done, long total) { if (done < last[0]) mono[0] = false; last[0] = done; totalSeen[0] = total; }
            public boolean cancelled() { return false; }
        });
        check(out.exists() && out.length() == apk.length && UpdateClient.sha256Hex(out).equals(sha), "a redirected download arrives complete and identical");
        check(!new File(out.getPath() + ".part").exists(), "no .part file is left behind");
        check(mono[0] && last[0] == apk.length && totalSeen[0] == apk.length, "progress counts up to the full size");

        // wrong checksum
        File out2 = new File(dir, "bad-hash.apk");
        try { UpdateClient.download(base + "/latest/download/ax-hud.apk", out2, apk.length, "00".repeat(32), 5000, null); check(false, "wrong checksum must fail"); }
        catch (IOException e) { check(e.getMessage().contains("checksum"), "a wrong checksum is caught: " + e.getMessage()); }
        check(!out2.exists() && !new File(out2.getPath() + ".part").exists(), "a file with a wrong checksum is deleted");
        // wrong size
        File out3 = new File(dir, "bad-size.apk");
        try { UpdateClient.download(base + "/latest/download/ax-hud.apk", out3, apk.length + 5, sha, 5000, null); check(false, "wrong size must fail"); }
        catch (IOException e) { check(e.getMessage().contains("size"), "a wrong size is caught: " + e.getMessage()); }
        check(!out3.exists(), "and nothing is saved");
        // truncated answer (the server says 1000 bytes; we expected 3 MB)
        try { UpdateClient.download(base + "/short.apk", new File(dir, "short.apk"), apk.length, sha, 5000, null); check(false, "a short file must fail"); }
        catch (IOException e) { check(!new File(dir, "short.apk").exists(), "a short file is refused: " + e.getMessage()); }
        // cancel part-way
        File out4 = new File(dir, "cancel.apk");
        try {
            UpdateClient.download(base + "/latest/download/ax-hud.apk", out4, apk.length, sha, 5000, new UpdateClient.Progress() {
                public void onProgress(long done, long total) {}
                public boolean cancelled() { return served.get() > 100000; }
            });
            check(false, "cancel must stop the download");
        } catch (IOException e) { check(e.getMessage().equals("Cancelled"), "cancelling stops the download: " + e.getMessage()); }
        check(!out4.exists() && !new File(out4.getPath() + ".part").exists(), "a cancelled download leaves nothing");
        // a 404 on the file
        try { UpdateClient.download(base + "/missing", new File(dir, "x.apk"), apk.length, sha, 5000, null); check(false, "404 must fail"); } catch (IOException e) { check(e.getMessage().contains("404"), "a missing file is reported"); }
        // replacing an older download
        Files.write(out.toPath(), new byte[]{1, 2, 3});
        UpdateClient.download(base + "/latest/download/ax-hud.apk", out, apk.length, sha, 5000, null);
        check(out.length() == apk.length && UpdateClient.sha256Hex(out).equals(sha), "a new download replaces an old file");
        // no expected values: still downloads (the caller decides)
        File out5 = new File(dir, "nocheck.apk");
        UpdateClient.download(base + "/latest/download/ax-hud.apk", out5, 0, null, 5000, null);
        check(out5.length() == apk.length, "downloading without expectations still works");
        srv.stop(0);

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
