package com.plugin.floatingwindow

import kotlin.math.roundToInt

internal data class WindowGeometry(val width: Int, val height: Int, val x: Float, val y: Float) {
    companion object {
        fun from(width: Double?, height: Double?, x: Double?, y: Double?, density: Float): WindowGeometry {
            fun dimension(value: Double?, defaultDp: Int): Int {
                val pixels = value ?: (defaultDp * density).toDouble()
                require(pixels.isFinite() && pixels >= 1 && pixels <= Int.MAX_VALUE) {
                    "Window dimensions must be positive physical pixels"
                }
                return pixels.roundToInt()
            }
            fun coordinate(value: Double?): Float {
                val pixels = value ?: 0.0
                require(pixels.isFinite() && pixels >= 0 && pixels <= Int.MAX_VALUE) {
                    "Window coordinates must be non-negative physical pixels"
                }
                return pixels.toFloat()
            }
            return WindowGeometry(dimension(width, 260), dimension(height, 320), coordinate(x), coordinate(y))
        }
    }
}
