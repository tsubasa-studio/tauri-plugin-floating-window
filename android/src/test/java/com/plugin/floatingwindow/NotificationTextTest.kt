package com.plugin.floatingwindow

import org.junit.Assert.*
import org.junit.Test

class NotificationTextTest {
    @Test fun defaultsUseHostNameAndNeutralText() {
        val text = NotificationText.resolve("Example App")
        assertEquals("Example App", text.title)
        assertTrue(text.body.contains("Example App"))
        assertEquals("Example App floating windows", text.channelName)
        assertEquals("Keeps floating windows active while in use.", text.channelDescription)
    }

    @Test fun customFieldsAndBlankFallbackAreSupported() {
        val args = NotificationArgs().apply {
            title = "Custom title"
            body = "Custom body"
            channelName = "Custom channel"
            channelDescription = "Custom description"
        }
        assertEquals(NotificationText("Custom title", "Custom body", "Custom channel", "Custom description"), NotificationText.resolve("Host", args))
        args.title = " "
        assertEquals("Host", NotificationText.resolve("Host", args).title)
    }
}
