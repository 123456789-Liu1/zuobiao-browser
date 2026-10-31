package com.zuobiao.browser;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 全部设置项集中在这里，SharedPreferences 单一入口。 */
final class Prefs {

    static final String[] ENGINES = {"百度", "必应", "Google", "DuckDuckGo", "搜狗", "360 搜索"};
    static final String[] ENGINE_URLS = {
            "https://www.baidu.com/s?wd=%s",
            "https://cn.bing.com/search?q=%s",
            "https://www.google.com/search?q=%s",
            "https://duckduckgo.com/?q=%s",
            "https://www.sogou.com/web?query=%s",
            "https://www.so.com/s?q=%s"
    };

    private final SharedPreferences sp;

    // 热点开关缓存：shouldInterceptRequest 每个请求都会读，避免反复走 SharedPreferences
    private volatile boolean cNight, cAdblock, cSniff, cAdSkip, cSafe;

    Prefs(Context c) {
        sp = c.getSharedPreferences("zb", Context.MODE_PRIVATE);
        cNight = sp.getBoolean("night", false);
        cAdblock = sp.getBoolean("adblock", true);
        cSniff = sp.getBoolean("sniff", true);
        cAdSkip = sp.getBoolean("ad_skip", true);
        cSafe = sp.getBoolean("safe_mode", false);
    }

    int engine() { return sp.getInt("engine", 0); }
    void engine(int v) { sp.edit().putInt("engine", v).apply(); }

    String home() { return sp.getString("home", "about:blank"); }
    void home(String v) { sp.edit().putString("home", v).apply(); }

    boolean night() { return cNight; }
    void night(boolean v) { cNight = v; sp.edit().putBoolean("night", v).apply(); }

    boolean adblock() { return cAdblock; }
    void adblock(boolean v) { cAdblock = v; sp.edit().putBoolean("adblock", v).apply(); }

    long blocked() { return sp.getLong("blocked", 0); }
    void blocked(long v) { sp.edit().putLong("blocked", v).apply(); }

    /** 0 = 手机 UA，1 = 桌面 UA，2 = 自定义 */
    int uaMode() { return sp.getInt("ua", 0); }
    void uaMode(int v) { sp.edit().putInt("ua", v).apply(); }

    String customUa() { return sp.getString("custom_ua", ""); }
    void customUa(String v) { sp.edit().putString("custom_ua", v).apply(); }

    Set<String> desktopSites() {
        return new HashSet<>(sp.getStringSet("desktop_sites", Collections.emptySet()));
    }

    boolean desktopFor(String host) { return host != null && desktopSites().contains(host); }

    void setDesktopFor(String host, boolean on) {
        Set<String> s = desktopSites();
        if (on) s.add(host); else s.remove(host);
        sp.edit().putStringSet("desktop_sites", s).apply();
    }

    Set<String> allowSites() {
        return new HashSet<>(sp.getStringSet("adblock_allow", Collections.emptySet()));
    }

    boolean adblockAllowed(String host) { return host != null && allowSites().contains(host); }

    void setAdblockAllowed(String host, boolean on) {
        Set<String> s = allowSites();
        if (on) s.add(host); else s.remove(host);
        sp.edit().putStringSet("adblock_allow", s).apply();
    }

    void setAllowSites(Set<String> s) { sp.edit().putStringSet("adblock_allow", s).apply(); }

    String subscriptions() { return sp.getString("subs", "[]"); }
    void subscriptions(String json) { sp.edit().putString("subs", json).apply(); }

    Set<String> userBlock() {
        return new HashSet<>(sp.getStringSet("user_block", Collections.emptySet()));
    }

    void setUserBlock(Set<String> s) { sp.edit().putStringSet("user_block", s).apply(); }

    int readerSize() { return sp.getInt("reader_size", 18); }
    void readerSize(int v) { sp.edit().putInt("reader_size", v).apply(); }

    int textZoom() { return sp.getInt("zoom", 100); }
    void textZoom(int v) { sp.edit().putInt("zoom", v).apply(); }

    boolean gestures() { return sp.getBoolean("gestures", true); }
    void gestures(boolean v) { sp.edit().putBoolean("gestures", v).apply(); }

    boolean restore() { return sp.getBoolean("restore", true); }
    void restore(boolean v) { sp.edit().putBoolean("restore", v).apply(); }

    String session() { return sp.getString("session", ""); }
    void session(String v) { sp.edit().putString("session", v).apply(); }

    // ---- 嗅探与播放 ----
    boolean sniff() { return cSniff; }
    void sniff(boolean v) { cSniff = v; sp.edit().putBoolean("sniff", v).apply(); }

    boolean sniffFab() { return sp.getBoolean("sniff_fab", true); }
    void sniffFab(boolean v) { sp.edit().putBoolean("sniff_fab", v).apply(); }

