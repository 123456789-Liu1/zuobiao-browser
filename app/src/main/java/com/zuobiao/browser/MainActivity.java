package com.zuobiao.browser;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 主界面：地址栏 + WebView 容器 + 底部工具栏 + 各类菜单。 */
public class MainActivity extends AppCompatActivity implements Tab.Host, TabManager.Listener {

    static final int REQ_LIBRARY = 1001;
    static final int REQ_SETTINGS = 1002;
    static final int REQ_WRITE = 1003;

    private static java.lang.ref.WeakReference<MainActivity> INSTANCE;

    private Prefs prefs;
    private Db db;
    private ScriptManager scripts;
    private TabManager tabs;

    private EditText urlBar;
    private LinearLayout urlView;
    private TextView urlText;
    private TextView urlShield;
    private View urlWrap;
    private LinearLayout topBar;
    private boolean urlCollapsed;
    private boolean lastSafe;
    private ProgressBar progress;
    private FrameLayout webContainer;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh;
    private android.app.Dialog menuDialog;
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private TextView btnBack, btnForward, btnTabs, btnStop, sniffFab;

    private boolean editingUrl;
    private boolean findVisible;
    private long lastBackTime;

    // ---------------- 生命周期 ----------------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        INSTANCE = new java.lang.ref.WeakReference<>(this);
        setContentView(R.layout.activity_main);
        Ui.applyInsets(this);

        App app = App.get();
        prefs = app.prefs();
        lastSafe = prefs.safeMode();
        db = app.db();
        scripts = app.scripts();

        bindViews();
        tabs = new TabManager(this, prefs, this, webContainer, this);

