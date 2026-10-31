package com.zuobiao.browser;

/**
 * 本地代理内核（Xray）外壳。
 *
 * 通过反射调用 gomobile 生成的 xraymobile.Xraymobile，
 * 这样在没有打包 AAR 的构建里也能正常编译/运行（只是「内核不可用」）。
 */
final class ProxyCore {

    private static final String CLS = "xraymobile.Xraymobile";

    private ProxyCore() {}

    static boolean available() {
        try {
            Class.forName(CLS);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static String version() {
        try {
            Object v = Class.forName(CLS).getMethod("version").invoke(null);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean running() {
        try {
            Object v = Class.forName(CLS).getMethod("running").invoke(null);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 返回空串表示成功。 */
    static String start(String json) {
        if (!available()) return "内核未打包进本安装包";
        try {
            Object v = Class.forName(CLS).getMethod("start", String.class).invoke(null, json);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "启动失败：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " " + t.getMessage());
        }
    }

    static void stop() {
        try {
            Class.forName(CLS).getMethod("stop").invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
