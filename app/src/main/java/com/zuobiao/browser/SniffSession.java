package com.zuobiao.browser;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单个标签页的嗅探结果集：去重、限流、按类型取用。 */
final class SniffSession {

    private static final int MAX = 600;

    private final Map<String, ResourceItem> items = new LinkedHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;

    void setListener(Runnable r) { listener = r; }

    synchronized boolean add(ResourceItem it) {
        if (it == null || it.url == null || it.url.isEmpty()) return false;
        String key = it.url;
        ResourceItem old = items.get(key);
        if (old != null) {
            if (old.duration <= 0 && it.duration > 0) old.duration = it.duration;
            if (old.width <= 0 && it.width > 0) old.width = it.width;
            if (old.height <= 0 && it.height > 0) old.height = it.height;
            if ((old.poster == null || old.poster.isEmpty()) && it.poster != null) old.poster = it.poster;
            if ((old.name == null || old.name.isEmpty()) && it.name != null) old.name = it.name;
            return false;
        }
        if (items.size() >= MAX) {
            // 淘汰最早的一条非媒体资源
            String victim = null;
            for (Map.Entry<String, ResourceItem> e : items.entrySet()) {
                if (e.getValue().type == ResourceItem.IMAGE) { victim = e.getKey(); break; }
            }
            if (victim == null) {
                for (String k : items.keySet()) { victim = k; break; }
            }
            if (victim != null) items.remove(victim);
        }
        items.put(key, it);
        notifyLater();
        return true;
    }

    /** 强制通知（影院/安全状态这类低频变化）。 */
    void ping() {
        final Runnable r = listener;
        if (r != null) main.post(r);
    }

    private long lastNotify;

    /** 普通新增资源：最多每 250ms 通知一次，避免图片多的页面刷爆主线程。 */
    private void notifyLater() {
        final Runnable r = listener;
        if (r == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNotify < 250) return;
        lastNotify = now;
        main.post(r);
    }

    synchronized void clear() {
        items.clear();
        notifyLater();
    }

    synchronized int count() { return items.size(); }

    synchronized int count(int type) {
        int n = 0;
        for (ResourceItem it : items.values()) if (it.type == type) n++;
        return n;
    }

    /** 视频 → 音频 → 文件 → 图片，同类按发现时间倒序。 */
    synchronized List<ResourceItem> list() {
        List<ResourceItem> out = new ArrayList<>(items.values());
        out.sort((a, b) -> {
            if (a.type != b.type) return a.type - b.type;
            return Long.compare(b.foundAt, a.foundAt);
        });
        return out;
    }

    synchronized List<ResourceItem> list(int type) {
        List<ResourceItem> out = new ArrayList<>();
        for (ResourceItem it : items.values()) if (it.type == type) out.add(it);
        out.sort((a, b) -> Long.compare(b.foundAt, a.foundAt));
        return out;
    }

    synchronized boolean hasPlayable() {
        for (ResourceItem it : items.values()) if (it.playable()) return true;
        return false;
    }
}
