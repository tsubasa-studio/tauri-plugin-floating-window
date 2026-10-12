package com.plugin.floatingwindow

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/** Observe the child touch stream, including CANCEL when FloatingX starts dragging. */
internal class WebViewTapListener(context: Context, onTap: () -> Unit) : View.OnTouchListener {
    private val detector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                onTap()
                return true
            }
        }).apply {
            // Report each tap; do not swallow the second tap as a double-tap gesture.
            setOnDoubleTapListener(null)
        }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        // WebView must still receive DOWN/UP for links, buttons and the close bridge.
        // The parent remains free to intercept MOVE and deliver CANCEL on a drag.
        return false
    }
}
