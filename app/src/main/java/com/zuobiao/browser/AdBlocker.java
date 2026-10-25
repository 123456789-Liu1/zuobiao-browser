package com.zuobiao.browser;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 广告与追踪拦截。
 *
 * 规则来源：内置 hosts + 订阅（Easylist 子集）+ 用户自定义 + 放行站点。
 * 性能要点：
 *   1. 规则编译成不可变 RuleSet，整体替换，命中判断纯内存、无锁；
 *   2. 拦截计数走 AtomicInteger，攒够一批或页面暂停时才落盘，避免每个请求都写 SharedPreferences。
 */
final class AdBlocker {

    /** 编译后的规则集，整体替换，读线程无需加锁。 */
    private static final class RuleSet {
        final Set<String> hosts = new HashSet<>();
        final Set<String> allowHosts = new HashSet<>();
        final List<Pattern> patterns = new ArrayList<>();
        final List<String> cosmetic = new ArrayList<>();
    }

    static final class Sub {
        String name;
        String url;
        boolean enabled = true;
        int count;
        long updatedAt;
    }

    private static final int MAX_HOSTS = 80000;
    private static final int MAX_PATTERNS = 4000;
    private static final int MAX_COSMETIC = 3000;
    private static final int FLUSH_EVERY = 32;

    private final Context ctx;
    private final Prefs prefs;
    private volatile RuleSet rules = new RuleSet();
    private volatile boolean enabled;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean loaded;

    AdBlocker(Context c, Prefs p) {
        ctx = c.getApplicationContext();
        prefs = p;
        enabled = p.adblock();
    }

    // ---------------- 加载 ----------------

    synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        reload();
    }

    synchronized void reload() {
        RuleSet rs = new RuleSet();
        try (InputStream in = ctx.getAssets().open("adblock_hosts.txt")) {
            parseInto(readAll(in), rs);
        } catch (IOException ignored) {
        }
        for (Sub s : subscriptions()) {
            if (!s.enabled) continue;
            File f = subFile(s.url);
            if (!f.exists()) continue;
            try {
                parseInto(readAll(new java.io.FileInputStream(f)), rs);
            } catch (Exception ignored) {
            }
        }
        for (String h : prefs.userBlock()) addHost(rs.hosts, h);
        for (String h : prefs.allowSites()) addHost(rs.allowHosts, h);
        rules = rs;
    }

    // ---------------- 匹配 ----------------

    boolean isBlocked(String url) {
        if (!enabled || url == null) return false;
        if (!loaded) ensureLoaded();
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            return false;
        }
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        RuleSet rs = rules;
        if (matchHost(host, rs.allowHosts)) return false;
        boolean blocked = matchHost(host, rs.hosts);
        if (!blocked && !rs.patterns.isEmpty()) {
            for (int i = 0; i < rs.patterns.size(); i++) {
                if (rs.patterns.get(i).matcher(url).find()) { blocked = true; break; }
            }
        }
        if (blocked && pending.incrementAndGet() >= FLUSH_EVERY) flush();
        return blocked;
    }

    void setEnabled(boolean on) {
        enabled = on;
        prefs.adblock(on);
    }

    boolean isEnabled() { return enabled; }

    /** 把攒下的拦截次数写回磁盘。Activity onPause / 退出时调用。 */
    void flush() {
        int n = pending.getAndSet(0);
        if (n > 0) prefs.blocked(prefs.blocked() + n);
    }

    long blockedCount() { return prefs.blocked() + pending.get(); }

    int ruleCount() {
        RuleSet rs = rules;
        return rs.hosts.size() + rs.patterns.size();
    }

    int cosmeticCount() { return rules.cosmetic.size(); }

    String cosmeticCss() {
        StringBuilder sb = new StringBuilder(BASE_COSMETIC);
        List<String> list = rules.cosmetic;
        for (int i = 0; i < list.size(); i++) {
            sb.append(',').append(list.get(i));
        }
        sb.append("{display:none!important;height:0!important;overflow:hidden!important}");
        return sb.toString();
    }

    // ---------------- 自定义 / 放行 ----------------

    synchronized void addUserRule(String host) {
        host = host.toLowerCase(Locale.ROOT).trim();
        if (host.isEmpty()) return;
        Set<String> s = prefs.userBlock();
        s.add(host);
        prefs.setUserBlock(s);
        reload();
    }

    synchronized void removeUserRule(String host) {
        Set<String> s = prefs.userBlock();
        s.remove(host.toLowerCase(Locale.ROOT));
        prefs.setUserBlock(s);
        reload();
    }

    Set<String> userRules() { return prefs.userBlock(); }

    Set<String> allowSites() { return prefs.allowSites(); }

    synchronized void setAllow(String host, boolean allow) {
        host = host.toLowerCase(Locale.ROOT).trim();
        if (host.isEmpty()) return;
        Set<String> s = prefs.allowSites();
        if (allow) s.add(host); else s.remove(host);
        prefs.setAllowSites(s);
        reload();
    }

    // ---------------- 订阅 ----------------

    List<Sub> subscriptions() {
        List<Sub> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(prefs.subscriptions());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Sub s = new Sub();
                s.name = o.optString("name");
                s.url = o.optString("url");
                s.enabled = o.optBoolean("enabled", true);
                s.count = o.optInt("count", 0);
                s.updatedAt = o.optLong("updatedAt", 0);
                if (s.url != null && !s.url.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void saveSubscriptions(List<Sub> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Sub s : list) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", s.name);
                o.put("url", s.url);
                o.put("enabled", s.enabled);
                o.put("count", s.count);
                o.put("updatedAt", s.updatedAt);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.subscriptions(arr.toString());
    }

    void addSubscription(String name, String url) {
        List<Sub> list = subscriptions();
        Sub s = new Sub();
        s.name = name == null || name.trim().isEmpty() ? hostOf(url) : name.trim();
        s.url = url.trim();
        list.add(s);
        saveSubscriptions(list);
    }

    void removeSubscription(int index) {
        List<Sub> list = subscriptions();
        if (index < 0 || index >= list.size()) return;
        Sub s = list.remove(index);
        File f = subFile(s.url);
        if (f.exists()) f.delete();
        saveSubscriptions(list);
        reload();
    }

    void toggleSubscription(int index) {
        List<Sub> list = subscriptions();
        if (index < 0 || index >= list.size()) return;
        list.get(index).enabled = !list.get(index).enabled;
        saveSubscriptions(list);
        reload();
    }

    /** 下载并替换订阅缓存（阻塞，需在后台线程调用）。 */
    boolean updateSubscription(int index) {
        List<Sub> list = subscriptions();
        if (index < 0 || index >= list.size()) return false;
        Sub s = list.get(index);
        if ("manual://local".equals(s.url)) return false;
        try {
            String text = httpGet(s.url);
            if (text == null || text.length() < 16) return false;
            File f = subFile(s.url);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes("UTF-8"));
            }
            RuleSet tmp = new RuleSet();
            parseInto(text, tmp);
            s.count = tmp.hosts.size() + tmp.patterns.size();
            s.updatedAt = System.currentTimeMillis();
            saveSubscriptions(list);
            reload();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 手动导入规则文本（追加到 manual.txt）。 */
    boolean importRules(String text) {
        try {
            File f = new File(subsDir(), "manual.txt");
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(("\n" + text + "\n").getBytes("UTF-8"));
            }
            ensureManualSub();
            reload();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void ensureManualSub() {
        for (Sub s : subscriptions()) if ("manual://local".equals(s.url)) return;
        List<Sub> list = subscriptions();
        Sub s = new Sub();
        s.name = "手动导入";
        s.url = "manual://local";
        list.add(s);
        saveSubscriptions(list);
    }

    // ---------------- 内部 ----------------

    private File subsDir() { return new File(ctx.getFilesDir(), "subs"); }

    private File subFile(String url) {
        if ("manual://local".equals(url)) return new File(subsDir(), "manual.txt");
        return new File(subsDir(), Integer.toHexString(url.hashCode()) + ".txt");
    }

    static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 ZuobiaoBrowser/1.2");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) return null;
        return readAll(c.getInputStream());
    }

    private static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? url : h;
        } catch (Exception e) {
            return url;
        }
    }

    // ---- 解析：Easylist 子集 ----

    private static void parseInto(String text, RuleSet rs) {
        if (text == null) return;
        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("!")) continue;
            if (line.startsWith("[")) continue;

            // 外观规则
            int hash = line.indexOf("##");
            if (hash >= 0) {
                if (rs.cosmetic.size() < MAX_COSMETIC) {
                    String sel = line.substring(hash + 2).trim();
                    if (isPlausibleSelector(sel)) rs.cosmetic.add(sel);
                }
                continue;
            }
            if (line.contains("#@#") || line.contains("#?#")) continue;

            boolean exception = line.startsWith("@@");
            if (exception) line = line.substring(2);
            if (line.isEmpty()) continue;

            // 0.0.0.0 host
            String low = line.toLowerCase(Locale.ROOT);
            if (low.startsWith("0.0.0.0 ") || low.startsWith("127.0.0.1 ")) {
                String h = line.substring(line.indexOf(' ') + 1).trim();
                if (exception) addHost(rs.allowHosts, h); else addHost(rs.hosts, h);
                continue;
            }

            int dollar = line.indexOf('$');
            if (dollar > 0) line = line.substring(0, dollar);

            if (line.startsWith("||")) {
                String body = line.substring(2);
                if (body.endsWith("^")) body = body.substring(0, body.length() - 1);
                if (body.contains("/") || body.contains("*")) {
                    if (!exception && rs.patterns.size() < MAX_PATTERNS) {
                        Pattern p = toPattern("||" + body);
                        if (p != null) rs.patterns.add(p);
                    }
                } else if (exception) {
                    addHost(rs.allowHosts, body);
                } else {
                    addHost(rs.hosts, body);
                }
                continue;
            }

            if (line.length() > 2 && line.startsWith("/") && line.endsWith("/")) {
                if (!exception && rs.patterns.size() < MAX_PATTERNS) {
                    try {
                        rs.patterns.add(Pattern.compile(line.substring(1, line.length() - 1),
                                Pattern.CASE_INSENSITIVE));
                    } catch (Exception ignored) {
                    }
                }
                continue;
            }

            if (!line.contains("/") && !line.contains("*") && !line.contains(" ")
                    && line.contains(".")) {
                if (exception) addHost(rs.allowHosts, line); else addHost(rs.hosts, line);
                continue;
            }

            if (!exception && (line.contains("*") || line.contains("/"))
                    && rs.patterns.size() < MAX_PATTERNS) {
                Pattern p = toPattern(line);
                if (p != null) rs.patterns.add(p);
            }
        }
    }

    private static boolean isPlausibleSelector(String sel) {
        if (sel.isEmpty() || sel.length() > 120) return false;
        char c = sel.charAt(0);
        return c == '.' || c == '#' || c == '[' || c == '*'
                || Character.isLetter(c);
    }

    private static void addHost(Set<String> set, String host) {
        if (set.size() >= MAX_HOSTS) return;
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith(".")) host = host.substring(1);
        if (host.isEmpty() || !host.contains(".")) return;
        set.add(host);
    }

    private static Pattern toPattern(String rule) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rule.length(); i++) {
            char c = rule.charAt(i);
            if (c == '*') sb.append(".*");
            else if (c == '^') sb.append("([/?#]|$)");
            else if ("\\.[]{}()+-?$|".indexOf(c) >= 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        try {
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchHost(String host, Set<String> set) {
        if (set.isEmpty()) return false;
        if (set.contains(host)) return true;
        int idx = 0;
        int guard = 0;
        while ((idx = host.indexOf('.', idx)) >= 0 && guard++ < 3) {
            if (set.contains(host.substring(idx + 1))) return true;
            idx++;
        }
        return false;
    }

    static android.webkit.WebResourceResponse empty() {
        return new android.webkit.WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    static final String BASE_COSMETIC =
            "[id^=\"ad-\"],[id^=\"ads-\"],[id$=\"-ad\"],[class^=\"ad-\"],[class^=\"ads-\"],"
            + "[class$=\"-ad\"],[class*=\" ad-\"],[class*=\"adsbygoogle\"],"
            + "[id=\"taboola\"],[class*=\"taboola\"],[class*=\"outbrain\"],"
            + "[data-ad],[data-ad-slot],[data-ad-client],ins.adsbygoogle,"
            + ".adsbygoogle,[aria-label=\"Advertisement\"],[aria-label=\"广告\"]";
}
