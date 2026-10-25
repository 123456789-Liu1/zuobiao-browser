package com.zuobiao.browser;

import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;

/**
 * 注入脚本与原生之间的桥。
 * 每个标签页一个随机 token，只接受带正确 token 的调用，避免页面自身的脚本乱喂数据。
 */
final class SniffBridge {

    private final Tab tab;
    private final String token;

    SniffBridge(Tab tab) {
        this.tab = tab;
        byte[] b = new byte[8];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        this.token = sb.toString();
    }

    String token() { return token; }

    private boolean ok(String tk) { return token.equals(tk); }

    @JavascriptInterface
    public void report(String tk, String json) {
        if (!ok(tk) || json == null) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ResourceItem it = MediaSniffer.fromDom(o);
                if (it != null) tab.sniff.add(it);
            }
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void video(String tk, String json) {
        if (!ok(tk)) return;
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            long d = o.optLong("d", -1);
            if (d > 0) {
                tab.hasVideo = true;
                tab.videoDuration = d;
                tab.notifySniffChanged();
            }
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void skipped(String tk, String label) {
        if (!ok(tk)) return;
        tab.skippedAds++;
        tab.notifySniffChanged();
    }

    @JavascriptInterface
    public void blocked(String tk, String api) {
        if (!ok(tk)) return;
        tab.safeBlocked++;
        if (tab.safeBlocked % 8 == 1) tab.notifySniffChanged();
    }

    @JavascriptInterface
    public void progress(String tk, String json) {
        if (!ok(tk) || json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            long p = o.optLong("p", 0);
            long d = o.optLong("d", 0);
            if (p > 1000) {
                new PlaybackStore(App.get()).put("page:" + tab.url, p, d, 1f);
            }
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void cinema(String tk, String state) {
        if (!ok(tk)) return;
        tab.inCinema = "1".equals(state);
        tab.notifySniffChanged();
    }

    @JavascriptInterface
    public void toast(String tk, final String msg) {
        if (!ok(tk)) return;
        Ui.toast(App.get(), msg);
    }
}
