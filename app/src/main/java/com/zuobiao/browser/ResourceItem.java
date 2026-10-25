package com.zuobiao.browser;

/** 一条被嗅探到的资源。 */
final class ResourceItem {

    static final int VIDEO = 0;
    static final int AUDIO = 1;
    static final int IMAGE = 2;
    static final int FILE = 3;

    String url;
    String name;
    String mime;
    String poster;
    int type = FILE;
    long size = -1;
    long duration = -1;
    int width = -1;
    int height = -1;
    boolean fromDom;
    boolean blob;
    long foundAt = System.currentTimeMillis();

    static String typeName(int t) {
        switch (t) {
            case VIDEO: return "视频";
            case AUDIO: return "音频";
            case IMAGE: return "图片";
            default: return "文件";
        }
    }

    boolean playable() {
        if (url == null) return false;
        return (type == VIDEO || type == AUDIO) && !blob
                && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
