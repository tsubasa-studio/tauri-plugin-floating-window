package com.plugin.floatingwindow

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxScopeType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

@InvokeArg
class CreateArgs {
    var tag: String? = null
    var width: Double? = null
    var height: Double? = null
    var x: Double? = null
    var y: Double? = null
    var url: String? = null
    var devServerUrl: String? = null
    var assetRoot: String? = null
    var notification: NotificationArgs? = null
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
        private const val LOG_TAG = "FloatingWindowPlugin"

        // Keep the same origin as Tauri's main WebView so localStorage is shared.
        private const val TAURI_DOMAIN = "tauri.localhost"
    }

    private val webViews = mutableMapOf<String, WebView>()

    private fun disposeFloatingWindow(tag: String) {
        val webView = webViews.remove(tag)
        try {
            FloatingX.control(tag).cancel()
        } catch (_: Exception) {
            // Destroy is intentionally idempotent.
        }
        webView?.apply {
            stopLoading()
            onPause()
            removeAllViews()
            destroy()
        }
        if (webViews.isEmpty()) {
            FloatingWindowForegroundService.stop(activity.applicationContext)
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity)

    @Command
    fun check_permission(invoke: Invoke) {
        val granted = hasOverlayPermission()
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
        ret.put("granted", hasOverlayPermission())
        invoke.resolve(ret)
    }

    @Command
    fun create(invoke: Invoke) {
        var createdTag: String? = null
        try {
            val args = invoke.parseArgs(CreateArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            check(hasOverlayPermission()) {
                "Overlay permission is required. Call requestPermission(), grant access, then retry create()."
            }
            val geometry = WindowGeometry.from(
                args.width, args.height, args.x, args.y, activity.resources.displayMetrics.density
            )
            val url = args.url
            val devServerUrl = args.devServerUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.trimEnd('/')
            val bundledAssetRoot = args.assetRoot
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::File)
                ?.canonicalFile
                ?.takeIf { it.isDirectory }

            if (devServerUrl == null && bundledAssetRoot == null) {
                throw IllegalStateException("Bundled floating-window assets are unavailable")
            }

            // Reusing a tag must replace and release the previous WebView.
            disposeFloatingWindow(tag)
            createdTag = tag
            val layout = LayoutInflater.from(activity).inflate(R.layout.fx_default, FrameLayout(activity), false)
            layout.layoutParams = FrameLayout.LayoutParams(geometry.width, geometry.height)
            val webView = checkNotNull(layout.findViewById<WebView>(R.id.fx_webview)) {
                "Floating window layout must contain a WebView"
            }
            // Register before installation so every failure path can release the WebView.
            webViews[tag] = webView
            val control = FloatingX.install {
                setContext(activity.application)
                setLayoutView(layout)
                setXY(geometry.x, geometry.y)
                // Do not let FloatingX create an uninitialized window after an async grant.
                setPermissionInterceptor { _, _ ->
                    throw IllegalStateException("Overlay permission was revoked; grant access and retry create().")
                }
                setTag(tag)
                setScopeType(FxScopeType.SYSTEM)
            }

            webView.setOnTouchListener(WebViewTapListener(webView.context) {
                val data = JSObject()
                data.put("tag", tag)
                trigger(CLICK_EVENT, data)
            })

            control.show()

            checkNotNull(control.getView()) { "Floating window view was not created" }
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            }

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

                    // Only `tauri android dev` supplies a dev server URL.
                    if (devServerUrl != null) {
                        return proxyRequest(request, devServerUrl)
                    }

                    // Build APKs use the assets extracted from Tauri's embedded Rust
                    // resource bundle. Never fall through to the network for this host.
                    return serveBundledAsset(request, bundledAssetRoot!!)
                }
            }

            // Transparent background so the content floats over other apps
            webView.setBackgroundColor(Color.TRANSPARENT)

            // Expose a JS interface so the WebView can close itself
            // (the standalone WebView has no Tauri IPC bridge)
            webView.addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    fun close() {
                        activity.runOnUiThread {
                            try {
                                // Notify the host to clean up its window state.
                                // Android WebViews share localStorage (same origin) but
                                // do NOT fire storage events across WebView instances,
                                // so the Tauri event bridge is needed for notification.
                                val data = JSObject()
                                data.put("tag", tag)
                                trigger("floating-window-closed", data)
                                disposeFloatingWindow(tag)
                            } catch (_: Exception) {
                            }
                        }
                    }
                },
                "AndroidFloatingWindow"
            )

            FloatingWindowForegroundService.start(activity.applicationContext, args.notification, updateConfiguration = true)
            webView.onResume()

            if (url != null) {
                // Always load the tauri.localhost URL so the floating window
                // shares the main app's origin (localStorage is shared).
                // Build APK: extracted embedded assets are served locally.
                // Dev: shouldInterceptRequest proxies to the Vite dev server.
                webView.loadUrl(url)
            }

            val ret = JSObject()
            ret.put("tag", tag)
            invoke.resolve(ret)
        } catch (e: Exception) {
            createdTag?.let(::disposeFloatingWindow)
            invoke.reject(e.message ?: "Failed to create floating window")
        }
    }

    private fun serveBundledAsset(
        request: WebResourceRequest,
        assetRoot: File
    ): WebResourceResponse {
        if (request.method != "GET" && request.method != "HEAD") {
            return errorResponse(405, "Method Not Allowed")
        }

        val acceptHeader = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value
            .orEmpty()
        val requestedPath = request.url.path.orEmpty().trimStart('/')
        val file = try {
            BundledAssets.resolve(assetRoot, requestedPath, request.isForMainFrame, acceptHeader)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to resolve bundled asset $requestedPath", e)
            null
        } ?: return errorResponse(404, "Not Found")
        return fileResponse(request, file)
    }

    private fun fileResponse(
        request: WebResourceRequest,
        file: File
    ): WebResourceResponse {
        val totalLength = file.length()
        val mimeType = guessMimeType(file.name)
        val encoding = if (
            mimeType.startsWith("text/") ||
            mimeType == "application/javascript" ||
            mimeType == "application/json" ||
            mimeType == "image/svg+xml"
        ) {
            "UTF-8"
        } else {
            null
        }
        val rangeHeader = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value

        if (rangeHeader != null) {
            val range = parseByteRange(rangeHeader, totalLength)
                ?: return errorResponse(
                    416,
                    "Range Not Satisfiable",
                    mapOf("Content-Range" to "bytes */$totalLength")
                )
            val contentLength = range.last - range.first + 1
            val stream = FileInputStream(file)
            if (!skipFully(stream, range.first)) {
                stream.close()
                return errorResponse(416, "Range Not Satisfiable")
            }
            val body: InputStream = if (request.method == "HEAD") {
                stream.close()
                ByteArrayInputStream(ByteArray(0))
            } else {
                BoundedInputStream(stream, contentLength)
            }
            return WebResourceResponse(
                mimeType,
                encoding,
                206,
                "Partial Content",
                mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Length" to contentLength.toString(),
                    "Content-Range" to "bytes ${range.first}-${range.last}/$totalLength"
                ),
                body
            )
        }

        val body: InputStream = if (request.method == "HEAD") {
            ByteArrayInputStream(ByteArray(0))
        } else {
            FileInputStream(file)
        }
        return WebResourceResponse(
            mimeType,
            encoding,
            200,
            "OK",
            mapOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to totalLength.toString()
            ),
            body
        )
    }

    private fun parseByteRange(value: String, totalLength: Long): LongRange? {
        if (totalLength <= 0 || !value.startsWith("bytes=", ignoreCase = true)) return null
        val spec = value.substringAfter('=').substringBefore(',').trim()
        val bounds = spec.split('-', limit = 2)
        if (bounds.size != 2) return null

        val startText = bounds[0].trim()
        val endText = bounds[1].trim()
        val start: Long
        val end: Long
        if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            start = (totalLength - suffixLength).coerceAtLeast(0)
            end = totalLength - 1
        } else {
            start = startText.toLongOrNull() ?: return null
            if (start < 0 || start >= totalLength) return null
            end = endText.toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
            if (end < start) return null
        }
        return start..end
    }

    private fun skipFully(stream: InputStream, byteCount: Long): Boolean {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (stream.read() == -1) {
                return false
            } else {
                remaining -= 1
            }
        }
        return true
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "js", "mjs" -> "application/javascript"
            "json", "map" -> "application/json"
            "wasm" -> "application/wasm"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    private fun errorResponse(
        statusCode: Int,
        reasonPhrase: String,
        headers: Map<String, String> = emptyMap()
    ): WebResourceResponse {
        val body = reasonPhrase.toByteArray(Charsets.UTF_8)
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            statusCode,
            reasonPhrase,
            headers + mapOf("Content-Length" to body.size.toString()),
            ByteArrayInputStream(body)
        )
    }

    /**
     * Dev mode: proxy a GET request to [baseUrl] (the Vite dev server).
     * The floating WebView keeps the tauri.localhost origin so it shares storage
     * with the main WebView. Forwards Range headers so video streaming works.
     */
    private fun proxyRequest(
        request: WebResourceRequest,
        baseUrl: String
    ): WebResourceResponse? {
        if (request.method != "GET") return null

        val proxyUrl = request.url.toString()
            .replace(Regex("^https?://${Regex.escape(TAURI_DOMAIN)}"), baseUrl)

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
            Log.e(LOG_TAG, "Failed to proxy $proxyUrl", e)
            null
        }
    }

    @Command
    fun show(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            webViews[tag]?.onResume()
            if (webViews.containsKey(tag)) {
                FloatingWindowForegroundService.start(activity.applicationContext)
            }
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
            webViews[tag]?.onPause()
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
            disposeFloatingWindow(tag)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to destroy floating window")
        }
    }

}

private class BoundedInputStream(
    input: InputStream,
    private var remaining: Long
) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = super.read()
        if (value == -1) {
            remaining = 0
        } else {
            remaining -= 1
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        val allowed = minOf(length.toLong(), remaining).toInt()
        val count = super.read(buffer, offset, allowed)
        if (count == -1) {
            remaining = 0
        } else {
            remaining -= count.toLong()
        }
        return count
    }

    override fun skip(byteCount: Long): Long {
        val skipped = super.skip(minOf(byteCount, remaining))
        remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()
}
