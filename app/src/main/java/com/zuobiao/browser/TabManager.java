package com.zuobiao.browser;

import android.content.Context;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 标签集合：负责 WebView 的挂载/卸载、会话保存与恢复。 */
final class TabManager {

    interface Listener {
        void onTabsChanged();
    }

    private static final int MAX_TABS = 20;

    private final Context ctx;
    private final Prefs prefs;
    private final Tab.Host host;
    private final ViewGroup container;
    private final Listener listener;
    private final List<Tab> tabs = new ArrayList<>();
    private int active = -1;

    TabManager(Context c, Prefs p, Tab.Host host, ViewGroup container, Listener l) {
        this.ctx = c;
        this.prefs = p;
        this.host = host;
        this.container = container;
        this.listener = l;
    }

    Tab current() {
        if (active < 0 || active >= tabs.size()) return null;
        return tabs.get(active);
    }

    int size() { return tabs.size(); }
    int activeIndex() { return active; }
    List<Tab> list() { return new ArrayList<>(tabs); }

    Tab open(String url, boolean incognito, boolean select) {
        if (tabs.size() >= MAX_TABS) {
            for (int i = 0; i < tabs.size(); i++) {
                if (i != active) { close(i); break; }
            }
        }
        Tab t = new Tab(ctx, prefs, host, incognito);
        tabs.add(t);
        if (select || active < 0) {
            attach(tabs.size() - 1);
        }
        if (url != null) t.load(url);
        notifyChanged();
        return t;
    }

    Tab openBlankForWindow() {
        Tab t = new Tab(ctx, prefs, host, false);
        tabs.add(t);
        attach(tabs.size() - 1);
        notifyChanged();
        return t;
    }

    void select(int index) {
        if (index < 0 || index >= tabs.size() || index == active) return;
        attach(index);
        notifyChanged();
    }

    private void attach(int index) {
        if (active >= 0 && active < tabs.size()) {
            Tab old = tabs.get(active);
            old.onPause();
            container.removeView(old.web);
        }
        active = index;
        Tab t = tabs.get(index);
        container.removeView(t.web);
        container.addView(t.web);
        t.onResume();
    }

    void close(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab t = tabs.remove(index);
        container.removeView(t.web);
        t.destroy();
        if (tabs.isEmpty()) {
            active = -1;
            notifyChanged();
            return;
        }
        if (index <= active) active = Math.max(0, active - 1);
        if (active >= tabs.size()) active = tabs.size() - 1;
        attach(active);
        notifyChanged();
    }

    void closeCurrent() {
        if (active >= 0) close(active);
    }

    void destroyAll() {
        for (Tab t : tabs) {
            container.removeView(t.web);
            t.destroy();
        }
        tabs.clear();
        active = -1;
    }

    void pauseAll() {
        for (Tab t : tabs) t.onPause();
    }

    void resumeCurrent() {
        Tab t = current();
        if (t != null) t.onResume();
    }

    // ---------------- 会话 ----------------

    void saveSession() {
        try {
            JSONArray arr = new JSONArray();
            for (Tab t : tabs) {
                if (t.incognito) continue;
                if (t.url == null || t.url.isEmpty() || t.url.equals("about:blank")) continue;
                JSONObject o = new JSONObject();
                o.put("url", t.url);
                o.put("title", t.title);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("tabs", arr);
            root.put("active", active);
            prefs.session(root.toString());
        } catch (Exception ignored) {
        }
    }

    boolean restoreSession() {
        String raw = prefs.session();
        if (raw == null || raw.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("tabs");
            if (arr == null || arr.length() == 0) return false;
            for (int i = 0; i < arr.length(); i++) {
                String u = arr.getJSONObject(i).optString("url");
                if (!u.isEmpty()) open(u, false, false);
            }
            int a = root.optInt("active", 0);
            if (a < 0 || a >= tabs.size()) a = 0;
            attach(a);
            notifyChanged();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyChanged() {
        if (listener != null) listener.onTabsChanged();
    }
}
