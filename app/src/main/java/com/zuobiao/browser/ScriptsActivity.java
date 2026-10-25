package com.zuobiao.browser;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 用户脚本管理：启用/停用、编辑、新建、删除。 */
public class ScriptsActivity extends AppCompatActivity {

    private ScriptManager sm;
    private final List<ScriptManager.Script> data = new ArrayList<>();
    private Adapter adapter;
    private ListView list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        Ui.applyInsets(this);
        sm = App.get().scripts();
        ((TextView) findViewById(R.id.title)).setText(R.string.scripts);
        TextView action = findViewById(R.id.action);
        action.setText("新建");
        action.setOnClickListener(v -> edit(-1));

        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            sm.toggle(pos);
            reload();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setItems(new String[]{"编辑", "删除"}, (d, w) -> {
                        if (w == 0) edit(pos);
                        else Ui.confirm(this, "删除脚本", "确定删除这个脚本？", (d2, w2) -> {
                            sm.remove(pos);
                            reload();
                        });
                    }).show();
            return true;
        });

        Ui.back(this, findViewById(android.R.id.content));
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(sm.all());
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("还没有脚本");
    }

    private void edit(int index) {
        ScriptManager.Script origin = index >= 0 ? sm.all().get(index)
                : new ScriptManager.Script("", "*", "");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);

        EditText name = Ui.input(this, "脚本名称", origin.name);
        EditText pattern = Ui.input(this, "匹配规则，例如 *://*.example.com/*", origin.pattern);
        EditText code = Ui.input(this, "JavaScript 代码", origin.code);
        code.setMinLines(6);
        code.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        box.addView(label("名称"));
        box.addView(name, lp());
        box.addView(label("匹配"));
        box.addView(pattern, lp());
        box.addView(label("代码"));
        box.addView(code, lp());

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(index >= 0 ? "编辑脚本" : "新建脚本")
                .setView(sc)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String p = pattern.getText().toString().trim();
            String c = code.getText().toString();
            if (n.isEmpty()) { Ui.toast(this, "请填写名称"); return; }
            if (c.trim().isEmpty()) { Ui.toast(this, "请填写代码"); return; }
            if (p.isEmpty()) p = "*";
            ScriptManager.Script s = new ScriptManager.Script(n, p, c);
            s.enabled = true;
            if (index >= 0) sm.update(index, s); else sm.add(s);
            d.dismiss();
            reload();
            Ui.toast(this, "已保存，刷新页面生效");
        });
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 11.5f, getColor(R.color.text_sub));
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        return t;
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : getLayoutInflater().inflate(R.layout.item_row, parent, false);
            ScriptManager.Script s = data.get(i);
            ((TextView) v.findViewById(R.id.title)).setText(s.name);
            ((TextView) v.findViewById(R.id.sub)).setText(s.pattern);
            TextView tail = v.findViewById(R.id.tail);
            tail.setText(s.enabled ? "● 启用" : "○ 停用");
            tail.setTextColor(getColor(s.enabled ? R.color.ok : R.color.text_dim));
            return v;
        }
    }
}
