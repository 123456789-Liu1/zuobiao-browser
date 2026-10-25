package com.zuobiao.browser;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 资源嗅探结果面板：分类筛选 + 播放/下载/复制。 */
final class SnifferSheet {

    private SnifferSheet() {}

    static void show(final MainActivity act, final Tab tab) {
        if (tab == null) return;
        final Prefs prefs = App.get().prefs();

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 12));

        // 标题行
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(act, 18), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        final TextView title = Ui.text(act, "", 17, act.getColor(R.color.text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = Ui.text(act, "清空", 13, act.getColor(R.color.accent));
        clear.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 10), Ui.dp(act, 6));
        head.addView(clear);
        root.addView(head);

        // 分类 chips
        final String[] labels = {"全部", "视频", "音频", "图片", "文件"};
        final int[] types = {-1, ResourceItem.VIDEO, ResourceItem.AUDIO, ResourceItem.IMAGE, ResourceItem.FILE};
        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(act, 14), Ui.dp(act, 2), Ui.dp(act, 14), Ui.dp(act, 8));
        final List<TextView> chipViews = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final TextView c = Ui.text(act, labels[i], 12.5f, act.getColor(R.color.text_sub));
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(act, 12), Ui.dp(act, 7), Ui.dp(act, 12), Ui.dp(act, 7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(act, 8);
            chips.addView(c, lp);
            chipViews.add(c);
        }
        root.addView(chips);

        final ListView list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1,
                (int) (act.getResources().getDisplayMetrics().heightPixels * 0.56f));
        root.addView(list, llp);

        final BottomSheetDialog sheet = new BottomSheetDialog(act);
        sheet.setContentView(root);

        final int[] current = {0};
        final Adapter adapter = new Adapter(act, tab, prefs, sheet);
        list.setAdapter(adapter);

        View.OnClickListener chipClick = v -> {
            for (int i = 0; i < chipViews.size(); i++) {
                if (chipViews.get(i) == v) { current[0] = i; break; }
            }
            styleChips(act, chipViews, current[0]);
            adapter.setFilter(types[current[0]]);
            title.setText(headerText(tab, adapter.getCount()));
        };
        for (TextView c : chipViews) c.setOnClickListener(chipClick);
        styleChips(act, chipViews, 0);

        title.setText(headerText(tab, adapter.getCount()));
        clear.setOnClickListener(v -> {
            tab.sniff.clear();
            adapter.reload();
            title.setText(headerText(tab, adapter.getCount()));
        });

        list.setOnItemClickListener((p, v, pos, id) -> {
            ResourceItem it = adapter.getItemAt(pos);
            if (it == null) return;
            open(act, tab, it, sheet, prefs);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            final ResourceItem it = adapter.getItemAt(pos);
            if (it == null) return true;
            final String[] items = {"播放 / 预览", "新标签打开", "复制链接", "下载"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                    .setTitle(it.name == null ? "资源" : it.name)
                    .setItems(items, (d, w) -> {
                        switch (w) {
                            case 0: open(act, tab, it, sheet, prefs); break;
                            case 1: act.openInNewTab(it.url); sheet.dismiss(); break;
                            case 2: act.copyText(it.url); break;
                            default: download(act, tab, it);
                        }
                    }).show();
            return true;
        });

        sheet.show();
    }

    private static String headerText(Tab tab, int shown) {
        int total = tab.sniff.count();
        String s = "资源 · " + total;
        if (tab.skippedAds > 0) s += "　已跳过广告 " + tab.skippedAds;
        if (shown != total) s += "　当前 " + shown;
        return s;
    }

    private static void styleChips(MainActivity act, List<TextView> chips, int selected) {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            boolean on = i == selected;
            c.setTextColor(on ? Color.parseColor("#0B1220") : act.getColor(R.color.text_sub));
            c.setBackground(Ui.round(on ? act.getColor(R.color.accent) : act.getColor(R.color.surface2),
                    on ? 0 : act.getColor(R.color.stroke), 16, act));
        }
    }

    private static void open(MainActivity act, Tab tab, ResourceItem it,
                             BottomSheetDialog sheet, Prefs prefs) {
        if (it.playable()) {
            sheet.dismiss();
            String cookie = android.webkit.CookieManager.getInstance().getCookie(it.url);
            String ua = tab.web.getSettings().getUserAgentString();
            act.startActivity(PlayerActivity.intent(act, it.url,
                    it.name == null ? "播放" : it.name, tab.url, ua, cookie));
            return;
        }
        if (it.type == ResourceItem.VIDEO || it.type == ResourceItem.AUDIO) {
            // blob: 或无法直链 —— 交给影院模式接管页面里的播放器
            sheet.dismiss();
            act.enterCinema();
            return;
        }
        if (it.type == ResourceItem.IMAGE) {
            sheet.dismiss();
            act.openInNewTab(it.url);
            return;
        }
        download(act, tab, it);
    }

    private static void download(MainActivity act, Tab tab, ResourceItem it) {
        if (it.url != null && it.url.toLowerCase(Locale.ROOT).contains(".m3u8")) {
            if (HlsDownloader.isBusy()) { Ui.toast(act, "已有下载任务在进行"); return; }
            sheetToast(act, "开始下载 HLS，可在通知栏查看进度");
            HlsDownloader.start(act, it.url, tab.url,
                    tab.web.getSettings().getUserAgentString(),
                    android.webkit.CookieManager.getInstance().getCookie(it.url),
                    (ok, msg) -> Ui.toast(act, msg));
            return;
        }
        try {
            String name = Downloader.enqueue(act, it.url,
                    tab.web.getSettings().getUserAgentString(), null, null, tab.url);
            Ui.toast(act, "已加入下载：" + name);
        } catch (Exception e) {
            Ui.toast(act, "下载失败：" + e.getMessage());
        }
    }

    // ---------------- 适配器 ----------------

    private static void sheetToast(MainActivity act, String s) { Ui.toast(act, s); }

    private static final class Adapter extends BaseAdapter {
        private final MainActivity act;
        private final Tab tab;
        private final Prefs prefs;
        private final BottomSheetDialog sheet;
        private List<ResourceItem> data = new ArrayList<>();
        private int filter = -1;

        Adapter(MainActivity act, Tab tab, Prefs prefs, BottomSheetDialog sheet) {
            this.act = act;
            this.tab = tab;
            this.prefs = prefs;
            this.sheet = sheet;
            reload();
        }

        void setFilter(int type) { filter = type; reload(); }

        void reload() {
            data = filter < 0 ? tab.sniff.list() : tab.sniff.list(filter);
            notifyDataSetChanged();
        }

        ResourceItem getItemAt(int i) {
            return i >= 0 && i < data.size() ? data.get(i) : null;
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : act.getLayoutInflater().inflate(R.layout.item_row, parent, false);
            ResourceItem it = data.get(i);
            TextView title = v.findViewById(R.id.title);
            TextView sub = v.findViewById(R.id.sub);
            TextView tail = v.findViewById(R.id.tail);

            String name = it.name == null || it.name.isEmpty() ? MediaSniffer.hostOf(it.url) : it.name;
            title.setText(name);

            StringBuilder sb = new StringBuilder();
            sb.append(ResourceItem.typeName(it.type));
            if (it.duration > 0) sb.append(" · ").append(MediaSniffer.humanDuration(it.duration));
            if (it.width > 0 && it.height > 0) sb.append(" · ").append(it.width).append("x").append(it.height);
            String host = MediaSniffer.hostOf(it.url);
            if (!host.isEmpty()) sb.append(" · ").append(host);
            if (it.blob) sb.append(" · blob");
            sub.setText(sb.toString());

            if (it.playable()) {
                tail.setText("▶");
                tail.setTextColor(act.getColor(R.color.accent));
            } else if (it.type == ResourceItem.IMAGE) {
                tail.setText("◫");
                tail.setTextColor(act.getColor(R.color.text_dim));
            } else {
                tail.setText("⤓");
                tail.setTextColor(act.getColor(R.color.text_dim));
            }
            return v;
        }
    }
}
