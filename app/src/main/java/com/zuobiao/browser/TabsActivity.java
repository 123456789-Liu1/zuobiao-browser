package com.zuobiao.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.List;

/** 标签总览：点击切换，长按关闭。 */
public class TabsActivity extends AppCompatActivity {

    private TabManager tabs;
    private GridView grid;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_tabs);
        Ui.applyInsets(this);
        MainActivity host = MainActivity.current();
        if (host == null) { finish(); return; }
        tabs = host.tabManager();

        grid = findViewById(R.id.grid);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            tabs.select(pos);
            finish();
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            Ui.confirm(this, "关闭标签", "关闭「" + tabs.list().get(pos).title + "」？", (d, w) -> {
                tabs.close(pos);
                if (tabs.size() == 0) { finish(); return; }
                adapter.notifyDataSetChanged();
            });
            return true;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNew).setOnClickListener(v -> {
            tabs.open("about:blank", false, true);
            finish();
        });
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return tabs.size(); }
        @Override public Object getItem(int i) { return tabs.list().get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : getLayoutInflater().inflate(R.layout.item_tab, parent, false);
            List<Tab> list = tabs.list();
            if (i >= list.size()) return v;
            Tab t = list.get(i);
            TextView title = v.findViewById(R.id.tabTitle);
            TextView url = v.findViewById(R.id.tabUrl);
            title.setText(t.title == null || t.title.isEmpty() ? "新标签页" : t.title);
            String u = t.url == null ? "" : t.url;
            url.setText(t.incognito ? "无痕 · " + u : u);
            boolean active = i == tabs.activeIndex();
            v.setBackground(Ui.round(getColor(active ? R.color.surface2 : R.color.surface),
                    active ? getColor(R.color.accent) : getColor(R.color.stroke), 12, TabsActivity.this));
            return v;
        }
    }
}