    boolean adSkip() { return cAdSkip; }
    void adSkip(boolean v) { cAdSkip = v; sp.edit().putBoolean("ad_skip", v).apply(); }

    boolean cinemaAuto() { return sp.getBoolean("cinema_auto", false); }
    void cinemaAuto(boolean v) { sp.edit().putBoolean("cinema_auto", v).apply(); }

    // ---- 网络：加载救援 / 省流 / 代理 ----
    boolean rescue() { return sp.getBoolean("rescue", true); }
    void rescue(boolean v) { sp.edit().putBoolean("rescue", v).apply(); }

    boolean liteMode() { return sp.getBoolean("lite", false); }
    void liteMode(boolean v) { sp.edit().putBoolean("lite", v).apply(); }

    boolean proxyEnabled() { return sp.getBoolean("proxy_on", false); }
    void proxyEnabled(boolean v) { sp.edit().putBoolean("proxy_on", v).apply(); }

    /** http 或 socks */
    String proxyType() { return sp.getString("proxy_type", "http"); }
    String proxyHost() { return sp.getString("proxy_host", ""); }
    int proxyPort() { return sp.getInt("proxy_port", 0); }
    String proxyUser() { return sp.getString("proxy_user", ""); }
    String proxyPass() { return sp.getString("proxy_pass", ""); }
    String proxyBypass() { return sp.getString("proxy_bypass", ""); }

    void proxy(String type, String host, int port, String user, String pass, String bypass) {
        sp.edit().putString("proxy_type", type)
                .putString("proxy_host", host)
                .putInt("proxy_port", port)
                .putString("proxy_user", user)
                .putString("proxy_pass", pass)
                .putString("proxy_bypass", bypass)
                .apply();
    }

    String proxyRule() {
        String host = proxyHost();
        if (host == null || host.isEmpty() || proxyPort() <= 0) return "";
        return proxyType() + "://" + host + ":" + proxyPort();
    }

    // ---- 安全模式（严格） ----
    boolean safeMode() { return cSafe; }

    void safeMode(boolean v) {
        cSafe = v;
        sp.edit().putBoolean("safe_mode", v).apply();
    }

    Set<String> safeAllow() {
        return new HashSet<>(sp.getStringSet("safe_allow", Collections.emptySet()));
    }

    void setSafeAllow(Set<String> s) { sp.edit().putStringSet("safe_allow", s).apply(); }

    void setSafeAllow(String host, boolean allow) {
        if (host == null || host.isEmpty()) return;
        Set<String> s = safeAllow();
        if (allow) s.add(host.toLowerCase(java.util.Locale.ROOT));
        else s.remove(host.toLowerCase(java.util.Locale.ROOT));
        setSafeAllow(s);
    }

    boolean safeAllowedFor(String host) {
        if (host == null) return false;
        Set<String> s = safeAllow();
        if (s.contains(host)) return true;
        int idx = 0, guard = 0;
        while ((idx = host.indexOf('.', idx)) >= 0 && guard++ < 3) {
            if (s.contains(host.substring(idx + 1))) return true;
            idx++;
        }
        return false;
    }

    // ---- 本地（HLS）下载记录 ----
    String localDownloads() { return sp.getString("local_dl", "[]"); }

    void addLocalDownload(String name, String uri, long size) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(localDownloads());
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", name);
            o.put("uri", uri);
            o.put("size", size);
            o.put("time", System.currentTimeMillis());
            arr.put(o);
            sp.edit().putString("local_dl", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void removeLocalDownload(String uri) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(localDownloads());
            org.json.JSONArray out = new org.json.JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!uri.equals(arr.getJSONObject(i).optString("uri"))) out.put(arr.getJSONObject(i));
            }
            sp.edit().putString("local_dl", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---- WebDAV 备份 ----
    String webdavUrl() { return sp.getString("wd_url", ""); }
    String webdavUser() { return sp.getString("wd_user", ""); }
    String webdavPass() { return sp.getString("wd_pass", ""); }
    String webdavName() { return sp.getString("wd_name", "zuobiao-backup.json"); }

    void webdav(String url, String user, String pass, String name) {
        sp.edit().putString("wd_url", url)
                .putString("wd_user", user)
                .putString("wd_pass", pass)
                .putString("wd_name", name)
                .apply();
    }

    String searchUrl(String q) {
        int i = Math.min(Math.max(engine(), 0), ENGINE_URLS.length - 1);
        return String.format(ENGINE_URLS[i], android.net.Uri.encode(q));
    }

    String ua(Context c) {
        switch (uaMode()) {
            case 1:
                return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
            case 2:
                String s = customUa();
                return s.isEmpty() ? null : s;
            default:
                return null;
        }
    }

    void clearAll(Context c) {
        sp.edit().clear().apply();
        c.getSharedPreferences("zb_scripts", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
