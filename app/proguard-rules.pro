# 保留业务类名，便于崩溃定位；第三方库交给 R8 正常裁剪
-keep class com.zuobiao.browser.** { *; }

# WebView / JS 侧可能引用的方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留行号，崩溃栈可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
