package com.zuobiao.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 下载中心：列出本应用的下载任务，支持打开、删除、重试。 */
public class DownloadsActivity extends Activity {

    private static final class Item {
        long id;
        String title;
        String mime;
        long soFar;
        long total;
        int status;
        Uri localUri;
        String url;
        boolean local;
        String uriString;
        long size;
    }

    private DownloadManager dm;
    private final List<Item> data = new ArrayList<>();
    private Adapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        Ui.applyInsets(this);
        dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        ((TextView) findViewById(R.id.title)).setText("下载");
        TextView action = findViewById(R.id.action);
        action.setText("系统下载");
        action.setOnClickListener(v -> {
            try {
                startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            } catch (Exception e) {
                Ui.toast(this, "系统没有下载界面");
            }
        });

        ListView list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> open(data.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            final Item it = data.get(pos);
            String[] items = {"打开", "复制链接", "删除（含文件）"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(it.title)
                    .setItems(items, (d, w) -> {
                        if (w == 0) open(it);
                        else if (w == 1) {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("url", it.url));
                            Ui.toast(this, "已复制");
                        } else {
                            Ui.confirm(this, "删除", "同时删除已下载的文件？", (dd, ww) -> {
                                if (it.local) {
                                    try {
                                        Uri u = Uri.parse(it.uriString);
                                        if ("content".equals(u.getScheme())) {
                                            getContentResolver().delete(u, null, null);
                                        } else {
                                            new java.io.File(it.uriString).delete();
                                        }
                                    } catch (Exception ignored) {
                                    }
                                    App.get().prefs().removeLocalDownload(it.uriString);
                                } else {
                                    try { dm.remove(it.id); } catch (Exception ignored) { }
                                }
                                reload();
                            });
                        }
                    }).show();
            return true;
        });

        Ui.back(this, findViewById(android.R.id.content));
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        if (dm != null) {
            Cursor c = dm.query(new DownloadManager.Query());
            if (c != null) {
                int iId = c.getColumnIndex(DownloadManager.COLUMN_ID);
                int iTitle = c.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int iMime = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                int iSoFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int iTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int iStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int iLocal = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int iUrl = c.getColumnIndex(DownloadManager.COLUMN_URI);
                while (c.moveToNext()) {
                    Item it = new Item();
                    it.id = c.getLong(iId);
                    it.title = c.getString(iTitle);
                    it.mime = iMime >= 0 ? c.getString(iMime) : "";
                    it.soFar = iSoFar >= 0 ? c.getLong(iSoFar) : 0;
                    it.total = iTotal >= 0 ? c.getLong(iTotal) : -1;
                    it.status = c.getInt(iStatus);
                    String local = iLocal >= 0 ? c.getString(iLocal) : null;
                    it.localUri = local == null ? null : Uri.parse(local);
                    it.url = iUrl >= 0 ? c.getString(iUrl) : "";
                    data.add(it);
                }
                c.close();
            }
        }
        // HLS 合并产物（不在 DownloadManager 里，单独记录）
        try {
            org.json.JSONArray arr = new org.json.JSONArray(App.get().prefs().localDownloads());
            for (int i = arr.length() - 1; i >= 0; i--) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.local = true;
                it.title = o.optString("name");
                it.url = "";
                it.uriString = o.optString("uri");
                it.size = o.optLong("size", -1);
                it.status = DownloadManager.STATUS_SUCCESSFUL;
                it.mime = it.title != null && it.title.endsWith(".mp4") ? "video/mp4" : "video/mp2t";
                data.add(it);
            }
        } catch (Exception ignored) {
        }

        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("还没有下载记录");
    }

    private void open(Item it) {
        if (it.local) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                Uri u = it.uriString != null && it.uriString.startsWith("content:")
                        ? Uri.parse(it.uriString)
                        : Uri.parse(it.uriString == null ? "" : "file://" + it.uriString);
                i.setDataAndType(u, it.mime == null || it.mime.isEmpty() ? "*/*" : it.mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
            } catch (Exception e) {
                Ui.toast(this, "没有能打开 .ts 的应用，文件已存到 " + (it.uriString == null ? "" : it.uriString));
            }
            return;
        }
        if (it.status != DownloadManager.STATUS_SUCCESSFUL || it.localUri == null) {
            Ui.toast(this, "文件还没下载完成");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(it.localUri, it.mime == null || it.mime.isEmpty()
                    ? "*/*" : it.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "没有能打开该文件的应用");
        }
    }

    private static String statusText(Item it) {
        if (it.local) {
            return "m3u8 合并" + (it.size > 0 ? " · " + MediaSniffer.humanSize(it.size) : "");
        }
        switch (it.status) {
            case DownloadManager.STATUS_SUCCESSFUL: return "已完成";
            case DownloadManager.STATUS_RUNNING:
                if (it.total > 0) {
                    return String.format(Locale.ROOT, "下载中 %d%% · %s / %s",
                            it.soFar * 100 / it.total,
                            MediaSniffer.humanSize(it.soFar),
                            MediaSniffer.humanSize(it.total));
                }
                return "下载中 · " + MediaSniffer.humanSize(it.soFar);
            case DownloadManager.STATUS_PAUSED: return "已暂停";
            case DownloadManager.STATUS_PENDING: return "等待中";
            case DownloadManager.STATUS_FAILED: return "失败";
            default: return "";
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return data.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : getLayoutInflater().inflate(R.layout.item_row, parent, false);
            Item it = data.get(i);
            ((TextView) v.findViewById(R.id.title)).setText(it.title);
            String sub = statusText(it);
            String u = it.url == null ? "" : it.url;
            if (!u.isEmpty()) sub += " · " + MediaSniffer.hostOf(u);
            ((TextView) v.findViewById(R.id.sub)).setText(sub);
            TextView tail = v.findViewById(R.id.tail);
            tail.setText(it.status == DownloadManager.STATUS_SUCCESSFUL ? "▶" : "…");
            tail.setTextColor(getColor(it.status == DownloadManager.STATUS_SUCCESSFUL
                    ? R.color.accent : R.color.text_dim));
            return v;
        }
    }
}
