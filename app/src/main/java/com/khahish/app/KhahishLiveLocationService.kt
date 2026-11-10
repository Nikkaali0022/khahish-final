package com.khahish.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Foreground location sharing for a single chat.
 * Firestore access is intentionally scoped to chats/{chatId}/liveLocations/{uid};
 * the matching participant-only rules will be supplied and deployed in the backend phase.
 */
class KhahishLiveLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var chatId = ""
    private var uid = ""
    private var expiresAt = 0L
    private var startedAt = 0L
    private var locationListener: LocationListener? = null
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        val duration = intent.getLongExtra(EXTRA_DURATION_MS, 60L * 60L * 1000L)
        if (chatId.isBlank() || uid.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startedAt = System.currentTimeMillis()
        expiresAt = startedAt + duration.coerceIn(60_000L, 8L * 60L * 60L * 1000L)
        stopped = false
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        beginLocationUpdates()
        handler.postDelayed({ stopSharing() }, expiresAt - System.currentTimeMillis())
        return START_NOT_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun beginLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                publishLocation(location)
            }
            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationListener = listener
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.filter { provider ->
                try { locationManager.isProviderEnabled(provider) } catch (_: Exception) { false }
            }.forEach { provider ->
                if (hasLocationPermission()) {
                    locationManager.requestLocationUpdates(provider, 10_000L, 10f, listener, Looper.getMainLooper())
                    try { locationManager.getLastKnownLocation(provider)?.let(::publishLocation) } catch (_: SecurityException) { }
                }
            }
        } catch (_: SecurityException) {
            stopSharing()
        } catch (_: Exception) {
            stopSharing()
        }
    }

    private fun publishLocation(location: Location) {
        if (stopped || System.currentTimeMillis() >= expiresAt || uid.isBlank() || chatId.isBlank()) return
        db.collection("chats").document(chatId).collection("liveLocations").document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "updatedAt" to System.currentTimeMillis(),
                    "startedAt" to startedAt,
                    "expiresAt" to expiresAt,
                    "isActive" to true
                ),
                SetOptions.merge()
            )
    }

    private fun stopSharing() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try { locationListener?.let { locationManager.removeUpdates(it) } } catch (_: Exception) { }
        locationListener = null
        if (uid.isNotBlank() && chatId.isNotBlank()) {
            db.collection("chats").document(chatId).collection("liveLocations").document(uid)
                .set(
                    mapOf("isActive" to false, "endedAt" to System.currentTimeMillis(), "updatedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while Khahish is sharing your live location"
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, KhahishLiveLocationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4002, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Khahish is sharing your live location")
            .setContentText("Tap Stop to end location sharing")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        if (!stopped) {
            stopped = true
            handler.removeCallbacksAndMessages(null)
            try { locationListener?.let { locationManager.removeUpdates(it) } } catch (_: Exception) { }
            if (uid.isNotBlank() && chatId.isNotBlank()) {
                db.collection("chats").document(chatId).collection("liveLocations").document(uid)
                    .set(mapOf("isActive" to false, "endedAt" to System.currentTimeMillis()), SetOptions.merge())
            }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.khahish.app.action.START_LIVE_LOCATION"
        const val ACTION_STOP = "com.khahish.app.action.STOP_LIVE_LOCATION"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_UID = "uid"
        const val EXTRA_DURATION_MS = "duration_ms"
        private const val CHANNEL_ID = "khahish_live_location"
        private const val NOTIFICATION_ID = 4001
    }
}
