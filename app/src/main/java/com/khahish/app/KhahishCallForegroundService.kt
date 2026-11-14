package com.khahish.app

import android.app.Notification
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
 * Keeps the active call represented by an ongoing Android notification and
 * promotes the app process to a microphone/camera foreground service.
 */
class KhahishCallForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Khahish user" }
        val isVideo = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        if (callId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification(callId, peerName, isVideo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (isVideo) types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                startForeground(NOTIFICATION_ID, notification, types)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(callId: String, peerName: String, isVideo: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("khahish_call_id", callId)
            putExtra("khahish_resume_call", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (isVideo) "Khahish video call" else "Khahish voice call")
            .setContentText("$peerName · Tap to return to call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows active Khahish voice and video calls"
                    setSound(null, null)
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var retainedAgoraSession: KhahishAgoraSession? = null

        /** Keeps the RTC engine reachable while the Activity/UI is backgrounded or recreated. */
        fun retainSession(session: KhahishAgoraSession) {
            retainedAgoraSession = session
        }

        /** Called only when the call is explicitly ended. */
        fun releaseSession(session: KhahishAgoraSession? = null) {
            if (session == null || retainedAgoraSession === session) {
                retainedAgoraSession = null
            }
        }

        private const val CHANNEL_ID = "khahish_ongoing_calls"
        private const val NOTIFICATION_ID = 73021
        private const val ACTION_STOP = "com.khahish.app.STOP_ACTIVE_CALL"
        private const val EXTRA_CALL_ID = "call_id"
        private const val EXTRA_PEER_NAME = "peer_name"
        private const val EXTRA_VIDEO = "is_video"

        fun start(context: Context, call: KhahishCall, isVideo: Boolean) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val peerName = if (call.callerUid == uid) call.calleeName else call.callerName
            val intent = Intent(context, KhahishCallForegroundService::class.java).apply {
                putExtra(EXTRA_CALL_ID, call.id)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_VIDEO, isVideo)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            retainedAgoraSession = null
            context.stopService(Intent(context, KhahishCallForegroundService::class.java))
        }
    }
}
