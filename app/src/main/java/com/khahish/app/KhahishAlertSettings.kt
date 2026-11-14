package com.khahish.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * User-controlled sound and vibration preferences for message and call alerts.
 * Channel IDs include the selected settings because Android notification
 * channels become immutable after creation on Android 8.0 and newer.
 */
internal object KhahishAlertSettings {
    private const val PREFS = "khahish_alert_settings"
    private const val MESSAGE_SOUND = "message_sound"
    private const val CALL_SOUND = "call_sound"
    private const val MESSAGE_VIBRATION = "message_vibration"
    private const val CALL_VIBRATION = "call_vibration"
    const val VOICE_HAPTICS = "voice_recording_haptics"

    private const val MESSAGE_PREFIX = "khahish_messages_"
    private const val CALL_PREFIX = "khahish_incoming_calls_"

    fun messageSound(context: Context): Uri? = readSound(context, MESSAGE_SOUND, RingtoneManager.TYPE_NOTIFICATION)
    fun callSound(context: Context): Uri? = readSound(context, CALL_SOUND, RingtoneManager.TYPE_RINGTONE)

    fun messageVibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MESSAGE_VIBRATION, true)

    fun callVibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CALL_VIBRATION, true)

    fun saveMessageSound(context: Context, value: String) = save(context, MESSAGE_SOUND, value)
    fun saveCallSound(context: Context, value: String) = save(context, CALL_SOUND, value)
    fun saveMessageVibration(context: Context, enabled: Boolean) = save(context, MESSAGE_VIBRATION, enabled)
    fun saveCallVibration(context: Context, enabled: Boolean) = save(context, CALL_VIBRATION, enabled)

    fun savedSoundValue(context: Context, key: String, defaultType: Int): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
        return saved ?: RingtoneManager.getDefaultUri(defaultType).toString()
    }

    fun selectedSoundLabel(context: Context, key: String, defaultType: Int): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
        if (saved == "silent") return "Silent"
        if (saved.isNullOrBlank()) return "Phone default"
        val uri = Uri.parse(saved)
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Selected ringtone"
        } catch (_: Exception) {
            "Selected ringtone"
        }
    }

    fun messageChannelId(context: Context): String {
        ensureChannels(context)
        return channelId(MESSAGE_PREFIX, messageSound(context), messageVibrationEnabled(context))
    }

    fun callChannelId(context: Context): String {
        ensureChannels(context)
        return channelId(CALL_PREFIX, callSound(context), callVibrationEnabled(context))
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(
            manager,
            channelId(MESSAGE_PREFIX, messageSound(context), messageVibrationEnabled(context)),
            "Khahish messages",
            "New messages and message requests",
            messageSound(context),
            messageVibrationEnabled(context),
            AudioAttributes.USAGE_NOTIFICATION
        )
        createChannel(
            manager,
            channelId(CALL_PREFIX, callSound(context), callVibrationEnabled(context)),
            "Khahish incoming calls",
            "Incoming voice and video call alerts",
            callSound(context),
            callVibrationEnabled(context),
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        )
        // Remove the previous fixed channels so Android Settings does not show
        // duplicate, obsolete Khahish alert controls.
        manager.deleteNotificationChannel("khahish_messages")
        manager.deleteNotificationChannel("khahish_incoming_calls_v2")
    }

    private fun readSound(context: Context, key: String, defaultType: Int): Uri? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
        return when {
            saved == "silent" -> null
            saved.isNullOrBlank() -> RingtoneManager.getDefaultUri(defaultType)
            else -> Uri.parse(saved)
        }
    }

    private fun channelId(prefix: String, sound: Uri?, vibration: Boolean): String {
        val signature = (sound?.toString() ?: "silent") + "|" + vibration
        return prefix + Integer.toHexString(signature.hashCode())
    }

    private fun createChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String,
        sound: Uri?,
        vibration: Boolean,
        usage: Int
    ) {
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            enableVibration(vibration)
            vibrationPattern = longArrayOf(0L, 350L, 250L, 350L)
            setSound(
                sound,
                AudioAttributes.Builder().setUsage(usage).build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
        manager.notificationChannels
            .filter { it.id.startsWith(if (id.startsWith(MESSAGE_PREFIX)) MESSAGE_PREFIX else CALL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    private fun save(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
        ensureChannels(context)
    }

    private fun save(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
        ensureChannels(context)
    }
}
