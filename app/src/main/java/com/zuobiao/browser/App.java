package com.zuobiao.browser;

import android.app.Application;

/** 进程级单例容器，避免到处传 Context。 */
public class App extends Application {

    private static App inst;

    private Prefs prefs;
    private AdBlocker adblock;
    private Db db;
    private ScriptManager scripts;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        prefs = new Prefs(this);
        db = new Db(this);
        scripts = new ScriptManager(this);
        adblock = new AdBlocker(this, prefs);
        new Thread(() -> {
            try { adblock.ensureLoaded(); } catch (Throwable ignored) {}
        }, "zb-rules").start();
        // 启动时套用代理设置（不影响系统网络，只作用于本应用的 WebView）
        try {
            ProxyManager.apply(this, null);
        } catch (Throwable ignored) {
        }
    }

    static App get() { return inst; }

    Prefs prefs() { return prefs; }
    AdBlocker adblock() { return adblock; }
    Db db() { return db; }
    ScriptManager scripts() { return scripts; }
}
