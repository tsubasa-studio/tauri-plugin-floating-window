package com.plugin.floatingwindow

import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CancellationException

/** Asynchronous, document-scoped bridge for the standalone WebView. JNI never
 * blocks waiting for the UI thread. A replaced WebView cannot reach a new tag. */
internal class PetOverlayBridge(
    private val window: PetOverlayWindow,
    private val isCurrent: () -> Boolean,
    private val closeWindow: () -> Unit,
) {
    @Volatile private var documentId = UUID.randomUUID().toString()
    @Volatile private var instanceId: String? = null
    private var motionToken: String? = null

    init { window.onUnavailable = ::interrupt }

    fun newDocument() {
        interrupt()
        documentId = UUID.randomUUID().toString()
        instanceId = null
    }

    fun interrupt() {
        motionToken = null
        window.endMotion()
        window.menu.close()
        emit("pet-overlay-interrupted", JSONObject().put("documentId", documentId).put("instanceId", instanceId ?: JSONObject.NULL))
    }

    @JavascriptInterface
    fun session(): String = JSONObject().put("documentId", documentId)
        .put("instanceId", instanceId ?: JSONObject.NULL).toString()

    @JavascriptInterface
    fun request(raw: String) {
        if (raw.length > 262144) return
        val request = try { JSONObject(raw) } catch (_: Exception) { return }
        window.webView.post {
            val response = JSONObject().put("id", request.optString("id"))
                .put("documentId", request.optString("documentId"))
            try {
                if (!isCurrent() || request.getString("documentId") != documentId)
                    throw CancellationException("悬浮窗文档已更新")
                response.put("value", dispatch(request) ?: JSONObject.NULL)
            } catch (error: Exception) {
                response.put("error", error.message ?: "悬浮窗操作失败")
                    .put("cancelled", error is CancellationException)
            }
            if (isCurrent()) emit("pet-overlay-reply", response)
        }
    }

    private fun dispatch(request: JSONObject): Any? {
        val owner = request.getString("instanceId")
        require(owner.isNotBlank() && owner.length <= 128)
        val op = request.getString("op")
        if (op == "bind") {
            val expected = if (request.isNull("expected")) null else request.optString("expected")
            if (instanceId != owner && instanceId != expected) throw CancellationException("宠物实例已更新")
            if (instanceId != null && instanceId != owner) interrupt()
            instanceId = owner
            return null
        }
        if (instanceId != owner) throw CancellationException("宠物实例已失效")
        when (op) {
            "release" -> interrupt() // Retain owner so its close can finish after React unmount.
            "fit" -> window.fit(OverlayFit(request.getDouble("widthDp"), request.getDouble("aspect"),
                body(request.getJSONObject("before")), body(request.getJSONObject("after"))))
            "motion-open" -> {
                window.requireVisible()
                motionToken = request.getString("token")
                window.beginMotion()
                return window.geometry()
            }
            "motion-frame" -> {
                window.requireVisible()
                if (motionToken != request.getString("token")) throw CancellationException("运动已取消")
                val position = request.getJSONObject("position")
                window.move(position.getDouble("x"), position.getDouble("y"))
                return window.geometry()
            }
            "motion-resize" -> {
                window.requireVisible()
                if (motionToken != request.getString("token")) throw CancellationException("运动已取消")
                window.resize(request.getDouble("widthDp"))
                return window.geometry()
            }
            "motion-end" -> if (motionToken == request.getString("token")) {
                motionToken = null
                window.endMotion()
            }
            "menu-open" -> {
                window.requireVisible()
                motionToken = null
                window.endMotion()
                val menuId = request.getString("menuId")
                val document = documentId
                window.menu.show(menuId, request.getJSONArray("tree")) { id ->
                    if (isCurrent() && documentId == document && instanceId == owner)
                        emit("pet-overlay-menu", JSONObject().put("documentId", document)
                            .put("instanceId", owner).put("menuId", menuId).put("id", id))
                }
            }
            "menu-close" -> window.menu.close(request.getString("menuId"))
            "close" -> closeWindow()
            else -> error("Unknown overlay operation")
        }
        return null
    }

    private fun emit(name: String, detail: JSONObject) {
        if (!isCurrent()) return
        val script = "window.dispatchEvent(new CustomEvent(${JSONObject.quote(name)}, {detail:JSON.parse(${JSONObject.quote(detail.toString())})}))"
        window.webView.evaluateJavascript(script, null)
    }

    private fun body(value: JSONObject): OverlayBox {
        val box = OverlayBox(value.getDouble("x"), value.getDouble("y"), value.getDouble("width"), value.getDouble("height"))
        require(listOf(box.x, box.y, box.width, box.height).all { it.isFinite() })
        require(box.x >= 0 && box.y >= 0 && box.width > 0 && box.height > 0 && box.right <= 1.01 && box.bottom <= 1.01)
        return box
    }
}
