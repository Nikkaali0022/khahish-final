package com.khahish.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat

/**
 * Keeps Android's MediaProjection foreground-service requirement satisfied
 * while the user is actively sharing their screen in a Khahish call.
 */
class KhahishScreenShareService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Khahish screen sharing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shown while your screen is being shared in a call."
                }
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Khahish screen sharing")
            .setContentText("Your screen is being shared with the person on this call.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent?.getParcelableExtra(EXTRA_READY_RECEIVER) as? ResultReceiver
        // onCreate() has already promoted this service to the mediaProjection foreground type.
        receiver?.send(Activity.RESULT_OK, Bundle())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_READY_RECEIVER = "com.khahish.app.EXTRA_SCREEN_SHARE_READY_RECEIVER"
        private const val CHANNEL_ID = "khahish_screen_share_service"
        private const val NOTIFICATION_ID = 7412
    }
}