        boolean handled = handleIntent(getIntent());
        if (!handled) {
            boolean restored = prefs.restore() && tabs.restoreSession();
            if (!restored) tabs.open(prefs.home(), false, true);
        }
        applyPrefsToUi();
        updateTabsButton();
    }

    private void bindViews() {
        urlBar = findViewById(R.id.urlBar);
        progress = findViewById(R.id.progress);
        webContainer = findViewById(R.id.webContainer);
        refresh = findViewById(R.id.refresh);
        refresh.setColorSchemeColors(getColor(R.color.accent));
        refresh.setProgressBackgroundColorSchemeColor(getColor(R.color.surface2));
        refresh.setOnRefreshListener(() -> {
            Tab t = tabs == null ? null : tabs.current();
            if (t != null) t.reload();
            refresh.setRefreshing(true);
            refresh.postDelayed(() -> refresh.setRefreshing(false), 6000);
        });
        findBar = findViewById(R.id.findBar);
        findInput = findViewById(R.id.findInput);
        findCount = findViewById(R.id.findCount);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnTabs = findViewById(R.id.btnTabs);
        btnStop = findViewById(R.id.btnStop);
        sniffFab = findViewById(R.id.sniffFab);
        sniffFab.setOnClickListener(v -> SnifferSheet.show(this, tabs.current()));

        findViewById(R.id.btnHome).setOnClickListener(v -> openUrl(prefs.home()));
        btnBack.setOnClickListener(v -> {
            Tab t = tabs.current();
            if (t != null) { t.goBack(); updateNav(); }
        });
        btnForward.setOnClickListener(v -> {
            Tab t = tabs.current();
            if (t != null) { t.goForward(); updateNav(); }
        });
        btnTabs.setOnClickListener(v ->
                startActivity(new Intent(this, TabsActivity.class)));
        findViewById(R.id.btnMenu).setOnClickListener(v -> showMenu());
        btnStop.setOnClickListener(v -> {
            Tab t = tabs.current();
            if (t != null) { t.stop(); progress.setVisibility(View.GONE); }
            btnStop.setVisibility(View.GONE);
        });

        urlView = findViewById(R.id.urlView);
        urlText = findViewById(R.id.urlText);
        urlShield = findViewById(R.id.urlShield);
        urlShield.setOnClickListener(v -> showSafeSheet());
        urlWrap = findViewById(R.id.urlWrap);
        topBar = findViewById(R.id.topBar);
        urlView.setOnClickListener(v -> enterEditMode());

        urlBar.setOnFocusChangeListener((v, has) -> {
            if (!has && editingUrl) exitEditMode();
        });
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitUrl();
                return true;
            }
            return false;
        });

        // 查找栏
        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { doFind(s.toString()); }
        });
        findViewById(R.id.findPrev).setOnClickListener(v -> findPrev());
        findViewById(R.id.findNext).setOnClickListener(v -> findNext());
        findViewById(R.id.findClose).setOnClickListener(v -> closeFind());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleIntent(intent)) updateTabsButton();
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            tabs.open(data.toString(), false, true);
            return true;
        }
        String extra = intent.getStringExtra("open_url");
        if (extra != null && !extra.isEmpty()) {
            tabs.open(extra, false, true);
            intent.removeExtra("open_url");
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        INSTANCE = new java.lang.ref.WeakReference<>(this);
        Tab t = tabs.current();
        if (t != null) {
            t.onResume();
            t.applyUserAgent();
            t.setTextZoom(prefs.textZoom());
            t.web.evaluateJavascript(Js.night(prefs.night()), null);
            if (lastSafe != prefs.safeMode()) {
                lastSafe = prefs.safeMode();
                t.reload();
            }
        }
        applyPrefsToUi();
        updateTabsButton();
        updateSafeBadge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tabs.pauseAll();
        App.get().adblock().flush();
        if (prefs.restore()) tabs.saveSession();
    }

    @Override
    protected void onDestroy() {
        if (INSTANCE != null && INSTANCE.get() == this) INSTANCE = null;
        if (isFinishing()) {
            tabs.destroyAll();
        }
        super.onDestroy();
    }

    private void applyPrefsToUi() {
        Tab t = tabs == null ? null : tabs.current();
        if (t != null && !editingUrl) setUrlDisplay(t.url);
        updateNav();
        updateTabsButton();
    }

    // ---------------- 导航 ----------------

    private void submitUrl() {
        String raw = urlBar.getText().toString().trim();
        if (raw.isEmpty()) return;
        String url = normalize(raw);
        openUrl(url);
        exitEditMode();
    }

    void openUrl(String url) {
        Tab t = tabs.current();
        if (t == null) {
            tabs.open(url, false, true);
        } else {
            t.load(url);
        }
    }

    static String normalize(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")
                || low.startsWith("file://") || low.startsWith("about:")
                || low.startsWith("data:") || low.startsWith("content://")) {
            return s;
        }
        if (!s.contains(" ") && s.contains(".") && s.indexOf('.') > 0
                && s.indexOf('.') < s.length() - 1) {
            return "https://" + s;
        }
        if (s.equals("localhost") || s.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?.*")) {
            return "http://" + s;
        }
        return App.get().prefs().searchUrl(s);
    }

    private void updateNav() {
        Tab t = tabs.current();
        boolean canB = t != null && t.canGoBack();
        boolean canF = t != null && t.canGoForward();
        btnBack.setAlpha(canB ? 1f : 0.3f);
        btnForward.setAlpha(canF ? 1f : 0.3f);
        btnBack.setEnabled(canB);
        btnForward.setEnabled(canF);
    }

    private void updateTabsButton() {
        int n = tabs == null ? 0 : Math.max(1, tabs.size());
        String txt = "▢ " + n;
        Tab t = tabs == null ? null : tabs.current();
        if (t != null && t.incognito) txt = "▢ 无痕";
        btnTabs.setText(txt);
    }

    /** 只读条上显示的内容：域名 + 路径，去掉 www. 与查询串。 */
    static String displayUrl(String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return "";
        try {
            android.net.Uri u = android.net.Uri.parse(url);
            String host = u.getHost();
            if (host == null) return url;
            if (host.startsWith("www.")) host = host.substring(4);
            String path = u.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) return host;
            return host + path;
        } catch (Exception e) {
            return url;
        }
    }

    private void setUrlDisplay(String url) {
        if (urlText != null) urlText.setText(displayUrl(url));
    }

    // ---------------- 地址栏：只读条 ⇄ 输入态 ----------------

    private void enterEditMode() {
        Tab t = tabs == null ? null : tabs.current();
        editingUrl = true;
        animateTopBar(false);
        urlView.setVisibility(View.GONE);
        urlBar.setVisibility(View.VISIBLE);
        urlBar.setText(t == null || t.url.startsWith("about:") ? "" : t.url);
        urlBar.requestFocus();
        urlBar.setSelection(urlBar.getText().length());
        showKeyboard(urlBar);
    }

    private void exitEditMode() {
        editingUrl = false;
        hideKeyboard();
        urlBar.clearFocus();
        urlBar.setVisibility(View.GONE);
        urlView.setVisibility(View.VISIBLE);
        Tab t = tabs == null ? null : tabs.current();
        setUrlDisplay(t == null ? "" : t.url);
    }

    /** 滚动时收起 / 展开顶部地址条（只动高度，不遮挡页面）。 */
    private void animateTopBar(final boolean collapse) {
        if (topBar == null || urlCollapsed == collapse) return;
        urlCollapsed = collapse;
        final int full = Ui.dp(this, 52);
        final int from = topBar.getHeight() > 0 ? topBar.getHeight() : (collapse ? full : 0);
        final int to = collapse ? 0 : full;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(from, to);
        a.setDuration(180);
        a.addUpdateListener(an -> {
            ViewGroup.LayoutParams lp = topBar.getLayoutParams();
            lp.height = (int) an.getAnimatedValue();
            topBar.setLayoutParams(lp);
        });
        a.start();
    }

    // ---------------- Tab.Host ----------------

    @Override
    public void onScroll(Tab t, int dy) {
        if (t != tabs.current() || editingUrl) return;
        if (dy > 8) animateTopBar(true);
        else if (dy < -8) animateTopBar(false);
    }

    @Override
    public void onPageStarted(Tab t) {
        animateTopBar(false);
        t.sniff.clear();
        t.hasVideo = false;
        t.videoDuration = -1;
        if (t != tabs.current()) return;
        if (!editingUrl) setUrlDisplay(t.url);
        progress.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        updateNav();
    }

    @Override
    public void onPageFinished(Tab t) {
        if (t == tabs.current()) {
            progress.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            refresh.setRefreshing(false);
            if (t.internalPage) urlText.setText("隐私自检");
            else if (!editingUrl) setUrlDisplay(t.url);
            updateSafeBadge();
            updateNav();
        }
        if (!t.incognito) db.addHistory(t.title, t.url);

        // 注入：外观清理 → 夜间 → 桌面视口 → 用户脚本
        t.web.evaluateJavascript(Js.cosmetic(App.get().adblock().cosmeticCss()), null);
        if (prefs.night()) t.web.evaluateJavascript(Js.night(true), null);
        if (t.desktop) t.web.evaluateJavascript(Js.desktopViewport(), null);
        String code = scripts.codeFor(t.url);
        if (!code.isEmpty()) t.web.evaluateJavascript(code, null);
        if (prefs.sniff()) {
            t.web.evaluateJavascript(Js.sniffer(t.bridgeToken()),
                    v -> android.util.Log.d("ZBCONSOLE", "sniffer=" + v));
        }
        if (prefs.adSkip()) {
            t.web.evaluateJavascript(Js.adSkip(t.bridgeToken()),
                    v -> android.util.Log.d("ZBCONSOLE", "adskip=" + v));
        }
        if (prefs.night() || t.desktop) {
            t.web.postDelayed(() -> {
                if (prefs.night()) t.web.evaluateJavascript(Js.night(true), null);
                if (t.desktop) t.web.evaluateJavascript(Js.desktopViewport(), null);
            }, 400);
        }
    }

    @Override
    public void onProgress(Tab t, int p) {
        if (t != tabs.current()) return;
        progress.setProgress(p);
        if (p >= 100) {
            progress.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onTitle(Tab t, String title) {
        if (t == tabs.current()) updateTabsButton();
    }

    @Override
    public void onDownload(Tab t, String url, String ua, String cd, String mime, long len) {
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            Ui.toast(this, "请先授予存储权限后重新下载");
            return;
        }
        try {
            String name = Downloader.enqueue(this, url, ua, cd, mime, t == null ? null : t.url);
            Ui.toast(this, "已加入下载：" + name);
        } catch (Exception e) {
            Ui.toast(this, "下载失败：" + e.getMessage());
        }
    }

    @Override
    public void onLongPress(Tab t, WebView.HitTestResult r) {
        int type = r.getType();
        final String extra = r.getExtra();
        List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE && extra != null) {
            items.add("在新标签打开"); actions.add(() -> tabs.open(extra, false, true));
            items.add("复制链接"); actions.add(() -> copy(extra));
            items.add("分享链接"); actions.add(() -> share(extra));
            items.add("下载链接"); actions.add(() -> onDownload(t, extra, null, null, null, 0));
        } else if (type == WebView.HitTestResult.IMAGE_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            if (extra != null) {
                items.add("查看图片"); actions.add(() -> tabs.open(extra, false, true));
                items.add("下载图片"); actions.add(() -> onDownload(t, extra, null, null, "image/*", 0));
                items.add("复制图片地址"); actions.add(() -> copy(extra));
            }
        }
        items.add("复制页面地址"); actions.add(() -> copy(t.url));
        items.add("分享页面"); actions.add(() -> share(t.url));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setItems(items.toArray(new String[0]), (d, i) -> actions.get(i).run())
                .show();
    }

    @Override
    public Tab createTabForWindow() {
        return tabs.openBlankForWindow();
    }

    @Override
    public void requestPermissions(String[] resources) {
        Ui.toast(this, "网页请求系统权限，已按隐私策略拒绝");
    }

    @Override
    public void onSslWarning(Tab t, android.webkit.SslErrorHandler handler, String url) {
        handler.cancel();
        Ui.toast(this, "证书校验失败，已阻止访问");
    }

    @Override
    public void onTabsChanged() {
        Tab t = tabs.current();
        if (t != null) t.sniff.setListener(this::updateSniffFab);
        updateTabsButton();
        updateNav();
        updateSniffFab();
        updateSafeBadge();
    }

    private void updateSafeBadge() {
        if (urlShield == null) return;
        Tab t = tabs == null ? null : tabs.current();
        boolean on = t != null && t.safeMode;
        urlShield.setTextColor(getColor(on ? R.color.accent : R.color.text_dim));
        urlShield.setAlpha(on ? 1f : 0.55f);
    }

    void openSafeCheck() {
        Tab t = tabs.current();
        if (t == null) return;
        t.loadInternal(SafeJs.checkPage(t.safeBlocked));
    }

    private void showSafeSheet() {
        final Tab t = tabs.current();
        if (t == null) return;
        final String host = MediaSniffer.hostOf(t.url);
        final boolean on = prefs.safeMode();
        final boolean allowed = prefs.safeAllowedFor(host);

        final com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 18));

        TextView title = Ui.text(this, "安全模式（严格）", 17, getColor(R.color.text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 4));
        box.addView(title);

        TextView desc = Ui.text(this,
                "伪装设备信息、拒绝定位/传感器/媒体设备等敏感接口、阻断 WebRTC 与追踪 beacon，"
                        + "并对 Canvas / WebGL / 音频指纹加噪。", 12, getColor(R.color.text_sub));
        desc.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 10));
        desc.setLineSpacing(0, 1.25f);
        box.addView(desc);

        box.addView(safeRow(on ? "已开启（全站）" : "已关闭", on ? "点击关闭" : "点击开启", () -> {
            prefs.safeMode(!on);
            sheet.dismiss();
            if (tabs.current() != null) tabs.current().reload();
            updateSafeBadge();
        }));
        box.addView(safeRow(host.isEmpty() ? "当前页面" : host,
                allowed ? "已在例外（不启用）" : "受保护 · 点击加入例外", () -> {
                    prefs.setSafeAllow(host, !allowed);
                    sheet.dismiss();
                    if (tabs.current() != null) tabs.current().reload();
                    updateSafeBadge();
                }));
        box.addView(safeRow("本页已拦截 API 调用", t.safeBlocked + " 次", () -> {
            sheet.dismiss();
            openSafeCheck();
        }));

        sheet.setContentView(box);
        sheet.show();
        // 关闭面板后刷新一次徽标
        sheet.setOnDismissListener(d -> updateSafeBadge());
    }

    private View safeRow(String title, String value, Runnable action) {
        LinearLayout row = Ui.row(this);
        TextView t = Ui.text(this, title, 14.5f, getColor(R.color.text));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(this, value, 12, getColor(R.color.text_sub));
        v.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        row.addView(v);
        row.setOnClickListener(x -> {
            if (menuDialog != null && menuDialog.isShowing()) menuDialog.dismiss();
            action.run();
        });
        return row;
    }

    private void updateSniffFab() {
        if (sniffFab == null) return;
        Tab t = tabs == null ? null : tabs.current();
        if (t == null || !prefs.sniff() || !prefs.sniffFab() || t.inCinema || t.sniff.count() == 0) {
            sniffFab.setVisibility(View.GONE);
            return;
        }
        sniffFab.setText((t.hasVideo ? "▶ " : "◎ ") + t.sniff.count());
        sniffFab.setVisibility(View.VISIBLE);
    }

    void openInNewTab(String url) {
        if (url != null && !url.isEmpty()) tabs.open(url, false, true);
    }

    void copyText(String s) { copy(s); }

    void enterCinema() {
        Tab t = tabs.current();
        if (t == null) return;
        PlaybackStore store = new PlaybackStore(this);
        PlaybackStore.Entry e = store.get("page:" + t.url);
        long resume = PlaybackStore.worthResume(e) ? e.pos : 0;
        t.web.evaluateJavascript(Js.cinema(prefs.adSkip(), t.bridgeToken(), resume), v -> {
            String r = v == null ? "" : v.replace("\"", "");
            if (r.contains("novideo")) Ui.toast(this, "本页没有可接管的视频");
            else if (r.contains("err")) Ui.toast(this, "影院模式失败：" + r);
            else if (!r.contains("already")) Ui.toast(this, "已进入影院模式，点屏幕调出控制条");
        });
    }

    // ---------------- 菜单 ----------------

    private void showMenu() {
        final Tab t = tabs.current();
        final boolean marked = t != null && db.isBookmarked(t.url);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 14));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        addCell(grid, "＋", "新标签", () -> tabs.open("about:blank", false, true));
        addCell(grid, "★", "书签", () -> startActivityForResult(
                new Intent(this, LibraryActivity.class).putExtra("mode", "bookmark"), REQ_LIBRARY));
        addCell(grid, "◷", "历史", () -> startActivityForResult(
                new Intent(this, LibraryActivity.class).putExtra("mode", "history"), REQ_LIBRARY));
        addCell(grid, "⤓", "下载", this::openDownloads);
        addCell(grid, "⚙", "设置", () -> startActivityForResult(
                new Intent(this, SettingsActivity.class), REQ_SETTINGS));
        addCell(grid, "⤴", "分享", () -> { if (t != null) share(t.url); });
        addCell(grid, marked ? "★" : "☆", marked ? "取消书签" : "加书签", this::toggleBookmark);
        addCell(grid, "☰", "阅读", this::readerMode);
        addCell(grid, prefs.night() ? "☾" : "☀", prefs.night() ? "日间" : "夜间", this::toggleNight);
        addCell(grid, "▭", "桌面版", this::toggleDesktop);
        addCell(grid, "⌕", "查找", this::openFind);
        addCell(grid, "◐", "无痕", this::openIncognito);
        addCell(grid, "ⓘ", "页面信息", this::pageInfo);
        addCell(grid, "⟳", "刷新", () -> { if (t != null) t.reload(); });
        addCell(grid, "◎", "资源", () -> SnifferSheet.show(this, tabs.current()));
        addCell(grid, "▶", "影院", this::enterCinema);

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.setContentView(body);
        sheet.setOnDismissListener(x -> menuDialog = null);
        menuDialog = sheet;
        sheet.show();
    }

    private void addCell(GridLayout grid, String glyph, String label, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        cell.setBackgroundResource(android.R.drawable.list_selector_background);

        TextView g = Ui.text(this, glyph, 19, getColor(R.color.text));
        g.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 11f, getColor(R.color.text_sub));
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(this, 6), 0, 0);
        cell.addView(g);
        cell.addView(l);
        cell.setOnClickListener(v -> {
            if (menuDialog != null && menuDialog.isShowing()) menuDialog.dismiss();
            action.run();
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = -2;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        grid.addView(cell, lp);
    }

    // ---------------- 功能 ----------------

    private void toggleBookmark() {
        Tab t = tabs.current();
        if (t == null) return;
        if (db.isBookmarked(t.url)) {
            Ui.confirm(this, "取消书签", "移除此页面的书签？", (d, w) -> {
                db.removeBookmark(t.url);
                Ui.toast(this, "已移除");
            });
        } else {
            db.addBookmark(t.title, t.url);
            Ui.toast(this, "已添加书签");
        }
    }

    private void readerMode() {
        Tab t = tabs.current();
        if (t == null) return;
        t.web.evaluateJavascript(Js.reader(prefs.readerSize()), v -> {
            if (v != null && v.contains("empty")) Ui.toast(this, "没找到正文，试试别的页面");
            else if (v != null && v.contains("err")) Ui.toast(this, "提取失败");
            else Ui.toast(this, "已进入阅读模式（刷新可退出）");
        });
    }

    private void toggleNight() {
        boolean on = !prefs.night();
        prefs.night(on);
        Tab t = tabs.current();
        if (t != null) t.web.evaluateJavascript(Js.night(on), null);
        Ui.toast(this, on ? "夜间模式已开" : "夜间模式已关");
    }

    private void toggleDesktop() {
        Tab t = tabs.current();
        if (t == null) return;
        t.desktop = !t.desktop;
        String host = host(t.url);
        if (host != null) prefs.setDesktopFor(host, t.desktop);
        t.applyUserAgent();
        t.reload();
        Ui.toast(this, t.desktop ? "已切换桌面版 UA" : "已切回手机版 UA");
    }

    private void openIncognito() {
        tabs.open("about:blank", true, true);
        Ui.toast(this, "无痕标签：不记录历史，退出后不保留缓存");
    }

    private void openFind() {
        findBar.setVisibility(View.VISIBLE);
        findVisible = true;
        findInput.requestFocus();
        showKeyboard(findInput);
    }

    private void closeFind() {
        findBar.setVisibility(View.GONE);
        findVisible = false;
        findInput.setText("");
        Tab t = tabs.current();
        if (t != null) t.web.clearMatches();
        hideKeyboard();
    }

    private void doFind(String q) {
        Tab t = tabs.current();
        if (t == null) return;
        if (q == null || q.isEmpty()) {
            t.web.clearMatches();
            findCount.setText("");
            return;
        }
        t.web.findAllAsync(q);
    }

    private void findNext() {
        Tab t = tabs.current();
        if (t != null) t.web.findNext(true);
    }

    private void findPrev() {
        Tab t = tabs.current();
        if (t != null) t.web.findNext(false);
    }

    private void openDownloads() {
        startActivity(new Intent(this, DownloadsActivity.class));
    }

    private void pageInfo() {
        Tab t = tabs.current();
        if (t == null) return;
        t.web.evaluateJavascript(Js.pageInfo(), v -> {
            String s = v == null ? "" : v.replace("\"", "");
            String[] p = s.split("\\|");
            String msg = "标题：" + (p.length > 0 ? p[0] : "")
                    + "\n地址：" + (p.length > 1 ? p[1] : "")
                    + "\n正文字数：" + (p.length > 2 ? p[2] : "0")
                    + "\n链接数：" + (p.length > 3 ? p[3] : "0")
                    + "\n图片数：" + (p.length > 4 ? p[4] : "0");
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("页面信息").setMessage(msg)
                    .setPositiveButton("好", null).show();
        });
    }

    private void share(String url) {
        if (url == null || url.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(i, "分享链接"));
    }

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", s));
            Ui.toast(this, "已复制");
        }
    }

    // ---------------- 手势 ----------------

    private float gDownX, gDownY;
    private boolean gEdge;

    /**
     * 边缘滑动前进/后退：只观察、不拦截，避免影响页面自身滚动。
     * 从屏幕左侧 24dp 内向右滑 → 后退；右侧向左滑 → 前进。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (prefs != null && prefs.gestures()) {
            int edge = Ui.dp(this, 24);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gDownX = e.getX();
                    gDownY = e.getY();
                    gEdge = gDownX < edge || gDownX > getWindow().getDecorView().getWidth() - edge;
                    break;
                case MotionEvent.ACTION_UP:
                    if (gEdge) {
                        float dx = e.getX() - gDownX;
                        float dy = e.getY() - gDownY;
                        gEdge = false;
                        if (Math.abs(dx) > Ui.dp(this, 70) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            Tab t = tabs.current();
                            if (t != null) {
                                if (dx > 0) t.goBack(); else t.goForward();
                                updateNav();
                                return true;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    // ---------------- 工具 ----------------

    static String host(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void showKeyboard(View v) {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void onBackPressed() {
        if (editingUrl) { exitEditMode(); return; }
        if (findVisible) { closeFind(); return; }
        Tab t = tabs.current();
        if (t != null && t.canGoBack()) { t.goBack(); updateNav(); return; }
        if (tabs.size() > 1) { tabs.closeCurrent(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBackTime < 2000) {
            super.onBackPressed();
        } else {
            lastBackTime = now;
            Ui.toast(this, "再按一次退出");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LIBRARY && res == RESULT_OK && data != null) {
            String url = data.getStringExtra("url");
            if (url != null) openUrl(url);
        } else if (req == REQ_SETTINGS) {
            Tab t = tabs.current();
            if (t != null) {
                t.setTextZoom(prefs.textZoom());
                t.applyUserAgent();
            }
        }
    }

    /** 供其他界面安全地取到当前实例（弱引用，避免泄漏）。 */
    static MainActivity current() {
        return INSTANCE == null ? null : INSTANCE.get();
    }

    /** 供 TabsActivity 访问当前标签集合。 */
    TabManager tabManager() { return tabs; }
    Prefs prefsRef() { return prefs; }
    Db dbRef() { return db; }
    ScriptManager scriptsRef() { return scripts; }
}
