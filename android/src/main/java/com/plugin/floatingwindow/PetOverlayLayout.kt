package com.plugin.floatingwindow

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class OverlayBox(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right get() = x + width
    val bottom get() = y + height
    fun within(window: OverlayBox) = OverlayBox(
        window.x + x * window.width, window.y + y * window.height,
        width * window.width, height * window.height,
    )
}

/** Both body boxes come from the frontend's measured media/config, not duplicated constants. */
internal data class OverlayFit(val widthDp: Double, val aspect: Double, val before: OverlayBox, val after: OverlayBox)

internal class OverlayLayoutQueue {
    private var moving = false
    private var pending: OverlayFit? = null
    fun begin() { moving = true }
    fun offer(fit: OverlayFit): OverlayFit? {
        if (!moving) return fit
        pending = fit
        return null
    }
    fun finish(): OverlayFit? {
        moving = false
        return pending.also { pending = null }
    }
    fun clear() { moving = false; pending = null }
}

/** Resize around the body, preserving an already reached screen edge. */
internal fun fitOverlay(current: OverlayBox, area: OverlayBox, fit: OverlayFit, density: Double): OverlayBox {
    val width = minOf(fit.widthDp * density, area.width * 0.8, area.height * 0.6 * fit.aspect).roundToInt().coerceAtLeast(1).toDouble()
    val height = (width / fit.aspect).roundToInt().coerceAtLeast(1).toDouble()
    val oldBody = fit.before.within(current)
    val body = fit.after.within(OverlayBox(0.0, 0.0, width, height))
    val left = area.x - body.x
    val right = maxOf(left, area.right - body.right)
    val top = area.y - body.y
    val bottom = maxOf(top, area.bottom - body.bottom)
    // Native positioning rounds to whole physical pixels.
    val x = when {
        abs(oldBody.x - area.x) <= 2 -> left
        abs(oldBody.right - area.right) <= 2 -> right
        else -> (oldBody.x + oldBody.width / 2 - body.x - body.width / 2).coerceIn(left, right)
    }
    val y = when {
        abs(oldBody.bottom - area.bottom) <= 2 -> bottom
        abs(oldBody.y - area.y) <= 2 -> top
        else -> (oldBody.bottom - body.bottom).coerceIn(top, bottom)
    }
    return OverlayBox(x, y, width, height)
}

/** Pre-R parent frames may start after a side navigation bar. */
internal data class OverlayOrigin(val x: Int = 0, val y: Int = 0) {
    fun layoutX(screenX: Double) = (screenX - x).roundToInt()
    fun layoutY(screenY: Double) = (screenY - y).roundToInt()
    fun screenX(layoutX: Int) = (layoutX + x).toDouble()
    fun screenY(layoutY: Int) = (layoutY + y).toDouble()
}

internal data class OverlayInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** App metrics already exclude navigation and cutouts, but not the status bar. */
internal fun legacyOverlayArea(
    realWidth: Int, realHeight: Int, appWidth: Int, appHeight: Int,
    navigationLeft: Boolean, statusHeight: Int, cutout: OverlayInsets = OverlayInsets(),
): OverlayBox {
    val sideNavigation = (realWidth - appWidth - cutout.left - cutout.right).coerceAtLeast(0)
    val bottomNavigation = (realHeight - appHeight - cutout.top - cutout.bottom).coerceAtLeast(0)
    val left = cutout.left + if (navigationLeft) sideNavigation else 0
    val right = cutout.right + if (navigationLeft) 0 else sideNavigation
    val top = maxOf(statusHeight, cutout.top).coerceIn(0, (realHeight - 1).coerceAtLeast(0))
    val bottom = cutout.bottom + bottomNavigation
    return OverlayBox(left.toDouble(), top.toDouble(), (realWidth - left - right).coerceAtLeast(1).toDouble(),
        (realHeight - top - bottom).coerceAtLeast(1).toDouble())
}
