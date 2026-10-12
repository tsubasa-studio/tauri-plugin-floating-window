package com.plugin.floatingwindow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process that owns the system-overlay WebView out of Android's cached
 * state while a user-visible floating window is running.
 */
class FloatingWindowForegroundService : Service() {
    companion object {
        internal const val CHANNEL_ID = "floating-window-overlay"
        private const val PREFERENCES = "floating-window-notification"
        private const val NOTIFICATION_ID = 0x50554349

        fun start(context: Context, configuration: NotificationArgs? = null, updateConfiguration: Boolean = false) {
            val appContext = context.applicationContext
            if (updateConfiguration) saveConfiguration(appContext, configuration)
            val intent = Intent(appContext, FloatingWindowForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        internal fun saveConfiguration(context: Context, configuration: NotificationArgs?) {
            // Save overrides, not resolved defaults, so a renamed/localized app uses its current label.
            // commit() persists before Android can create or recreate the service.
            check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString("title", configuration?.title)
                .putString("body", configuration?.body)
                .putString("channelName", configuration?.channelName)
                .putString("channelDescription", configuration?.channelDescription)
                .commit()) { "Failed to persist floating window notification configuration" }
        }

        internal fun readConfiguration(context: Context): NotificationText {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val configuration = NotificationArgs().apply {
                title = preferences.getString("title", null)
                body = preferences.getString("body", null)
                channelName = preferences.getString("channelName", null)
                channelDescription = preferences.getString("channelDescription", null)
            }
            val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
            return NotificationText.resolve(appName, configuration)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, FloatingWindowForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val text = readConfiguration(this)
        createNotificationChannel(text)
        startForeground(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel(text: NotificationText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            text.channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = text.channelDescription
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(text: NotificationText) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_menu_view)
        .setContentTitle(text.title)
        .setContentText(text.body)
        .setContentIntent(createLaunchPendingIntent())
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun createLaunchPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
