package com.khahish.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val lockedContactUids = if (currentUid.isNotBlank() && notificationType != "call") {
            KhahishSecretChatLock.lockedUids(this, currentUid)
        } else emptySet()
        val notificationBelongsToLockedChat = if (
            chatId.isNotBlank() && currentUid.isNotBlank() &&
            lockedContactUids.isNotEmpty() && notificationType != "call"
        ) {
            try {
                val chatSnapshot = Tasks.await(
                    FirebaseFirestore.getInstance().collection("chats").document(chatId).get(),
                    3,
                    TimeUnit.SECONDS
                )
                val members = (chatSnapshot.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                members.any { it != currentUid && it in lockedContactUids }
            } catch (_: Exception) {
                false
            }
        } else false

        val title = if (notificationBelongsToLockedChat) {
            "Khahish • Locked Chat"
        } else {
            payload["title"] ?: message.notification?.title ?: "Khahish"
        }
        val body = if (notificationBelongsToLockedChat) {
            "New message in a locked chat. Unlock Khahish to view it."
        } else {
            payload["body"] ?: message.notification?.body ?: "You have a new notification."
        }
        val callId = payload["callId"].orEmpty()
        val isIncomingCall = notificationType == "call" && callId.isNotBlank()
        KhahishAlertSettings.ensureChannels(this)

        fun activityIntent(action: String = "open"): Intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            payload["chatId"]?.let { putExtra("khahish_chat_id", it) }
            payload["callId"]?.let { putExtra("khahish_call_id", it) }
            payload["type"]?.let { putExtra("khahish_notification_type", it) }
            if (isIncomingCall) putExtra("khahish_call_action", action)
        }

        val requestBase = (payload["chatId"] ?: callId.ifBlank { title }).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, requestBase, activityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Use the call ID for incoming-call notifications so every cancel path targets the same ID.
        val notificationId = if (isIncomingCall) callId.hashCode() else (payload["notificationId"] ?: System.currentTimeMillis().toString()).hashCode()
        val channelId = if (isIncomingCall) KhahishAlertSettings.callChannelId(this) else KhahishAlertSettings.messageChannelId(this)
        val selectedSound = if (isIncomingCall) KhahishAlertSettings.callSound(this) else KhahishAlertSettings.messageSound(this)
        val vibrationEnabled = if (isIncomingCall) KhahishAlertSettings.callVibrationEnabled(this) else KhahishAlertSettings.messageVibrationEnabled(this)
        val builder = NotificationCompat.Builder(this, channelId)
            .setSound(selectedSound)
            .setVibrate(if (vibrationEnabled) longArrayOf(0L, 350L, 250L, 350L) else longArrayOf(0L))
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(!isIncomingCall)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        if (isIncomingCall) {
            val acceptIntent = PendingIntent.getActivity(
                this, requestBase + 1, activityIntent("accept"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val rejectIntent = PendingIntent.getActivity(
                this, requestBase + 2, activityIntent("reject"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setTimeoutAfter(60_000L)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(android.R.drawable.ic_menu_call, "Accept", acceptIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectIntent)
        }
        val notification = builder.build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission has not been granted.
        }
    }


    companion object {
        fun cancelIncomingCallNotification(context: Context, callId: String) {
            if (callId.isNotBlank()) NotificationManagerCompat.from(context).cancel(callId.hashCode())
        }

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
