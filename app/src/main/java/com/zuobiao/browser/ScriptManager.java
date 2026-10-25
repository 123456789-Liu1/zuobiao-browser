package com.zuobiao.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 用户脚本：站点匹配 + 页面加载后注入，规则极简（* 通配）。 */
final class ScriptManager {

    static final class Script {
        String name;
        String pattern;
        String code;
        boolean enabled = true;

        Script(String n, String p, String c) { name = n; pattern = p; code = c; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("pattern", pattern);
            o.put("code", code);
            o.put("enabled", enabled);
            return o;
        }

        static Script from(JSONObject o) {
            Script s = new Script(o.optString("name"), o.optString("pattern", "*"),
                    o.optString("code"));
            s.enabled = o.optBoolean("enabled", true);
            return s;
        }
    }

    private final SharedPreferences sp;
    private final Context ctx;

    ScriptManager(Context c) {
        ctx = c.getApplicationContext();
        sp = c.getSharedPreferences("zb_scripts", Context.MODE_PRIVATE);
        if (!sp.getBoolean("seeded", false)) seed();
    }

    private void seed() {
        List<Script> list = new ArrayList<>();
        String backToTop = "(function(){if(window.__zbTop)return;window.__zbTop=1;"
                + "document.addEventListener('dblclick',function(e){"
                + "if(window.getSelection&&String(window.getSelection()).length)return;"
                + "window.scrollTo({top:0,behavior:'smooth'});});})();";
        list.add(new Script("双击页面回到顶部", "*", backToTop));

        String noCopyLimit = "(function(){document.addEventListener('copy',function(e){"
                + "e.stopPropagation();},true);"
                + "document.addEventListener('contextmenu',function(e){e.stopPropagation();},true);"
                + "document.querySelectorAll('[oncopy],[oncontextmenu]').forEach(function(n){"
                + "n.oncopy=null;n.oncontextmenu=null;});})();";
        list.add(new Script("解除复制/右键限制", "*", noCopyLimit));

        save(list);
        sp.edit().putBoolean("seeded", true).apply();
    }

    List<Script> all() {
        List<Script> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Script.from(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    void save(List<Script> list) {
        JSONArray arr = new JSONArray();
        for (Script s : list) {
            try {
                arr.put(s.toJson());
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString("list", arr.toString()).apply();
    }

    void add(Script s) { List<Script> l = all(); l.add(s); save(l); }

    void remove(int index) {
        List<Script> l = all();
        if (index >= 0 && index < l.size()) { l.remove(index); save(l); }
    }

    void toggle(int index) {
        List<Script> l = all();
        if (index >= 0 && index < l.size()) { l.get(index).enabled = !l.get(index).enabled; save(l); }
    }

    void update(int index, Script s) {
        List<Script> l = all();
        if (index >= 0 && index < l.size()) { l.set(index, s); save(l); }
    }

    /** 命中当前 URL 的已启用脚本，按顺序拼接。 */
    String codeFor(String url) {
        if (url == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Script s : all()) {
            if (s.enabled && match(s.pattern, url) && s.code != null && !s.code.isEmpty()) {
                sb.append("try{\n").append(s.code).append("\n}catch(e){}\n");
            }
        }
        return sb.toString();
    }

    static boolean match(String pattern, String url) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) return true;
        String p = pattern.trim();
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char ch = p.charAt(i);
            if (ch == '*') re.append(".*");
            else if ("\\.[]{}()+-^$|?".indexOf(ch) >= 0) re.append('\\').append(ch);
            else re.append(ch);
        }
        try {
            return url.matches("(?i)^" + re + "$") || url.matches("(?i).*" + re + ".*");
        } catch (Exception e) {
            return false;
        }
    }
}
