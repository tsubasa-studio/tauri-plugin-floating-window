package com.plugin.floatingwindow

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxScopeType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@InvokeArg
class CreateArgs {
    var tag: String? = null
    var width: Double? = null
    var height: Double? = null
    var x: Double? = null
    var y: Double? = null
    var url: String? = null
}

@InvokeArg
class TagArgs {
    var tag: String? = null
}

@InvokeArg
class MoveArgs {
    var tag: String? = null
    var x: Double? = null
    var y: Double? = null
    var animated: Boolean? = null
}

@TauriPlugin
class FloatingWindowPlugin(private val activity: Activity) : Plugin(activity) {

    companion object {
        private const val DEFAULT_TAG = "default"
        private const val CLICK_EVENT = "floating-window-click"

        // Tauri serves app assets from tauri.localhost via WebViewAssetLoader.
        // We replicate this for the floating window's standalone WebView.
        // See: https://github.com/tauri-apps/wry/blob/dev/src/android/kotlin/RustWebViewClient.kt
        private const val TAURI_DOMAIN = "tauri.localhost"
    }

    /**
     * Dev server URL, resolved from tauri.conf.json's build.devUrl.
     * If devUrl uses localhost, it's replaced with 10.0.2.2
     * (Android emulator's alias for the host machine's localhost).
     * See: https://developer.android.com/studio/run/emulator-networking
     */
    private val devServerUrl: String by lazy { resolveDevServerUrl() }

    private fun resolveDevServerUrl(): String {
        return try {
            val json = activity.assets.open("tauri.conf.json")
                .bufferedReader().use { it.readText() }
            val devUrl = JSONObject(json)
                .optJSONObject("build")
                ?.optString("devUrl", "")
                ?: ""
            if (devUrl.isNotEmpty()) {
                var url = devUrl.trimEnd('/')
                // Android emulator maps 10.0.2.2 → host's localhost.
                // For physical devices, devUrl should already use a LAN IP.
                if (url.contains("localhost")) {
                    url = url.replace("localhost", "10.0.2.2")
                }
                url
            } else {
                "http://10.0.2.2:5173"
            }
        } catch (e: Exception) {
            "http://10.0.2.2:5173"
        }
    }

