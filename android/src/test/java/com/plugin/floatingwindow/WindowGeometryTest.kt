package com.plugin.floatingwindow

import org.junit.Assert.*
import org.junit.Test

class WindowGeometryTest {
    @Test fun explicitSizesAndPositionsArePhysicalPixels() {
        assertEquals(WindowGeometry(140, 140, 120f, 120f), WindowGeometry.from(140.0, 140.0, 120.0, 120.0, 3f))
        assertEquals(WindowGeometry(240, 180, 20f, 350f), WindowGeometry.from(240.0, 180.0, 20.0, 350.0, 3f))
    }

    @Test fun omittedDimensionsRetainDensityScaledDefaults() {
        assertEquals(WindowGeometry(520, 640, 0f, 0f), WindowGeometry.from(null, null, null, null, 2f))
    }

    @Test fun invalidValuesAreRejectedBeforeWindowCreation() {
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { WindowGeometry.from(invalid, null, null, null, 1f) }
        }
        assertThrows(IllegalArgumentException::class.java) { WindowGeometry.from(null, null, Double.NaN, null, 1f) }
    }
}
