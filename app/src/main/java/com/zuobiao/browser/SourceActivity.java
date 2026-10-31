package com.zuobiao.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 查看源代码：渲染后 DOM 与原始响应两种来源。 */
public class SourceActivity extends Activity {

    private static final int MAX_BYTES = 1024 * 1024;

    private TextView code, title, modeText, findCount;
    private EditText findInput;
    private LinearLayout findRow;
    private ScrollView scroll;
    private ProgressBar progress;

    private String text = "";
    /** 实际渲染到 TextView 的部分：大页面只显示头部，避免主线程 layout 卡死。 */
    private String shown = "";
    private static final int MAX_SHOW = 120000;
    private String url = "";
    private String ua = "";
    private boolean dom;
    private final List<Integer> matches = new ArrayList<>();
    private int matchIndex = -1;
    private String query = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_source);
        Ui.applyInsets(this);

        url = getIntent().getStringExtra("url") == null ? "" : getIntent().getStringExtra("url");
        ua = getIntent().getStringExtra("ua");
        dom = "dom".equals(getIntent().getStringExtra("mode"));

        code = findViewById(R.id.code);
        title = findViewById(R.id.title);
        modeText = findViewById(R.id.modeText);
        findCount = findViewById(R.id.findCount);
        findInput = findViewById(R.id.findInput);
        findRow = findViewById(R.id.findRow);
        scroll = findViewById(R.id.scroll);
        progress = findViewById(R.id.progress);

        title.setText(url);
        modeText.setText(dom ? "渲染后 DOM" : "原始响应");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnFind).setOnClickListener(v -> toggleFind());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMore());
        findViewById(R.id.findClose).setOnClickListener(v -> toggleFind());
        findViewById(R.id.findPrev).setOnClickListener(v -> step(-1));
        findViewById(R.id.findNext).setOnClickListener(v -> step(1));

        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString();
                highlight(query);
            }
        });

        load();
    }

    // ---------------- 载入 ----------------

    private void load() {
        progress.setVisibility(View.VISIBLE);
        code.setText("加载中…");
        if (dom) {
            String path = getIntent().getStringExtra("file");
            new Thread(() -> {
                String s;
                try {
                    s = readFile(new File(path));
                } catch (Exception e) {
                    s = "读取失败：" + e.getMessage();
                }
                final String out = s;
                runOnUiThread(() -> render(out));
            }, "zb-src-file").start();
        } else {
            new Thread(() -> {
                String s;
                try {
                    s = fetchRaw();
                } catch (Exception e) {
                    s = "请求失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                            + "\n\n可以试试「渲染后的源码」。";
                }
                final String out = s;
                runOnUiThread(() -> render(out));
            }, "zb-src-net").start();
        }
    }

    private void render(String s) {
        progress.setVisibility(View.GONE);
        text = s == null ? "" : s;
        shown = text.length() > MAX_SHOW
                ? text.substring(0, MAX_SHOW) + "\n\n…（仅显示前 " + MAX_SHOW
                + " 字符，共 " + text.length() + " 字符；复制与保存仍是全文）"
                : text;
        if (!query.isEmpty()) highlight(query);
        else {
            code.setText(shown);
            findCount.setText("");
        }
    }

    private static String readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            return new String(readLimited(in, MAX_BYTES), "UTF-8");
        }
    }

    private String fetchRaw() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        String agent = ua == null || ua.isEmpty()
                ? "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
                : ua;
        c.setRequestProperty("User-Agent", agent);
        String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        c.setRequestProperty("Referer", url);
        int code = c.getResponseCode();
        if (code >= 400) throw new java.io.IOException("HTTP " + code);
        String ctype = c.getContentType();
        try (InputStream in = c.getInputStream()) {
            return decode(readLimited(in, MAX_BYTES), ctype);
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            int remain = max - bos.size();
            if (remain <= 0) break;
            bos.write(buf, 0, Math.min(n, remain));
        }
        return bos.toByteArray();
    }

    private static String decode(byte[] data, String contentType) {
        String cs = null;
        if (contentType != null) {
            int i = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (i >= 0) cs = contentType.substring(i + 8).trim().split("[;\\s]")[0].replace("\"", "");
        }
        if (cs == null) {
            String head = new String(data, 0, Math.min(data.length, 2048), Charset.forName("ISO-8859-1"));
            int i = head.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (i >= 0) {
                String s = head.substring(i + 8, Math.min(head.length(), i + 48));
                cs = s.split("[\"'>\\s;]")[0];
            }
        }
        Charset c;
        try {
            c = cs == null || cs.isEmpty() ? Charset.forName("UTF-8") : Charset.forName(cs);
        } catch (Exception e) {
            c = Charset.forName("UTF-8");
        }
        return new String(data, c);
    }

    // ---------------- 查找 ----------------

    private void toggleFind() {
        if (findRow.getVisibility() == View.VISIBLE) {
            findRow.setVisibility(View.GONE);
            query = "";
            findInput.setText("");
            code.setText(shown);
            Ui.hideKeyboard(this, findInput);
        } else {
            findRow.setVisibility(View.VISIBLE);
            findInput.requestFocus();
            Ui.showKeyboard(this, findInput);
        }
    }

    private void highlight(String q) {
        matches.clear();
        matchIndex = -1;
        if (q == null || q.isEmpty() || shown.isEmpty()) {
            code.setText(shown);
            findCount.setText("");
            return;
        }
        SpannableString sp = new SpannableString(shown);
        String hay = shown.toLowerCase(Locale.ROOT);
        String needle = q.toLowerCase(Locale.ROOT);
        int from = 0;
        while (matches.size() < 300) {
            int i = hay.indexOf(needle, from);
            if (i < 0) break;
            matches.add(i);
            sp.setSpan(new BackgroundColorSpan(0x66FFB300), i, i + q.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = i + Math.max(1, q.length());
        }
        code.setText(sp);
        findCount.setText(matches.isEmpty() ? "0" : ("0/" + matches.size()));
        if (!matches.isEmpty()) {
            matchIndex = 0;
            scrollTo(0);
            findCount.setText("1/" + matches.size());
        }
    }

    private void step(int dir) {
        if (matches.isEmpty()) return;
        matchIndex = (matchIndex + dir + matches.size()) % matches.size();
        scrollTo(matchIndex);
        findCount.setText((matchIndex + 1) + "/" + matches.size());
    }

    private void scrollTo(final int idx) {
        if (idx < 0 || idx >= matches.size()) return;
        final int pos = matches.get(idx);
        scroll.post(() -> {
            Layout l = code.getLayout();
            if (l == null) return;
            int line = l.getLineForOffset(pos);
            int y = l.getLineTop(line);
            scroll.smoothScrollTo(0, Math.max(0, y - Ui.dp(this, 80)));
        });
    }

    // ---------------- 操作 ----------------

    private void showMore() {
        String[] items = {"复制全部", "分享", "保存到下载",
                dom ? "改看原始响应" : "改看渲染后的 DOM"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: copy(); break;
                        case 1: share(); break;
                        case 2: save(); break;
                        default:
                            startActivity(new Intent(this, SourceActivity.class)
                                    .putExtra("url", url)
                                    .putExtra("ua", ua)
                                    .putExtra("mode", dom ? "raw" : "dom")
                                    .putExtra("file", getIntent().getStringExtra("file")));
                            finish();
                    }
                }).show();
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("source", text));
            Ui.toast(this, "已复制 " + text.length() + " 字符");
        }
    }

    private void share() {
        if (text.isEmpty()) return;
        String body = text.length() > 100000 ? text.substring(0, 100000) + "\n…（已截断）" : text;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, url);
        i.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(i, "分享源码"));
    }

    private void save() {
        if (text.isEmpty()) return;
        String name = "source-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new java.util.Date()) + ".html";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Zuobiao");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new java.io.IOException("创建文件失败");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new java.io.IOException("写入失败");
                    os.write(text.getBytes("UTF-8"));
                }
                Ui.toast(this, "已保存到 下载/Zuobiao/" + name);
            } else {
                if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Ui.toast(this, "需要存储权限才能保存");
                    return;
                }
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "Zuobiao");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(text.getBytes("UTF-8"));
                }
                android.media.MediaScannerConnection.scanFile(this,
                        new String[]{f.getAbsolutePath()}, new String[]{"text/html"}, null);
                Ui.toast(this, "已保存到 " + f.getAbsolutePath());
            }
        } catch (Exception e) {
            Ui.toast(this, "保存失败：" + e.getMessage());
        }
    }
}
