package com.zuobiao.browser;

import android.net.Uri;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** URL 分类与规范化，网络层与 DOM 层共用。 */
final class MediaSniffer {

    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
            "mp4", "m4v", "m3u8", "mpd", "webm", "mkv", "flv", "mov", "ts", "3gp", "avi"));
    private static final Set<String> AUDIO = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "wma"));
    private static final Set<String> IMAGE = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "ico"));
    private static final Set<String> FILE = new HashSet<>(Arrays.asList(
            "pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "epub", "mobi", "torrent", "exe", "dmg"));

    private MediaSniffer() {}

    static String extOf(String url) {
        if (url == null) return "";
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#');
        if (h >= 0) path = path.substring(0, h);
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot < 0 || dot < slash) return "";
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.length() > 6 ? "" : ext;
    }

    static int typeByExt(String ext) {
        if (VIDEO.contains(ext)) return ResourceItem.VIDEO;
        if (AUDIO.contains(ext)) return ResourceItem.AUDIO;
        if (IMAGE.contains(ext)) return ResourceItem.IMAGE;
        if (FILE.contains(ext)) return ResourceItem.FILE;
        return -1;
    }

    /** 网络层：只保留可识别的媒体/文件，图片与广告请求不在这里处理。 */
    static ResourceItem fromNetwork(String url) {
        if (url == null) return null;
        String low = url.toLowerCase(Locale.ROOT);
        if (!low.startsWith("http")) return null;
        int type = typeByExt(extOf(url));
        if (type < 0 || type == ResourceItem.IMAGE) return null;
        ResourceItem it = new ResourceItem();
        it.url = url;
        it.type = type;
        it.name = nameOf(url);
        it.fromDom = false;
        return it;
    }

    /** DOM 层：从注入脚本回传的 JSON 构造。 */
    static ResourceItem fromDom(JSONObject o) {
        String u = o.optString("u");
        if (u == null || u.isEmpty()) return null;
        String jsType = o.optString("t");
        String ext = extOf(u);
        int type = typeByExt(ext);
        if ("video".equals(jsType)) type = ResourceItem.VIDEO;
        else if ("audio".equals(jsType)) type = ResourceItem.AUDIO;
        else if ("image".equals(jsType)) type = ResourceItem.IMAGE;
        else if ("file".equals(jsType) && type < 0) type = ResourceItem.FILE;
        if (type < 0) return null;

        ResourceItem it = new ResourceItem();
        it.url = u;
        it.type = type;
        it.name = o.optString("n");
        if (it.name == null || it.name.isEmpty()) it.name = nameOf(u);
        it.duration = o.optLong("d", -1);
        it.width = o.optInt("w", -1);
        it.height = o.optInt("h", -1);
        it.poster = o.optString("p");
        it.fromDom = true;
        it.blob = u.startsWith("blob:");
        return it;
    }

    static String nameOf(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (path == null || path.isEmpty()) return hostOf(url);
            if (path.length() > 80) path = path.substring(0, 80);
            return path;
        } catch (Exception e) {
            return url;
        }
    }

    static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    static String humanSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.ROOT, "%.2f GB", bytes / 1073741824.0);
    }

    static String humanDuration(long ms) {
        if (ms <= 0) return "";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.ROOT, "%d:%02d", m, sec);
    }
}
