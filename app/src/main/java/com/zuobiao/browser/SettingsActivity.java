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
        valueRow("版本", "1.0.0", () -> { });
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
