package com.zuobiao.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * m3u8 下载器：拉取分片并按顺序合并。
 *
 * 支持：master playlist（选最高码率）、#EXT-X-KEY: AES-128、#EXT-X-MAP 初始化段。
 * 输出：.ts（或含 MAP 时的 .mp4），落到「下载/Zuobiao」。
 * 限制：不做 DRM（SAMPLE-AES / Widevine），不做断点续传；单任务串行。
 */
final class HlsDownloader {

    private static final String CHANNEL = "zb_download";
    private static final int NOTI_ID = 0xB501;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static final Pattern BANDWIDTH = Pattern.compile("BANDWIDTH=(\\d+)");
    private static final Pattern KEY_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern KEY_IV = Pattern.compile("IV=0x([0-9A-Fa-f]+)");
    private static final Pattern KEY_METHOD = Pattern.compile("METHOD=([A-Z0-9-]+)");

    private HlsDownloader() {}

    interface Callback {
        void done(boolean ok, String msg);
    }

    static boolean isBusy() { return RUNNING.get(); }

    static void start(final Context c, final String url, final String referer,
                      final String ua, final String cookie, final Callback cb) {
        if (!RUNNING.compareAndSet(false, true)) {
            if (cb != null) cb.done(false, "已有下载任务在进行");
            return;
        }
        new Thread(() -> {
            String msg = "";
            boolean ok = false;
            try {
                File out = download(c, url, referer, ua, cookie);
                long size = out.length();
                String[] saved = export(c, out);
                App.get().prefs().addLocalDownload(saved[0], saved[1], size);
                ok = true;
                msg = "已保存到 " + saved[0];
            } catch (Exception e) {
                msg = "下载失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                RUNNING.set(false);
                notifyDone(c, ok, msg);
            }
            final boolean okf = ok;
            final String msgf = msg;
            if (cb != null) cb.done(okf, msgf);
        }, "zb-hls").start();
    }

    // ---------------- 主流程 ----------------

