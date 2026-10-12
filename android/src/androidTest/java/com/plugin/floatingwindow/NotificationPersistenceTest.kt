package com.plugin.floatingwindow

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NotificationPersistenceTest {
    @Test fun savedOverridesSurviveFreshContextAndCanResetToDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        try {
            FloatingWindowForegroundService.saveConfiguration(context, null)
            assertEquals(NotificationText.resolve(appName), FloatingWindowForegroundService.readConfiguration(context))
            val args = NotificationArgs().apply {
                title = "Test host title"
                body = "Test host body"
                channelName = "Test channel"
                channelDescription = "Test description"
            }
            FloatingWindowForegroundService.saveConfiguration(context, args)
            // Service.onCreate reads from a new Context after the previous instance has gone.
            val recreatedContext = context.createPackageContext(context.packageName, Context.CONTEXT_IGNORE_SECURITY)
            assertEquals(NotificationText.resolve(appName, args), FloatingWindowForegroundService.readConfiguration(recreatedContext))
            FloatingWindowForegroundService.saveConfiguration(context, null)
            assertEquals(NotificationText.resolve(appName), FloatingWindowForegroundService.readConfiguration(recreatedContext))
        } finally {
            FloatingWindowForegroundService.saveConfiguration(context, null)
        }
    }
}
