package com.plugin.floatingwindow

import org.junit.Assert.*
import org.junit.Test

class PetOverlayLayoutTest {
    private val body = OverlayBox(0.1, 0.04, 0.8, 0.92)
    private val area = OverlayBox(0.0, 0.0, 1200.0, 700.0)

    @Test fun fitDuringMovementWaitsAndOnlyAppliesTheLatestMedia() {
        val queue = OverlayLayoutQueue()
        val first = OverlayFit(160.0, 1.0, body, body)
        val second = first.copy(aspect = 0.5)
        queue.begin()
        assertNull(queue.offer(first))
        assertNull(queue.offer(second))
        assertEquals(second, queue.finish())
        assertNull(queue.finish())
    }

    @Test fun deferredPortraitFitPreservesFinalBodyLeftAndFloorEdges() {
        // New portrait video is letterboxed inside the old square while flying.
        val before = OverlayBox(0.3, 0.04, 0.4, 0.92)
        val current = OverlayBox(-48.0, 700.0 - 153.6, 160.0, 160.0)
        val fit = OverlayFit(160.0, 0.5, before, body)
        val queue = OverlayLayoutQueue()
        queue.begin()
        queue.offer(fit)
        val next = fitOverlay(current, area, checkNotNull(queue.finish()), 1.0)
        val afterBody = body.within(next)
        assertEquals(0.0, afterBody.x, 0.001)
        assertEquals(700.0, afterBody.bottom, 0.001)
        assertTrue(next.x < 0) // Transparent margin may remain outside the screen.
    }

    @Test fun shrinkingAtRightEdgeDoesNotLeaveAnAbsorptionGap() {
        val current = OverlayBox(1200.0 - 288.0, 200.0, 320.0, 320.0)
        val next = fitOverlay(current, area, OverlayFit(160.0, 1.0, body, body), 1.0)
        assertEquals(1200.0, body.within(next).right, 0.001)
    }

    @Test fun legacySideNavigationOriginRoundTripsScreenCoordinates() {
        val origin = OverlayOrigin(48, 24)
        assertEquals(-64, origin.layoutX(-16.0))
        assertEquals(-16.0, origin.screenX(origin.layoutX(-16.0)), 0.001)
        assertEquals(200.0, origin.screenY(origin.layoutY(200.0)), 0.001)
    }

    @Test fun legacyWorkAreaUsesRealDisplayRatherThanNoLimitsFrame() {
        assertEquals(OverlayBox(48.0, 24.0, 1152.0, 776.0), legacyOverlayArea(1200, 800, 1152, 800, true, 24))
        assertEquals(OverlayBox(0.0, 24.0, 800.0, 1128.0), legacyOverlayArea(800, 1200, 800, 1152, false, 24))
    }

    @Test fun legacyLandscapeSeparatesLeftCutoutFromRightNavigation() {
        val actual = legacyOverlayArea(1200, 800, 1112, 800, false, 24, OverlayInsets(left = 40))
        assertEquals(OverlayBox(40.0, 24.0, 1112.0, 776.0), actual)
        assertEquals(1152.0, actual.right, 0.001)
    }

    @Test fun legacyPortraitCountsTopCutoutOnlyOnce() {
        val actual = legacyOverlayArea(800, 1200, 800, 1112, false, 24, OverlayInsets(top = 40))
        assertEquals(OverlayBox(0.0, 40.0, 800.0, 1112.0), actual)
        assertEquals(1152.0, actual.bottom, 0.001)
    }
}
