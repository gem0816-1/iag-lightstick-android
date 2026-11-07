package com.iag.lightstick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/** Keeps the process eligible to maintain the BLE timeline while a video player is foregrounded. */
class ArrangementPlaybackService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "灯光编排播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "编排在后台播放时维持荧光棒连接"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "灯光编排" }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val realign = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_REALIGN).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("正在播放灯光编排")
            .setContentText(title)
            .setContentIntent(openApp)
            .addAction(0, "重新对齐", realign)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            reportStartFailure(e)
        } catch (e: IllegalStateException) {
            reportStartFailure(e)
        }
        return START_NOT_STICKY
    }

    private fun reportStartFailure(error: Exception) {
        Log.w("IAGPlayback", "Foreground playback unavailable", error)
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra(EXTRA_ERROR, "后台保活暂不可用，请回到应用后重新播放"))
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "com.iag.lightstick.ARRANGEMENT_TITLE"
        const val ACTION_REALIGN = "com.iag.lightstick.REALIGN"
        const val ACTION_STATUS = "com.iag.lightstick.PLAYBACK_STATUS"
        const val EXTRA_ERROR = "playback_error"
        private const val CHANNEL_ID = "arrangement_playback"
        private const val NOTIFICATION_ID = 701
    }
}
