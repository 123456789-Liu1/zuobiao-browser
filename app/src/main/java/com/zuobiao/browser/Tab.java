package com.zuobiao.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

/** 一个标签页 = 一个 WebView + 一点状态。 */
@SuppressLint("ViewConstructor")
final class Tab {

    interface Host {
        void onPageStarted(Tab t);
        void onPageFinished(Tab t);
        void onProgress(Tab t, int progress);
        void onTitle(Tab t, String title);
        void onDownload(Tab t, String url, String userAgent, String contentDisposition,
                        String mime, long contentLength);
        void onLongPress(Tab t, WebView.HitTestResult result);
        void onScroll(Tab t, int dy);
        Tab createTabForWindow();
        void requestPermissions(String[] resources);
        void onSslWarning(Tab t, SslErrorHandler handler, String url);
        void onLoadTimeout(Tab t);
    }

    final WebView web;
    final boolean incognito;
    final SniffSession sniff = new SniffSession();
    boolean hasVideo;
    long videoDuration = -1;
    int skippedAds;
    boolean inCinema;
    boolean safeMode;
    boolean internalPage;
    int safeBlocked;
    boolean lite;
    boolean rescueTried;
    private boolean pageLoading;
    private Runnable timeoutCheck;
    private androidx.webkit.ScriptHandler safeHandle;
    private SniffBridge bridge;
    String url = "";
    String title = "新标签页";
    boolean desktop;
    Bitmap favicon;

    private final Host host;
    private final Prefs prefs;

