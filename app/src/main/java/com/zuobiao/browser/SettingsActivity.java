package com.zuobiao.browser;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.CompoundButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;

/** 设置页：全部用代码拼装，改一项立刻落到 Prefs。 */
public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        Ui.applyInsets(this);
        prefs = App.get().prefs();
        container = findViewById(R.id.settingsContainer);
        Ui.back(this, findViewById(android.R.id.content));
        build();

        android.content.IntentFilter f = new android.content.IntentFilter(
                android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dlReceiver, f, android.content.Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(dlReceiver, f);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(dlReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void build() {
        container.removeAllViews();

        section("浏览");
        valueRow("搜索引擎", Prefs.ENGINES[Math.min(prefs.engine(), Prefs.ENGINES.length - 1)],
                this::pickEngine);
        valueRow("主页", prefs.home().equals("about:blank") ? "空白页" : prefs.home(), () -> {
            EditText e = Ui.input(this, "https://…", prefs.home().equals("about:blank") ? "" : prefs.home());
            Ui.dialog(this, "主页", e, "保存", (d, w) -> {
                String v = e.getText().toString().trim();
                prefs.home(v.isEmpty() ? "about:blank" : MainActivity.normalize(v));
                build();
            }).show();
        });
        valueRow("默认 UA", uaLabel(), this::pickUa);
        switchRow("启动时恢复上次标签", prefs.restore(), (btn, v) -> prefs.restore(v));

        section("外观");
        switchRow("显示地址栏（顶部）", prefs.showUrlBar(), (btn, v) -> prefs.showUrlBar(v));
        switchRow("夜间模式（网页反色）", prefs.night(), (btn, v) -> prefs.night(v));
        valueRow("阅读模式字号", prefs.readerSize() + " px", () -> {
            final int[] sizes = {14, 16, 18, 20, 22, 24, 26};
            String[] items = new String[sizes.length];
            for (int i = 0; i < sizes.length; i++) items[i] = sizes[i] + " px";
            int cur = 2;
            for (int i = 0; i < sizes.length; i++) if (sizes[i] == prefs.readerSize()) cur = i;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("阅读模式字号")
                    .setSingleChoiceItems(items, cur, (d, w) -> {
                        prefs.readerSize(sizes[w]);
                        d.dismiss();
                        build();
                    }).show();
        });
        valueRow("网页文字缩放", prefs.textZoom() + "%", () -> {
            final int[] zooms = {80, 90, 100, 110, 125, 150};
            String[] items = new String[zooms.length];
            for (int i = 0; i < zooms.length; i++) items[i] = zooms[i] + "%";
            int cur = 2;
            for (int i = 0; i < zooms.length; i++) if (zooms[i] == prefs.textZoom()) cur = i;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("网页文字缩放")
                    .setSingleChoiceItems(items, cur, (d, w) -> {
                        prefs.textZoom(zooms[w]);
                        d.dismiss();
                        build();
                    }).show();
        });
        switchRow("边缘滑动前进 / 后退", prefs.gestures(), (btn, v) -> prefs.gestures(v));

        section("拦截与隐私");
        switchRow("广告拦截", prefs.adblock(), (btn, v) -> prefs.adblock(v));
        valueRow("拦截规则与订阅", "已拦截 " + App.get().adblock().blockedCount() + " 次 · "
                + App.get().adblock().ruleCount() + " 条规则",
                () -> startActivity(new Intent(this, RulesActivity.class)));
        valueRow("清除浏览数据", "Cookie / 缓存 / 历史", this::clearData);

        section("隐私与安全");
        switchRow("安全模式（严格）", prefs.safeMode(), (btn, v) -> prefs.safeMode(v));
        valueRow("站点例外", prefs.safeAllow().size() + " 个站点不启用",
                this::showSafeExceptions);
        valueRow("隐私自检", "回到网页点地址栏「安」查看指纹", () ->
                Ui.toast(this, "在网页里点地址栏左侧的「安」即可打开自检"));

        section("视频与资源");
        switchRow("资源嗅探（网页媒体 / 图片 / 文件）", prefs.sniff(), (btn, v) -> prefs.sniff(v));
        switchRow("显示嗅探悬浮按钮", prefs.sniffFab(), (btn, v) -> prefs.sniffFab(v));
        switchRow("自动跳过广告（只点“跳过广告”按钮）", prefs.adSkip(), (btn, v) -> prefs.adSkip(v));
        valueRow("影院模式", "接管页面里的播放器", this::enterCinemaDemo);

        section("内容");
        valueRow("书签", "", () -> startActivity(new Intent(this, LibraryActivity.class)
                .putExtra("mode", "bookmark")));
        valueRow("历史记录", "", () -> startActivity(new Intent(this, LibraryActivity.class)
                .putExtra("mode", "history")));
        valueRow("用户脚本", App.get().scripts().all().size() + " 个",
                () -> startActivity(new Intent(this, ScriptsActivity.class)));
        valueRow("下载管理", "含 m3u8 分片下载", () ->
                startActivity(new Intent(this, DownloadsActivity.class)));

        section("网络");
        switchRow("加载超时自动救援", prefs.rescue(), (btn, v) -> prefs.rescue(v));
        switchRow("省流模式（不加载图片）", prefs.liteMode(), (btn, v) -> prefs.liteMode(v));
        valueRow("网络代理", ProxyManager.summary(), this::configProxy);
        if (prefs.proxyEnabled()) {
            valueRow("关闭代理", "点此停用，改为直连", () -> {
                prefs.proxyEnabled(false);
                ProxyManager.apply(this, () -> {
                    Ui.toast(this, "已关闭代理");
                    build();
                });
            });
        }

        section("本地代理核心（实验）");
        valueRow("内核状态", ProxyCore.available()
                        ? (ProxyCore.running()
                        ? "运行中 · " + ProxyCore.version()
                        : "已就绪 · " + ProxyCore.version())
                        : "未打包进本安装包",
                () -> Ui.toast(this, ProxyCore.available()
                        ? "SOCKS 监听 127.0.0.1:" + prefs.corePort()
                        : "当前 APK 不含本地内核，需要带 xraymobile AAR 的构建"));
        if (ProxyCore.available()) {
            valueRow(ProxyCore.running() ? "停止内核" : "启动内核",
                    "SOCKS · 127.0.0.1:" + prefs.corePort(), this::toggleCore);
            valueRow("一键启用", "启动内核并让浏览器走本地代理", this::useCoreProxy);
            valueRow("内核配置", "Xray JSON，可粘贴自己的节点", this::editCoreConfig);
        }

        section("数据与备份");
        String wd = prefs.webdavUrl();
        valueRow("WebDAV 备份地址", wd.isEmpty() ? "未配置" : wd, this::configWebdav);
        valueRow("立即备份", "书签 / 脚本 / 规则 / 设置", () -> {
            if (prefs.webdavUrl().isEmpty()) { configWebdav(); return; }
            Ui.toast(this, "正在上传…");
            WebDavBackup.upload(this, wdTarget(), prefs.webdavUser(), prefs.webdavPass(),
                    (ok, msg) -> Ui.toast(this, msg));
        });
        valueRow("从备份恢复", "合并书签与脚本，覆盖设置", () -> {
            if (prefs.webdavUrl().isEmpty()) { configWebdav(); return; }
            Ui.confirm(this, "从备份恢复", "会用备份里的设置覆盖当前设置，书签与脚本为合并。继续？", (d, w) -> {
                Ui.toast(this, "正在下载…");
                WebDavBackup.restore(this, wdTarget(), prefs.webdavUser(), prefs.webdavPass(),
                        (ok, msg) -> { Ui.toast(this, msg); build(); });
            });
        });

        section("关于");
        String verName = "1.0.0";
        try {
            android.content.pm.PackageInfo pi = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            if (pi != null && pi.versionName != null) verName = pi.versionName;
        } catch (Exception ignored) {
        }
        valueRow("版本", verName + " · WebView 内核", () -> { });
        valueRow("检查更新", "查询 GitHub 最新正式版", this::checkUpdate);
        valueRow("关于坐标浏览器", "WebView 内核 · 本地优先", () ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("坐标浏览器")
                        .setMessage("一个把常用能力做实的轻浏览器：\n"
                                + "· 广告拦截（host 规则 + 站点放行 + 自定义）\n"
                                + "· 用户脚本（站点匹配注入）\n"
                                + "· 阅读模式 / 夜间模式 / 桌面版 UA\n"
                                + "· 多标签、书签、历史、下载、页内查找\n"
                                + "· 书签 JSON 导入导出、无痕标签\n\n"
                                + "所有数据保存在本机，不联网上报。")
                        .setPositiveButton("好", null).show());

        container.addView(Ui.spacer(this, 20));
    }

    // ---------------- 行 ----------------

    private void section(String title) {
        TextView t = Ui.text(this, title, 11.5f, getColor(R.color.text_dim));
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 8));
        container.addView(t);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.stroke));
        container.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    }

    private void switchRow(String title, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(title);
        sw.setTextSize(15);
        sw.setTextColor(getColor(R.color.text));
        sw.setChecked(checked);
        sw.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        sw.setOnCheckedChangeListener(l);
        container.addView(sw, new LinearLayout.LayoutParams(-1, -2));
        divider();
    }

    private void valueRow(String title, String value, Runnable action) {
        LinearLayout row = Ui.row(this);
        TextView t = Ui.text(this, title, 15, getColor(R.color.text));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(this, value == null ? "" : value, 13, getColor(R.color.text_sub));
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(v, new LinearLayout.LayoutParams(-2, -2));
        TextView arrow = Ui.text(this, "›", 16, getColor(R.color.text_dim));
        arrow.setPadding(Ui.dp(this, 8), 0, 0, 0);
        row.addView(arrow);
        row.setOnClickListener(x -> action.run());
        container.addView(row);
        divider();
    }

    private void divider() {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.stroke));
        container.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    }

    // ---------------- 具体交互 ----------------

    private String uaLabel() {
        switch (prefs.uaMode()) {
            case 1: return "桌面版";
            case 2: return "自定义";
            default: return "手机版";
        }
    }

    private void pickEngine() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("搜索引擎")
                .setSingleChoiceItems(Prefs.ENGINES, prefs.engine(), (d, w) -> {
                    prefs.engine(w);
                    d.dismiss();
                    build();
                }).show();
    }

    private void pickUa() {
        String[] items = {"手机版（跟随 WebView）", "桌面版（Windows Chrome）", "自定义 UA…"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("默认 User-Agent")
                .setSingleChoiceItems(items, prefs.uaMode(), (d, w) -> {
                    d.dismiss();
                    if (w == 2) {
                        EditText e = Ui.input(this, "Mozilla/5.0 …", prefs.customUa());
                        Ui.dialog(this, "自定义 UA", e, "保存", (d2, w2) -> {
                            prefs.customUa(e.getText().toString().trim());
                            prefs.uaMode(2);
                            build();
                        }).show();
                    } else {
                        prefs.uaMode(w);
                        build();
                    }
                }).show();
    }

    private void showBlockDialog() {
        AdBlocker ab = App.get().adblock();
        int allow = prefs.allowSites().size();
        int rules = ab.userRules().size();
        String[] items = {
                "添加放行站点（当前 " + allow + "）",
                "添加自定义拦截域名（当前 " + rules + "）",
                "清空拦截统计"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("已拦截 " + prefs.blocked() + " 次 · 规则 " + ab.ruleCount() + " 条")
                .setItems(items, (d, w) -> {
                    if (w == 0) addAllow();
                    else if (w == 1) addRule();
                    else {
                        prefs.blocked(0);
                        build();
                        Ui.toast(this, "已清零");
                    }
                }).show();
    }

    private void addAllow() {
        EditText e = Ui.input(this, "example.com", "");
        Ui.dialog(this, "放行站点（不拦截该域名）", e, "保存", (d, w) -> {
            String h = e.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!h.isEmpty()) {
                prefs.setAdblockAllowed(h, true);
                build();
                Ui.toast(this, "已放行 " + h);
            }
        }).show();
    }

    private void addRule() {
        EditText e = Ui.input(this, "ad.example.com", "");
        Ui.dialog(this, "自定义拦截域名", e, "保存", (d, w) -> {
            String h = e.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!h.isEmpty()) {
                App.get().adblock().addUserRule(h);
                build();
                Ui.toast(this, "已添加规则");
            }
        }).show();
    }

    private String wdTarget() {
        String base = prefs.webdavUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + prefs.webdavName();
    }

    private void configWebdav() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, 0, pad, 0);
        EditText url = Ui.input(this, "https://dav.example.com/remote.php/dav/files/me",
                prefs.webdavUrl());
        EditText user = Ui.input(this, "用户名", prefs.webdavUser());
        EditText pass = Ui.input(this, "密码", prefs.webdavPass());
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText name = Ui.input(this, "文件名", prefs.webdavName());
        box.addView(url);
        box.addView(user);
        box.addView(pass);
        box.addView(name);
        final androidx.appcompat.app.AlertDialog d =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("WebDAV 配置")
                        .setMessage("填目录地址，备份会写到 目录/文件名。密码以明文保存在本机。")
                        .setView(box)
                        .setPositiveButton("保存", null)
                        .setNegativeButton("取消", null)
                        .create();
        d.show();
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (!u.startsWith("http")) { Ui.toast(this, "请填写 http(s) 地址"); return; }
            String n = name.getText().toString().trim();
            prefs.webdav(u, user.getText().toString().trim(),
                    pass.getText().toString(), n.isEmpty() ? "zuobiao-backup.json" : n);
            d.dismiss();
            build();
            Ui.toast(this, "已保存");
        });
    }

    private void enterCinemaDemo() {
        Ui.toast(this, "回到网页后，从菜单点「影院」即可接管当前页面的视频");
    }

    private void toggleCore() {
        if (ProxyCore.running()) {
            ProxyCore.stop();
            Ui.toast(this, "内核已停止");
            build();
            return;
        }
        String err = ProxyCore.start(prefs.coreConfig());
        Ui.toast(this, err.isEmpty() ? "内核已启动" : err);
        build();
    }

    private void useCoreProxy() {
        if (!ProxyCore.running()) {
            String err = ProxyCore.start(prefs.coreConfig());
            if (!err.isEmpty()) {
                Ui.toast(this, err);
                return;
            }
        }
        prefs.proxy("socks", "127.0.0.1", prefs.corePort(), "", "", "");
        prefs.proxyEnabled(true);
        ProxyManager.apply(this, () -> {
            Ui.toast(this, "已启用本地内核代理，刷新页面生效");
            build();
        });
    }

    private void editCoreConfig() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        box.setPadding(pad, 0, pad, 0);

        final EditText port = Ui.input(this, "端口", String.valueOf(prefs.corePort()));
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText cfg = new EditText(this);
        cfg.setText(prefs.coreConfig());
        cfg.setTextSize(12);
        cfg.setTypeface(android.graphics.Typeface.MONOSPACE);
        cfg.setTextColor(getColor(R.color.text));
        cfg.setBackgroundColor(getColor(R.color.surface2));
        cfg.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(cfg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 260));
        slp.topMargin = Ui.dp(this, 8);
        box.addView(port);
        box.addView(sv, slp);

        final AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("内核配置")
                .setMessage("Xray JSON。默认是「SOCKS 入站 + freedom 直连」，"
                        + "把 outbounds 换成自己的 VLESS/VMess 节点即可真正加速。")
                .setView(box)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            prefs.coreConfig(cfg.getText().toString());
            try {
                prefs.corePort(Integer.parseInt(port.getText().toString().trim()));
            } catch (Exception ignored) {
            }
            d.dismiss();
            build();
            Ui.toast(this, ProxyCore.running() ? "已保存，重启内核后生效" : "已保存");
        });
    }

    private void configProxy() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, 0, pad, 0);
        EditText type = Ui.input(this, "类型：http 或 socks", prefs.proxyType());
        EditText host = Ui.input(this, "主机，如 127.0.0.1", prefs.proxyHost());
        EditText port = Ui.input(this, "端口，如 7890",
                prefs.proxyPort() > 0 ? String.valueOf(prefs.proxyPort()) : "");
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText user = Ui.input(this, "用户名（可留空）", prefs.proxyUser());
        EditText pass = Ui.input(this, "密码（可留空）", prefs.proxyPass());
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText bypass = Ui.input(this, "跳过代理（逗号分隔，可留空）", prefs.proxyBypass());
        box.addView(type);
        box.addView(host);
        box.addView(port);
        box.addView(user);
        box.addView(pass);
        box.addView(bypass);

        final AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("网络代理")
                .setMessage("只作用于本应用的网页请求，不改系统网络。SOCKS 需要较新的 WebView；"
                        + "留空用户名密码即为免认证代理。")
                .setView(box)
                .setPositiveButton("保存并启用", null)
                .setNegativeButton("取消", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = type.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!t.equals("socks")) t = "http";
            String h = host.getText().toString().trim();
            int num = 0;
            try {
                num = Integer.parseInt(port.getText().toString().trim());
            } catch (Exception ignored) {
            }
            if (h.isEmpty() || num <= 0) {
                Ui.toast(this, "主机和端口不能为空");
                return;
            }
            prefs.proxy(t, h, num, user.getText().toString().trim(),
                    pass.getText().toString(), bypass.getText().toString().trim());
            prefs.proxyEnabled(true);
            d.dismiss();
            ProxyManager.apply(this, () -> {
                Ui.toast(this, ProxyManager.supported()
                        ? "代理已启用，刷新页面生效"
                        : "当前 WebView 不支持代理");
                build();
            });
        });
    }

    // ---------------- 检查更新 ----------------

    private static final String LATEST_API =
            "https://api.github.com/repos/123456789-Liu1/zuobiao-browser/releases/latest";

    private long dlId = -1;

    private final android.content.BroadcastReceiver dlReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context c, Intent i) {
                    if (i == null || !android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE
                            .equals(i.getAction())) return;
                    long id = i.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id != dlId) return;
                    android.app.DownloadManager dm =
                            (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm == null) return;
                    int status = -1;
                    android.database.Cursor cur = dm.query(
                            new android.app.DownloadManager.Query().setFilterById(id));
                    try {
                        if (cur != null && cur.moveToFirst()) {
                            status = cur.getInt(cur.getColumnIndexOrThrow(
                                    android.app.DownloadManager.COLUMN_STATUS));
                        }
                    } finally {
                        if (cur != null) cur.close();
                    }
                    if (status != android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        Ui.toast(SettingsActivity.this, "下载失败或被取消，可稍后重试");
                        return;
                    }
                    android.net.Uri uri = dm.getUriForDownloadedFile(id);
                    if (uri == null) {
                        Ui.toast(SettingsActivity.this, "找不到已下载的安装包");
                        return;
                    }
                    installApk(uri);
                }
            };

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /** 取前三段数字，忽略 v 前缀与 -core-poc 之类后缀。 */
    static int[] verParts(String s) {
        int[] r = new int[]{0, 0, 0};
        if (s == null) return r;
        String[] p = s.replaceAll("^[vV]", "").split("[^0-9]+");
        int idx = 0;
        for (String x : p) {
            if (x.isEmpty()) continue;
            if (idx >= 3) break;
            try {
                r[idx++] = Integer.parseInt(x);
            } catch (Exception ignored) {
            }
        }
        return r;
    }

    static boolean isNewer(String remote, String local) {
        int[] a = verParts(remote), b = verParts(local);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    private static String readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private void checkUpdate() {
        Ui.toast(this, "正在检查更新…");
        new Thread(() -> {
            String err = null, tag = "", notes = "", apkUrl = "", htmlUrl = "";
            long size = 0;
            try {
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) new java.net.URL(LATEST_API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "ZuobiaoBrowser");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                int code = c.getResponseCode();
                if (code == 403 || code == 429) throw new java.io.IOException("rate");
                if (code != 200) throw new java.io.IOException("http " + code);
                org.json.JSONObject o = new org.json.JSONObject(readAll(c.getInputStream()));
                tag = o.optString("tag_name", "");
                notes = o.optString("body", "");
                htmlUrl = o.optString("html_url", "");
                org.json.JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        org.json.JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "");
                        if (name.endsWith(".apk")) {
                            size = a.optLong("size", 0);
                            apkUrl = a.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                // GitHub 接口限流（60 次/小时/IP）时，退回「发布页跳转」检测：
                // HEAD releases/latest 会 302 到 /releases/tag/<tag>，不占用 API 额度。
                if ("rate".equals(err)) {
                    try {
                        java.net.HttpURLConnection c2 = (java.net.HttpURLConnection)
                                new java.net.URL("https://github.com/123456789-Liu1/zuobiao-browser/releases/latest")
                                        .openConnection();
                        c2.setInstanceFollowRedirects(false);
                        c2.setConnectTimeout(8000);
                        c2.setReadTimeout(10000);
                        c2.setRequestProperty("User-Agent", "ZuobiaoBrowser");
                        int code2 = c2.getResponseCode();
                        String loc = c2.getHeaderField("Location");
                        if (code2 >= 300 && code2 < 400 && loc != null && loc.contains("/tag/")) {
                            tag = loc.substring(loc.lastIndexOf("/tag/") + 5);
                            // 资源名不带 v 前缀：v1.4.0 -> ZuobiaoBrowser-1.4.0.apk
                            String ver = tag.startsWith("v") || tag.startsWith("V")
                                    ? tag.substring(1) : tag;
                            htmlUrl = "https://github.com/123456789-Liu1/zuobiao-browser/releases/tag/" + tag;
                            apkUrl = "https://github.com/123456789-Liu1/zuobiao-browser/releases/download/"
                                    + tag + "/ZuobiaoBrowser-" + ver + ".apk";
                            notes = "（GitHub 接口限流，已改用发布页检测；详细说明请点「打开发布页」）";
                            err = null;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            final String fErr = err, fTag = tag, fNotes = notes, fApk = apkUrl, fHtml = htmlUrl;
            final long fSize = size;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fErr != null) {
                    if ("rate".equals(fErr)) {
                        Ui.toast(this, "GitHub 请求过于频繁，请过一会儿再试");
                    } else {
                        Ui.toast(this, "检查失败：网络不可用或超时，稍后可再试（不影响正常使用）");
                    }
                    return;
                }
                String local = versionName();
                if (!isNewer(fTag, local)) {
                    Ui.toast(this, "已是最新版本（" + local + "）");
                    return;
                }
                showUpdateDialog(fTag, fNotes, fApk, fHtml, fSize);
            });
        }, "zb-update").start();
    }

    private void showUpdateDialog(String tag, String notes, String apkUrl,
                                  String htmlUrl, long size) {
        String desc = notes == null ? "" : notes.trim();
        if (desc.length() > 1500) desc = desc.substring(0, 1500) + "…";
        String msg = "版本：" + tag
                + "\n大小：" + (size > 0 ? (size / 1024 / 1024) + " MB" : "未知")
                + "\n\n" + desc;

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(msg);
        tv.setTextSize(13);
        tv.setTextColor(getColor(R.color.text));
        int p = Ui.dp(this, 16);
        tv.setPadding(p, Ui.dp(this, 8), p, 0);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("发现新版本")
                .setView(sv)
                .setPositiveButton("下载并安装", (d, w) -> startUpdateDownload(apkUrl, tag))
                .setNeutralButton("打开发布页", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(htmlUrl)));
                    } catch (Exception e) {
                        Ui.toast(this, "没有可用的浏览器");
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void startUpdateDownload(String url, String tag) {
        if (url == null || url.isEmpty()) {
            Ui.toast(this, "该版本没有提供 APK 下载");
            return;
        }
        try {
            android.app.DownloadManager dm =
                    (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                Ui.toast(this, "系统下载服务不可用");
                return;
            }
            String name = "ZuobiaoBrowser-" + tag.replaceAll("[^0-9A-Za-z._-]", "") + ".apk";
            android.app.DownloadManager.Request r =
                    new android.app.DownloadManager.Request(android.net.Uri.parse(url))
                            .setTitle("坐标浏览器 " + tag)
                            .setDescription("正在下载更新")
                            .setMimeType("application/vnd.android.package-archive")
                            .setNotificationVisibility(
                                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(
                                    android.os.Environment.DIRECTORY_DOWNLOADS, name);
            dlId = dm.enqueue(r);
            android.util.Log.d("ZBUpd", "enqueued id=" + dlId + " url=" + url);
            Ui.toast(this, "已开始下载，完成后会提示安装");
        } catch (Exception e) {
            android.util.Log.e("ZBUpd", "enqueue failed", e);
            Ui.toast(this, "下载失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
    }

    private void installApk(android.net.Uri uri) {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            Ui.toast(this, "请先允许「安装未知应用」，再点一次检查更新");
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "无法启动安装器，请到「下载」里手动打开");
        }
    }

    private void showSafeExceptions() {
        java.util.Set<String> list = prefs.safeAllow();
        final java.util.List<String> arr = new java.util.ArrayList<>(list);
        String[] items = new String[arr.size() + 1];
        for (int i = 0; i < arr.size(); i++) items[i] = arr.get(i) + "（点击移除）";
        items[arr.size()] = "＋ 添加例外站点";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("安全模式例外")
                .setItems(items, (d, w) -> {
                    if (w == arr.size()) {
                        EditText e = Ui.input(this, "example.com", "");
                        Ui.dialog(this, "添加例外", e, "添加", (dd, ww) -> {
                            String h = e.getText().toString().trim();
                            if (!h.isEmpty()) {
                                java.util.Set<String> s2 = prefs.safeAllow();
                                s2.add(h.toLowerCase(java.util.Locale.ROOT));
                                prefs.setSafeAllow(s2);
                                showSafeExceptions();
                            }
                        }).show();
                    } else {
                        java.util.Set<String> s2 = prefs.safeAllow();
                        s2.remove(arr.get(w));
                        prefs.setSafeAllow(s2);
                        showSafeExceptions();
                    }
                }).show();
    }

    private void clearData() {
        List<String> items = new ArrayList<>();
        items.add("清除 Cookie 与网页数据");
        items.add("清除浏览历史");
        items.add("全部清除");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清除浏览数据")
                .setItems(items.toArray(new String[0]), (d, w) -> Ui.confirm(this, "确认",
                        "该操作不可撤销。", (dd, ww) -> {
                            if (w == 0 || w == 2) {
                                CookieManager.getInstance().removeAllCookies(null);
                                CookieManager.getInstance().flush();
                                WebStorage.getInstance().deleteAllData();
                                WebView wv = new WebView(this);
                                wv.clearCache(true);
                                wv.clearFormData();
                                wv.clearHistory();
                                wv.destroy();
                            }
                            if (w == 1 || w == 2) App.get().db().clearHistory();
                            prefs.session("");
                            Ui.toast(this, "已清除");
                        })).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }
}
