package com.plugin.floatingwindow

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Owns the small OS window and physical geometry, never gestures or physics.
 * Unlike the generic FloatingX path, no parent intercepts WebView drag events. */
internal class PetOverlayWindow(
    private val context: Context,
    private val root: View,
    val webView: WebView,
    geometry: WindowGeometry,
) : ComponentCallbacks {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params = WindowManager.LayoutParams(
        geometry.width, geometry.height, overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = geometry.x.roundToInt()
        y = geometry.y.roundToInt()
        // Positions and MotionEvent.rawX/rawY share the full physical display.
        // Avoid adding the system-bar inset twice; collision workArea excludes it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
    private var attached = false
    private val layouts = OverlayLayoutQueue()
    private var appliedFit: OverlayFit? = null
    private var awaitingFit: OverlayFit? = null
    private var origin = OverlayOrigin()
    private var calibrateOrigin = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    private val globalLayout = ViewTreeObserver.OnGlobalLayoutListener {
        if (calibrateOrigin && attached) {
            val screen = IntArray(2)
            root.getLocationOnScreen(screen)
            origin = OverlayOrigin(screen[0] - params.x, screen[1] - params.y)
            calibrateOrigin = false
            val next = awaitingFit ?: appliedFit
            awaitingFit = null
            next?.let(::applyFit)
        }
    }
    private var cursorX = 0.0
    private var cursorY = 0.0
    var onUnavailable: () -> Unit = {}
    val menu = PetOverlayMenu(context, ::workArea)

    init {
        context.registerComponentCallbacks(this)
        root.layoutParams.width = params.width
        root.layoutParams.height = params.height
        root.viewTreeObserver.addOnGlobalLayoutListener(globalLayout)
    }

    fun show() {
        if (!attached) {
            manager.addView(root, params)
            attached = true
            root.requestLayout()
        }
        root.visibility = View.VISIBLE
    }

    fun hide() {
        onUnavailable()
        menu.close()
        root.visibility = View.GONE
    }

    fun dispose() {
        layouts.clear()
        awaitingFit = null
        onUnavailable()
        menu.close()
        context.unregisterComponentCallbacks(this)
        root.viewTreeObserver.removeOnGlobalLayoutListener(globalLayout)
        if (attached) manager.removeViewImmediate(root)
        attached = false
    }

    fun observeTouch(event: MotionEvent) {
        cursorX = event.rawX.toDouble()
        cursorY = event.rawY.toDouble()
    }

    fun requireVisible() {
        if (!attached || root.visibility != View.VISIBLE) throw java.util.concurrent.CancellationException("悬浮窗已隐藏或关闭")
    }

    fun beginMotion() {
        if (calibrateOrigin) throw java.util.concurrent.CancellationException("屏幕布局尚未就绪")
        layouts.begin()
    }

    fun endMotion() {
        layouts.finish()?.let(::applyFit)
    }

    /** A compact media viewport; dimensions come from frontend layout config. */
    fun fit(fit: OverlayFit) {
        require(fit.widthDp.isFinite() && fit.widthDp in 72.0..320.0)
        require(fit.aspect.isFinite() && fit.aspect in 0.2..5.0)
        // A media transition cannot become a second position writer while a
        // finger/throw/snap owns movement. Apply its latest layout at rest.
        layouts.offer(fit)?.let(::applyFit)
    }

    private fun applyFit(fit: OverlayFit) {
        if (calibrateOrigin) { awaitingFit = fit; return }
        val area = workArea()
        val next = fitOverlay(
            OverlayBox(origin.screenX(params.x), origin.screenY(params.y), params.width.toDouble(), params.height.toDouble()),
            OverlayBox(area.left.toDouble(), area.top.toDouble(), area.width().toDouble(), area.height().toDouble()),
            fit, context.resources.displayMetrics.density.toDouble(),
        )
        params.width = next.width.roundToInt()
        params.height = next.height.roundToInt()
        root.layoutParams.width = params.width
        root.layoutParams.height = params.height
        move(next.x, next.y)
        appliedFit = fit.copy(before = fit.after)
    }

    fun move(x: Double, y: Double) {
        require(x.isFinite() && y.isFinite() && kotlin.math.abs(x) < Int.MAX_VALUE && kotlin.math.abs(y) < Int.MAX_VALUE)
        params.x = origin.layoutX(x)
        params.y = origin.layoutY(y)
        if (attached) manager.updateViewLayout(root, params)
    }

    fun geometry(): JSONObject {
        requireVisible()
        val area = workArea()
        return JSONObject().put("position", point(origin.screenX(params.x), origin.screenY(params.y)))
            .put("cursor", point(cursorX, cursorY))
            .put("client", rect(0, 0, params.width, params.height))
            .put("scale", context.resources.displayMetrics.density.toDouble())
            .put("workAreas", JSONArray().put(rect(area.left, area.top, area.width(), area.height())))
    }

    fun workArea(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            return Rect(metrics.bounds).apply {
                left += insets.left; top += insets.top
                right -= insets.right; bottom -= insets.bottom
            }
        }
        // NO_LIMITS makes getWindowVisibleDisplayFrame report a synthetic
        // -10000..10000 frame on old Android. Use display metrics, not that frame.
        @Suppress("DEPRECATION")
        val display = manager.defaultDisplay
        val real = android.util.DisplayMetrics()
        val usable = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(real)
        @Suppress("DEPRECATION")
        display.getMetrics(usable)
        val statusId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusHeight = if (statusId != 0) context.resources.getDimensionPixelSize(statusId) else 0
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) display.cutout?.let {
            OverlayInsets(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom)
        } ?: OverlayInsets() else OverlayInsets()
        val area = legacyOverlayArea(real.widthPixels, real.heightPixels, usable.widthPixels, usable.heightPixels,
            display.rotation == Surface.ROTATION_270, statusHeight, cutout)
        return Rect(area.x.toInt(), area.y.toInt(), area.right.toInt(), area.bottom.toInt())
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        calibrateOrigin = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        onUnavailable()
        menu.close()
        // Recompute after Android has applied the new display metrics.
        root.post {
            if (attached) {
                if (calibrateOrigin) root.requestLayout() else appliedFit?.let(::fit)
            }
        }
    }
    override fun onLowMemory() {}

    companion object {
        @Suppress("DEPRECATION")
        fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        private fun point(x: Double, y: Double) = JSONObject().put("x", x).put("y", y)
        private fun rect(x: Int, y: Int, width: Int, height: Int) =
            JSONObject().put("x", x).put("y", y).put("width", width).put("height", height)
    }
}