    Tab(Context ctx, Prefs prefs, Host host, boolean incognito) {
        this.prefs = prefs;
        this.host = host;
        this.incognito = incognito;
        this.timeoutCheck = () -> {
            if (pageLoading) host.onLoadTimeout(Tab.this);
        };
        web = new WebView(ctx);
        web.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -1));
        web.setBackgroundColor(0xFF0E1116);
        bridge = new SniffBridge(this);
        web.addJavascriptInterface(bridge, "ZBSniff");
        web.addJavascriptInterface(bridge, "ZBSafe");
        configure();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setDefaultTextEncodingName("UTF-8");
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        s.setTextZoom(prefs.textZoom());
        // 关闭 WebView 自带的算法暗化，夜间模式交给统一注入的 CSS 过滤器，避免双重反色
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                s.setAlgorithmicDarkeningAllowed(false);
            } else if (Build.VERSION.SDK_INT >= 29) {
                s.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
        } catch (Throwable ignored) {
        }
        applyUserAgent();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String u, Bitmap icon) {
                url = u;
                pageLoading = true;
                web.removeCallbacks(timeoutCheck);
                if (prefs.rescue()) web.postDelayed(timeoutCheck, 12000);
                host.onPageStarted(Tab.this);
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                url = u;
                pageLoading = false;
                web.removeCallbacks(timeoutCheck);
                host.onPageFinished(Tab.this);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String u, boolean reload) {
                url = u;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                host.onSslWarning(Tab.this, handler, error.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                if (Build.VERSION.SDK_INT >= 23 && req != null && req.isForMainFrame()) {
                    showErrorPage(req.getUrl() == null ? url : req.getUrl().toString(),
                            String.valueOf(err == null ? "" : err.getDescription()));
                }
            }

            @Override
            public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
                if (Build.VERSION.SDK_INT < 23) showErrorPage(failingUrl, desc);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String hostName, String realm) {
                // 代理认证：主机与配置的代理一致时，用保存的账号密码
                if (prefs.proxyEnabled() && hostName != null
                        && hostName.equalsIgnoreCase(prefs.proxyHost())
                        && prefs.proxyUser() != null && !prefs.proxyUser().isEmpty()) {
                    handler.proceed(prefs.proxyUser(), prefs.proxyPass());
                    return;
                }
                handler.cancel();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return intercept(req.getUrl() == null ? null : req.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String u) {
                return intercept(u);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                host.onProgress(Tab.this, p);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.d("ZBCONSOLE", m.message() + " @" + m.lineNumber()
                        + " " + m.sourceId());
                return true;
            }

            @Override
            public void onReceivedTitle(WebView view, String t) {
                title = t;
                host.onTitle(Tab.this, t);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                favicon = icon;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                                          Message resultMsg) {
                Tab t = host.createTabForWindow();
                if (t == null) return false;
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(t.web);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                host.requestPermissions(request.getResources());
                request.deny();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback cb) {
                cb.invoke(origin, false, false);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                host.onDownload(Tab.this, u, ua, cd, mime, len);
            }
        });

        web.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
            try {
                host.onScroll(Tab.this, sy - osy);
            } catch (Exception ignored) {
            }
        });

        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult r = web.getHitTestResult();
                if (r != null) host.onLongPress(Tab.this, r);
                return true;
            }
        });

        applyLite(prefs.liteMode());
    }

    /** 内置页面（隐私自检）：同样套用安全模式注入，便于对比。 */
    void loadInternal(String html) {
        internalPage = true;
        applySafeScript("https://safe.local/");
        web.loadDataWithBaseURL("https://safe.local/", html, "text/html", "utf-8", null);
    }

    /** 安全模式：原生收紧 + document start 注入。 */
    private void applySafeScript(String u) {
        try {
            if (safeHandle != null) {
                safeHandle.remove();
                safeHandle = null;
            }
        } catch (Exception ignored) {
        }
        String host = MediaSniffer.hostOf(u);
        boolean on = prefs.safeMode() && !prefs.safeAllowedFor(host);
        safeMode = on;
        safeBlocked = 0;
        try {
            WebSettings s = web.getSettings();
            s.setGeolocationEnabled(!on);
            if (on) {
                s.setSaveFormData(false);
                s.setJavaScriptCanOpenWindowsAutomatically(false);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(false);
                s.setMediaPlaybackRequiresUserGesture(true);
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            } else {
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            }
        } catch (Exception ignored) {
        }
        if (!on) return;
        String js = SafeJs.strict(bridgeToken());
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                safeHandle = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                        web, js, java.util.Collections.singleton("*"));
                return;
            }
        } catch (Exception ignored) {
        }
        // 兜底：低版本 WebView 没有 document-start，只能尽早注入
        web.evaluateJavascript(js, null);
    }

    /** 拦截链路：先判广告，再顺手记一条媒体资源。 */
    private WebResourceResponse intercept(String u) {
        if (u == null) return null;
        try {
            if (App.get().adblock().isBlocked(u)) return AdBlocker.empty();
        } catch (Exception ignored) {
        }
        try {
            if (prefs.sniff()) {
                ResourceItem it = MediaSniffer.fromNetwork(u);
                if (it != null) sniff.add(it);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    String bridgeToken() { return bridge == null ? "" : bridge.token(); }

    void notifySniffChanged() {
        sniff.ping();
    }

    void applyUserAgent() {
        WebSettings s = web.getSettings();
        String ua = prefs.ua(App.get());
        if (desktop) {
            ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        }
        s.setUserAgentString(ua);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
    }

    void setTextZoom(int v) { web.getSettings().setTextZoom(v); }

    void load(String u) {
        if (u == null || u.isEmpty()) return;
        internalPage = false;
        rescueTried = false;
        applySafeScript(u);
        if (u.startsWith("about:")) {
            // 先定 url 再加载：loadData 的回调顺序不可靠，避免地址栏拿到上一页的地址
            url = "about:blank";
            web.loadDataWithBaseURL("about:blank", blankPage(), "text/html", "UTF-8", null);
            host.onPageStarted(this);
            web.post(() -> host.onPageFinished(this));
            return;
        }
        url = u;
        web.loadUrl(u);
    }

    /** 省流：不加载图片。 */
    void applyLite(boolean on) {
        lite = on;
        try {
            WebSettings s = web.getSettings();
            s.setBlockNetworkImage(on);
            s.setLoadsImagesAutomatically(!on);
        } catch (Exception ignored) {
        }
    }

    boolean canGoBack() { return web.canGoBack(); }
    boolean canGoForward() { return web.canGoForward(); }
    void goBack() { if (web.canGoBack()) web.goBack(); }
    void goForward() { if (web.canGoForward()) web.goForward(); }
    void reload() { web.reload(); }
    void stop() { web.stopLoading(); }

    void onPause() { web.onPause(); web.pauseTimers(); }
    void onResume() { web.onResume(); web.resumeTimers(); }

    void destroy() {
        try {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.removeAllViews();
            web.destroy();
        } catch (Exception ignored) {
        }
    }

    /** 处理非 http(s) 协议：能交给系统就交给系统，否则留在内部。 */
    boolean handleUrl(String u) {
        if (u == null) return false;
        String scheme = Uri.parse(u).getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")) {
            return false;
        }
        if (scheme.equals("intent")) {
            try {
                Intent i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                App.get().startActivity(i);
                return true;
            } catch (Exception e) {
                Ui.toast(App.get(), "无法打开该链接");
                return true;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.get().startActivity(i);
        } catch (Exception e) {
            Ui.toast(App.get(), "没有可处理该协议的应用");
        }
        return true;
    }

    private void showErrorPage(String failingUrl, String desc) {
        String html = "<!doctype html><html><head><meta name=\"viewport\" "
                + "content=\"width=device-width\"><style>html,body{margin:0;height:100%;"
                + "background:#0E1116;color:#8B949E;font-family:-apple-system,'PingFang SC',"
                + "'Microsoft YaHei',sans-serif}.w{height:100%;display:flex;flex-direction:column;"
                + "align-items:center;justify-content:center;gap:12px;padding:24px;text-align:center}"
                + ".t{font-size:16px;color:#E6EDF3}.u{font-size:12px;color:#5C6773;word-break:break-all}"
                + ".d{font-size:12px;color:#5C6773}</style></head><body><div class=\"w\">"
                + "<div class=\"t\">网页打不开</div>"
                + "<div class=\"u\">" + (failingUrl == null ? "" : failingUrl) + "</div>"
                + "<div class=\"d\">" + (desc == null ? "" : desc) + "</div>"
                + "<div class=\"d\">检查网络后下拉刷新，或换个地址试试</div>"
                + "</div></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", failingUrl);
        host.onPageFinished(Tab.this);
    }

    private String blankPage() {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width\">"
                + "<style>html,body{margin:0;height:100%;background:#0E1116;color:#5C6773;"
                + "font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif}"
                + ".w{height:100%;display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;gap:10px}.t{font-size:15px;color:#8B949E}"
                + ".s{font-size:12px}</style></head><body><div class=\"w\">"
                + "<div class=\"t\">坐标浏览器</div>"
                + "<div class=\"s\">点击地址栏开始，或从菜单打开书签</div>"
                + "</div></body></html>";
    }
}
