package com.zuobiao.browser;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * WebDAV 备份 / 恢复（单文件 JSON）。
 * 只做「整包上传 / 整包恢复」，不做增量与冲突合并；密码按用户要求在本地明文保存。
 */
final class WebDavBackup {

    interface Cb { void done(boolean ok, String msg); }

    private WebDavBackup() {}

    // ---------------- 组装 ----------------

    static String buildJson(Context c) throws Exception {
        App app = App.get();
        JSONObject root = new JSONObject();
        root.put("app", "zuobiao");
        root.put("version", 2);
        root.put("time", System.currentTimeMillis());

        JSONArray bm = new JSONArray();
        for (Db.Item it : app.db().bookmarks()) {
            JSONObject o = new JSONObject();
            o.put("title", it.title);
            o.put("url", it.url);
            bm.put(o);
        }
        root.put("bookmarks", bm);

        JSONArray sc = new JSONArray();
        for (ScriptManager.Script s : app.scripts().all()) {
            JSONObject o = new JSONObject();
            o.put("name", s.name);
            o.put("pattern", s.pattern);
            o.put("code", s.code);
            o.put("enabled", s.enabled);
            sc.put(o);
        }
        root.put("scripts", sc);

        JSONObject rules = new JSONObject();
        rules.put("user", new JSONArray(app.adblock().userRules()));
        rules.put("allow", new JSONArray(app.adblock().allowSites()));
        JSONArray subs = new JSONArray();
        for (AdBlocker.Sub s : app.adblock().subscriptions()) {
            JSONObject o = new JSONObject();
            o.put("name", s.name);
            o.put("url", s.url);
            o.put("enabled", s.enabled);
            subs.put(o);
        }
        rules.put("subs", subs);
        root.put("rules", rules);

        Prefs p = app.prefs();
        JSONObject st = new JSONObject();
        st.put("engine", p.engine());
        st.put("home", p.home());
        st.put("night", p.night());
        st.put("adblock", p.adblock());
        st.put("adSkip", p.adSkip());
        st.put("ua", p.uaMode());
        st.put("customUa", p.customUa());
        st.put("zoom", p.textZoom());
        st.put("reader", p.readerSize());
        st.put("gestures", p.gestures());
        st.put("restore", p.restore());
        root.put("settings", st);

        return root.toString();
    }

    // ---------------- 上传 ----------------

    static void upload(Context c, final String url, final String user, final String pass,
                       final Cb cb) {
        new Thread(() -> {
            try {
                String json = buildJson(c);
                int code = request("PUT", url, user, pass,
                        json.getBytes(StandardCharsets.UTF_8));
                if (code >= 200 && code < 300) cb.done(true, "备份已上传（" + (json.length() / 1024) + " KB）");
                else cb.done(false, "上传失败：HTTP " + code);
            } catch (Exception e) {
                cb.done(false, "上传失败：" + e.getMessage());
            }
        }, "zb-webdav-up").start();
    }

    // ---------------- 恢复 ----------------

    static void restore(Context c, final String url, final String user, final String pass,
                        final Cb cb) {
        new Thread(() -> {
            try {
                byte[] data = requestBytes("GET", url, user, pass, null);
                if (data == null) { cb.done(false, "下载失败：空响应"); return; }
                JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
                if (!"zuobiao".equals(root.optString("app"))) {
                    cb.done(false, "不是坐标浏览器的备份文件");
                    return;
                }
                int bm = 0, sc = 0;
                JSONArray bookmarks = root.optJSONArray("bookmarks");
                if (bookmarks != null) {
                    for (int i = 0; i < bookmarks.length(); i++) {
                        JSONObject o = bookmarks.getJSONObject(i);
                        String u = o.optString("url");
                        if (!u.isEmpty()) {
                            App.get().db().addBookmark(o.optString("title", u), u);
                            bm++;
                        }
                    }
                }
                JSONArray scripts = root.optJSONArray("scripts");
                if (scripts != null) {
                    for (int i = 0; i < scripts.length(); i++) {
                        JSONObject o = scripts.getJSONObject(i);
                        ScriptManager.Script s = new ScriptManager.Script(
                                o.optString("name"), o.optString("pattern", "*"), o.optString("code"));
                        s.enabled = o.optBoolean("enabled", true);
                        App.get().scripts().add(s);
                        sc++;
                    }
                }
                JSONObject rules = root.optJSONObject("rules");
                if (rules != null) {
                    JSONArray user2 = rules.optJSONArray("user");
                    if (user2 != null) {
                        Set<String> set = App.get().prefs().userBlock();
                        for (int i = 0; i < user2.length(); i++) set.add(user2.getString(i));
                        App.get().prefs().setUserBlock(set);
                    }
                    JSONArray allow2 = rules.optJSONArray("allow");
                    if (allow2 != null) {
                        Set<String> set = App.get().prefs().allowSites();
                        for (int i = 0; i < allow2.length(); i++) set.add(allow2.getString(i));
                        App.get().prefs().setAllowSites(set);
                    }
                    JSONArray subs = rules.optJSONArray("subs");
                    if (subs != null) {
                        for (int i = 0; i < subs.length(); i++) {
                            JSONObject o = subs.getJSONObject(i);
                            String u = o.optString("url");
                            if (!u.isEmpty()) App.get().adblock().addSubscription(o.optString("name"), u);
                        }
                    }
                    App.get().adblock().reload();
                }
                JSONObject st = root.optJSONObject("settings");
                if (st != null) {
                    Prefs p = App.get().prefs();
                    p.engine(st.optInt("engine", p.engine()));
                    p.home(st.optString("home", p.home()));
                    p.night(st.optBoolean("night", p.night()));
                    p.adblock(st.optBoolean("adblock", p.adblock()));
                    p.adSkip(st.optBoolean("adSkip", p.adSkip()));
                    p.uaMode(st.optInt("ua", p.uaMode()));
                    p.customUa(st.optString("customUa", p.customUa()));
                    p.textZoom(st.optInt("zoom", p.textZoom()));
                    p.readerSize(st.optInt("reader", p.readerSize()));
                    p.gestures(st.optBoolean("gestures", p.gestures()));
                    p.restore(st.optBoolean("restore", p.restore()));
                }
                cb.done(true, "已恢复：" + bm + " 条书签、" + sc + " 个脚本、规则与设置");
            } catch (Exception e) {
                cb.done(false, "恢复失败：" + e.getMessage());
            }
        }, "zb-webdav-down").start();
    }

    // ---------------- HTTP ----------------

    private static int request(String method, String url, String user, String pass, byte[] body)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(body != null);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 ZuobiaoBrowser/1.2");
        if (body != null) conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (user != null && !user.isEmpty()) {
            String token = Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + token);
        }
        if (body != null) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }
        return conn.getResponseCode();
    }

    private static byte[] requestBytes(String method, String url, String user, String pass,
                                       byte[] body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        if (user != null && !user.isEmpty()) {
            String token = Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + token);
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) return null;
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
