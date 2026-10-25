package com.zuobiao.browser;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 轻量 UI 工具：手写控件、圆角背景、弹窗。 */
final class Ui {

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    static void toast(final Context c, final CharSequence msg) {
        MAIN.post(() -> {
            try {
                Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        });
    }

    static GradientDrawable round(int color, int strokeColor, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    static TextView button(Context c, String s, View.OnClickListener l) {
        TextView t = text(c, s, 13.5f, c.getColor(R.color.text));
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c.getColor(R.color.surface2), c.getColor(R.color.stroke), 10, c));
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setOnClickListener(l);
        return t;
    }

    static TextView primaryButton(Context c, String s, View.OnClickListener l) {
        TextView t = text(c, s, 13.5f, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c.getColor(R.color.accent), 0, 10, c));
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setOnClickListener(l);
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.stroke));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    static EditText input(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(14);
        e.setTextColor(c.getColor(R.color.text));
        e.setHintTextColor(c.getColor(R.color.text_dim));
        e.setBackground(round(c.getColor(R.color.surface2), c.getColor(R.color.stroke), 10, c));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }

    static AlertDialog dialog(Context c, String title, View body, String positive,
                              android.content.DialogInterface.OnClickListener onOk) {
        AlertDialog.Builder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(c);
        b.setTitle(title);
        b.setView(body);
        b.setPositiveButton(positive == null ? c.getString(R.string.ok) : positive, onOk);
        b.setNegativeButton(c.getString(R.string.cancel), null);
        return b.create();
    }

    static void listDialog(Context c, String title, String[] items,
                           android.content.DialogInterface.OnClickListener l) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setItems(items, l)
                .setNegativeButton(c.getString(R.string.cancel), null)
                .show();
    }

    static void confirm(Context c, String title, String msg,
                        android.content.DialogInterface.OnClickListener ok) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(c.getString(R.string.ok), ok)
                .setNegativeButton(c.getString(R.string.cancel), null)
                .show();
    }

    static int accent(Context c) { return c.getColor(R.color.accent); }

    static ColorStateList csl(int color) { return ColorStateList.valueOf(color); }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        return l;
    }

    static void back(Activity a, View root) {
        View b = root.findViewById(R.id.btnBack);
        if (b != null) b.setOnClickListener(v -> a.finish());
    }

    /** edge-to-edge：内容区统一补系统栏 + 输入法内边距。 */
    static void applyInsets(final Activity a) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(a.getWindow(), false);
        final View root = a.findViewById(android.R.id.content);
        if (root == null) return;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets b = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, Math.max(b.bottom, ime.bottom));
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }
}
