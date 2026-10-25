package com.zuobiao.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

/** 统一下载入口：系统 DownloadManager，透传 UA / Cookie / Referer。 */
final class Downloader {

    private Downloader() {}

    static String enqueue(Context c, String url, String ua, String contentDisposition,
                          String mime, String referer) {
        String name = URLUtil.guessFileName(url, contentDisposition, mime);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        if (mime != null && !mime.isEmpty()) r.setMimeType(mime);
        r.addRequestHeader("User-Agent", ua == null || ua.isEmpty()
                ? "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
                : ua);
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isEmpty()) r.addRequestHeader("Cookie", cookie);
        if (referer != null && !referer.isEmpty()) r.addRequestHeader("Referer", referer);
        r.setTitle(name);
        r.setDescription(url);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) throw new IllegalStateException("系统下载服务不可用");
        dm.enqueue(r);
        return name;
    }
}
