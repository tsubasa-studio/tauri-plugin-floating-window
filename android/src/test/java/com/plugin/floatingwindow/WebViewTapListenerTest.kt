package com.plugin.floatingwindow

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.petterp.floatingx.assist.helper.FxAppHelper
import com.petterp.floatingx.view.FxBasicContainerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewTapListenerTest {
    private val activityController = Robolectric.buildActivity(Activity::class.java)
    private lateinit var webView: ConsumingPageView
    private lateinit var container: TouchContainer
    private val clickedTags = mutableListOf<String>()
    private var parentClicks = 0
    private var downTime = 0L

    @Before fun setUp() {
        val activity = activityController.setup().get()
        webView = ConsumingPageView(activity)
        webView.layoutParams = FrameLayout.LayoutParams(300, 300)
        webView.setOnTouchListener(WebViewTapListener(webView.context) { clickedTags.add("second-window") })
        val helper = FxAppHelper.builder()
            .setContext(activity.application)
            .setLayoutView(webView)
            .setEnableEdgeAdsorption(false)
            .setOnClickListener { parentClicks++ }
            .build()
        container = TouchContainer(helper, activity)
        container.initView()
        activity.setContentView(container)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, 300, 300)
        webView.layout(0, 0, 300, 300)
        assertEquals(1, container.childCount)
        assertEquals(300, webView.width)
        assertEquals(300, webView.height)
        downTime = SystemClock.uptimeMillis()
    }

    @After fun tearDown() {
        activityController.pause().stop().destroy()
    }

    @Test fun consumingChildTapEmitsOnceAndStillDeliversPageTouches() {
        send(MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        send(MotionEvent.ACTION_UP, 50f, 50f, 50)
        assertEquals(listOf("second-window"), clickedTags)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), webView.actions)
        // Reproduces why listening on FloatingX's parent missed WebView clicks.
        assertEquals(0, parentClicks)
    }

    @Test fun parentDragCancelsWebViewTapWithoutDisablingMovement() {
        send(MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        send(MotionEvent.ACTION_MOVE, 150f, 150f, 20)
        send(MotionEvent.ACTION_MOVE, 180f, 180f, 40)
        send(MotionEvent.ACTION_UP, 180f, 180f, 60)
        assertTrue(clickedTags.isEmpty())
        assertTrue(webView.actions.contains(MotionEvent.ACTION_CANCEL))
        assertTrue(container.dragEvents > 0)
    }

    @Test fun cancelledGestureDoesNotEmit() {
        send(MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        send(MotionEvent.ACTION_CANCEL, 50f, 50f, 20)
        assertTrue(clickedTags.isEmpty())
    }

    @Test fun rapidSeparateTapsEachEmitOnce() {
        send(MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        send(MotionEvent.ACTION_UP, 50f, 50f, 20)
        downTime += 70
        send(MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        send(MotionEvent.ACTION_UP, 50f, 50f, 20)
        assertEquals(listOf("second-window", "second-window"), clickedTags)
    }

    private fun send(action: Int, x: Float, y: Float, elapsed: Long) {
        val event = MotionEvent.obtain(downTime, downTime + elapsed, action, x, y, 0)
        try { container.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    // Robolectric has no Chromium renderer. Model a page with a consuming View, but
    // use real Android dispatch/GestureDetector and the actual FloatingX touch helper.
    private class ConsumingPageView(context: Context) : View(context) {
        val actions = mutableListOf<Int>()
        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions.add(event.actionMasked)
            return true
        }
    }

    private class TouchContainer(helper: FxAppHelper, context: Context) : FxBasicContainerView(helper, context) {
        var dragEvents = 0
        override fun initView() { super.initView(); installChildView() }
        override fun parentSize() = 1080 to 1920
        override fun updateXY(x: Float, y: Float) {}
        override fun onTouchDown(event: MotionEvent) {}
        override fun onTouchMove(event: MotionEvent) { dragEvents++ }
        override fun onTouchCancel(event: MotionEvent) {}
    }
}
