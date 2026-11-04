# Standalone WebView calls these methods by name, outside Tauri's invoke path.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