    private static File download(Context c, String pageUrl, String referer,
                                 String ua, String cookie) throws Exception {
        String playlistUrl = pageUrl;
        String text = httpGet(playlistUrl, referer, ua, cookie);
        if (text == null) throw new IOException("无法获取 m3u8");

        // master playlist → 选最高码率
        if (text.contains("#EXT-X-STREAM-INF")) {
            String best = null;
            long bestBw = -1;
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.startsWith("#EXT-X-STREAM-INF")) continue;
                long bw = 0;
                Matcher m = BANDWIDTH.matcher(line);
                if (m.find()) bw = Long.parseLong(m.group(1));
                for (int j = i + 1; j < lines.length; j++) {
                    String cand = lines[j].trim();
                    if (cand.isEmpty() || cand.startsWith("#")) continue;
                    if (bw > bestBw) { bestBw = bw; best = cand; }
                    break;
                }
            }
            if (best == null) throw new IOException("master playlist 里没找到流");
            playlistUrl = new URL(new URL(pageUrl), best).toString();
            text = httpGet(playlistUrl, referer, ua, cookie);
            if (text == null) throw new IOException("无法获取子播放列表");
        }

        // 逐行解析：密钥按 #EXT-X-KEY 切换，每个分片记住自己用的 key/iv
        final class Seg {
            String url;
            byte[] key;
            byte[] iv;
            long seq;
        }
        List<Seg> segs = new ArrayList<>();
        Map<String, byte[]> keyCache = new HashMap<>();
        String mapUrl = null;
        byte[] curKey = null;
        byte[] curIv = null;
        long seqNo = 0;
        String baseUrl = playlistUrl;

        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-KEY")) {
                Matcher mm = KEY_METHOD.matcher(line);
                String method = mm.find() ? mm.group(1) : "NONE";
                if ("AES-128".equals(method)) {
                    Matcher mu = KEY_URI.matcher(line);
                    String ku = mu.find() ? new URL(new URL(baseUrl), mu.group(1)).toString() : null;
                    curKey = null;
                    if (ku != null) {
                        if (keyCache.containsKey(ku)) {
                            curKey = keyCache.get(ku);
                        } else {
                            curKey = httpGetBytes(ku, referer, ua, cookie);
                            keyCache.put(ku, curKey);
                        }
                    }
                    Matcher mi = KEY_IV.matcher(line);
                    curIv = mi.find() ? hexToBytes(mi.group(1)) : null;
                } else {
                    curKey = null;
                    curIv = null;
                }
                continue;
            }
            if (line.startsWith("#EXT-X-MAP")) {
                Matcher mu = KEY_URI.matcher(line);
                if (mu.find()) mapUrl = new URL(new URL(baseUrl), mu.group(1)).toString();
                continue;
            }
            if (line.startsWith("#")) continue;

            Seg seg = new Seg();
            seg.url = new URL(new URL(baseUrl), line).toString();
            seg.key = curKey;
            seg.iv = curIv;
            seg.seq = seqNo++;
            segs.add(seg);
        }
        if (segs.isEmpty()) throw new IOException("播放列表里没有分片");

        File out = new File(c.getCacheDir(), "hls_" + System.currentTimeMillis()
                + (mapUrl != null ? ".mp4" : ".ts"));
        int total = segs.size();
        notifyProgress(c, 0, total, "准备下载");

        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (mapUrl != null) {
                byte[] init = httpGetBytes(mapUrl, referer, ua, cookie);
                if (init != null) fos.write(init);
            }
            for (int i = 0; i < total; i++) {
                Seg seg = segs.get(i);
                byte[] data = httpGetBytes(seg.url, referer, ua, cookie);
                if (data == null) throw new IOException("分片 " + (i + 1) + " 下载失败");
                if (seg.key != null) {
                    byte[] useIv = seg.iv;
                    if (useIv == null) {
                        useIv = new byte[16];
                        long n = seg.seq;
                        for (int k = 15; k >= 0; k--) {
                            useIv[k] = (byte) (n & 0xFF);
                            n >>= 8;
                        }
                    }
                    data = aesDecrypt(data, seg.key, useIv);
                }
                fos.write(data);
                if (i % 3 == 0 || i == total - 1) notifyProgress(c, i + 1, total, "下载中");
            }
        }
        return out;
    }

    // ---------------- 导出到下载目录 ----------------

    private static String[] export(Context c, File src) throws Exception {
        String name = "zuobiao-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new java.util.Date())
                + (src.getName().endsWith(".mp4") ? ".mp4" : ".ts");

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE,
                    name.endsWith(".mp4") ? "video/mp4" : "video/mp2t");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Zuobiao");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("无法创建下载文件");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri);
                 InputStream is = new java.io.FileInputStream(src)) {
                copy(is, os);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, done, null, null);
            src.delete();
            return new String[]{"下载/Zuobiao/" + name, uri.toString()};
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "Zuobiao");
        if (!dir.exists()) dir.mkdirs();
        File dst = new File(dir, name);
        try (OutputStream os = new FileOutputStream(dst);
             InputStream is = new java.io.FileInputStream(src)) {
            copy(is, os);
        }
        src.delete();
        MediaScannerConnection.scanFile(c, new String[]{dst.getAbsolutePath()},
                new String[]{"video/mp2t"}, null);
        return new String[]{dst.getAbsolutePath(), dst.getAbsolutePath()};
    }

    // ---------------- 网络 ----------------

    private static String httpGet(String url, String referer, String ua, String cookie)
            throws IOException {
        byte[] b = httpGetBytes(url, referer, ua, cookie);
        return b == null ? null : new String(b, "UTF-8");
    }

    private static byte[] httpGetBytes(String url, String referer, String ua, String cookie)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", ua == null || ua.isEmpty()
                ? "Mozilla/5.0 ZuobiaoBrowser/1.2" : ua);
        if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);
        if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) return null;
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            copy(in, bos);
            return bos.toByteArray();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    // ---------------- 解密 ----------------

    private static byte[] aesDecrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    // ---------------- 通知 ----------------

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "视频下载",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private static void notifyProgress(Context c, int done, int total, String phase) {
        try {
            ensureChannel(c);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_dl)
                    .setContentTitle("正在下载视频")
                    .setContentText(phase + " " + done + "/" + total)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(Math.max(1, total), done, false);
            nm.notify(NOTI_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    private static void notifyDone(Context c, boolean ok, String msg) {
        try {
            ensureChannel(c);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            PendingIntent pi = PendingIntent.getActivity(c, 0,
                    new Intent(c, DownloadsActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_dl)
                    .setContentTitle(ok ? "视频下载完成" : "视频下载失败")
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            nm.notify(NOTI_ID, b.build());
        } catch (Exception ignored) {
        }
    }
}
