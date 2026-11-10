package com.khahish.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        val title = payload["title"] ?: message.notification?.title ?: "Khahish"
        val body = payload["body"] ?: message.notification?.body ?: "You have a new notification."
        ensureNotificationChannel()

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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Khahish messages and calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New messages, message requests and incoming call alerts"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "khahish_messages"

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