    @Command
    fun check_permission(invoke: Invoke) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true
        }
        val ret = JSObject()
        ret.put("granted", granted)
        invoke.resolve(ret)
    }

    @Command
    fun request_permission(invoke: Invoke) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
        val ret = JSObject()
        ret.put("granted", Settings.canDrawOverlays(activity))
        invoke.resolve(ret)
    }

    @Command
    fun create(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(CreateArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            val url = args.url

            // WebViewAssetLoader matching Tauri/WRY's configuration.
            // Serves the app's bundled assets from tauri.localhost (production mode).
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain(TAURI_DOMAIN)
                .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(activity))
                .build()

            val control = FloatingX.install {
                setContext(activity.application)
                setLayout(R.layout.fx_default)
                setTag(tag)
                setScopeType(FxScopeType.SYSTEM)
            }

            control.setClickListener {
                val data = JSObject()
                data.put("tag", tag)
                trigger(CLICK_EVENT, data)
            }

            control.show()

            // Configure the WebView after the floating window is shown
            val rootView = control.getView()
            if (rootView != null) {
                val webView = rootView.findViewById<WebView>(R.id.fx_webview)
                if (webView != null) {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.mediaPlaybackRequiresUserGesture = false

                    // Custom WebViewClient that serves tauri.localhost content,
                    // matching how the main Tauri WebView (WRY) serves assets.
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            // Only intercept tauri.localhost requests;
                            // let external URLs (API calls, etc.) load normally
                            val host = request.url.host
                            if (host != TAURI_DOMAIN) {
                                return null
                            }

                            // 1. Production: serve from bundled assets
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }

                            // 2. SPA fallback: serve index.html for navigation requests
                            //    (e.g. /desktop-pet is a client-side route, not a file)
                            val acceptHeader = request.requestHeaders["Accept"] ?: ""
                            if (acceptHeader.contains("text/html")) {
                                try {
                                    val inputStream = activity.assets.open("index.html")
                                    return WebResourceResponse("text/html", "UTF-8", inputStream)
                                } catch (e: Exception) {
                                    // index.html not in assets — dev mode, fall through
                                }
                            }

                            // 3. Dev mode fallback: proxy to Vite dev server.
                            //    In dev mode there are no bundled assets, so all
                            //    tauri.localhost requests fall through to here.
                            return proxyRequest(request, devServerUrl)
                        }
                    }

                    // Transparent background so the pet floats over other apps
                    webView.setBackgroundColor(Color.TRANSPARENT)

                    // Expose a JS interface so the WebView can close itself
                    // (the standalone WebView has no Tauri IPC bridge)
                    webView.addJavascriptInterface(
                        object : Any() {
                            @JavascriptInterface
                            fun close() {
                                activity.runOnUiThread {
                                    try {
                                        // Notify the main app to clean up desktop pet state.
                                        // Android WebViews share localStorage (same origin) but
                                        // do NOT fire storage events across WebView instances,
                                        // so the Tauri event bridge is needed for notification.
                                        val data = JSObject()
                                        data.put("tag", tag)
                                        trigger("floating-window-closed", data)
                                        FloatingX.control(tag).cancel()
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        },
                        "AndroidFloatingWindow"
                    )

                    if (url != null) {
                        // Always load the tauri.localhost URL so the floating window
                        // shares the main app's origin (localStorage is shared).
                        // Production: assetLoader serves bundled assets.
                        // Dev: shouldInterceptRequest proxies to the Vite dev server.
                        webView.loadUrl(url)
                    }
                }
            }

            val ret = JSObject()
            ret.put("tag", tag)
            invoke.resolve(ret)
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to create floating window")
        }
    }

    /**
     * Dev mode fallback: proxy a GET request to [baseUrl] (the Vite dev server).
     * Only used for stray tauri.localhost requests that reach shouldInterceptRequest
     * in dev mode — in normal dev operation the window loads [devServerUrl] directly
     * and this is never called. Forwards Range headers so video streaming works.
     */
    private fun proxyRequest(
        request: WebResourceRequest,
        baseUrl: String
    ): WebResourceResponse? {
        if (request.method != "GET") return null

        val proxyUrl = request.url.toString()
            .replace("http://$TAURI_DOMAIN", baseUrl)

        return try {
            val connection = URL(proxyUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Forward request headers, but skip ones that HttpURLConnection
            // manages automatically (Host, Connection, Accept-Encoding, etc.)
            for ((key, value) in request.requestHeaders) {
                if (key.equals("Host", ignoreCase = true)) continue
                if (key.equals("Connection", ignoreCase = true)) continue
                if (key.equals("Accept-Encoding", ignoreCase = true)) continue
                if (key.equals("Content-Length", ignoreCase = true)) continue
                if (key.equals("Transfer-Encoding", ignoreCase = true)) continue
                connection.setRequestProperty(key, value)
            }
            connection.connectTimeout = 5000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            val inputStream = if (responseCode >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            } ?: return null

            val contentTypeHeader = connection.contentType ?: "application/octet-stream"
            val contentTypeParts = contentTypeHeader.split(";").map { it.trim() }
            val mimeType = contentTypeParts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "application/octet-stream"
            val encoding = contentTypeParts
                .drop(1)
                .mapNotNull {
                    if (it.startsWith("charset=", ignoreCase = true)) {
                        it.substringAfter("=")
                    } else {
                        null
                    }
                }
                .firstOrNull()

            // Collect response headers for proper media playback
            // (Content-Range, Accept-Ranges, Content-Length, etc.)
            // Skip Content-Encoding and Transfer-Encoding because
            // HttpURLConnection.inputStream already returns decompressed data.
            val responseHeaders = mutableMapOf<String, String>()
            for (entry in connection.headerFields) {
                val key = entry.key ?: continue
                if (key.equals("Transfer-Encoding", ignoreCase = true)) continue
                if (key.equals("Content-Encoding", ignoreCase = true)) continue
                if (entry.value.isNotEmpty()) {
                    responseHeaders[key] = entry.value.joinToString(", ")
                }
            }

            WebResourceResponse(
                mimeType,
                encoding,
                responseCode,
                connection.responseMessage ?: "OK",
                responseHeaders,
                inputStream
            )
        } catch (e: Exception) {
            null
        }
    }

    @Command
    fun show(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).show()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to show floating window")
        }
    }

    @Command
    fun hide(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).hide()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to hide floating window")
        }
    }

    @Command
    fun move_window(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(MoveArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            val x = args.x?.toFloat() ?: 0f
            val y = args.y?.toFloat() ?: 0f
            val animated = args.animated ?: true
            FloatingX.control(tag).move(x, y, animated)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to move floating window")
        }
    }

    @Command
    fun destroy(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).cancel()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to destroy floating window")
        }
    }

}
