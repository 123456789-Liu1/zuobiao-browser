package com.zuobiao.browser;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 书签 / 历史 列表，支持删除与书签的导入导出。 */
public class LibraryActivity extends AppCompatActivity {

    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;

    private String mode = "bookmark";
    private Db db;
    private ListView list;
    private TextView empty;
    private final List<Db.Item> data = new ArrayList<>();
    private final List<Db.Item> all = new ArrayList<>();
    private String query = "";
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        Ui.applyInsets(this);
        db = App.get().db();
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "bookmark";

        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        ((TextView) findViewById(R.id.title)).setText(
                mode.equals("bookmark") ? R.string.bookmarks : R.string.history);

        TextView action = findViewById(R.id.action);
        if (mode.equals("bookmark")) {
            action.setText("更多");
            action.setOnClickListener(v -> showBookmarkActions());
        } else {
            action.setText("清空");
            action.setOnClickListener(v -> Ui.confirm(this, "清空历史", "将删除全部浏览记录，不可恢复。",
                    (d, w) -> { db.clearHistory(); reload(); }));
        }

        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Db.Item it = data.get(pos);
            Intent r = new Intent();
            r.putExtra("url", it.url);
            setResult(RESULT_OK, r);
            finish();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Db.Item it = data.get(pos);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setItems(new String[]{"删除", "复制链接"}, (d, w) -> {
                        if (w == 0) {
                            if (mode.equals("bookmark")) db.removeBookmark(it.id);
                            else db.deleteHistory(it.id);
                            reload();
                        } else {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                    getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", it.url));
                                Ui.toast(this, "已复制");
                            }
                        }
                    }).show();
            return true;
        });

        android.widget.EditText search = findViewById(R.id.search);
        search.setVisibility(View.VISIBLE);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim();
                applyFilter();
            }
        });

        Ui.back(this, findViewById(android.R.id.content));
        reload();
    }

    private void showBookmarkActions() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setItems(new String[]{"导出为 JSON", "从 JSON 导入"}, (d, w) -> {
                    if (w == 0) {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, "zuobiao-bookmarks.json");
                        startActivityForResult(i, REQ_EXPORT);
                    } else {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_IMPORT);
                    }
                }).show();
    }

    private void reload() {
        all.clear();
        all.addAll(mode.equals("bookmark") ? db.bookmarks() : db.history());
        applyFilter();
    }

    private void applyFilter() {
        data.clear();
        String q = query.toLowerCase(java.util.Locale.ROOT);
        for (Db.Item it : all) {
            if (q.isEmpty()) { data.add(it); continue; }
            String t = it.title == null ? "" : it.title.toLowerCase(java.util.Locale.ROOT);
            String u = it.url == null ? "" : it.url.toLowerCase(java.util.Locale.ROOT);
            if (t.contains(q) || u.contains(q)) data.add(it);
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(mode.equals("bookmark") ? "还没有书签" : "还没有浏览记录");
        if (!all.isEmpty() && data.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("没有匹配的结果");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent dataIntent) {
        super.onActivityResult(req, res, dataIntent);
        if (res != RESULT_OK || dataIntent == null || dataIntent.getData() == null) return;
        Uri uri = dataIntent.getData();
        if (req == REQ_EXPORT) export(uri);
        else if (req == REQ_IMPORT) importFrom(uri);
    }

    private void export(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            JSONArray arr = new JSONArray();
            for (Db.Item it : db.bookmarks()) {
                JSONObject o = new JSONObject();
                o.put("title", it.title);
                o.put("url", it.url);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("app", "zuobiao");
            root.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date()));
            root.put("bookmarks", arr);
            os.write(root.toString().getBytes("UTF-8"));
            Ui.toast(this, "已导出 " + arr.length() + " 条书签");
        } catch (Exception e) {
            Ui.toast(this, "导出失败：" + e.getMessage());
        }
    }

    private void importFrom(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("bookmarks");
            if (arr == null && sb.charAt(0) == '[') arr = new JSONArray(sb.toString());
            if (arr == null) { Ui.toast(this, "文件格式不正确"); return; }
            int n = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String url = o.optString("url");
                if (!url.isEmpty()) { db.addBookmark(o.optString("title", url), url); n++; }
            }
            Ui.toast(this, "已导入 " + n + " 条书签");
            reload();
        } catch (Exception e) {
            Ui.toast(this, "导入失败：" + e.getMessage());
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
            Db.Item it = data.get(i);
            ((TextView) v.findViewById(R.id.title)).setText(it.title);
            String sub = it.url;
            if (mode.equals("history") && it.time > 0) {
                sub = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(it.time))
                        + "  ·  " + it.url;
            }
            ((TextView) v.findViewById(R.id.sub)).setText(sub);
            TextView tail = v.findViewById(R.id.tail);
            tail.setText(mode.equals("bookmark") ? "★" : "");
            return v;
        }
    }
}
