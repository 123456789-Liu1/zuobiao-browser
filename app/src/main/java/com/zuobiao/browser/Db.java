package com.zuobiao.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 书签与历史：一张 SQLite 表两颗业务，够用且不引入 ORM。 */
final class Db extends SQLiteOpenHelper {

    static final class Item {
        long id;
        String title;
        String url;
        long time;
        Item(long id, String title, String url, long time) {
            this.id = id; this.title = title; this.url = url; this.time = time;
        }
    }

    Db(Context c) {
        super(c, "zb.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, url TEXT UNIQUE, created INTEGER)");
        db.execSQL("CREATE TABLE history(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, url TEXT, time INTEGER)");
        db.execSQL("CREATE INDEX idx_history_time ON history(time DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    // ---------------- 书签 ----------------

    void addBookmark(String title, String url) {
        ContentValues v = new ContentValues();
        v.put("title", title == null ? url : title);
        v.put("url", url);
        v.put("created", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("bookmarks", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    void removeBookmark(String url) {
        getWritableDatabase().delete("bookmarks", "url=?", new String[]{url});
    }

    void removeBookmark(long id) {
        getWritableDatabase().delete("bookmarks", "_id=?", new String[]{String.valueOf(id)});
    }

    boolean isBookmarked(String url) {
        if (url == null) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", new String[]{url});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    List<Item> bookmarks() {
        return query("SELECT _id,title,url,created FROM bookmarks ORDER BY created DESC");
    }

    // ---------------- 历史 ----------------

    void addHistory(String title, String url) {
        if (url == null || url.startsWith("about:")) return;
        ContentValues v = new ContentValues();
        v.put("title", title == null ? url : title);
        v.put("url", url);
        v.put("time", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, v);
        // 只保留最近 3000 条
        getWritableDatabase().execSQL(
                "DELETE FROM history WHERE _id NOT IN (SELECT _id FROM history ORDER BY time DESC LIMIT 3000)");
    }

    List<Item> history() {
        return query("SELECT _id,title,url,time FROM history ORDER BY time DESC LIMIT 2000");
    }

    List<Item> historyFor(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return history();
        String k = "%" + keyword.trim() + "%";
        return query("SELECT _id,title,url,time FROM history WHERE title LIKE ? OR url LIKE ?"
                + " ORDER BY time DESC LIMIT 200", new String[]{k, k});
    }

    void deleteHistory(long id) {
        getWritableDatabase().delete("history", "_id=?", new String[]{String.valueOf(id)});
    }

    void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    private List<Item> query(String sql) { return query(sql, null); }

    private List<Item> query(String sql, String[] args) {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            out.add(new Item(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return out;
    }
}
