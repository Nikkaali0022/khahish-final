package com.khahish.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/**
 * Receives FCM messages and registers this Android device's push token.
 *
 * A trusted sender/backend is still required to trigger per-message pushes.
 * No FCM server credentials are stored in this Android application.
 */
class KhahishMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenForCurrentUser(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = message.data
        val chatId = payload["chatId"].orEmpty()
        val notificationType = payload["type"].orEmpty()

        // Per-chat mute applies to message alerts only; incoming calls remain audible.
        if (chatId.isNotBlank() && notificationType != "call") {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (uid.isNotBlank()) {
                val muted = try {
                    Tasks.await(
                        FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .collection("appSettings").document("chat_$chatId")
                            .get(),
                        3,
                        TimeUnit.SECONDS
                    ).getBoolean("notificationsMuted") == true
                } catch (_: Exception) {
                    false
                }
                if (muted) return
            }
        }

        val title = payload["title"] ?: message.notification?.title ?: "Khahish"
        val body = payload["body"] ?: message.notification?.body ?: "You have a new notification."
        val isIncomingCall = notificationType == "call"
        val channelId = ensureNotificationChannel(isIncomingCall)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            payload["chatId"]?.let { putExtra("khahish_chat_id", it) }
            payload["callId"]?.let { putExtra("khahish_call_id", it) }
            payload["type"]?.let { putExtra("khahish_notification_type", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (payload["chatId"] ?: payload["callId"] ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationId = (payload["notificationId"] ?: System.currentTimeMillis().toString()).hashCode()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission has not been granted.
        }
    }

    private fun ensureNotificationChannel(isIncomingCall: Boolean): String {
        val channelId = if (isIncomingCall) CALL_CHANNEL_ID else MESSAGE_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val isCall = isIncomingCall
                val channel = NotificationChannel(
                    channelId,
                    if (isCall) "Incoming calls" else "Messages and chat notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (isCall)
                        "Incoming Khahish calls use your phone's default ringtone"
                    else
                        "New Khahish messages use your phone's default notification sound"
                    enableVibration(true)
                    val soundUri = RingtoneManager.getDefaultUri(
                        if (isCall) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
                    )
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(if (isCall) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(soundUri, audioAttributes)
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    companion object {
        // New channel IDs ensure Android applies the correct default tones separately.
        // Android itself respects the device's ring mode, ringtone/notification volume,
        // and the user's per-channel sound settings.
        private const val MESSAGE_CHANNEL_ID = "khahish_messages_v2"
        private const val CALL_CHANNEL_ID = "khahish_calls_v2"

        fun syncCurrentToken(context: Context) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                saveTokenForCurrentUser(context, token)
            }
        }

        fun removeCurrentToken(uid: String, onComplete: () -> Unit = {}) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                val token = if (tokenTask.isSuccessful) tokenTask.result.orEmpty() else ""
                if (token.isBlank()) {
                    onComplete()
                } else {
                    val tokenId = MessageDigest.getInstance("SHA-256")
                        .digest(token.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .collection("fcmTokens").document(tokenId)
                        .delete()
                        .addOnCompleteListener { onComplete() }
                }
            }
        }

        private fun saveTokenForCurrentUser(context: Context, token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val tokenId = MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("fcmTokens").document(tokenId)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to System.currentTimeMillis(),
                        "deviceId" to android.provider.Settings.Secure.getString(
                            context.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ).orEmpty()
                    ),
                    SetOptions.merge()
                )
        }
    }
}
