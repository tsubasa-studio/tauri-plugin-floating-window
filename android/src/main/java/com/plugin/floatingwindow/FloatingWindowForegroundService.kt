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
 * state while the user-visible desktop pet is running.
 */
class FloatingWindowForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "desktop-pet-overlay"
        private const val NOTIFICATION_ID = 0x50554349

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, FloatingWindowForegroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, FloatingWindowForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "桌宠运行状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持系统桌宠持续播放"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_menu_view)
        .setContentTitle("桌宠正在运行")
        .setContentText("点击返回扑次呲")
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
