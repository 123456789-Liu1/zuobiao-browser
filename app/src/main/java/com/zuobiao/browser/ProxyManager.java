package com.zuobiao.browser;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.util.concurrent.Executor;

/**
 * WebView 级代理（只影响本应用，不动系统网络）。
 * 依赖 androidx.webkit 的 PROXY_OVERRIDE 能力，老 WebView 上会自动跳过。
 */
final class ProxyManager {

    private ProxyManager() {}

    private static final Executor MAIN = command -> new Handler(Looper.getMainLooper()).post(command);

    static boolean supported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把当前设置应用到 WebView；回调在主线程。 */
    static void apply(Context c, Runnable done) {
        Prefs p = App.get().prefs();
        Runnable finish = () -> {
            if (done != null) done.run();
        };
        try {
            if (!supported() || !p.proxyEnabled() || p.proxyRule().isEmpty()) {
                ProxyController.getInstance().clearProxyOverride(MAIN, finish);
                return;
            }
            ProxyConfig.Builder b = new ProxyConfig.Builder();
            b.addProxyRule(p.proxyRule());
            String bypass = p.proxyBypass();
            if (bypass != null && !bypass.trim().isEmpty()) {
                for (String s : bypass.split("[,;\\s]+")) {
                    if (!s.isEmpty()) b.addBypassRule(s);
                }
            }
            ProxyController.getInstance().setProxyOverride(b.build(), MAIN, finish);
        } catch (Throwable t) {
            finish.run();
        }
    }

    static String summary() {
        Prefs p = App.get().prefs();
        if (!supported()) return "当前 WebView 不支持";
        if (!p.proxyEnabled()) return "未启用";
        String rule = p.proxyRule();
        return rule.isEmpty() ? "未配置" : rule;
    }
}
