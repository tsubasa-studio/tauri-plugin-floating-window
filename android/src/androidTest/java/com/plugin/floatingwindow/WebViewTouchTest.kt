package com.plugin.floatingwindow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.petterp.floatingx.assist.helper.FxAppHelper
import com.petterp.floatingx.view.FxBasicContainerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device-only: a real Chromium WebView beneath FloatingX's actual touch helper. */
class WebViewTouchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: WebViewTouchActivity

    @Before fun launch() {
        val intent = Intent(instrumentation.context, WebViewTouchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent) as WebViewTouchActivity
        assertTrue("HTML page did not become ready", activity.pageReady.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    @After fun finish() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun tapReportsTagOnceAndHtmlCloseButtonStillCallsBridge() {
        gesture(drag = false)
        assertTrue("HTML close button did not call its bridge", activity.closed.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertEquals(listOf("second-window"), activity.clickedTags)
            assertEquals(0, activity.parentClicks)
        }
    }

    @Test fun dragIsHandledByFloatingXWithoutClickingThePage() {
        gesture(drag = true)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(activity.clickedTags.isEmpty())
            assertTrue(activity.container.dragEvents > 0)
        }
        assertFalse("Dragging activated the HTML close button", activity.closed.await(500, TimeUnit.MILLISECONDS))
    }

    private fun gesture(drag: Boolean) {
        instrumentation.runOnMainSync {
            val container = activity.container
            assertTrue(container.width > 0 && container.height > 0)
            val x = container.width / 2f
            val y = container.height / 2f
            val distance = android.view.ViewConfiguration.get(activity).scaledTouchSlop * 3f
            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, dx: Float, elapsed: Long) {
                val event = MotionEvent.obtain(downTime, downTime + elapsed, action, x + dx, y, 0)
                try { container.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 0f, 0)
            if (drag) {
                send(MotionEvent.ACTION_MOVE, distance, 20)
                send(MotionEvent.ACTION_MOVE, distance * 2, 40)
            }
            send(MotionEvent.ACTION_UP, if (drag) distance * 2 else 0f, 60)
        }
    }
}

/** Declared only in the test APK, with no overlay permission or production activity. */
class WebViewTouchActivity : Activity() {
    val pageReady = CountDownLatch(1)
    val closed = CountDownLatch(1)
    val clickedTags = mutableListOf<String>()
    var parentClicks = 0
    lateinit var container: DeviceTouchContainer
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            setOnTouchListener(WebViewTapListener(context) { clickedTags.add("second-window") })
            addJavascriptInterface(CloseBridge(closed), "AndroidFloatingWindow")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Wait until Chromium has painted the button before injecting touch.
                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                        view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) { pageReady.countDown() }
                        })
                    } else {
                        view.post { pageReady.countDown() }
                    }
                }
            }
        }
        val helper = FxAppHelper.builder()
            .setContext(application)
            .setLayoutView(webView)
            .setEnableEdgeAdsorption(false)
            .setOnClickListener { parentClicks++ }
            .build()
        container = DeviceTouchContainer(helper, this)
        container.initView()
        setContentView(container)
        webView.loadDataWithBaseURL("http://tauri.localhost/", """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body,button{margin:0;width:100%;height:100%}</style></head>
            <body><button onclick="window.AndroidFloatingWindow.close()">Close</button></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        container.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}

class CloseBridge(private val closed: CountDownLatch) {
    @JavascriptInterface fun close() { closed.countDown() }
}

class DeviceTouchContainer(helper: FxAppHelper, context: Context) : FxBasicContainerView(helper, context) {
    var dragEvents = 0
    override fun initView() { super.initView(); installChildView() }
    override fun parentSize() = resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    override fun updateXY(x: Float, y: Float) {}
    override fun onTouchDown(event: MotionEvent) {}
    override fun onTouchMove(event: MotionEvent) { dragEvents++ }
    override fun onTouchCancel(event: MotionEvent) {}
}
