package com.zuobiao.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** 播放进度与倍速记忆：夸克那套 SnifferVideoData 的 playPos / playSpeed，落到本地。 */
final class PlaybackStore {

    static final class Entry {
        long pos;
        long dur;
        float speed = 1f;
        long time;
    }

    private static final int MAX = 300;
    private final SharedPreferences sp;

    PlaybackStore(Context c) {
        sp = c.getSharedPreferences("zb_playback", Context.MODE_PRIVATE);
    }

    Entry get(String key) {
        if (key == null) return null;
        try {
            String raw = sp.getString(key, null);
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            Entry e = new Entry();
            e.pos = o.optLong("p", 0);
            e.dur = o.optLong("d", 0);
            e.speed = (float) o.optDouble("s", 1f);
            e.time = o.optLong("t", 0);
            return e;
        } catch (Exception e) {
            return null;
        }
    }

    void put(String key, long pos, long dur, float speed) {
        if (key == null || pos <= 0) return;
        try {
            JSONObject o = new JSONObject();
            o.put("p", pos);
            o.put("d", dur);
            o.put("s", speed);
            o.put("t", System.currentTimeMillis());
            sp.edit().putString(key, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void remove(String key) {
        if (key != null) sp.edit().remove(key).apply();
    }

    /** 超过 300 条时按时间淘汰，避免无限增长。 */
    void prune() {
        try {
            List<String> keys = new ArrayList<>();
            for (String k : sp.getAll().keySet()) keys.add(k);
            if (keys.size() <= MAX) return;
            keys.sort(Comparator.comparingLong(k -> {
                Entry e = get(k);
                return e == null ? 0 : e.time;
            }));
            SharedPreferences.Editor ed = sp.edit();
            for (int i = 0; i < keys.size() - MAX; i++) ed.remove(keys.get(i));
            ed.apply();
        } catch (Exception ignored) {
        }
    }

    /** 进度是否值得续播：太靠前不续；剩余太短（不足 10s 或 30%）也不续。 */
    static boolean worthResume(Entry e) {
        if (e == null) return false;
        if (e.pos < 5000) return false;
        if (e.dur > 0) {
            long remain = e.dur - e.pos;
            long minRemain = Math.min(10000, (long) (e.dur * 0.3));
            if (remain <= minRemain) return false;
        }
        return true;
    }
}
