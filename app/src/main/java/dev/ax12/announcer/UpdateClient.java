package dev.ax12.announcer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Downloads over HTTPS and checks what arrived. Plain Java (no Android classes) so it is tested on a PC
 * against a local web server, including redirects like GitHub's.
 * Only https:// addresses are accepted (plain http only for the machine itself, for tests).
 */
public final class UpdateClient {

    public interface Progress {
        void onProgress(long done, long total);
        boolean cancelled();
    }

    /** The user cancelled the download. */
    public static final class Cancelled extends IOException {
        private static final long serialVersionUID = 1L;

        public Cancelled() {
            super(I18n.t("Cancelled"));
        }
    }

    private UpdateClient() {}

    private static void checkUrl(String url) throws IOException {
        if (url.startsWith("https://")) return;
        if (url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")) return;     // tests only
        throw new IOException(I18n.t("Only https:// addresses are allowed"));
    }

    private static HttpURLConnection open(String url, int timeoutMs) throws IOException {
        checkUrl(url);
        URL parsed;
        try {
            parsed = java.net.URI.create(url).toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException(I18n.t("Not a valid address"));
        }
        HttpURLConnection c = (HttpURLConnection) parsed.openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "AX-HUD-updater");
        c.setRequestProperty("Accept", "*/*");
        return c;
    }

    /** A small text file (the update description). */
    public static String fetchText(String url, int timeoutMs, int maxBytes) throws IOException {
        HttpURLConnection c = open(url, timeoutMs);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException(I18n.t("The server answered {0}", code));
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) throw new IOException(I18n.t("The answer is too large"));
                }
                return new String(out.toByteArray(), "UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Downloads to dest. The data goes to dest + ".part" first and is renamed only when the size and the
     * SHA-256 are right, so a broken or cancelled download never leaves something that looks complete.
     */
    public static void download(String url, File dest, long expectedSize, String expectedSha256,
                                int timeoutMs, Progress progress) throws IOException {
        File part = new File(dest.getPath() + ".part");
        part.delete();
        HttpURLConnection c = open(url, timeoutMs);
        boolean ok = false;
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException(I18n.t("The server answered {0}", code));
            long total = c.getContentLengthLong();
            if (total > UpdateInfo.MAX_APK_BYTES || expectedSize > UpdateInfo.MAX_APK_BYTES) throw new IOException(I18n.t("The file is too large"));
            if (expectedSize > 0 && total > 0 && total != expectedSize) throw new IOException(I18n.t("The file has an unexpected size"));
            MessageDigest md = newDigest();
            long done = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (progress != null && progress.cancelled()) throw new Cancelled();
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (done > UpdateInfo.MAX_APK_BYTES) throw new IOException(I18n.t("The file is too large"));
                    if (progress != null) progress.onProgress(done, total > 0 ? total : expectedSize);
                }
                out.getFD().sync();
            }
            if (expectedSize > 0 && done != expectedSize) throw new IOException(I18n.t("The download is incomplete or the wrong size"));
            if (expectedSha256 != null && !hex(md.digest()).equalsIgnoreCase(expectedSha256)) {
                throw new IOException(I18n.t("The checksum does not match: the file is damaged or not the published one"));
            }
            if (dest.exists() && !dest.delete()) throw new IOException(I18n.t("Cannot replace the old file"));
            if (!part.renameTo(dest)) throw new IOException(I18n.t("Cannot save the file"));
            ok = true;
        } finally {
            c.disconnect();
            if (!ok) part.delete();
        }
    }

    // ------------------------------------------------------------------ hashing

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(I18n.t("SHA-256 is not available"));
        }
    }

    public static String sha256Hex(File f) throws IOException {
        MessageDigest md = newDigest();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }
}
