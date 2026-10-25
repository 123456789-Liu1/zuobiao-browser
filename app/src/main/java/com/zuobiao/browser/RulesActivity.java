package com.zuobiao.browser;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 拦截规则管理：订阅、自定义域名、放行站点、手动导入。 */
public class RulesActivity extends Activity {

    private AdBlocker ab;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        Ui.applyInsets(this);
        ab = App.get().adblock();
        Ui.back(this, findViewById(android.R.id.content));
        container = findViewById(R.id.settingsContainer);
        ((TextView) findViewById(R.id.title)).setText("拦截规则");
        build();
    }

    private void build() {
        container.removeAllViews();

        section("概览");
        valueRow("已拦截", ab.blockedCount() + " 次", this::resetCount);
        valueRow("生效规则", ab.ruleCount() + " 条", () -> Ui.toast(this, "含内置、订阅与自定义域名"));
        valueRow("外观规则", ab.cosmeticCount() + " 条", () ->
                Ui.toast(this, "来自订阅的 ## 选择器，注入为隐藏样式"));

        section("规则订阅（Easylist 子集）");
        List<AdBlocker.Sub> subs = ab.subscriptions();
        for (int i = 0; i < subs.size(); i++) {
            final int idx = i;
            AdBlocker.Sub s = subs.get(i);
            String sub = s.url;
            if (s.count > 0) {
                sub += " · " + s.count + " 条";
                if (s.updatedAt > 0) {
                    sub += " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                            .format(new Date(s.updatedAt));
                }
            }
            valueRow((s.enabled ? "" : "已停用 · ") + s.name, sub, () -> subActions(idx));
        }
        valueRow("＋ 添加订阅地址", "支持 hosts 与 Adblock Plus 语法", this::addSub);
        valueRow("更新全部订阅", "联网拉取最新规则", this::updateAll);

        section("自定义拦截域名");
        for (String h : ab.userRules()) {
            valueRow(h, "点击删除", () -> Ui.confirm(this, "删除规则", "不再拦截 " + h + "？", (d, w) -> {
                ab.removeUserRule(h);
                build();
            }));
        }
        valueRow("＋ 添加拦截域名", "例：ads.example.com", this::addRule);

        section("放行站点");
        for (String h : ab.allowSites()) {
            valueRow(h, "点击取消放行", () -> Ui.confirm(this, "取消放行", "恢复对 " + h + " 的拦截？", (d, w) -> {
                ab.setAllow(h, false);
                build();
            }));
        }
        valueRow("＋ 添加放行站点", "该域名下的资源不再拦截", this::addAllow);

        section("手动导入");
        valueRow("粘贴规则文本", "每行一条，支持 ||domain^ 与 ##selector", this::importText);

        container.addView(Ui.spacer(this, 24));
    }

    // ---------------- 操作 ----------------

    private void resetCount() {
        Ui.confirm(this, "重置统计", "把已拦截计数清零？", (d, w) -> {
            App.get().prefs().blocked(0);
            build();
        });
    }

    private void subActions(int index) {
        AdBlocker.Sub s = ab.subscriptions().get(index);
        String[] items = {s.enabled ? "停用" : "启用", "立即更新", "删除"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(s.name)
                .setItems(items, (d, w) -> {
                    if (w == 0) { ab.toggleSubscription(index); build(); }
                    else if (w == 1) updateOne(index);
                    else {
                        ab.removeSubscription(index);
                        build();
                    }
                }).show();
    }

    private void updateOne(int index) {
        Ui.toast(this, "正在更新…");
        new Thread(() -> {
            boolean ok = ab.updateSubscription(index);
            runOnUiThread(() -> {
                Ui.toast(this, ok ? "已更新" : "更新失败，检查地址或网络");
                build();
            });
        }, "zb-sub").start();
    }

    private void updateAll() {
        List<AdBlocker.Sub> subs = ab.subscriptions();
        if (subs.isEmpty()) { Ui.toast(this, "还没有订阅"); return; }
        Ui.toast(this, "开始更新 " + subs.size() + " 个订阅…");
        new Thread(() -> {
            int ok = 0;
            for (int i = 0; i < ab.subscriptions().size(); i++) {
                if (ab.updateSubscription(i)) ok++;
            }
            final int n = ok;
            runOnUiThread(() -> {
                Ui.toast(this, "更新完成：" + n + " 个");
                build();
            });
        }, "zb-subs").start();
    }

    private void addSub() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, 0, pad, 0);
        EditText name = Ui.input(this, "名称（可留空）", "");
        EditText url = Ui.input(this, "https://example.com/rules.txt", "");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(url);
        final AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("添加订阅")
                .setView(box)
                .setPositiveButton("添加", null)
                .setNegativeButton("取消", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (!u.startsWith("http")) { Ui.toast(this, "请填写 http(s) 地址"); return; }
            ab.addSubscription(name.getText().toString(), u);
            d.dismiss();
            build();
            updateOne(ab.subscriptions().size() - 1);
        });
    }

    private void addRule() {
        EditText e = Ui.input(this, "ads.example.com", "");
        Ui.dialog(this, "添加拦截域名", e, "保存", (d, w) -> {
            String h = e.getText().toString().trim();
            if (!h.isEmpty()) {
                ab.addUserRule(h);
                build();
            }
        }).show();
    }

    private void addAllow() {
        EditText e = Ui.input(this, "example.com", "");
        Ui.dialog(this, "放行站点", e, "保存", (d, w) -> {
            String h = e.getText().toString().trim();
            if (!h.isEmpty()) {
                ab.setAllow(h, true);
                build();
            }
        }).show();
    }

    private void importText() {
        EditText e = Ui.input(this, "! 注释\n||ads.example.com^\nexample.com##.ad-box", "");
        e.setMinLines(6);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Ui.dialog(this, "粘贴规则", e, "导入", (d, w) -> {
            String t = e.getText().toString();
            if (t.trim().isEmpty()) return;
            boolean ok = ab.importRules(t);
            Ui.toast(this, ok ? "已导入并生效" : "导入失败");
            build();
        }).show();
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

    private void valueRow(String title, String value, Runnable action) {
        LinearLayout row = Ui.row(this);
        TextView t = Ui.text(this, title, 15, getColor(R.color.text));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(this, value == null ? "" : value, 12, getColor(R.color.text_sub));
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        v.setMaxWidth(Ui.dp(this, 200));
        row.addView(v);
        row.setOnClickListener(x -> action.run());
        container.addView(row);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.stroke));
        container.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    }
}
