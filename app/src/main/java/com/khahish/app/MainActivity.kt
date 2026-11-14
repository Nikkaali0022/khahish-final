package com.khahish.app

import android.Manifest
import android.app.Activity
import android.location.LocationManager
import android.provider.ContactsContract
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.statusBarsPadding
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private const val CLOUDINARY_CLOUD_NAME = "koctiaes"
private const val CLOUDINARY_UPLOAD_PRESET = "ml_default"

internal val Green = Color(0xFF18A889)
internal val Ink = Color(0xFF101A19)
internal val Pale = Color(0xFFF4F8F6)
internal val Muted = Color(0xFF788681)

data class Chat(
    val id: String,
    val peerUid: String,
    val name: String,
    val message: String,
    val time: String,
    val initials: String,
    val isGroup: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val initialMessageId: String = ""
)

data class ChatMessageSearchResult(
    val chat: Chat,
    val messageId: String,
    val text: String,
    val timestamp: Long
)

data class LiveLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val isActive: Boolean,
    val updatedAt: Long,
    val expiresAt: Long
)

data class MessageRequestItem(val id: String, val senderUid: String, val senderName: String, val senderUsername: String, val createdAt: Long, val status: String = "pending")

internal fun normalizeKhahishUsername(value: String): String = value.trim().removePrefix("@").lowercase(Locale.ROOT)
internal fun isValidKhahishUsername(value: String): Boolean = Regex("^[a-z0-9_]{3,20}$").matches(normalizeKhahishUsername(value))

data class AppUser(
    val uid: String = "",
    val name: String = "",
    val nameLowercase: String = "",
    val photoUrl: String = "",
    val username: String = "",
    val bio: String = "",
    val isOnline: Boolean = false,
    val lastSeenAt: Long = 0L,
    val lastSeenVisible: Boolean = true,
    val onlineStatusVisible: Boolean = true
)

data class MessageItem(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val mediaType: String = "text",
    val timestamp: Long = 0L,
    val deliveredTo: List<String> = emptyList(),
    val readBy: List<String> = emptyList(),
    val hiddenFor: List<String> = emptyList(),
    val expiresAt: Long = 0L,
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val editedAt: Long = 0L,
    val starredBy: List<String> = emptyList(),
    val restoredFromBackup: Boolean = false,
    val isPending: Boolean = false,
    val uploadProgress: Int = -1,
    val localPreviewUri: String = "",
    val uploadUri: String = "",
    val uploadFailed: Boolean = false,
    val deleteLocalFileAfterUpload: Boolean = false
)

private fun privateChatId(uid1: String, uid2: String): String =
    listOf(uid1, uid2).sorted().joinToString("_")

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun saveUserToFirestore(
    db: FirebaseFirestore,
    uid: String?,
    name: String,
    contact: String
) {
    if (uid.isNullOrBlank()) return

    val cleanName = name.trim().ifBlank { "Khahish User" }
    val now = System.currentTimeMillis()

    val privateData = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to cleanName,
        "contact" to contact,
        "updatedAt" to now
    )

    val publicData = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to cleanName,
        "nameLowercase" to cleanName.lowercase(Locale.ROOT),
        "photoUrl" to "",
        "updatedAt" to now,
        "email" to FieldValue.delete(),
        "contact" to FieldValue.delete(),
        "phone" to FieldValue.delete(),
        "phoneNumber" to FieldValue.delete()
    )

    db.collection("users").document(uid)
        .set(privateData, SetOptions.merge())

    db.collection("publicProfiles").document(uid)
        .set(publicData, SetOptions.merge())
}

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    var pendingNotificationIntent by mutableStateOf<Intent?>(null)
        private set

    fun consumePendingNotificationIntent(): Intent? {
        val pending = pendingNotificationIntent
        pendingNotificationIntent = null
        return pending
    }

    private var appInForeground = false
    private var presenceUid: String? = null
    private val presenceAuthListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newUid = firebaseAuth.currentUser?.uid
        if (presenceUid != null && presenceUid != newUid) updateKhahishPresence(presenceUid, false)
        if (newUid != null && presenceUid != newUid) {
            KhahishMessagingService.syncCurrentToken(this@MainActivity)
        }
        presenceUid = newUid
        if (appInForeground) updateKhahishPresence(newUid, true)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // Request the display's high refresh rate when the device supports it. Android may
        // still choose a lower rate based on device policy, battery saver, or display settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        pendingNotificationIntent = intent.takeIf {
            it.hasExtra("khahish_chat_id") || it.hasExtra("khahish_call_id")
        }

        // Ask Android 13+ users for permission to show message notifications.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                3001
            )
        }

        FirebaseAuth.getInstance().addAuthStateListener(presenceAuthListener)

        setContent {
            KhahishApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent = intent.takeIf {
            it.hasExtra("khahish_chat_id") || it.hasExtra("khahish_call_id")
        }
    }

    override fun onStart() {
        super.onStart()
        appInForeground = true
        presenceUid = FirebaseAuth.getInstance().currentUser?.uid
        updateKhahishPresence(presenceUid, true)
    }

    override fun onStop() {
        appInForeground = false
        updateKhahishPresence(presenceUid, false)
        // Revoke the server-side Secret Locked Chats session as soon as the app leaves foreground.
        KhahishSecretChatApi.lock()
        super.onStop()
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(presenceAuthListener)
        super.onDestroy()
    }

    private fun updateKhahishPresence(uid: String?, foreground: Boolean) {
        if (uid.isNullOrBlank()) return
        val prefs = getSharedPreferences("khahish_privacy_settings", Context.MODE_PRIVATE)
        val showOnline = prefs.getBoolean("online_status_visible", true)
        val showLastSeen = prefs.getBoolean("last_seen_visible", true)
        val now = System.currentTimeMillis()
        val privateUpdates = hashMapOf<String, Any>(
            "isOnline" to (foreground && showOnline),
            "lastSeenAt" to if (foreground) FieldValue.delete() else now
        )
        val publicUpdates = hashMapOf<String, Any>(
            "isOnline" to (foreground && showOnline),
            "onlineStatusVisible" to showOnline,
            "lastSeenVisible" to showLastSeen,
            // Remove any legacy private fields from public profile documents.
            "email" to FieldValue.delete(),
            "contact" to FieldValue.delete(),
            "phone" to FieldValue.delete(),
            "phoneNumber" to FieldValue.delete()
        )
        if (!foreground && showLastSeen) publicUpdates["lastSeenAt"] = now
        else if (!showLastSeen) publicUpdates["lastSeenAt"] = FieldValue.delete()
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).set(privateUpdates, SetOptions.merge())
        db.collection("publicProfiles").document(uid).set(publicUpdates, SetOptions.merge())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KhahishApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val appContext = LocalContext.current
    val uiPreferences = remember {
        appContext.getSharedPreferences("khahish_ui_settings", Context.MODE_PRIVATE)
    }
    var animationsEnabled by remember {
        mutableStateOf(uiPreferences.getBoolean("animations_enabled", true))
    }
    var slideTransitionsEnabled by remember {
        mutableStateOf(uiPreferences.getBoolean("slide_transitions_enabled", true))
    }
    val advancedPreferences = remember {
        appContext.getSharedPreferences("khahish_advanced_settings", Context.MODE_PRIVATE)
    }
    var oneHandedMode by remember {
        mutableStateOf(advancedPreferences.getBoolean("one_handed", false))
    }
    var themeMode by remember {
        mutableStateOf(uiPreferences.getString("theme_mode", "system") ?: "system")
    }
    val systemDark = isSystemInDarkTheme()
    val isGlassTheme = themeMode == "glass"
    val useDarkTheme = when (themeMode) {
        "dark", "glass" -> true
        "light" -> false
        else -> systemDark
    }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var hasAdminClaim by remember { mutableStateOf(false) }
    var maintenanceMode by remember { mutableStateOf(false) }
    var maintenanceMessage by remember { mutableStateOf("Khahish is temporarily under maintenance. Please try again soon.") }
    var accountRestricted by remember { mutableStateOf(false) }
    var accountRestrictionReason by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Chats") }
    val tabOrder = listOf("Chats", "Contacts", "Updates", "Calls", "Settings")
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var profileTargetUid by remember { mutableStateOf("") }
    var lockedContactUids by remember(currentUser?.uid) {
        mutableStateOf(KhahishSecretChatLock.lockedUids(appContext, currentUser?.uid.orEmpty()))
    }
    var secretPasswordHasPassword by remember { mutableStateOf(false) }
    var secretUnlockExpiresAtMs by remember { mutableStateOf(0L) }
    var showLockedChats by remember { mutableStateOf(false) }
    var showSecretPasswordDialog by remember { mutableStateOf(false) }
    var secretPasswordCreateMode by remember { mutableStateOf(false) }
    var pendingLockedChat by remember { mutableStateOf<Chat?>(null) }
    var openedChatFromLockedChats by remember { mutableStateOf(false) }
    var showLockPasswordSettings by remember { mutableStateOf(false) }
    var brandTapCount by remember { mutableStateOf(0) }
    var lastBrandTapAt by remember { mutableStateOf(0L) }

    fun openChatWithLockCheck(chat: Chat) {
        if (!chat.isGroup && chat.peerUid in lockedContactUids) {
            pendingLockedChat = chat
            secretPasswordCreateMode = false
            showSecretPasswordDialog = true
        } else {
            openedChatFromLockedChats = false
            selectedChat = chat
        }
    }

    fun handleKhahishBrandTap() {
        val now = System.currentTimeMillis()
        if (now - lastBrandTapAt > 2200L) brandTapCount = 0
        lastBrandTapAt = now
        brandTapCount += 1
        if (brandTapCount >= 4) {
            brandTapCount = 0
            secretPasswordCreateMode = !secretPasswordHasPassword
            pendingLockedChat = null
            showSecretPasswordDialog = true
        }
    }
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<KhahishCall?>(null) }
    var incomingCall by remember { mutableStateOf(false) }
    var autoAcceptIncoming by remember { mutableStateOf(false) }
    val latestActiveCall by rememberUpdatedState(activeCall)

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Refresh the custom claim and observe Owner-controlled account/app status.
    // The restriction document is readable only by its own account and admins.
    DisposableEffect(currentUser?.uid) {
        val user = currentUser
        if (user == null) {
            hasAdminClaim = false
            maintenanceMode = false
            accountRestricted = false
            accountRestrictionReason = ""
            onDispose { }
        } else {
            var active = true
            user.getIdToken(true).addOnSuccessListener { token ->
                if (active) hasAdminClaim = token.claims["admin"] == true
            }.addOnFailureListener {
                if (active) hasAdminClaim = false
            }

            val db = FirebaseFirestore.getInstance()
            val configListener = db.collection("appConfig").document("global")
                .addSnapshotListener { snapshot, _ ->
                    if (active) {
                        maintenanceMode = snapshot?.getBoolean("maintenanceMode") == true
                        maintenanceMessage = snapshot?.getString("maintenanceMessage")
                            ?: "Khahish is temporarily under maintenance. Please try again soon."
                    }
                }
            val restrictionListener = db.collection("accountRestrictions").document(user.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (active) {
                        val permanent = snapshot?.getBoolean("permanent") == true
                        val expiresAt = snapshot?.getLong("expiresAt") ?: 0L
                        accountRestricted = snapshot?.exists() == true &&
                            (permanent || expiresAt > System.currentTimeMillis())
                        accountRestrictionReason = snapshot?.getString("reason").orEmpty()
                    }
                }

            onDispose {
                active = false
                configListener.remove()
                restrictionListener.remove()
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            lockedContactUids = emptySet()
            secretPasswordHasPassword = false
            secretUnlockExpiresAtMs = 0L
        } else {
            KhahishSecretChatApi.loadConfig(
                onSuccess = { config ->
                    lockedContactUids = config.lockedContactUids
                    secretPasswordHasPassword = config.hasPassword
                    KhahishSecretChatLock.saveLockedUids(appContext, uid, config.lockedContactUids)
                },
                onError = { message ->
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    LaunchedEffect(secretUnlockExpiresAtMs) {
        val expiry = secretUnlockExpiresAtMs
        if (expiry > 0L) {
            val waitMs = (expiry - System.currentTimeMillis()).coerceAtLeast(0L)
            kotlinx.coroutines.delay(waitMs)
            if (secretUnlockExpiresAtMs == expiry) {
                if (selectedChat?.peerUid in lockedContactUids) selectedChat = null
                showLockedChats = false
                openedChatFromLockedChats = false
                secretUnlockExpiresAtMs = 0L
                KhahishSecretChatApi.lock()
                Toast.makeText(appContext, "Locked Chats were secured again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val hostActivity = appContext as? MainActivity
    val pendingNotificationIntent = hostActivity?.pendingNotificationIntent

    LaunchedEffect(currentUser?.uid, pendingNotificationIntent) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        val notificationIntent = hostActivity?.consumePendingNotificationIntent()
            ?: return@LaunchedEffect
        val callId = notificationIntent.getStringExtra("khahish_call_id").orEmpty()
        if (callId.isNotBlank()) {
            val callAction = notificationIntent.getStringExtra("khahish_call_action").orEmpty()
            KhahishMessagingService.cancelIncomingCallNotification(appContext, callId)
            FirebaseFirestore.getInstance().collection("calls").document(callId).get()
                .addOnSuccessListener { snapshot ->
                    val call = if (snapshot.exists()) KhahishCall.from(snapshot) else null
                    if (call == null || call.calleeUid != uid) {
                        Toast.makeText(appContext, "This incoming call is not available for your account.", Toast.LENGTH_LONG).show()
                    } else if (callAction == "reject") {
                        if (call.status == "ringing") {
                            KhahishCallRepository().rejectCall(callId) { message ->
                                Toast.makeText(appContext, "Could not reject call: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                        autoAcceptIncoming = false
                    } else if (call.status != "ringing" && call.status != "accepted") {
                        Toast.makeText(appContext, "This call is no longer ringing.", Toast.LENGTH_LONG).show()
                    } else {
                        incomingCall = true
                        autoAcceptIncoming = callAction == "accept"
                        activeCall = call
                        tab = "Calls"
                        selectedChat = null
                        showNewChat = false
                        showNewGroup = false
                        profileTargetUid = ""
                    }
                }
                .addOnFailureListener { error ->
                    Toast.makeText(appContext, "Could not open incoming call: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            return@LaunchedEffect
        }

        val chatId = notificationIntent.getStringExtra("khahish_chat_id").orEmpty()
        if (chatId.isBlank()) return@LaunchedEffect

        FirebaseFirestore.getInstance().collection("chats").document(chatId).get()
            .addOnSuccessListener { snapshot ->
                val members = (snapshot.get("members") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                if (!snapshot.exists() || uid !in members) {
                    Toast.makeText(appContext, "This chat is not available.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val isGroup = snapshot.getBoolean("isGroup") == true
                val peerUid = members.firstOrNull { it != uid }.orEmpty()
                val memberNames = snapshot.get("memberNames") as? Map<*, *>
                val storedPeerName = memberNames?.get(peerUid) as? String
                val lastMessage = snapshot.getString("lastMessage").orEmpty()

                fun openChat(name: String) {
                    val cleanName = name.ifBlank { if (isGroup) "Group chat" else "Khahish User" }
                    val notificationChat = Chat(
                        id = chatId,
                        peerUid = if (isGroup) "" else peerUid,
                        name = cleanName,
                        message = lastMessage,
                        time = "",
                        initials = cleanName.take(1).uppercase(Locale.ROOT),
                        isGroup = isGroup,
                        memberUids = members
                    )
                    if (!isGroup && peerUid in lockedContactUids) {
                        pendingLockedChat = notificationChat
                        secretPasswordCreateMode = false
                        showSecretPasswordDialog = true
                    } else {
                        selectedChat = notificationChat
                    }
                    tab = "Chats"
                    showNewChat = false
                    showNewGroup = false
                    profileTargetUid = ""
                }

                if (isGroup) {
                    openChat(snapshot.getString("groupName").orEmpty())
                } else if (peerUid.isBlank()) {
                    Toast.makeText(appContext, "This chat has no valid other participant.", Toast.LENGTH_LONG).show()
                } else {
                    FirebaseFirestore.getInstance().collection("publicProfiles").document(peerUid).get()
                        .addOnSuccessListener { profile ->
                            openChat(
                                storedPeerName
                                    ?: profile.getString("name")
                                    ?: profile.getString("displayName")
                                    ?: "Khahish User"
                            )
                        }
                        .addOnFailureListener {
                            openChat(storedPeerName ?: "Khahish User")
                        }
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(appContext, "Could not open chat: " + (error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
    }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            onDispose { }
        } else {
            val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() } ?: "android-device"
            val deviceRef = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("trustedDevices").document(deviceId)
            val deviceName = Build.MANUFACTURER + " " + Build.MODEL
            var active = true
            var revocationStarted = false
            var deviceRegistration: com.google.firebase.firestore.ListenerRegistration? = null
            deviceRef.set(mapOf(
                "deviceName" to deviceName,
                "platform" to "Android",
                "lastActiveAt" to System.currentTimeMillis(),
                "trusted" to true
            ), SetOptions.merge()).addOnSuccessListener {
                if (active) {
                    deviceRegistration = deviceRef.addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && (!snapshot.exists() || snapshot.getBoolean("trusted") == false) && !revocationStarted) {
                            revocationStarted = true
                            KhahishMessagingService.removeCurrentToken(uid) {
                                auth.signOut()
                                currentUser = null
                                Toast.makeText(appContext, "This device was removed from trusted devices. Please sign in again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .collection("loginHistory").add(mapOf(
                            "time" to System.currentTimeMillis(),
                            "deviceName" to deviceName,
                            "platform" to "Android"
                        ))
                }
            }
            onDispose {
                active = false
                deviceRegistration?.remove()
            }
        }
    }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            onDispose { }
        } else {
            val registration = KhahishCallRepository().listenForIncomingCalls(uid) { incoming ->
                if (incoming != null && latestActiveCall == null) {
                    incomingCall = true
                    activeCall = incoming
                }
            }
            onDispose { registration.remove() }
        }
    }

    BackHandler(
        enabled = currentUser != null &&
            (selectedChat != null || showNewChat || showNewGroup || showLockedChats || profileTargetUid.isNotBlank() || tab != "Chats")
    ) {
        when {
            profileTargetUid.isNotBlank() -> profileTargetUid = ""
            activeCall != null -> {
                activeCall = null
                incomingCall = false
            }
            selectedChat != null -> {
                val wasLockedChat = selectedChat?.peerUid in lockedContactUids
                selectedChat = null
                if (openedChatFromLockedChats) {
                    showLockedChats = true
                } else if (wasLockedChat) {
                    secretUnlockExpiresAtMs = 0L
                    KhahishSecretChatApi.lock()
                }
                openedChatFromLockedChats = false
            }
            showLockedChats -> {
                showLockedChats = false
                secretUnlockExpiresAtMs = 0L
                KhahishSecretChatApi.lock()
            }
            showNewGroup -> showNewGroup = false
            showNewChat -> showNewChat = false
            tab != "Chats" -> tab = "Chats"
        }
    }

    MaterialTheme(
        colorScheme = if (isGlassTheme) darkColorScheme(
            primary = Color(0xFF8CF5DF),
            onPrimary = Color(0xFF07352E),
            background = Color(0xFF102B35),
            onBackground = Color(0xFFE9FFFC),
            surface = Color(0xFF1C3B45),
            onSurface = Color(0xFFE9FFFC),
            surfaceVariant = Color(0xFF284B54),
            onSurfaceVariant = Color(0xFFC0DEDA),
            outlineVariant = Color(0xFF52747A)
        ) else if (useDarkTheme) darkColorScheme(
            primary = Color(0xFF55D6B4),
            onPrimary = Color(0xFF00382D),
            background = Color(0xFF101918),
            onBackground = Color(0xFFE6F0ED),
            surface = Color(0xFF1B2725),
            onSurface = Color(0xFFE6F0ED),
            surfaceVariant = Color(0xFF263632),
            onSurfaceVariant = Color(0xFFB6C9C2),
            outlineVariant = Color(0xFF40534D)
        ) else lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            background = Pale,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEAF2EF),
            onSurfaceVariant = Muted,
            outlineVariant = Color(0xFFD8E5E0)
        )
    ) {
        SideEffect {
            val activityWindow = (appContext as? Activity)?.window
            activityWindow?.let { systemWindow ->
                val barColor = if (useDarkTheme) {
                    android.graphics.Color.rgb(16, 25, 24)
                } else {
                    android.graphics.Color.WHITE
                }
                systemWindow.statusBarColor = barColor
                systemWindow.navigationBarColor = barColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    var flags = systemWindow.decorView.systemUiVisibility
                    flags = if (useDarkTheme) {
                        flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    } else {
                        flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = if (useDarkTheme) {
                            flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                        } else {
                            flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        }
                    }
                    systemWindow.decorView.systemUiVisibility = flags
                }
            }
        }
        AnimatedContent(
            targetState = currentUser != null,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 130)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 110))
            },
            label = "authentication_transition"
        ) { loggedIn ->
            if (!loggedIn) {
                AuthScreen { currentUser = auth.currentUser }
            } else {
                val user = currentUser
                if (user == null) {
                    AuthScreen { currentUser = auth.currentUser }
                    return@AnimatedContent
                }

                if (accountRestricted && !hasAdminClaim) {
                    Column(
                        Modifier.fillMaxSize().background(Pale).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.GppBad, null, tint = Color(0xFFB3261E), modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Account temporarily restricted", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(accountRestrictionReason.ifBlank { "This account has been restricted by Khahish moderation." }, color = Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { auth.signOut(); currentUser = null }) { Text("Sign out") }
                    }
                } else if (maintenanceMode && !hasAdminClaim) {
                    Column(
                        Modifier.fillMaxSize().background(Pale).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Build, null, tint = Green, modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Khahish is under maintenance", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(maintenanceMessage, color = Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = { auth.signOut(); currentUser = null }) { Text("Sign out") }
                    }
                } else if (activeCall != null) {
                    KhahishCallScreen(
                        call = activeCall!!,
                        isIncoming = incomingCall,
                        autoAcceptIncoming = autoAcceptIncoming,
                        onExit = {
                            activeCall = null
                            incomingCall = false
                            autoAcceptIncoming = false
                        }
                    )
                } else if (profileTargetUid.isNotBlank()) {
                    KhahishUserProfileScreen(
                        uid = profileTargetUid,
                        onBack = { profileTargetUid = "" },
                        onStartCall = { peerUid, peerName, isVideo ->
                            KhahishCallRepository().createOutgoingCall(
                                callerUid = user.uid,
                                callerName = user.displayName ?: "Khahish User",
                                calleeUid = peerUid,
                                calleeName = peerName,
                                isVideo = isVideo,
                                onSuccess = { call ->
                                    incomingCall = false
                                    activeCall = call
                                },
                                onError = { message ->
                                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                } else if (selectedChat != null) {
                    ConversationScreen(
                        chat = selectedChat!!,
                        isGlassTheme = isGlassTheme,
                        onBack = {
                            val wasLockedChat = selectedChat?.peerUid in lockedContactUids
                            selectedChat = null
                            if (openedChatFromLockedChats) {
                                showLockedChats = true
                            } else if (wasLockedChat) {
                                secretUnlockExpiresAtMs = 0L
                                KhahishSecretChatApi.lock()
                            }
                            openedChatFromLockedChats = false
                        },
                        onViewProfile = { profileTargetUid = it },
                        onStartCall = { isVideo ->
                            KhahishCallRepository().createOutgoingCall(
                                callerUid = user.uid,
                                callerName = user.displayName ?: "Khahish User",
                                calleeUid = selectedChat!!.peerUid,
                                calleeName = selectedChat!!.name,
                                isVideo = isVideo,
                                onSuccess = { call ->
                                    incomingCall = false
                                    activeCall = call
                                },
                                onError = { message ->
                                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                } else if (showNewGroup) {
                    KhahishGroupChatScreen(
                        currentUid = user.uid,
                        onBack = { showNewGroup = false },
                        onCreated = {
                            openChatWithLockCheck(it)
                            showNewGroup = false
                        }
                    )
                } else if (showNewChat) {
                    NewChatScreen(
                        currentUid = user.uid,
                        onBack = { showNewChat = false },
                        onCreateGroup = { showNewChat = false; showNewGroup = true },
                        onChatCreated = {
                            openChatWithLockCheck(it)
                            showNewChat = false
                        }
                    )
                } else if (showLockedChats) {
                    LockedChatsHub(
                        currentUid = user.uid,
                        lockedContactUids = lockedContactUids,
                        onBack = {
                            showLockedChats = false
                            tab = "Chats"
                            secretUnlockExpiresAtMs = 0L
                            KhahishSecretChatApi.lock()
                        },
                        onAddContact = { contactUid, onResult ->
                            KhahishSecretChatApi.updateContact(
                                action = "add",
                                contactUid = contactUid,
                                onSuccess = { updated ->
                                    lockedContactUids = updated
                                    KhahishSecretChatLock.saveLockedUids(appContext, user.uid, updated)
                                    onResult(updated, null)
                                },
                                onError = { message -> onResult(null, message) }
                            )
                        },
                        onOpenChat = {
                            openedChatFromLockedChats = true
                            selectedChat = it
                            showLockedChats = false
                        },
                        onChangePassword = { showLockPasswordSettings = true }
                    )
                    if (showLockPasswordSettings) {
                        LockChatPasswordSettingsDialog(user.uid) { showLockPasswordSettings = false }
                    }
                } else {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        floatingActionButtonPosition = if (oneHandedMode) FabPosition.Center else FabPosition.End,
                        topBar = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF031D1B),
                                                Color(0xFF075C4C),
                                                Color(0xFF003B42)
                                            )
                                        )
                                    )
                                    .statusBarsPadding()
                                    .padding(horizontal = 18.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).combinedClickable(
                                            onClick = { handleKhahishBrandTap() },
                                            onLongClick = { }
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.khahish_launcher),
                                            contentDescription = "Khahish logo",
                                            modifier = Modifier.size(38.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Khahish",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            letterSpacing = 0.4.sp
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text("🦎", fontSize = 23.sp)
                                    }

                                    IconButton(onClick = { tab = "Settings" }) {
                                        Icon(
                                            Icons.Default.Settings,
                                            "Settings",
                                            tint = Color.White
                                        )
                                    }
                                }

                                Spacer(Modifier.height(5.dp))

                                Text(
                                    if (tab == "Chats") "Messages" else tab,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        bottomBar = {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                listOf(
                                    "Chats" to Icons.Default.Chat,
                                    "Contacts" to Icons.Default.People,
                                    "Updates" to Icons.Default.DonutLarge,
                                    "Calls" to Icons.Default.Call,
                                    "Settings" to Icons.Default.Settings
                                ).forEach { (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == label,
                                        onClick = { tab = label },
                                        icon = { Icon(icon, label) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            if (tab == "Chats") {
                                FloatingActionButton(
                                    onClick = { showNewChat = true },
                                    containerColor = Green,
                                    contentColor = Color.White
                                ) {
                                    Icon(Icons.Default.Edit, "New chat")
                                }
                            }
                        }
                    ) { padding ->
                        AnimatedContent(
                            targetState = tab,
                            modifier = Modifier.pointerInput(tab) {
                                var dragDistance = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                                    onDragEnd = {
                                        if (abs(dragDistance) > 80f) {
                                            val currentIndex = tabOrder.indexOf(tab)
                                            val nextIndex = if (dragDistance < 0f) currentIndex + 1 else currentIndex - 1
                                            if (nextIndex in tabOrder.indices) tab = tabOrder[nextIndex]
                                        }
                                        dragDistance = 0f
                                    },
                                    onDragCancel = { dragDistance = 0f }
                                )
                            },
                            transitionSpec = {
                                when {
                                    animationsEnabled && slideTransitionsEnabled -> {
                                        val movingForward = tabOrder.indexOf(targetState) > tabOrder.indexOf(initialState)
                                        (slideInHorizontally(animationSpec = tween(durationMillis = 160)) { width -> if (movingForward) width / 3 else -width / 3 } +
                                            fadeIn(animationSpec = tween(durationMillis = 130))) togetherWith
                                            (slideOutHorizontally(animationSpec = tween(durationMillis = 140)) { width -> if (movingForward) -width / 3 else width / 3 } +
                                                fadeOut(animationSpec = tween(durationMillis = 110)))
                                    }
                                    animationsEnabled -> fadeIn(animationSpec = tween(durationMillis = 130)) togetherWith
                                        fadeOut(animationSpec = tween(durationMillis = 110))
                                    else -> EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "main_tab_transition"
                        ) { selectedTab ->
                            when (selectedTab) {
                                "Chats" -> ChatList(
                                    modifier = Modifier.padding(padding),
                                    currentUid = user.uid,
                                    onOpen = { openChatWithLockCheck(it) },
                                    onViewProfile = { profileTargetUid = it },
                                    lockedContactUids = lockedContactUids
                                )

                                "Contacts" -> KhahishContactsScreen(
                                    currentUid = user.uid,
                                    modifier = Modifier.padding(padding),
                                    onOpenChat = { openChatWithLockCheck(it) },
                                    onViewProfile = { profileTargetUid = it }
                                )

                                "Updates" -> KhahishUpdatesScreen(
                                    currentUid = user.uid,
                                    currentName = user.displayName ?: "Khahish User",
                                    modifier = Modifier.padding(padding)
                                )

                                "Calls" -> KhahishCallHistoryScreen(
                                    uid = user.uid,
                                    modifier = Modifier.padding(padding),
                                    onStartCall = { peerUid, peerName, isVideo ->
                                        KhahishCallRepository().createOutgoingCall(
                                            callerUid = user.uid,
                                            callerName = user.displayName ?: "Khahish User",
                                            calleeUid = peerUid,
                                            calleeName = peerName,
                                            isVideo = isVideo,
                                            onSuccess = { call ->
                                                incomingCall = false
                                                activeCall = call
                                            },
                                            onError = { message ->
                                                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                )

                                "Settings" -> KhahishSettingsScreen(
                                    modifier = Modifier.padding(padding),
                                    onLogout = {
                                        KhahishMessagingService.removeCurrentToken(user.uid) {
                                            auth.signOut()
                                            currentUser = null
                                            selectedChat = null
                                            showNewChat = false
                                            tab = "Chats"
                                        }
                                    },
                                    onAnimationsChanged = { animationsEnabled = it },
                                    onSlideTransitionsChanged = { slideTransitionsEnabled = it },
                                    onOneHandedChanged = { oneHandedMode = it },
                                    themeMode = themeMode,
                                    onThemeModeChanged = {
                                        themeMode = it
                                        uiPreferences.edit().putString("theme_mode", it).apply()
                                    }
                                )
                            }
                        }
                    }
                }
                if (showSecretPasswordDialog) {
                    SecretChatPasswordDialog(
                        uid = user.uid,
                        createMode = secretPasswordCreateMode,
                        onDismiss = {
                            showSecretPasswordDialog = false
                            pendingLockedChat = null
                        },
                        onUnlocked = { expiresAt ->
                            showSecretPasswordDialog = false
                            secretPasswordHasPassword = true
                            secretUnlockExpiresAtMs = expiresAt
                            val requestedChat = pendingLockedChat
                            pendingLockedChat = null
                            if (requestedChat != null) {
                                selectedChat = requestedChat
                                openedChatFromLockedChats = false
                                showLockedChats = false
                            } else {
                                showLockedChats = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            isLoading = false
        } else {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    isLoading = false
                    showToast(context, "Google sign-in token missing. Check Web OAuth client setup.")
                } else {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            val signedInUser = authResult.user
                            val uid = signedInUser?.uid
                            if (uid.isNullOrBlank()) {
                                isLoading = false
                                showToast(context, "Google sign-in completed, but account details were unavailable.")
                            } else {
                                val displayName = signedInUser.displayName
                                    ?: account.displayName
                                    ?: "Khahish User"
                                val userEmail = signedInUser.email
                                    ?: account.email
                                    ?: ""
                                val photoUrl = signedInUser.photoUrl?.toString().orEmpty()
                                saveUserToFirestore(db, uid, displayName, userEmail)
                                val profileUpdates = mapOf<String, Any>(
                                    "uid" to uid,
                                    "name" to displayName,
                                    "nameLowercase" to displayName.lowercase(Locale.ROOT),
                                    "photoUrl" to photoUrl,
                                    "updatedAt" to System.currentTimeMillis()
                                )
                                db.collection("users").document(uid)
                                    .set(profileUpdates, SetOptions.merge())
                                db.collection("publicProfiles").document(uid)
                                    .set(profileUpdates, SetOptions.merge())
                                isLoading = false
                                onLoginSuccess()
                            }
                        }
                        .addOnFailureListener { error ->
                            isLoading = false
                            showToast(context, error.localizedMessage ?: "Google sign-in failed.")
                        }
                }
            } catch (error: ApiException) {
                isLoading = false
                showToast(context, "Google sign-in failed: ${error.statusCode}")
            } catch (error: Exception) {
                isLoading = false
                showToast(context, error.localizedMessage ?: "Google sign-in failed.")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.khahish_launcher),
            contentDescription = "Khahish logo",
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Khahish",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(3.dp))

        Text(
            "Private Messaging, Made Simple",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = Green
        )

        Text(
            "Developed by Nikka Ali",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (isSignUp) "Create your account" else "Welcome back",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        if (isSignUp) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = Green
            )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase(Locale.ROOT).filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }.take(20) },
                label = { Text("Unique username") },
                placeholder = { Text("your_username") },
                leadingIcon = { Text("@", color = Green) },
                supportingText = { Text("3–20 characters; letters, numbers and _ only.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = Green
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (6+ characters)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = Green
            )
        )

        if (!isSignUp) {
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        showToast(context, "Enter your email first")
                    } else {
                        auth.sendPasswordResetEmail(email.trim())
                            .addOnSuccessListener {
                                showToast(context, "Password reset email sent")
                            }
                            .addOnFailureListener {
                                showToast(context, it.message ?: "Reset failed")
                            }
                    }
                }
            ) {
                Text("Forgot password?", color = Green)
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !isLoading,
            onClick = {
                if (email.isBlank() || password.length < 6) {
                    showToast(context, "Enter a valid email and password")
                    return@Button
                }

                if (isSignUp && name.isBlank()) {
                    showToast(context, "Enter your name")
                    return@Button
                }
                if (isSignUp && !isValidKhahishUsername(username)) {
                    showToast(context, "Choose a valid unique username (3–20 characters).")
                    return@Button
                }

                isLoading = true

                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            val newUid = result.user?.uid
                            val cleanUsername = normalizeKhahishUsername(username)
                            if (newUid.isNullOrBlank()) {
                                auth.signOut()
                                isLoading = false
                                showToast(context, "Account created, but profile setup failed. Please sign in.")
                                return@addOnSuccessListener
                            }
                            val usernameRef = db.collection("usernames").document(cleanUsername)
                            db.runTransaction { transaction ->
                                val existingClaim = transaction.get(usernameRef)
                                if (existingClaim.exists()) throw IllegalStateException("That username is already taken.")
                                val now = System.currentTimeMillis()
                                val profileData = mapOf<String, Any>(
                                    "uid" to newUid,
                                    "name" to name.trim(),
                                    "nameLowercase" to name.trim().lowercase(Locale.ROOT),
                                    "email" to email.trim(),
                                    "username" to cleanUsername,
                                    "bio" to "",
                                    "photoUrl" to "",
                                    "createdAt" to now,
                                    "updatedAt" to now
                                )
                                transaction.set(usernameRef, mapOf("uid" to newUid, "username" to cleanUsername, "updatedAt" to now))
                                transaction.set(db.collection("usernamesByUid").document(newUid), mapOf("uid" to newUid, "username" to cleanUsername, "updatedAt" to now), SetOptions.merge())
                                transaction.set(db.collection("users").document(newUid), profileData, SetOptions.merge())
                                val publicProfileData = mapOf(
                                    "uid" to newUid,
                                    "name" to name.trim(),
                                    "nameLowercase" to name.trim().lowercase(Locale.ROOT),
                                    "username" to cleanUsername,
                                    "bio" to "",
                                    "photoUrl" to "",
                                    "createdAt" to now,
                                    "updatedAt" to now
                                )
                                transaction.set(db.collection("publicProfiles").document(newUid), publicProfileData, SetOptions.merge())
                                null
                            }.addOnSuccessListener {
                                saveUserToFirestore(db, newUid, name, email)
                                isLoading = false
                                onLoginSuccess()
                            }.addOnFailureListener { error ->
                                auth.signOut()
                                isLoading = false
                                showToast(context, (error.message ?: "Username setup failed") + ". Sign in and choose a username in Edit Profile if the account was created.")
                            }
                        }
                        .addOnFailureListener { error ->
                            isLoading = false
                            val message = when (error) {
                                is FirebaseAuthUserCollisionException ->
                                    "Account already exists. Please sign in."
                                else -> error.message ?: "Registration failed"
                            }
                            showToast(context, message)
                        }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            isLoading = false
                            val signedInUser = result.user
                            val uid = signedInUser?.uid

                            if (uid != null) {
                                db.collection("users").document(uid).get()
                                    .addOnSuccessListener { profile ->
                                        if (!profile.exists()) {
                                            saveUserToFirestore(
                                                db,
                                                uid,
                                                signedInUser.displayName ?: "Khahish User",
                                                email
                                            )
                                        }
                                    }
                            }
                            onLoginSuccess()
                        }
                        .addOnFailureListener { error ->
                            isLoading = false
                            showToast(
                                context,
                                when (error) {
                                    is FirebaseAuthInvalidCredentialsException ->
                                        "Incorrect email or password"
                                    is FirebaseAuthInvalidUserException ->
                                        "Account not found. Please sign up."
                                    else -> error.message ?: "Login failed"
                                }
                            )
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            Text(
                when {
                    isLoading -> "Please wait..."
                    isSignUp -> "Create account"
                    else -> "Sign in"
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            enabled = !isLoading,
            onClick = {
                isLoading = true
                try {
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, options)
                    googleSignInLauncher.launch(client.signInIntent)
                } catch (error: Exception) {
                    isLoading = false
                    showToast(context, error.localizedMessage ?: "Google sign-in could not start.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8E5)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF202A35),
                disabledContainerColor = Color.White,
                disabledContentColor = Color(0xFF82909A)
            )
        ) {
            GoogleGMark()
            Spacer(Modifier.width(12.dp))
            Text(
                "Continue with Google",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(
                if (isSignUp) "Already have an account? Sign in"
                else "New to Khahish? Create account",
                color = Green
            )
        }
    }
}

@Composable
private fun GoogleGMark() {
    Canvas(Modifier.size(23.dp)) {
        val strokeWidth = size.minDimension * 0.19f
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - strokeWidth,
            size.height - strokeWidth
        )
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.5f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.98f, size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

@Composable
fun NewChatScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    onBack: () -> Unit,
    onCreateGroup: () -> Unit = {},
    onChatCreated: (Chat) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<AppUser>()) }
    var loading by remember { mutableStateOf(false) }
    var creatingUid by remember { mutableStateOf("") }

    LaunchedEffect(search) {
        val term = normalizeKhahishUsername(search)
        if (term.length < 3 || !isValidKhahishUsername(term)) {
            users = emptyList(); loading = false
        } else {
            loading = true
            db.collection("usernames").document(term).get().addOnSuccessListener { claim ->
                val targetUid = claim.getString("uid").orEmpty()
                if (targetUid.isBlank() || targetUid == currentUid) {
                    users = emptyList(); loading = false
                } else db.collection("publicProfiles").document(targetUid).get().addOnSuccessListener { profile ->
                    users = listOfNotNull(profile.toObject(AppUser::class.java)?.takeIf { it.uid == targetUid })
                    loading = false
                }.addOnFailureListener { loading = false; showToast(context, "Profile lookup failed: " + it.message) }
            }.addOnFailureListener { loading = false; showToast(context, "Username search failed: " + it.message) }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            IconButton(onClick = onCreateGroup) {
                Icon(Icons.Default.GroupAdd, "Create group")
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter exact username, e.g. ali_123") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Text(
            "Find one person by their unique username",
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Green
            )
        }

        if (normalizeKhahishUsername(search).length < 3) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Enter a complete username (3–20 letters, numbers or _)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!loading && users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(users, key = { it.uid }) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (creatingUid.isNotEmpty()) return@clickable

                                creatingUid = user.uid

                                val chatId = privateChatId(currentUid, user.uid)
                                val chatRef = db.collection("chats").document(chatId)
                                val members = listOf(currentUid, user.uid).sorted()
                                val now = System.currentTimeMillis()

                                // Read the deterministic conversation document directly.
                                // A collection query is unnecessary here and can be rejected
                                // by Firestore list rules before a chat exists.
                                chatRef.get()
                                    .addOnSuccessListener { existing ->
                                        val existingIsMemberChat = existing.exists() &&
                                            (existing.get("members") as? List<*>)?.contains(currentUid) == true
                                        if (existingIsMemberChat) {
                                            creatingUid = ""
                                            onChatCreated(
                                                Chat(
                                                    id = chatId,
                                                    peerUid = user.uid,
                                                    name = user.name.ifBlank { "Khahish User" },
                                                    message = existing.getString("lastMessage") ?: "",
                                                    time = "",
                                                    initials = user.name.firstOrNull()
                                                        ?.uppercase() ?: "K"
                                                )
                                            )
                                        } else {
                                            val requestRef = db.collection("messageRequests").document(chatId)
                                            requestRef.get().addOnSuccessListener { request ->
                                                val status = request.getString("status").orEmpty()
                                                when {
                                                    !request.exists() -> {
                                                        val senderName = FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Khahish User"
                                                        requestRef.set(mapOf("senderUid" to currentUid, "receiverUid" to user.uid, "senderName" to senderName, "senderUsername" to "", "receiverName" to user.name, "status" to "pending", "createdAt" to now))
                                                            .addOnSuccessListener { creatingUid = ""; showToast(context, "Message request sent. Chat opens after acceptance.") }
                                                            .addOnFailureListener { creatingUid = ""; showToast(context, "Request failed: ${it.message}") }
                                                    }
                                                    status == "pending" && request.getString("senderUid") == currentUid -> { creatingUid = ""; showToast(context, "Your message request is already pending.") }
                                                    status == "pending" -> { creatingUid = ""; showToast(context, "This person has already requested to chat. Accept it from Chats.") }
                                                    status == "rejected" -> { creatingUid = ""; showToast(context, "This person rejected the request. Chat cannot start.") }
                                                    status == "accepted" -> {
                                                        creatingUid = ""
                                                        showToast(context, "This request was accepted. Open your Chats list to continue.")
                                                    }
                                                    else -> { creatingUid = ""; showToast(context, "A chat request already exists.") }
                                                }
                                            }.addOnFailureListener { creatingUid = ""; showToast(context, "Request lookup failed: ${it.message}") }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        creatingUid = ""
                                        showToast(
                                            context,
                                            "Chat lookup failed: ${error.message}"
                                        )
                                    }
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (user.photoUrl.isNotBlank()) {
                            AsyncImage(model = user.photoUrl, contentDescription = "Profile picture",
                                modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(user.name.firstOrNull()?.uppercase() ?: "K", color = Green, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.width(13.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                user.name.ifBlank { "Khahish User" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusText = when {
                                user.isOnline && user.onlineStatusVisible -> "Online"
                                user.lastSeenVisible && user.lastSeenAt > 0L ->
                                    "Last seen " + java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                        .format(java.util.Date(user.lastSeenAt))
                                else -> "Tap to send a message request"
                            }
                            Text(statusText, color = if (statusText == "Online") Green else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            if (user.bio.isNotBlank()) {
                                Text(user.bio, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
                            }
                        }

                        if (creatingUid == user.uid) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Green,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Chat, null, tint = Green)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun ChatList(
    modifier: Modifier = Modifier,
    currentUid: String,
    onOpen: (Chat) -> Unit,
    onViewProfile: (String) -> Unit,
    lockedContactUids: Set<String> = emptySet(),
    showOnlyLockedChats: Boolean = false
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var chats by remember { mutableStateOf(listOf<Chat>()) }
    var incomingRequests by remember { mutableStateOf(listOf<MessageRequestItem>()) }
    var outgoingRequests by remember { mutableStateOf(listOf<MessageRequestItem>()) }
    var showRequestsScreen by remember { mutableStateOf(false) }
    var showPeopleSearchDialog by remember { mutableStateOf(false) }
    var requestsTab by remember { mutableStateOf("Received") }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var profilePhotos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedProfileMenu by remember { mutableStateOf("") }
    var selectedPictureUrl by remember { mutableStateOf("") }
    var messageSearchResults by remember { mutableStateOf(emptyList<ChatMessageSearchResult>()) }
    var isSearchingMessages by remember { mutableStateOf(false) }
    var messageSearchCursors by remember { mutableStateOf(emptyMap<String, com.google.firebase.firestore.DocumentSnapshot>()) }
    var hasMoreMessageSearchPages by remember { mutableStateOf(false) }
    var isLoadingMoreMessageSearch by remember { mutableStateOf(false) }

    DisposableEffect(currentUid, lockedContactUids.joinToString("|"), showOnlyLockedChats) {
        var active = true
        loading = true

        if (showOnlyLockedChats) {
            if (lockedContactUids.isEmpty()) {
                chats = emptyList()
                loading = false
            } else {
                // Locked chats are fetched by deterministic document ID, not by a
                // collection query. Firestore get rules require the active unlock session.
                val tasks = lockedContactUids.map { peerUid ->
                    db.collection("chats").document(privateChatId(currentUid, peerUid)).get()
                }
                com.google.android.gms.tasks.Tasks
                    .whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks)
                    .addOnSuccessListener { documents ->
                        if (active) {
                            chats = documents.mapNotNull { doc ->
                                if (!doc.exists()) return@mapNotNull null
                                val members = doc.get("members") as? List<*>
                                val memberUids = members?.filterIsInstance<String>() ?: emptyList()
                                val peerUid = memberUids.firstOrNull { it != currentUid }
                                    ?: return@mapNotNull null
                                if (peerUid !in lockedContactUids || doc.getBoolean("isGroup") == true) {
                                    return@mapNotNull null
                                }
                                val names = doc.get("memberNames") as? Map<*, *>
                                val peerName = names?.get(peerUid) as? String ?: "Khahish User"
                                Chat(
                                    id = doc.id,
                                    peerUid = peerUid,
                                    name = peerName,
                                    message = doc.getString("lastMessage") ?: "",
                                    time = "",
                                    initials = peerName.firstOrNull()?.uppercase() ?: "K",
                                    isGroup = false,
                                    memberUids = memberUids
                                )
                            }.sortedByDescending { chat ->
                                documents.firstOrNull { it.id == chat.id }?.getLong("lastMessageAt") ?: 0L
                            }
                            loading = false
                        }
                    }
                    .addOnFailureListener {
                        if (active) {
                            chats = emptyList()
                            loading = false
                            showToast(context, "Could not load Locked Chats: ${it.message}")
                        }
                    }
            }
            onDispose { active = false }
        } else {
            val registration = db.collection("chats")
                .whereArrayContains("visibleToUids", currentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loading = false
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val docs = snapshot.documents
                        chats = docs.mapNotNull { doc ->
                            val members = doc.get("members") as? List<*>
                            val memberUids = members?.filterIsInstance<String>() ?: emptyList()
                            val peerUid = memberUids.firstOrNull { it != currentUid }
                                ?: return@mapNotNull null
                            val names = doc.get("memberNames") as? Map<*, *>
                            val isGroup = doc.getBoolean("isGroup") == true
                            val peerName = names?.get(peerUid) as? String ?: "Khahish User"
                            val displayName = if (isGroup) doc.getString("groupName") ?: "Group chat" else peerName

                            Chat(
                                id = doc.id,
                                peerUid = peerUid,
                                name = displayName,
                                message = doc.getString("lastMessage") ?: "",
                                time = "",
                                initials = displayName.firstOrNull()?.uppercase() ?: "K",
                                isGroup = isGroup,
                                memberUids = memberUids
                            )
                        }.filter { chat ->
                            if (chat.isGroup) true else chat.peerUid !in lockedContactUids
                        }.sortedByDescending { chat ->
                            docs.firstOrNull { it.id == chat.id }?.getLong("lastMessageAt") ?: 0L
                        }
                        loading = false
                    }
                }

            onDispose {
                active = false
                registration.remove()
            }
        }
    }

    DisposableEffect(currentUid) {
        val registration = db.collection("messageRequests").whereEqualTo("receiverUid", currentUid)
            .whereEqualTo("status", "pending").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                incomingRequests = snapshot.documents.map { doc ->
                    MessageRequestItem(doc.id, doc.getString("senderUid").orEmpty(), doc.getString("senderName").orEmpty().ifBlank { "Khahish User" }, doc.getString("senderUsername").orEmpty(), doc.getLong("createdAt") ?: 0L)
                }.sortedByDescending { it.createdAt }
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(currentUid) {
        val registration = db.collection("messageRequests").whereEqualTo("senderUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                outgoingRequests = snapshot.documents.map { doc ->
                    MessageRequestItem(
                        doc.id,
                        doc.getString("receiverUid").orEmpty(),
                        doc.getString("receiverName").orEmpty().ifBlank { "Khahish User" },
                        doc.getString("receiverUsername").orEmpty(),
                        doc.getLong("createdAt") ?: 0L,
                        doc.getString("status").orEmpty().ifBlank { "pending" }
                    )
                }.sortedByDescending { it.createdAt }
            }
        onDispose { registration.remove() }
    }

    // Har conversation ke unread messages ko real-time track karein.
    // Existing readBy receipt se sirf doosre user ke unread messages count hote hain.
    DisposableEffect(chats.map { it.id }.joinToString("|"), currentUid) {
        val registrations = chats.map { chat ->
            chat.id to db.collection("chats").document(chat.id)
                .collection("messages")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val count = snapshot.documents.count { doc ->
                        val senderId = doc.getString("senderId").orEmpty()
                        val readBy = doc.get("readBy") as? List<*>
                            ?: emptyList<Any>()
                        senderId.isNotBlank() &&
                            senderId != currentUid &&
                            currentUid !in readBy
                    }
                    unreadCounts = unreadCounts.toMutableMap().apply {
                        if (count > 0) put(chat.id, count) else remove(chat.id)
                    }
                }
        }
        onDispose { registrations.forEach { it.second.remove() } }
    }

    DisposableEffect(chats.map { it.peerUid }.distinct().joinToString("|")) {
        val registrations = chats.map { it.peerUid }.distinct().map { peerUid ->
            db.collection("publicProfiles").document(peerUid).addSnapshotListener { snap, _ ->
                val photo = snap?.getString("photoUrl").orEmpty()
                profilePhotos = profilePhotos.toMutableMap().apply { if (photo.isBlank()) remove(peerUid) else put(peerUid, photo) }
            }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    LaunchedEffect(search, chats.map { it.id }.joinToString("|")) {
        val queryText = search.trim()
        messageSearchResults = emptyList()
        messageSearchCursors = emptyMap()
        hasMoreMessageSearchPages = false
        isLoadingMoreMessageSearch = false

        if (queryText.length < 2) {
            isSearchingMessages = false
        } else {
            kotlinx.coroutines.delay(350)
            val currentChats = chats
            if (currentChats.isEmpty()) {
                isSearchingMessages = false
            } else {
                isSearchingMessages = true
                val tasks = currentChats.map { chat ->
                    db.collection("chats").document(chat.id).collection("messages")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(100)
                        .get()
                }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(tasks)
                    .addOnSuccessListener { snapshots ->
                        if (search.trim() == queryText) {
                            val nextCursors = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                            messageSearchResults = currentChats.flatMapIndexed { index, chat ->
                                val docs = snapshots.getOrNull(index)?.documents.orEmpty()
                                if (docs.size == 100) docs.lastOrNull()?.let { nextCursors[chat.id] = it }
                                docs.filter { doc ->
                                    doc.getString("text").orEmpty().contains(queryText, ignoreCase = true) ||
                                        doc.getString("replyToText").orEmpty().contains(queryText, ignoreCase = true)
                                }.map { doc ->
                                    ChatMessageSearchResult(
                                        chat = chat.copy(initialMessageId = doc.id),
                                        messageId = doc.id,
                                        text = doc.getString("text").orEmpty().ifBlank { doc.getString("replyToText").orEmpty() },
                                        timestamp = doc.getLong("timestamp") ?: 0L
                                    )
                                }
                            }.sortedByDescending { it.timestamp }
                            messageSearchCursors = nextCursors
                            hasMoreMessageSearchPages = nextCursors.isNotEmpty()
                        }
                        isSearchingMessages = false
                    }
                    .addOnFailureListener {
                        if (search.trim() == queryText) messageSearchResults = emptyList()
                        isSearchingMessages = false
                    }
            }
        }
    }

    fun loadOlderMessageSearchResults() {
        val queryText = search.trim()
        if (queryText.length < 2 || isLoadingMoreMessageSearch) return
        val pageChats = chats.mapNotNull { chat ->
            messageSearchCursors[chat.id]?.let { cursor -> chat to cursor }
        }
        if (pageChats.isEmpty()) {
            hasMoreMessageSearchPages = false
            return
        }

        isLoadingMoreMessageSearch = true
        val tasks = pageChats.map { (chat, cursor) ->
            db.collection("chats").document(chat.id).collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(cursor)
                .limit(100)
                .get()
        }
        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(tasks)
            .addOnSuccessListener { snapshots ->
                if (search.trim() == queryText) {
                    val nextCursors = messageSearchCursors.toMutableMap()
                    val nextResults = mutableListOf<ChatMessageSearchResult>()
                    pageChats.forEachIndexed { index, (chat, _) ->
                        val docs = snapshots.getOrNull(index)?.documents.orEmpty()
                        if (docs.size == 100) {
                            docs.lastOrNull()?.let { nextCursors[chat.id] = it }
                        } else {
                            nextCursors.remove(chat.id)
                        }
                        docs.filter { doc ->
                            doc.getString("text").orEmpty().contains(queryText, ignoreCase = true) ||
                                doc.getString("replyToText").orEmpty().contains(queryText, ignoreCase = true)
                        }.forEach { doc ->
                            nextResults.add(
                                ChatMessageSearchResult(
                                    chat = chat.copy(initialMessageId = doc.id),
                                    messageId = doc.id,
                                    text = doc.getString("text").orEmpty().ifBlank { doc.getString("replyToText").orEmpty() },
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            )
                        }
                    }
                    messageSearchResults = (messageSearchResults + nextResults)
                        .distinctBy { it.chat.id + "|" + it.messageId }
                        .sortedByDescending { it.timestamp }
                    messageSearchCursors = nextCursors
                    hasMoreMessageSearchPages = nextCursors.isNotEmpty()
                }
                isLoadingMoreMessageSearch = false
            }
            .addOnFailureListener {
                isLoadingMoreMessageSearch = false
                if (search.trim() == queryText) {
                    showToast(context, "Could not search older messages: ${it.message}")
                }
            }
    }

    val filtered = chats.filter {
        it.name.contains(search, true) || it.message.contains(search, true)
    }

    if (selectedPictureUrl.isNotBlank()) Dialog(onDismissRequest = { selectedPictureUrl = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.82f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = selectedPictureUrl, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { selectedPictureUrl = "" }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
    if (showPeopleSearchDialog) {
        PeopleSearchDialog(
            currentUid = currentUid,
            existingContacts = chats.filter { !it.isGroup },
            onDismiss = { showPeopleSearchDialog = false },
            onOpenChat = {
                showPeopleSearchDialog = false
                onOpen(it)
            }
        )
    }
    if (showRequestsScreen) {
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showRequestsScreen = false }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Message Requests", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Received", "Sent").forEach { label ->
                    TextButton(onClick = { requestsTab = label }) {
                        Text(label, color = if (requestsTab == label) Green else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (requestsTab == label) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (requestsTab == "Received") {
                if (incomingRequests.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No incoming requests", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(incomingRequests, key = { "received-${it.id}" }) { request ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(request.senderName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text(if (request.senderUsername.isNotBlank()) "@${request.senderUsername} wants to chat" else "Wants to start a conversation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = {
                                        db.collection("messageRequests").document(request.id).update(mapOf("status" to "rejected", "handledBy" to currentUid, "rejectedAt" to System.currentTimeMillis()))
                                            .addOnSuccessListener { showToast(context, "Message request rejected.") }
                                            .addOnFailureListener { showToast(context, "Could not reject request: ${it.message}") }
                                    }) { Text("Reject", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Button(onClick = {
                                        val reqRef = db.collection("messageRequests").document(request.id)
                                        val chatRef = db.collection("chats").document(request.id)
                                        val now = System.currentTimeMillis()
                                        val members = listOf(currentUid, request.senderUid).sorted()
                                        val batch = db.batch()
                                        batch.update(reqRef, mapOf("status" to "accepted", "acceptedBy" to currentUid, "handledBy" to currentUid, "acceptedAt" to now))
                                        batch.set(chatRef, mapOf("members" to members, "memberNames" to mapOf(currentUid to (FirebaseAuth.getInstance().currentUser?.displayName ?: "You"), request.senderUid to request.senderName), "lastMessage" to "", "lastMessageAt" to 0L, "updatedAt" to now, "createdAt" to now, "createdBy" to currentUid, "disappearingDurationMs" to 0L, "visibleToUids" to emptyList<String>()))
                                        batch.commit().addOnSuccessListener {
                                            KhahishSecretChatApi.syncChatVisibility(
                                                request.id,
                                                onSuccess = {
                                                    showToast(context, "Request accepted. Chat is ready.")
                                                    showRequestsScreen = false
                                                    onOpen(Chat(request.id, request.senderUid, request.senderName, "", "", request.senderName.firstOrNull()?.uppercase() ?: "K"))
                                                },
                                                onError = { message ->
                                                    showToast(context, "Chat created but privacy sync is pending. It remains hidden until synchronization succeeds. $message")
                                                    showRequestsScreen = false
                                                }
                                            )
                                        }.addOnFailureListener { showToast(context, "Could not accept request: ${it.message}") }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Accept", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            } else {
                if (outgoingRequests.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No sent requests", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(outgoingRequests, key = { "sent-${it.id}" }) { request ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(request.senderName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Status: ${request.status.replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                if (request.status == "pending") TextButton(onClick = {
                                    db.collection("messageRequests").document(request.id).update(mapOf("status" to "cancelled", "cancelledAt" to System.currentTimeMillis()))
                                        .addOnSuccessListener { showToast(context, "Request cancelled.") }
                                        .addOnFailureListener { showToast(context, "Could not cancel request: ${it.message}") }
                                }) { Text("Cancel Request", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    } else {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (incomingRequests.isNotEmpty()) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Green.copy(alpha = 0.12f)).clickable { requestsTab = "Received"; showRequestsScreen = true }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Requests ${incomingRequests.size}", color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onClick = { showPeopleSearchDialog = true }) {
                Icon(Icons.Default.Search, contentDescription = "Find people", tint = MaterialTheme.colorScheme.primary)
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search conversations") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Green)
            }

            filtered.isEmpty() && incomingRequests.isEmpty() && messageSearchResults.isEmpty() && !isSearchingMessages -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        null,
                        tint = Green,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (search.isBlank()) "No private chats yet"
                        else "No matching conversations",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tap the edit button to find a user",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filtered, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(chat) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chat.isGroup) {
                            Box(
                                Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Groups, "Group chat", tint = Green, modifier = Modifier.size(28.dp)) }
                        } else {
                            Box {
                                val photo = profilePhotos[chat.peerUid].orEmpty()
                                if (photo.isNotBlank()) {
                                    AsyncImage(model = photo, contentDescription = "Profile picture",
                                        modifier = Modifier.size(52.dp).clip(CircleShape).clickable { expandedProfileMenu = chat.id },
                                        contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .clickable { expandedProfileMenu = chat.id }, contentAlignment = Alignment.Center) {
                                        Text(chat.initials, color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                                DropdownMenu(expanded = expandedProfileMenu == chat.id, onDismissRequest = { expandedProfileMenu = "" }) {
                                    DropdownMenuItem(text = { Text("View profile picture") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                                        enabled = photo.isNotBlank(), onClick = { selectedPictureUrl = photo; expandedProfileMenu = "" })
                                    DropdownMenuItem(text = { Text("View profile") }, leadingIcon = { Icon(Icons.Default.Person, null) },
                                        onClick = { expandedProfileMenu = ""; onViewProfile(chat.peerUid) })
                                }
                            }
                        }

                        Spacer(Modifier.width(13.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.name,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                chat.message.ifBlank { "Start your conversation" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        val unreadCount = unreadCounts[chat.id] ?: 0
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .defaultMinSize(minWidth = 25.dp, minHeight = 25.dp)
                                    .background(Color(0xFF16A66A), CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 82.dp)
                    )
                }
                if (search.trim().length >= 2) {
                    item {
                        Text(
                            if (isSearchingMessages) "Searching messages…" else "Matching messages",
                            color = Green,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                    items(messageSearchResults, key = { "message-${it.chat.id}-${it.messageId}" }) { result ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(result.chat) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(result.chat.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(result.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                    if (hasMoreMessageSearchPages) {
                        item {
                            TextButton(
                                onClick = { loadOlderMessageSearchResults() },
                                enabled = !isLoadingMoreMessageSearch,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                if (isLoadingMoreMessageSearch) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Green)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Searching older messages…")
                                } else {
                                    Text("Search older messages", color = Green)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PeopleSearchDialog(
    currentUid: String,
    existingContacts: List<Chat>,
    onDismiss: () -> Unit,
    onOpenChat: (Chat) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Username") }
    var username by remember { mutableStateOf("") }
    var contactQuery by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AppUser?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("Search by an exact username to find someone on Khahish.") }
    val searchPrefs = remember { context.getSharedPreferences("khahish_people_search", Context.MODE_PRIVATE) }
    var recentSearches by remember { mutableStateOf(searchPrefs.getStringSet("recent_usernames", emptySet())?.toList().orEmpty()) }

    val filteredContacts = remember(existingContacts, contactQuery) {
        existingContacts.filter {
            it.name.contains(contactQuery.trim(), ignoreCase = true)
        }
    }

    fun searchUsername(value: String = username) {
        val term = normalizeKhahishUsername(value)
        if (!isValidKhahishUsername(term)) {
            searchMessage = "Username must be 3–20 characters: letters, numbers or _."
            result = null
            return
        }
        searching = true
        result = null
        searchMessage = "Searching…"
        db.collection("usernames").document(term).get()
            .addOnSuccessListener { claim ->
                val targetUid = claim.getString("uid").orEmpty()
                if (targetUid.isBlank() || targetUid == currentUid) {
                    searching = false
                    searchMessage = if (targetUid == currentUid) "This is your own username." else "No account found with @$term."
                } else {
                    db.collection("publicProfiles").document(targetUid).get()
                        .addOnSuccessListener { profile ->
                            searching = false
                            val found = profile.toObject(AppUser::class.java)
                            if (profile.exists() && found != null && found.uid == targetUid) {
                                result = found
                                searchMessage = ""
                                val updatedRecent = (listOf(term) + recentSearches.filterNot { it == term }).take(6)
                                recentSearches = updatedRecent
                                searchPrefs.edit().putStringSet("recent_usernames", updatedRecent.toSet()).apply()
                            } else {
                                searchMessage = "This profile is not available."
                            }
                        }
                        .addOnFailureListener {
                            searching = false
                            searchMessage = "Profile lookup failed: ${it.message ?: "Please try again."}"
                        }
                }
            }
            .addOnFailureListener {
                searching = false
                searchMessage = "Username search failed: ${it.message ?: "Please try again."}"
            }
    }

    fun openOrRequestChat(user: AppUser) {
        val chatId = privateChatId(currentUid, user.uid)
        val chatRef = db.collection("chats").document(chatId)
        chatRef.get()
            .addOnSuccessListener { existing ->
                val members = (existing.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (existing.exists() && currentUid in members && user.uid in members) {
                    onOpenChat(
                        Chat(
                            id = chatId,
                            peerUid = user.uid,
                            name = user.name.ifBlank { "Khahish User" },
                            message = existing.getString("lastMessage").orEmpty(),
                            time = "",
                            initials = user.name.firstOrNull()?.uppercase() ?: "K"
                        )
                    )
                    return@addOnSuccessListener
                }

                val requestRef = db.collection("messageRequests").document(chatId)
                requestRef.get()
                    .addOnSuccessListener { request ->
                        val status = request.getString("status").orEmpty()
                        when {
                            !request.exists() || status == "cancelled" -> {
                                val now = System.currentTimeMillis()
                                val senderName = FirebaseAuth.getInstance().currentUser?.displayName
                                    ?.takeIf { it.isNotBlank() } ?: "Khahish User"
                                requestRef.set(
                                    mapOf(
                                        "senderUid" to currentUid,
                                        "receiverUid" to user.uid,
                                        "senderName" to senderName,
                                        "senderUsername" to "",
                                        "receiverName" to user.name,
                                        "status" to "pending",
                                        "createdAt" to now
                                    )
                                ).addOnSuccessListener {
                                    showToast(context, "Message request sent. Chat opens after acceptance.")
                                    onDismiss()
                                }.addOnFailureListener {
                                    showToast(context, "Request failed: ${it.message}")
                                }
                            }
                            status == "pending" && request.getString("senderUid") == currentUid ->
                                showToast(context, "Your message request is already pending.")
                            status == "pending" ->
                                showToast(context, "This person has already requested to chat. Accept it from Chats.")
                            status == "rejected" ->
                                showToast(context, "This person rejected the request. Chat cannot start.")
                            status == "accepted" ->
                                showToast(context, "This request was accepted. Open your Chats list to continue.")
                            else -> showToast(context, "A chat request already exists.")
                        }
                    }
                    .addOnFailureListener {
                        showToast(context, "Request lookup failed: ${it.message}")
                    }
            }
            .addOnFailureListener {
                showToast(context, "Chat lookup failed: ${it.message}")
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF063D35), Color(0xFF087C65), Color(0xFF18A889))
                            )
                        )
                        .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 20.dp)
                ) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text("Find People", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Connect with people on Khahish", color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Username", "New Contact").forEach { tab ->
                        val selected = selectedTab == tab
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Green else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tab,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (selectedTab == "Username") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.lowercase(Locale.ROOT)
                                    .filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }
                                    .take(20)
                                result = null
                                searchMessage = "Tap Search to find this username."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter username") },
                            leadingIcon = { Text("@", color = Green, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { searchUsername() },
                            enabled = !searching,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) {
                            if (searching) {
                                CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(9.dp))
                            }
                            Text(if (searching) "Searching…" else "Search", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (result != null) {
                            val found = result!!
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { openOrRequestChat(found) }
                                    .padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (found.photoUrl.isNotBlank()) {
                                        AsyncImage(found.photoUrl, contentDescription = "Profile photo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Text(found.name.firstOrNull()?.uppercase() ?: "K", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(found.name.ifBlank { "Khahish User" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("@${found.username.ifBlank { normalizeKhahishUsername(username) }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.Chat, contentDescription = "Open or request chat", tint = Green)
                            }
                        } else if (searchMessage.isNotBlank()) {
                            Text(
                                searchMessage,
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (recentSearches.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Recent Searches", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            recentSearches.forEach { recent ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        username = recent
                                        searchUsername(recent)
                                    }.padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text("@$recent", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                    Icon(Icons.Default.NorthWest, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        OutlinedTextField(
                            value = contactQuery,
                            onValueChange = { contactQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search your contacts") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        if (filteredContacts.isEmpty()) {
                            Text(
                                "Your existing private-chat contacts will appear here.",
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                        } else {
                            LazyColumn(Modifier.heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filteredContacts, key = { it.peerUid }) { contact ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                            .clickable { onOpenChat(contact) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(contact.initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(11.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                            Text("Open conversation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = Green)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    chat: Chat,
    isGlassTheme: Boolean = false,
    onBack: () -> Unit,
    onViewProfile: (String) -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserId = auth.currentUser?.uid ?: return
    val readReceiptsEnabled = remember {
        context.getSharedPreferences("khahish_privacy_settings", Context.MODE_PRIVATE)
            .getBoolean("read_receipts", true)
    }

    val chatRef = remember(chat.id) {
        db.collection("chats").document(chat.id)
    }
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<MessageItem>()) }
    var liveLocations by remember(chat.id) { mutableStateOf(emptyMap<String, LiveLocationPoint>()) }
    var showLiveLocationOptions by remember { mutableStateOf(false) }
    var pendingLiveLocationDuration by remember { mutableStateOf(0L) }
    val messageListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val messageScrollScope = rememberCoroutineScope()
    var initialMessagesPositioned by remember(chat.id) { mutableStateOf(false) }
    val isAtLatestMessage by remember {
        derivedStateOf {
            messages.isEmpty() || messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == messages.lastIndex
        }
    }
    var isUploading by remember { mutableStateOf(false) }
    var pendingChatMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingChatMediaType by remember { mutableStateOf("") }
    var pendingChatMediaCaption by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var peerTyping by remember { mutableStateOf(false) }
    var peerOnline by remember { mutableStateOf(false) }
    var peerLastSeenAt by remember { mutableStateOf(0L) }
    var peerLastSeenVisible by remember { mutableStateOf(true) }
    var peerOnlineStatusVisible by remember { mutableStateOf(true) }
    var peerPhotoUrl by remember { mutableStateOf("") }
    var peerBio by remember { mutableStateOf("") }
    var showMediaPreviews by remember(chat.id) { mutableStateOf(true) }
    var notificationsMuted by remember(chat.id) { mutableStateOf(false) }
    var chatBubbleColorHex by remember(chat.id) { mutableStateOf("#18A889") }
    var chatWallpaperColorHex by remember(chat.id) { mutableStateOf("#F4F8F7") }
    var chatWallpaperImageUrl by remember(chat.id) { mutableStateOf("") }

    val chatSettingsRef = remember(currentUserId, chat.id) {
        db.collection("users").document(currentUserId)
            .collection("appSettings").document("chat_${chat.id}")
    }

    fun saveChatSetting(key: String, value: Any) {
        chatSettingsRef.set(
            mapOf(key to value, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        ).addOnFailureListener { showToast(context, "Could not save chat setting: ${it.message}") }
    }

    DisposableEffect(currentUserId, chat.id) {
        val settingsRegistration = chatSettingsRef.addSnapshotListener { snapshot, _ ->
            showMediaPreviews = snapshot?.getBoolean("showMediaPreviews") != false
            notificationsMuted = snapshot?.getBoolean("notificationsMuted") == true
            chatBubbleColorHex = snapshot?.getString("bubbleColor") ?: "#18A889"
            chatWallpaperColorHex = snapshot?.getString("wallpaperColor") ?: "#F4F8F7"
            chatWallpaperImageUrl = snapshot?.getString("wallpaperImageUrl").orEmpty()
        }
        onDispose { settingsRegistration.remove() }
    }
    var attachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceLocked by remember { mutableStateOf(false) }
    var voiceElapsedSeconds by remember { mutableLongStateOf(0L) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    val voiceHaptic = LocalHapticFeedback.current
    LaunchedEffect(isRecordingVoice) {
        while (isRecordingVoice) {
            delay(1000L)
            voiceElapsedSeconds += 1L
        }
    }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var editTarget by remember { mutableStateOf<MessageItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var pinnedMessageId by remember { mutableStateOf("") }
    var pinnedMessageText by remember { mutableStateOf("") }
    var pinnedBy by remember { mutableStateOf("") }
    var showGroupInfo by remember { mutableStateOf(false) }
    var showStarredDialog by remember { mutableStateOf(false) }
    var groupAdmins by remember(chat.id) { mutableStateOf(emptyList<String>()) }
    var groupMemberUids by remember(chat.id) { mutableStateOf(chat.memberUids) }
    var groupMemberNames by remember(chat.id) { mutableStateOf(emptyMap<String, String>()) }
    var displayGroupName by remember(chat.id) { mutableStateOf(chat.name) }
    var groupNameDraft by remember(chat.id) { mutableStateOf(chat.name) }
    var showAddGroupMembers by remember { mutableStateOf(false) }
    var groupMemberSearch by remember { mutableStateOf("") }
    var groupSearchResults by remember { mutableStateOf(emptyList<AppUser>()) }
    var peerMenuExpanded by remember { mutableStateOf(false) }
    var chatMenuExpanded by remember { mutableStateOf(false) }
    var showChatThemeDialog by remember { mutableStateOf(false) }
    var showPeerPicture by remember { mutableStateOf(false) }
    var videoToPlay by remember { mutableStateOf("") }
    var photoToView by remember { mutableStateOf("") }
    var showDeleteOptions by remember { mutableStateOf(false) }
    var showDisappearingOptions by remember { mutableStateOf(false) }
    var disappearingDurationMs by remember { mutableStateOf(0L) }
    var replyTarget by remember { mutableStateOf<MessageItem?>(null) }
    var reactionTarget by remember { mutableStateOf<MessageItem?>(null) }
    var reactionMenuExpanded by remember { mutableStateOf(false) }
    var selectedMoreExpanded by remember { mutableStateOf(false) }
    var messageInfoTarget by remember { mutableStateOf<MessageItem?>(null) }
    var showSecurityCodeInfo by remember { mutableStateOf(false) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardUsers by remember { mutableStateOf(emptyList<AppUser>()) }
    var forwardSearch by remember { mutableStateOf("") }
    var loadingForwardUsers by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf(false) }
    var chatSearchVisible by remember(chat.id) { mutableStateOf(false) }
    var chatSearchQuery by remember(chat.id) { mutableStateOf("") }

    var pendingMediaMessages by remember(chat.id) { mutableStateOf(emptyList<MessageItem>()) }
    val displayedMessages = remember(messages, pendingMediaMessages, chatSearchQuery) {
        val allMessages = (messages + pendingMediaMessages).sortedBy { it.timestamp }
        val query = chatSearchQuery.trim()
        if (query.isBlank()) allMessages else allMessages.filter { message ->
            message.text.contains(query, ignoreCase = true) ||
                message.replyToText.contains(query, ignoreCase = true)
        }
    }

    fun performChatMediaUpload(pendingMessage: MessageItem) {
        pendingMediaMessages = pendingMediaMessages.map { item ->
            if (item.id == pendingMessage.id) item.copy(uploadProgress = 0, uploadFailed = false) else item
        }
        uploadToCloudinary(
            context, Uri.parse(pendingMessage.uploadUri),
            when (pendingMessage.mediaType) { "image" -> "image"; "document" -> "raw"; else -> "video" },
            onSuccess = { url ->
                sendChatMessage(chatRef, currentUserId, pendingMessage.text, url, pendingMessage.mediaType, context) {
                    if (pendingMessage.deleteLocalFileAfterUpload) runCatching {
                        File(Uri.parse(pendingMessage.uploadUri).path.orEmpty()).delete()
                    }
                    pendingMediaMessages = pendingMediaMessages.filterNot { it.id == pendingMessage.id }
                }
            },
            onError = {
                pendingMediaMessages = pendingMediaMessages.map { item ->
                    if (item.id == pendingMessage.id) item.copy(uploadFailed = true) else item
                }
                showToast(context, it)
            },
            onProgress = { percent ->
                pendingMediaMessages = pendingMediaMessages.map { item ->
                    if (item.id == pendingMessage.id) item.copy(uploadProgress = percent) else item
                }
            }
        )
    }

    fun queueChatMedia(uri: Uri, mediaType: String, caption: String = "") {
        val pending = MessageItem(
            id = "local_upload_" + java.util.UUID.randomUUID(),
            senderId = currentUserId, text = caption, mediaType = mediaType,
            timestamp = System.currentTimeMillis(), uploadProgress = 0,
            localPreviewUri = uri.toString(), uploadUri = uri.toString()
        )
        pendingMediaMessages = pendingMediaMessages + pending
        messageScrollScope.launch {
            delay(120L)
            val lastIndex = displayedMessages.lastIndex
            if (lastIndex >= 0) messageListState.animateScrollToItem(lastIndex)
        }
        performChatMediaUpload(pending)
    }


    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingChatMediaUri = uri
            pendingChatMediaType = "image"
            pendingChatMediaCaption = ""
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(
                context, uri, "image",
                onSuccess = { url ->
                    isUploading = false
                    chatWallpaperImageUrl = url
                    saveChatSetting("wallpaperImageUrl", url)
                    showChatThemeDialog = false
                    showToast(context, "Chat wallpaper updated")
                },
                onError = {
                    isUploading = false
                    showToast(context, it)
                }
            )
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingChatMediaUri = uri
            pendingChatMediaType = "video"
            pendingChatMediaCaption = ""
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) queueChatMedia(uri, "audio") }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val columns = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0).orEmpty()
                        val number = cursor.getString(1).orEmpty()
                        val contactText = "Name: $name\nPhone: $number"
                        sendChatMessage(chatRef, currentUserId, contactText, "", "contact", context) { }
                    }
                }
            }
        }
    }

    fun sendLastKnownLocation() {
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
            if (location == null) {
                showToast(context, "No recent location is available. Turn on location and try again.")
            } else {
                val latitude = location.latitude
                val longitude = location.longitude
                val mapUrl = "https://maps.google.com/?q=$latitude,$longitude"
                val label = String.format(Locale.US, "Location shared: %.5f, %.5f", latitude, longitude)
                sendChatMessage(chatRef, currentUserId, label, mapUrl, "location", context) { }
            }
        } catch (error: SecurityException) {
            showToast(context, "Location permission is required.")
        } catch (error: Exception) {
            showToast(context, "Could not read location: ${error.message}")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) sendLastKnownLocation()
        else showToast(context, "Location permission was not granted.")
    }

    fun startLiveLocationService(durationMs: Long) {
        if (durationMs <= 0L) return
        val serviceIntent = Intent(context, KhahishLiveLocationService::class.java).apply {
            action = KhahishLiveLocationService.ACTION_START
            putExtra(KhahishLiveLocationService.EXTRA_CHAT_ID, chat.id)
            putExtra(KhahishLiveLocationService.EXTRA_UID, currentUserId)
            putExtra(KhahishLiveLocationService.EXTRA_DURATION_MS, durationMs)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            val label = when (durationMs) {
                15L * 60L * 1000L -> "15 minutes"
                60L * 60L * 1000L -> "1 hour"
                else -> "8 hours"
            }
            sendChatMessage(chatRef, currentUserId, "Live location shared for $label", "", "live_location", context) { }
            showToast(context, "Live location sharing started. Stop it from the notification at any time.")
        } catch (error: Exception) {
            showToast(context, "Could not start live location: ${error.message}")
        }
    }

    val liveLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        val duration = pendingLiveLocationDuration
        pendingLiveLocationDuration = 0L
        if (granted && duration > 0L) startLiveLocationService(duration)
        else showToast(context, "Location permission was not granted.")
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) queueChatMedia(uri, "document") }


    fun startVoiceRecording() {
        try {
            val output = File(context.cacheDir, "khahish_voice_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceRecorder = recorder
            voiceRecordingFile = output
            voiceElapsedSeconds = 0L
            voiceLocked = false
            isRecordingVoice = true
            showToast(context, "Recording… slide left to cancel, release to send")
        } catch (error: Exception) {
            voiceRecorder?.release()
            voiceRecorder = null
            isRecordingVoice = false
            showToast(context, "Could not start recording: ${error.message}")
        }
    }

    fun cancelVoiceRecording() {
        try {
            voiceRecorder?.stop()
        } catch (_: Exception) {
        } finally {
            voiceRecorder?.release()
            voiceRecorder = null
            voiceRecordingFile?.delete()
            voiceRecordingFile = null
            isRecordingVoice = false
            voiceLocked = false
            voiceElapsedSeconds = 0L
        }
    }

    fun finishVoiceRecording(send: Boolean) {
        val recorder = voiceRecorder
        val file = voiceRecordingFile
        var stoppedSuccessfully = false
        try { recorder?.stop(); stoppedSuccessfully = recorder != null }
        catch (_: Exception) { if (send) showToast(context, "Voice recording was too short. Please try again.") }
        finally {
            recorder?.release()
            voiceRecorder = null
            voiceRecordingFile = null
            isRecordingVoice = false
            voiceLocked = false
            voiceElapsedSeconds = 0L
        }
        if (!send || !stoppedSuccessfully || file == null || !file.exists() || file.length() < 1000L) {
            file?.delete(); return
        }
        val voiceUri = Uri.fromFile(file)
        val pendingVoice = MessageItem(
            id = "local_upload_" + java.util.UUID.randomUUID(),
            senderId = currentUserId, mediaType = "audio",
            timestamp = System.currentTimeMillis(), uploadProgress = 0,
            uploadUri = voiceUri.toString(), deleteLocalFileAfterUpload = true
        )
        pendingMediaMessages = pendingMediaMessages + pendingVoice
        messageScrollScope.launch {
            delay(120L)
            val lastIndex = displayedMessages.lastIndex
            if (lastIndex >= 0) messageListState.animateScrollToItem(lastIndex)
        }
        performChatMediaUpload(pendingVoice)
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { startVoiceRecording(); voiceLocked = true }
        else showToast(context, "Microphone permission is required for voice messages.")
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                voiceRecorder?.stop()
            } catch (_: Exception) {
            }
            voiceRecorder?.release()
            voiceRecorder = null
            voiceRecordingFile?.delete()
        }
    }

    DisposableEffect(chat.id) {
        val chatSettingsRegistration = chatRef.addSnapshotListener { snapshot, _ ->
            disappearingDurationMs = snapshot?.getLong("disappearingDurationMs") ?: 0L
            pinnedMessageId = snapshot?.getString("pinnedMessageId").orEmpty()
            pinnedMessageText = snapshot?.getString("pinnedMessageText").orEmpty()
            pinnedBy = snapshot?.getString("pinnedBy").orEmpty()
            if (chat.isGroup && snapshot != null) {
                groupMemberUids = snapshot.get("members") as? List<String> ?: chat.memberUids
                groupAdmins = snapshot.get("groupAdmins") as? List<String> ?: listOf(snapshot.getString("createdBy").orEmpty()).filter { it.isNotBlank() }
                groupMemberNames = (snapshot.get("memberNames") as? Map<*, *>)?.mapNotNull { (key, value) ->
                    if (key is String && value is String) key to value else null
                }?.toMap() ?: emptyMap()
                displayGroupName = snapshot.getString("groupName") ?: chat.name
                groupNameDraft = displayGroupName
                if (currentUserId !in groupMemberUids) onBack()
            }
        }
        onDispose { chatSettingsRegistration.remove() }
    }

    LaunchedEffect(showAddGroupMembers, groupMemberSearch, groupMemberUids.joinToString("|")) {
        if (!showAddGroupMembers) {
            groupSearchResults = emptyList()
        } else {
            val term = normalizeKhahishUsername(groupMemberSearch)
            if (term.length < 3 || !isValidKhahishUsername(term)) groupSearchResults = emptyList()
            else db.collection("usernames").document(term).get().addOnSuccessListener { claim ->
                val targetUid = claim.getString("uid").orEmpty()
                if (targetUid.isBlank() || targetUid == currentUserId || targetUid in groupMemberUids) groupSearchResults = emptyList()
                else db.collection("publicProfiles").document(targetUid).get().addOnSuccessListener { profile ->
                    groupSearchResults = listOfNotNull(profile.toObject(AppUser::class.java)?.takeIf { it.uid == targetUid })
                }
            }
        }
    }

    DisposableEffect(chat.peerUid) {
        val profileRegistration = db.collection("publicProfiles").document(chat.peerUid)
            .addSnapshotListener { snapshot, _ ->
                peerOnline = snapshot?.getBoolean("isOnline") == true
                peerPhotoUrl = snapshot?.getString("photoUrl").orEmpty()
                peerBio = snapshot?.getString("bio").orEmpty()
                peerLastSeenAt = snapshot?.getLong("lastSeenAt") ?: 0L
                peerLastSeenVisible = snapshot?.getBoolean("lastSeenVisible") ?: true
                peerOnlineStatusVisible = snapshot?.getBoolean("onlineStatusVisible") ?: true
            }
        onDispose { profileRegistration.remove() }
    }

    LaunchedEffect(messages.size, chat.id) {
        if (!initialMessagesPositioned && messages.isNotEmpty()) {
            val targetIndex = if (chat.initialMessageId.isNotBlank()) {
                messages.indexOfFirst { it.id == chat.initialMessageId }
            } else -1
            messageListState.scrollToItem(if (targetIndex >= 0) targetIndex else messages.lastIndex)
            initialMessagesPositioned = true
        }
    }

    // Automatically bring a newly sent message into view. Incoming messages do not
    // force-scroll the chat, so reading older messages remains undisturbed.
    LaunchedEffect(displayedMessages.lastOrNull()?.id, chat.id) {
        val newestMessage = displayedMessages.lastOrNull()
        if (newestMessage?.senderId == currentUserId) {
            delay(100L)
            val latestIndex = displayedMessages.lastIndex
            if (latestIndex >= 0) {
                messageListState.animateScrollToItem(latestIndex)
            }
        }
    }

    DisposableEffect(chat.id) {
        val registration = chatRef.collection("liveLocations").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            liveLocations = snapshot.documents.associate { doc ->
                doc.id to LiveLocationPoint(
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    isActive = doc.getBoolean("isActive") == true,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    expiresAt = doc.getLong("expiresAt") ?: 0L
                )
            }
        }
        onDispose { registration.remove() }
    }

    DisposableEffect(chat.id, currentUserId) {
        val messagesRegistration = chatRef.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                messages = snapshot.documents.map { doc ->
                    MessageItem(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        text = doc.getString("text") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        mediaType = doc.getString("mediaType") ?: "text",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        deliveredTo = doc.get("deliveredTo") as? List<String>
                            ?: emptyList(),
                        readBy = doc.get("readBy") as? List<String>
                            ?: emptyList(),
                        hiddenFor = doc.get("hiddenFor") as? List<String>
                            ?: emptyList(),
                        expiresAt = doc.getLong("expiresAt") ?: 0L,
                        replyToMessageId = doc.getString("replyToMessageId") ?: "",
                        replyToText = doc.getString("replyToText") ?: "",
                        reactions = (doc.get("reactions") as? Map<*, *>)?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap() ?: emptyMap(),
                        editedAt = doc.getLong("editedAt") ?: 0L,
                        starredBy = doc.get("starredBy") as? List<String> ?: emptyList(),
                        restoredFromBackup = doc.getBoolean("restoredFromBackup") == true,
                        isPending = doc.metadata.hasPendingWrites()
                    )
                }.filter { currentUserId !in it.hiddenFor && (it.expiresAt == 0L || it.expiresAt > System.currentTimeMillis()) }

                // Remove expired messages from Firestore once their deadline has passed.
                snapshot.documents.forEach { doc ->
                    val expiry = doc.getLong("expiresAt") ?: 0L
                    if (expiry > 0L && expiry <= System.currentTimeMillis()) {
                        doc.reference.delete()
                    }
                }

                // Sirf un messages par receipt update karein
                // jin par current user ki receipt abhi maujood nahi.
                snapshot.documents.forEach { doc ->
                    val senderId = doc.getString("senderId") ?: ""
                    val delivered = doc.get("deliveredTo") as? List<*>
                        ?: emptyList<Any>()
                    val read = doc.get("readBy") as? List<*>
                        ?: emptyList<Any>()

                    if (senderId.isNotBlank() && senderId != currentUserId) {
                        val updates = mutableMapOf<String, Any>()

                        if (currentUserId !in delivered) {
                            updates["deliveredTo"] =
                                FieldValue.arrayUnion(currentUserId)
                        }

                        if (readReceiptsEnabled && currentUserId !in read) {
                            updates["readBy"] =
                                FieldValue.arrayUnion(currentUserId)
                        }

                        if (updates.isNotEmpty()) {
                            doc.reference.update(updates)
                        }
                    }
                }
            }

        val typingRegistration = if (chat.isGroup) {
            chatRef.collection("typing").whereEqualTo("isTyping", true)
                .addSnapshotListener { snapshot, _ ->
                    peerTyping = snapshot?.documents?.any { it.id != currentUserId } == true
                }
        } else {
            chatRef.collection("typing").document(chat.peerUid)
                .addSnapshotListener { snapshot, _ ->
                    peerTyping = snapshot?.getBoolean("isTyping") == true
                }
        }

        onDispose {
            messagesRegistration.remove()
            typingRegistration.remove()

            chatRef.collection("typing")
                .document(currentUserId)
                .set(
                    mapOf(
                        "isTyping" to false,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
        }
    }

    fun updateTyping(isTyping: Boolean) {
        chatRef.collection("typing")
            .document(currentUserId)
            .set(
                mapOf(
                    "isTyping" to isTyping,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedMessageIds.isNotEmpty()) selectedMessageIds = emptySet() else onBack()
            }) { Icon(Icons.Default.ArrowBack, "Back") }
            if (selectedMessageIds.isNotEmpty()) {
                Text("${selectedMessageIds.size}", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                val selectedOne = messages.firstOrNull { it.id == selectedMessageIds.firstOrNull() }
                IconButton(
                    enabled = selectedMessageIds.size == 1,
                    onClick = { selectedOne?.let { replyTarget = it; selectedMessageIds = emptySet() } }
                ) { Icon(Icons.Default.Reply, "Reply") }
                IconButton(onClick = {
                    selectedMessageIds.forEach { id ->
                        val item = messages.firstOrNull { it.id == id } ?: return@forEach
                        val ref = chatRef.collection("messages").document(id)
                        if (currentUserId in item.starredBy) ref.update("starredBy", FieldValue.arrayRemove(currentUserId))
                        else ref.update("starredBy", FieldValue.arrayUnion(currentUserId))
                    }
                    selectedMessageIds = emptySet()
                }) { Icon(Icons.Default.Star, "Star") }
                IconButton(
                    enabled = selectedMessageIds.size == 1,
                    onClick = { messageInfoTarget = selectedOne }
                ) { Icon(Icons.Default.Info, "Message info") }
                IconButton(onClick = { showDeleteOptions = true }) {
                    Icon(Icons.Default.DeleteOutline, "Delete")
                }
                IconButton(onClick = { showForwardDialog = true }) {
                    Icon(Icons.Default.Forward, "Forward")
                }
                Box {
                    IconButton(onClick = { selectedMoreExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More message actions")
                    }
                    DropdownMenu(
                        expanded = selectedMoreExpanded,
                        onDismissRequest = { selectedMoreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pin") },
                            leadingIcon = { Icon(Icons.Default.PushPin, null) },
                            enabled = selectedMessageIds.size == 1,
                            onClick = {
                                selectedMoreExpanded = false
                                selectedOne?.let { item ->
                                    chatRef.update(mapOf(
                                        "pinnedMessageId" to item.id,
                                        "pinnedMessageText" to item.text.ifBlank { item.mediaType.replaceFirstChar { ch -> ch.uppercase() } },
                                        "pinnedBy" to currentUserId,
                                        "pinnedAt" to System.currentTimeMillis()
                                    )).addOnSuccessListener {
                                        showToast(context, "Message pinned")
                                        selectedMessageIds = emptySet()
                                    }.addOnFailureListener { showToast(context, "Could not pin message: ${it.message}") }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            enabled = selectedMessageIds.size == 1 && selectedOne?.senderId == currentUserId &&
                                selectedOne?.mediaType == "text" &&
                                (System.currentTimeMillis() - (selectedOne?.timestamp ?: 0L) <= 15 * 60 * 1000L),
                            onClick = {
                                selectedMoreExpanded = false
                                selectedOne?.let { editTarget = it; editText = it.text }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            enabled = selectedMessageIds.size == 1,
                            onClick = {
                                selectedMoreExpanded = false
                                selectedOne?.let { item ->
                                    val value = item.text.ifBlank { item.imageUrl }
                                    if (value.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Khahish message", value))
                                        showToast(context, "Copied")
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Translate") },
                            leadingIcon = { Icon(Icons.Default.Translate, null) },
                            enabled = selectedMessageIds.size == 1 && selectedOne?.text?.isNotBlank() == true,
                            onClick = {
                                selectedMoreExpanded = false
                                selectedOne?.text?.takeIf { it.isNotBlank() }?.let {
                                    openSharedUrl(context, "https://translate.google.com/?sl=auto&text=" + Uri.encode(it))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Verify security code") },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, null) },
                            onClick = { selectedMoreExpanded = false; showSecurityCodeInfo = true }
                        )
                    }
                }
            } else {
                if (chat.isGroup) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).clickable { showGroupInfo = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Groups, "Group info", tint = Green) }
                } else {
                    Box {
                        if (peerPhotoUrl.isNotBlank()) {
                            AsyncImage(model = peerPhotoUrl, contentDescription = "Profile picture",
                                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onViewProfile(chat.peerUid) }, contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).clickable { onViewProfile(chat.peerUid) }, contentAlignment = Alignment.Center) {
                                Text(chat.initials, color = Green, fontWeight = FontWeight.Bold)
                            }
                        }
                        DropdownMenu(expanded = peerMenuExpanded, onDismissRequest = { peerMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("View profile picture") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) }, enabled = peerPhotoUrl.isNotBlank(), onClick = { peerMenuExpanded = false; showPeerPicture = true })
                            DropdownMenuItem(text = { Text("View contact") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { peerMenuExpanded = false; onViewProfile(chat.peerUid) })
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (chat.isGroup) displayGroupName else chat.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!chat.isGroup) onViewProfile(chat.peerUid) else showGroupInfo = true },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    val peerStatusText = when {
                        chat.isGroup -> "Group · ${chat.memberUids.size} members"
                        peerTyping -> "typing..."
                        peerOnline && peerOnlineStatusVisible -> "Online"
                        peerLastSeenVisible && peerLastSeenAt > 0L -> {
                            val elapsed = (System.currentTimeMillis() - peerLastSeenAt).coerceAtLeast(0L)
                            val pattern = if (elapsed <= 7L * 24L * 60L * 60L * 1000L) "h:mm a" else "d MMM, h:mm a"
                            "Last seen " + java.text.SimpleDateFormat(pattern, Locale.getDefault())
                                .format(java.util.Date(peerLastSeenAt))
                        }
                        else -> ""
                    }
                    Text(peerStatusText, color = if (peerTyping || peerStatusText == "Online") Green else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    if (peerBio.isNotBlank()) Text(peerBio, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                }
                if (!chat.isGroup) {
                    IconButton(onClick = { onStartCall(false) }) { Icon(Icons.Default.Call, "Voice call", tint = Green) }
                    IconButton(onClick = { onStartCall(true) }) { Icon(Icons.Default.Videocam, "Video call", tint = Green) }
                }
                Box {
                    IconButton(onClick = { chatMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "Chat options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = chatMenuExpanded, onDismissRequest = { chatMenuExpanded = false }) {
                        if (!chat.isGroup) {
                            DropdownMenuItem(
                                text = { Text("View contact") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = { chatMenuExpanded = false; onViewProfile(chat.peerUid) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Search") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            onClick = { chatMenuExpanded = false; chatSearchVisible = true; chatSearchQuery = "" }
                        )
                        if (!chat.isGroup) {
                            DropdownMenuItem(
                                text = { Text("Report") },
                                leadingIcon = { Icon(Icons.Default.Report, null) },
                                onClick = { chatMenuExpanded = false; onViewProfile(chat.peerUid) }
                            )
                            DropdownMenuItem(
                                text = { Text("Block") },
                                leadingIcon = { Icon(Icons.Default.Block, null) },
                                onClick = { chatMenuExpanded = false; onViewProfile(chat.peerUid) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (notificationsMuted) "Unmute notifications" else "Mute notifications") },
                            leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                            onClick = {
                                chatMenuExpanded = false
                                notificationsMuted = !notificationsMuted
                                saveChatSetting("notificationsMuted", notificationsMuted)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Disappearing messages") },
                            leadingIcon = { Icon(Icons.Default.Timer, null) },
                            onClick = { chatMenuExpanded = false; showDisappearingOptions = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Chat Theme") },
                            leadingIcon = { Icon(Icons.Default.Palette, null) },
                            onClick = { chatMenuExpanded = false; showChatThemeDialog = true }
                        )
                        if (chat.isGroup) {
                            DropdownMenuItem(
                                text = { Text("Add members") },
                                leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                                onClick = { chatMenuExpanded = false; showAddGroupMembers = true }
                            )
                        }
                    }
                }
            }
        }

        if (chatSearchVisible) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { chatSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search all messages in this chat") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Text(displayedMessages.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                IconButton(onClick = { chatSearchVisible = false; chatSearchQuery = "" }) {
                    Icon(Icons.Default.Close, "Close search")
                }
            }
        }

        if (pinnedMessageId.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val index = messages.indexOfFirst { it.id == pinnedMessageId }
                        if (index >= 0) messageScrollScope.launch { messageListState.animateScrollToItem(index) }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PushPin, contentDescription = "Pinned message", tint = Green)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pinned message", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(pinnedMessageText, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1)
                }
                if (pinnedBy == currentUserId) {
                    IconButton(onClick = {
                        chatRef.update(mapOf(
                            "pinnedMessageId" to FieldValue.delete(),
                            "pinnedMessageText" to FieldValue.delete(),
                            "pinnedBy" to FieldValue.delete(),
                            "pinnedAt" to FieldValue.delete()
                        )).addOnFailureListener { showToast(context, "Could not unpin: ${it.message}") }
                    }) { Icon(Icons.Default.Close, contentDescription = "Unpin message", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        if (isUploading || isSending) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Green
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatWallpaperImageUrl.isNotBlank()) {
                AsyncImage(
                    model = chatWallpaperImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            LazyColumn(
                state = messageListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (chatWallpaperImageUrl.isBlank()) Color(android.graphics.Color.parseColor(chatWallpaperColorHex))
                        else Color.Transparent
                    )
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (chatSearchVisible && displayedMessages.isEmpty()) {
                    item { Text("No matching messages", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
                }
                items(displayedMessages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                val outgoingBubbleColor = remember(chatBubbleColorHex) {
                    runCatching {
                        Color(android.graphics.Color.parseColor(chatBubbleColorHex))
                    }.getOrElse { Green }
                }
                val outgoingBubbleTextColor = remember(outgoingBubbleColor) {
                    fun linearize(channel: Float): Float =
                        if (channel <= 0.04045f) channel / 12.92f
                        else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
                    val luminance =
                        0.2126f * linearize(outgoingBubbleColor.red) +
                        0.7152f * linearize(outgoingBubbleColor.green) +
                        0.0722f * linearize(outgoingBubbleColor.blue)
                    if (luminance > 0.179f) Color.Black else Color.White
                }
                val bubbleBackgroundColor = when {
                    isGlassTheme && isMe -> Color(0x6655D6B4)
                    isGlassTheme -> Color(0x55FFFFFF)
                    isMe -> Color(0xFFE1FFC3)
                    else -> Color.White
                }
                val bubbleTextColor = Color(0xFF101810)
                val bubbleSecondaryTextColor = if (isGlassTheme) {
                    if (isMe) Color(0xFFE4FFF8) else Color(0xFFF0FFFC)
                } else Color(0xFF89958A)
                val inlineTextMeta = msg.mediaType == "text" &&
                    msg.text.isNotBlank() &&
                    msg.replyToMessageId.isBlank() &&
                    !msg.restoredFromBackup &&
                    msg.reactions.isEmpty()

                val isSelected = msg.id in selectedMessageIds
                val swipeOffset = remember(msg.id) { Animatable(0f) }
                val swipeScope = rememberCoroutineScope()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 285.dp)
                            .graphicsLayer { translationX = swipeOffset.value }
                            .pointerInput(msg.id) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, amount ->
                                        swipeScope.launch {
                                            swipeOffset.snapTo(
                                                (swipeOffset.value + amount).coerceIn(-150f, 150f)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(swipeOffset.value) > 70f) replyTarget = msg
                                        swipeScope.launch {
                                            swipeOffset.animateTo(
                                                0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        swipeScope.launch {
                                            swipeOffset.animateTo(
                                                0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                )
                            }
                            .combinedClickable(
                                onClick = {
                                    if (selectedMessageIds.isNotEmpty()) {
                                        selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                    }
                                },
                                onLongClick = { reactionTarget = msg }
                            )
                            .shadow(
                                if (isGlassTheme) 9.dp else 2.dp,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                )
                            )
                            .border(
                                if (isGlassTheme) 1.dp else 0.5.dp,
                                if (isSelected) Green else if (isGlassTheme) Color.White.copy(alpha = 0.48f) else Color(0x1F52705D),
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                )
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surfaceVariant else bubbleBackgroundColor,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                )
                            )
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        if (chat.isGroup && !isMe) {
                            Text(
                                groupMemberNames[msg.senderId] ?: "Group member",
                                color = Green,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        when (msg.mediaType) {
                            "image" -> {
                                if (showMediaPreviews) {
                                    AsyncImage(
                                        model = msg.localPreviewUri.takeIf { msg.uploadProgress >= 0 } ?: msg.imageUrl,
                                        contentDescription = "Shared photo — tap to view full screen",
                                        modifier = Modifier
                                            .sizeIn(maxWidth = 250.dp, maxHeight = 250.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { photoToView = msg.imageUrl },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { photoToView = msg.imageUrl }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.VisibilityOff, null, tint = Green)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Photo preview hidden • tap to view", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            "video" -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { if (msg.uploadProgress < 0) videoToPlay = msg.imageUrl }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = Green, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (msg.uploadProgress >= 0) "Uploading video…" else if (showMediaPreviews) "Play video" else "Video preview hidden • tap to play",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            "audio" -> if (msg.uploadProgress >= 0) Text("Voice message uploading…", color = bubbleTextColor) else AudioMessagePlayer(msg.imageUrl)

                            "document" -> if (msg.uploadProgress >= 0) Text("Document uploading…", color = bubbleTextColor) else MediaOpenRow(
                                "Document — tap to open", Icons.Default.Description, msg.imageUrl, context
                            )

                            "live_location" -> {
                                val point = liveLocations[msg.senderId]
                                val active = point?.isActive == true && point.expiresAt > System.currentTimeMillis()
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            if (point != null && active) {
                                                openSharedUrl(context, "https://maps.google.com/?q=${point.latitude},${point.longitude}")
                                            } else {
                                                showToast(context, "Live location sharing has ended.")
                                            }
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MyLocation, null, tint = Green)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Live location", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text(if (active) "Tap to view latest location" else "Sharing ended", color = Green, fontSize = 11.sp)
                                        Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                            }

                            "location" -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { openSharedUrl(context, msg.imageUrl) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Shared location", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to open in Maps", color = Green, fontSize = 11.sp)
                                    Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                            "contact" -> Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContactPage, null, tint = Green)
                                    Spacer(Modifier.width(7.dp))
                                    Text("Shared contact", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                }
                                Text(msg.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }

                        if (msg.uploadProgress >= 0) {
                            UploadProgressBadge(
                                progress = msg.uploadProgress, failed = msg.uploadFailed,
                                onRetry = { performChatMediaUpload(msg) },
                                modifier = Modifier.align(Alignment.End).padding(top = 5.dp)
                            )
                        }
                        if (msg.replyToMessageId.isNotBlank()) {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(7.dp)) {
                                Text("Reply", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(msg.replyToText, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 2)
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        if (msg.text.isNotBlank() && msg.mediaType != "location" && msg.mediaType != "contact") {
                            if (inlineTextMeta) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        msg.text,
                                        modifier = Modifier.weight(1f, fill = false),
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else bubbleTextColor,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                                                .format(java.util.Date(msg.timestamp)),
                                            fontSize = 10.sp,
                                            color = bubbleSecondaryTextColor
                                        )
                                        if (msg.editedAt > 0L) {
                                            Text(" · edited", fontSize = 9.sp, color = bubbleSecondaryTextColor)
                                        }
                                        if (isMe) {
                                            Spacer(Modifier.width(4.dp))
                                            if (msg.isPending) {
                                                Icon(Icons.Default.Schedule, contentDescription = "Waiting to send", tint = bubbleSecondaryTextColor, modifier = Modifier.size(13.dp))
                                            } else {
                                                val read = msg.readBy.any { it != currentUserId }
                                                val delivered = msg.deliveredTo.any { it != currentUserId }
                                                Text(
                                                    when {
                                                        read -> "✓✓"
                                                        delivered -> "✓✓"
                                                        else -> "✓"
                                                    },
                                                    color = if (read) Color(0xFF35B7E8) else bubbleSecondaryTextColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(msg.text, color = if (isSelected) MaterialTheme.colorScheme.onSurface else bubbleTextColor, fontSize = 15.sp)
                            }
                        }
                        if (msg.restoredFromBackup) {
                            Text("Imported backup • sender not verified", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                        }
                        if (msg.reactions.isNotEmpty()) {
                            Row(
                                Modifier.align(Alignment.End)
                                    .padding(top = 2.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                msg.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                                    Text(emoji + if (count > 1) " $count" else "", fontSize = 11.sp)
                                }
                            }
                        }

                        if (!inlineTextMeta) Row(
                            Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                java.text.SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(java.util.Date(msg.timestamp)),
                                fontSize = 10.sp,
                                color = bubbleSecondaryTextColor
                            )
                            if (msg.editedAt > 0L) {
                                Text(" · edited", fontSize = 9.sp, color = bubbleSecondaryTextColor)
                            }


                            if (isMe) {
                                Spacer(Modifier.width(4.dp))
                                if (msg.isPending) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = "Waiting to send",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    val read = msg.readBy.any { it != currentUserId }
                                    val delivered = msg.deliveredTo.any { it != currentUserId }
                                    Text(
                                        when {
                                            read -> "✓✓"
                                            delivered -> "✓✓"
                                            else -> "✓"
                                        },
                                        color = if (read) Color(0xFF1686D9) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
            if (!isAtLatestMessage && messages.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = {
                        val lastIndex = messages.lastIndex
                        if (lastIndex >= 0) {
                            // Smoothly jump to the newest message.
                            messageScrollScope.launch {
                                messageListState.animateScrollToItem(lastIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Green,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest message")
                }
            }
        }

        if (replyTarget != null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Replying to message", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(replyTarget!!.text.ifBlank { replyTarget!!.mediaType }, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, fontSize = 12.sp)
                }
                IconButton(onClick = { replyTarget = null }) { Icon(Icons.Default.Close, "Cancel reply") }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(30.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(30.dp))
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { attachmentMenu = true }) {
                    Icon(Icons.Default.AttachFile, "Attachments", tint = Green)
                }

                DropdownMenu(
                    expanded = attachmentMenu,
                    onDismissRequest = { attachmentMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Photo") },
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        onClick = {
                            attachmentMenu = false
                            imagePicker.launch("image/*")
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Video") },
                        leadingIcon = { Icon(Icons.Default.Videocam, null) },
                        onClick = {
                            attachmentMenu = false
                            videoPicker.launch("video/*")
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Audio file") },
                        leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                        onClick = {
                            attachmentMenu = false
                            audioPicker.launch("audio/*")
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Document / PDF") },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        onClick = {
                            attachmentMenu = false
                            documentPicker.launch("*/*")
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Share location") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        onClick = {
                            attachmentMenu = false
                            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) sendLastKnownLocation()
                            else locationPermissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share live location") },
                        leadingIcon = { Icon(Icons.Default.MyLocation, null) },
                        onClick = {
                            attachmentMenu = false
                            showLiveLocationOptions = true
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Share contact") },
                        leadingIcon = { Icon(Icons.Default.ContactPage, null) },
                        onClick = {
                            attachmentMenu = false
                            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }
                    )
                }
            }

            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.PhotoLibrary, "Open gallery", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    updateTyping(it.isNotBlank())
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        if (isRecordingVoice && !voiceLocked) Color(0xFFE53935) else Green,
                        CircleShape
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val message = text.trim()
                            when {
                                isUploading || isSending -> Unit
                                isRecordingVoice -> { voiceHaptic.performHapticFeedback(HapticFeedbackType.LongPress); finishVoiceRecording(true) }
                                message.isNotBlank() -> {
                                    text = ""; updateTyping(false); isSending = true
                                    sendChatMessage(chatRef, currentUserId, message, "", "text", context,
                                        replyToMessageId = replyTarget?.id.orEmpty(),
                                        replyToText = replyTarget?.text?.ifBlank { replyTarget?.mediaType ?: "Media" }.orEmpty()
                                    ) { isSending = false; replyTarget = null }
                                }
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                                    voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                else -> {
                                    voiceHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startVoiceRecording()
                                    var lockedByGesture = false
                                    var cancelledByGesture = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        val verticalDrag = change.position.y - down.position.y
                                        val horizontalDrag = change.position.x - down.position.x
                                        if (!lockedByGesture && verticalDrag < -72f) {
                                            lockedByGesture = true; voiceLocked = true
                                            voiceHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else if (!lockedByGesture && (verticalDrag > 95f || abs(horizontalDrag) > 125f)) {
                                            cancelledByGesture = true
                                        }
                                        if (!change.pressed) break
                                    }
                                    voiceHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!lockedByGesture) finishVoiceRecording(!cancelledByGesture)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        voiceLocked && isRecordingVoice -> Icons.Default.Lock
                        isRecordingVoice -> Icons.Default.Mic
                        text.isNotBlank() -> Icons.Default.Send
                        else -> Icons.Default.Mic
                    },
                    when {
                        voiceLocked && isRecordingVoice -> "Recording locked; tap to send"
                        isRecordingVoice -> "Hold to record; swipe up to lock"
                        text.isNotBlank() -> "Send message"
                        else -> "Press and hold to record voice"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }

    if (showForwardDialog) {
        LaunchedEffect(showForwardDialog, forwardSearch) {
            val term = normalizeKhahishUsername(forwardSearch)
            if (!showForwardDialog || term.length < 3 || !isValidKhahishUsername(term)) {
                forwardUsers = emptyList()
                loadingForwardUsers = false
            } else {
                loadingForwardUsers = true
                db.collection("usernames").document(term).get().addOnSuccessListener { claim ->
                    val targetUid = claim.getString("uid").orEmpty()
                    if (targetUid.isBlank() || targetUid == currentUserId) {
                        forwardUsers = emptyList()
                        loadingForwardUsers = false
                    } else db.collection("publicProfiles").document(targetUid).get().addOnSuccessListener { profile ->
                        forwardUsers = listOfNotNull(profile.toObject(AppUser::class.java)?.takeIf { it.uid == targetUid })
                        loadingForwardUsers = false
                    }.addOnFailureListener {
                        loadingForwardUsers = false
                        showToast(context, "Profile lookup failed: ${it.message}")
                    }
                }.addOnFailureListener {
                    loadingForwardUsers = false
                    showToast(context, "Username lookup failed: ${it.message}")
                }
            }
        }
        Dialog(
            onDismissRequest = { if (!forwarding) showForwardDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 34.dp).heightIn(max = 560.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 18.dp
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Forward message", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${selectedMessageIds.size} selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { if (!forwarding) showForwardDialog = false }) {
                                Icon(Icons.Default.Close, "Close")
                            }
                        }
                        OutlinedTextField(
                            value = forwardSearch,
                            onValueChange = { forwardSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Recipient username") },
                            placeholder = { Text("Enter exact username") },
                            singleLine = true
                        )
                        if (loadingForwardUsers) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Green)
                            }
                        } else if (forwardUsers.isEmpty()) {
                            Text(
                                "Search for a Khahish user by username. The chat must already be available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.weight(1f))
                        } else {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                forwardUsers.forEach { target ->
                                    TextButton(
                                        enabled = !forwarding,
                                        onClick = {
                                            forwarding = true
                                            val targetRef = db.collection("chats").document(privateChatId(currentUserId, target.uid))
                                            targetRef.get().addOnSuccessListener { existing ->
                                                if (!existing.exists()) {
                                                    forwarding = false
                                                    showToast(context, "Send a message request and wait for acceptance before forwarding.")
                                                    return@addOnSuccessListener
                                                }
                                                val batch = db.batch()
                                                messages.filter { it.id in selectedMessageIds }.forEach { original ->
                                                    val ref = targetRef.collection("messages").document()
                                                    batch.set(ref, mapOf(
                                                        "senderId" to currentUserId,
                                                        "text" to original.text,
                                                        "imageUrl" to original.imageUrl,
                                                        "mediaType" to original.mediaType,
                                                        "timestamp" to System.currentTimeMillis(),
                                                        "deliveredTo" to emptyList<String>(),
                                                        "readBy" to listOf(currentUserId),
                                                        "forwarded" to true
                                                    ))
                                                }
                                                batch.commit().addOnSuccessListener {
                                                    targetRef.update(mapOf(
                                                        "lastMessage" to "Forwarded message",
                                                        "lastMessageAt" to System.currentTimeMillis(),
                                                        "updatedAt" to System.currentTimeMillis()
                                                    ))
                                                    forwarding = false
                                                    showForwardDialog = false
                                                    selectedMessageIds = emptySet()
                                                    forwardSearch = ""
                                                    showToast(context, "Messages forwarded to ${target.name}")
                                                }.addOnFailureListener {
                                                    forwarding = false
                                                    showToast(context, "Forward failed: ${it.message}")
                                                }
                                            }.addOnFailureListener {
                                                forwarding = false
                                                showToast(context, "Could not open chat: ${it.message}")
                                            }
                                        }
                                    ) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, null, tint = Green)
                                            Spacer(Modifier.width(10.dp))
                                            Text(target.name, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (!forwarding) showForwardDialog = false }, enabled = !forwarding) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAddGroupMembers && chat.isGroup) {
        AlertDialog(
            onDismissRequest = { showAddGroupMembers = false },
            title = { Text("Add participants") },
            text = {
                Column(Modifier.heightIn(max = 380.dp)) {
                    OutlinedTextField(
                        value = groupMemberSearch,
                        onValueChange = { groupMemberSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search by exact username") },
                        singleLine = true
                    )
                    if (normalizeKhahishUsername(groupMemberSearch).length < 3) {
                        Text("Enter at least 2 letters to search.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    } else if (groupSearchResults.isEmpty()) {
                        Text("No new users found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            groupSearchResults.forEach { candidate ->
                                TextButton(onClick = {
                                    chatRef.update(
                                        "members", FieldValue.arrayUnion(candidate.uid),
                                        "visibleToUids", FieldValue.arrayUnion(candidate.uid),
                                        "memberNames.${candidate.uid}", candidate.name.ifBlank { "Khahish User" }
                                    ).addOnSuccessListener {
                                        groupMemberSearch = ""
                                        showToast(context, "${candidate.name} added to group")
                                    }.addOnFailureListener { showToast(context, "Could not add participant: ${it.message}") }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(candidate.name, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddGroupMembers = false }) { Text("Done") } }
        )
    }

    if (showStarredDialog) {
        val starred = messages.filter { currentUserId in it.starredBy }
        AlertDialog(
            onDismissRequest = { showStarredDialog = false },
            title = { Text("Starred messages") },
            text = {
                if (starred.isEmpty()) Text("You have not starred any messages in this chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    starred.forEach { item ->
                        TextButton(onClick = {
                            val index = messages.indexOfFirst { it.id == item.id }
                            if (index >= 0) messageScrollScope.launch { messageListState.animateScrollToItem(index) }
                            showStarredDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.text.ifBlank { item.mediaType.replaceFirstChar { ch -> ch.uppercase() } }, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStarredDialog = false }) { Text("Close") } }
        )
    }

    if (showGroupInfo && chat.isGroup) {
        val isGroupAdmin = currentUserId in groupAdmins
        AlertDialog(
            onDismissRequest = { showGroupInfo = false },
            title = { Text("Group info") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (isGroupAdmin) {
                        OutlinedTextField(
                            value = groupNameDraft,
                            onValueChange = { groupNameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group name") },
                            singleLine = true
                        )
                        TextButton(onClick = {
                            val clean = groupNameDraft.trim()
                            if (clean.isNotBlank()) chatRef.update("groupName", clean)
                                .addOnSuccessListener { displayGroupName = clean; showToast(context, "Group name updated") }
                                .addOnFailureListener { showToast(context, "Could not rename group: ${it.message}") }
                        }) { Text("Save group name") }
                    }
                    Text("${groupMemberUids.size} participants", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (isGroupAdmin) {
                        TextButton(onClick = { showAddGroupMembers = true }) {
                            Icon(Icons.Default.PersonAdd, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Add participants")
                        }
                    }
                    groupMemberUids.forEach { memberUid ->
                        val memberName = groupMemberNames[memberUid] ?: if (memberUid == currentUserId) "You" else memberUid.take(10)
                        val memberIsAdmin = memberUid in groupAdmins
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(memberName, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                if (memberIsAdmin) Text("Group admin", color = Green, fontSize = 10.sp)
                            }
                            if (isGroupAdmin && memberUid != currentUserId) {
                                TextButton(onClick = {
                                    val update = if (memberIsAdmin) FieldValue.arrayRemove(memberUid) else FieldValue.arrayUnion(memberUid)
                                    chatRef.update("groupAdmins", update)
                                        .addOnSuccessListener { showToast(context, if (memberIsAdmin) "Admin role removed" else "Promoted to admin") }
                                        .addOnFailureListener { showToast(context, "Role update failed: ${it.message}") }
                                }) { Text(if (memberIsAdmin) "Remove admin" else "Make admin", fontSize = 10.sp) }
                                TextButton(onClick = {
                                    chatRef.update(
                                        "members", FieldValue.arrayRemove(memberUid),
                                        "visibleToUids", FieldValue.arrayRemove(memberUid),
                                        "groupAdmins", FieldValue.arrayRemove(memberUid),
                                        "memberNames.$memberUid", FieldValue.delete()
                                    ).addOnSuccessListener { showToast(context, "Participant removed") }
                                        .addOnFailureListener { showToast(context, "Could not remove participant: ${it.message}") }
                                }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                            }
                        }
                    }
                    val canLeave = currentUserId !in groupAdmins || groupAdmins.size > 1
                    TextButton(enabled = canLeave, onClick = {
                        chatRef.update(
                            "members", FieldValue.arrayRemove(currentUserId),
                            "visibleToUids", FieldValue.arrayRemove(currentUserId),
                            "groupAdmins", FieldValue.arrayRemove(currentUserId),
                            "memberNames.$currentUserId", FieldValue.delete()
                        ).addOnSuccessListener { showGroupInfo = false; onBack() }
                            .addOnFailureListener { showToast(context, "Could not leave group: ${it.message}") }
                    }) { Text("Leave group", color = MaterialTheme.colorScheme.error) }
                    if (!canLeave) Text("Promote another admin before leaving this group.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showGroupInfo = false }) { Text("Done") } }
        )
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    minLines = 2,
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = editTarget ?: return@TextButton
                    val updated = editText.trim()
                    if (updated.isBlank()) {
                        showToast(context, "Message cannot be empty.")
                    } else if (System.currentTimeMillis() - target.timestamp > 15 * 60 * 1000L) {
                        showToast(context, "The 15-minute edit window has expired.")
                    } else {
                        chatRef.collection("messages").document(target.id).update(
                            mapOf("text" to updated, "editedAt" to System.currentTimeMillis())
                        ).addOnSuccessListener {
                            chatRef.update(mapOf("lastMessage" to updated, "updatedAt" to System.currentTimeMillis()))
                            selectedMessageIds = emptySet()
                            editTarget = null
                            showToast(context, "Message updated")
                        }.addOnFailureListener { showToast(context, "Edit failed: ${it.message}") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } }
        )
    }

    if (reactionTarget != null) {
        val target = reactionTarget!!
        Dialog(onDismissRequest = { reactionTarget = null; reactionMenuExpanded = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Box {
                        IconButton(onClick = { reactionMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More message actions")
                        }
                        DropdownMenu(
                            expanded = reactionMenuExpanded,
                            onDismissRequest = { reactionMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                leadingIcon = { Icon(Icons.Default.Forward, null) },
                                onClick = {
                                    selectedMessageIds = setOf(target.id)
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                    showForwardDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = {
                                    selectedMessageIds = setOf(target.id)
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                    showDeleteOptions = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = {
                                    val value = target.text.ifBlank { target.imageUrl }
                                    if (value.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Khahish message", value))
                                        showToast(context, "Copied")
                                    }
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pin") },
                                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                onClick = {
                                    chatRef.update(mapOf(
                                        "pinnedMessageId" to target.id,
                                        "pinnedMessageText" to target.text.ifBlank { target.mediaType.replaceFirstChar { ch -> ch.uppercase() } },
                                        "pinnedBy" to currentUserId,
                                        "pinnedAt" to System.currentTimeMillis()
                                    )).addOnSuccessListener { showToast(context, "Message pinned") }
                                        .addOnFailureListener { showToast(context, "Could not pin message: ${it.message}") }
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Translate") },
                                leadingIcon = { Icon(Icons.Default.Translate, null) },
                                enabled = target.text.isNotBlank(),
                                onClick = {
                                    if (target.text.isNotBlank()) openSharedUrl(
                                        context,
                                        "https://translate.google.com/?sl=auto&text=" + Uri.encode(target.text)
                                    )
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Verify security code") },
                                leadingIcon = { Icon(Icons.Default.VerifiedUser, null) },
                                onClick = {
                                    showSecurityCodeInfo = true
                                    reactionTarget = null
                                    reactionMenuExpanded = false
                                }
                            )
                        }
                    }
                    Row(
                        Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "😂", "😮", "😢", "👍", "🙏").forEach { emoji ->
                            TextButton(onClick = {
                                val messageRef = chatRef.collection("messages").document(target.id)
                                val updateTask = if (target.reactions[currentUserId] == emoji) {
                                    messageRef.update("reactions.$currentUserId", FieldValue.delete())
                                } else {
                                    messageRef.update("reactions.$currentUserId", emoji)
                                }
                                updateTask.addOnSuccessListener { reactionTarget = null }
                                    .addOnFailureListener {
                                        showToast(context, "Reaction failed: ${it.message}")
                                        reactionTarget = null
                                    }
                            }) { Text(emoji, fontSize = 24.sp) }
                        }
                    }
                }
            }
        }
    }

    if (messageInfoTarget != null) {
        val item = messageInfoTarget!!
        AlertDialog(
            onDismissRequest = { messageInfoTarget = null },
            title = { Text("Message info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sent: " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date(item.timestamp)))
                    Text("Delivered to: ${item.deliveredTo.size}")
                    Text("Read by: ${item.readBy.size}")
                }
            },
            confirmButton = { TextButton(onClick = { messageInfoTarget = null }) { Text("Close") } }
        )
    }

    if (showSecurityCodeInfo) {
        AlertDialog(
            onDismissRequest = { showSecurityCodeInfo = false },
            title = { Text("Verify security code") },
            text = { Text("Security-code verification will become available when end-to-end encryption is enabled for Khahish chats. No unverified code is shown here.") },
            confirmButton = { TextButton(onClick = { showSecurityCodeInfo = false }) { Text("OK") } }
        )
    }
    if (showPeerPicture && peerPhotoUrl.isNotBlank()) Dialog(onDismissRequest = { showPeerPicture = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.82f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = peerPhotoUrl, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { showPeerPicture = false }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
    if (photoToView.isNotBlank()) Dialog(
        onDismissRequest = { photoToView = "" },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            var photoScale by remember(photoToView) { mutableFloatStateOf(1f) }
            var photoOffset by remember(photoToView) { mutableStateOf(Offset.Zero) }
            val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
                photoScale = (photoScale * zoomChange).coerceIn(1f, 5f)
                photoOffset = if (photoScale > 1f) photoOffset + panChange else Offset.Zero
            }
            AsyncImage(
                model = photoToView,
                contentDescription = "Full-screen photo — pinch to zoom",
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = photoScale,
                        scaleY = photoScale,
                        translationX = photoOffset.x,
                        translationY = photoOffset.y
                    )
                    .transformable(zoomState),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = { photoToView = "" },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .background(Color.Black.copy(alpha = .45f), CircleShape)
            ) { Icon(Icons.Default.Close, "Close photo", tint = Color.White) }
        }
    }
    if (pendingChatMediaUri != null) {
        Dialog(
            onDismissRequest = {
                if (!isUploading) {
                    pendingChatMediaUri = null
                    pendingChatMediaType = ""
                    pendingChatMediaCaption = ""
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (pendingChatMediaType == "video") "Preview video" else "Preview photo",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                    IconButton(
                        enabled = !isUploading,
                        onClick = {
                            pendingChatMediaUri = null
                            pendingChatMediaType = ""
                            pendingChatMediaCaption = ""
                        }
                    ) { Icon(Icons.Default.Close, "Cancel media") }
                }
                if (pendingChatMediaType == "video") {
                    KhahishVideoPlayer(
                        url = pendingChatMediaUri.toString(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    AsyncImage(
                        model = pendingChatMediaUri,
                        contentDescription = "Selected photo preview",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                OutlinedTextField(
                    value = pendingChatMediaCaption,
                    onValueChange = { pendingChatMediaCaption = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a caption...") },
                    maxLines = 3,
                    enabled = !isUploading
                )
                if (isUploading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                    Text("Uploading media…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !isUploading,
                        onClick = {
                            pendingChatMediaUri = null
                            pendingChatMediaType = ""
                            pendingChatMediaCaption = ""
                        }
                    ) { Text("Cancel") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isUploading && !isSending,
                        onClick = {
                            val uri = pendingChatMediaUri ?: return@Button
                            val type = pendingChatMediaType
                            val caption = pendingChatMediaCaption.trim()
                            queueChatMedia(uri, type, caption)
                            pendingChatMediaUri = null
                            pendingChatMediaType = ""
                            pendingChatMediaCaption = ""
                        }
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Send")
                    }
                }
            }
        }
    }

    if (videoToPlay.isNotBlank()) Dialog(
        onDismissRequest = { videoToPlay = "" },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            KhahishVideoPlayer(
                url = videoToPlay,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
            IconButton(
                onClick = { videoToPlay = "" },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .background(Color.Black.copy(alpha = .5f), CircleShape)
            ) { Icon(Icons.Default.Close, "Close video", tint = Color.White) }
        }
    }

    if (showLiveLocationOptions) {
        AlertDialog(
            onDismissRequest = { showLiveLocationOptions = false },
            title = { Text("Share live location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your location will update while sharing is active. A persistent Android notification lets you stop sharing at any time.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    listOf(
                        15L * 60L * 1000L to "15 minutes",
                        60L * 60L * 1000L to "1 hour",
                        8L * 60L * 60L * 1000L to "8 hours"
                    ).forEach { (duration, label) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showLiveLocationOptions = false
                                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) startLiveLocationService(duration)
                                else {
                                    pendingLiveLocationDuration = duration
                                    liveLocationPermissionLauncher.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ))
                                }
                            }
                        ) { Text(label, color = Green) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLiveLocationOptions = false }) { Text("Cancel") } }
        )
    }

    if (showChatThemeDialog) {
        val themeGroups = listOf(
            "Featured" to listOf(
                Triple("Khahish Green", "#18A889", "#F4F8F7"),
                Triple("Ocean", "#287DA8", "#EAF5FA"),
                Triple("Sunset", "#C56A48", "#FFF1E8")
            ),
            "Nature" to listOf(
                Triple("Forest", "#397A58", "#EAF4EC"),
                Triple("Sage", "#66856B", "#F0F5EC"),
                Triple("Earth", "#936B4D", "#F5EEE5")
            ),
            "Minimal" to listOf(
                Triple("Lavender", "#8070A8", "#F4F0FA"),
                Triple("Rose", "#B96E82", "#FFF1F4")
            ),
            "Dark" to listOf(
                Triple("Midnight", "#4E9B87", "#101918"),
                Triple("Graphite", "#8C9AA6", "#171C22")
            )
        )
        AlertDialog(
            onDismissRequest = { showChatThemeDialog = false },
            title = { Text("Chat Theme") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { wallpaperPicker.launch("image/*") },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUploading) "Uploading wallpaper…" else "Choose photo wallpaper")
                    }
                    if (chatWallpaperImageUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                chatWallpaperImageUrl = ""
                                saveChatSetting("wallpaperImageUrl", "")
                            }
                        ) { Text("Remove photo wallpaper") }
                    }
                    themeGroups.forEach { group ->
                        Text(group.first, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        group.second.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        chatBubbleColorHex = option.second
                                        chatWallpaperColorHex = option.third
                                        chatWallpaperImageUrl = ""
                                        saveChatSetting("bubbleColor", option.second)
                                        saveChatSetting("wallpaperColor", option.third)
                                        saveChatSetting("wallpaperImageUrl", "")
                                        showChatThemeDialog = false
                                    }
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color(android.graphics.Color.parseColor(option.third))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(option.second)))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(option.first, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    chatBubbleColorHex = "#18A889"
                    chatWallpaperColorHex = "#F4F8F7"
                    chatWallpaperImageUrl = ""
                    saveChatSetting("bubbleColor", "#18A889")
                    saveChatSetting("wallpaperColor", "#F4F8F7")
                    saveChatSetting("wallpaperImageUrl", "")
                    showChatThemeDialog = false
                }) { Text("Reset default") }
            },
            dismissButton = { TextButton(onClick = { showChatThemeDialog = false }) { Text("Close") } }
        )
    }

    if (showDisappearingOptions) {
        AlertDialog(
            onDismissRequest = { showDisappearingOptions = false },
            title = { Text("Disappearing messages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose how long new messages stay in this chat. This applies to both participants.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    listOf(0L to "Off", 86_400_000L to "24 Hours", 604_800_000L to "7 Days", 7_776_000_000L to "90 Days").forEach { (duration, label) ->
                        Row(Modifier.fillMaxWidth().clickable {
                            chatRef.update("disappearingDurationMs", duration)
                                .addOnSuccessListener { showToast(context, "Disappearing messages: $label") }
                                .addOnFailureListener { showToast(context, "Could not update setting: ${it.message}") }
                            showDisappearingOptions = false
                        }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = disappearingDurationMs == duration, onClick = {
                                chatRef.update("disappearingDurationMs", duration)
                                    .addOnSuccessListener { showToast(context, "Disappearing messages: $label") }
                                    .addOnFailureListener { showToast(context, "Could not update setting: ${it.message}") }
                                showDisappearingOptions = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDisappearingOptions = false }) { Text("Close") } }
        )
    }

    if (showDeleteOptions && selectedMessageIds.isNotEmpty()) {
        val selectedMessages = messages.filter { it.id in selectedMessageIds }
        val canDeleteForEveryone = selectedMessages.isNotEmpty() && selectedMessages.all { it.senderId == currentUserId }
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            title = { Text("Delete selected messages?") },
            text = { Text("Choose whether to remove these messages only from your chat or for everyone.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        selectedMessageIds.toList().forEach { id ->
                            chatRef.collection("messages").document(id)
                                .update("hiddenFor", FieldValue.arrayUnion(currentUserId))
                                .addOnFailureListener { showToast(context, "Could not delete message: ${it.message}") }
                        }
                        selectedMessageIds = emptySet()
                        showDeleteOptions = false
                    }) { Text("Delete for me") }
                    if (canDeleteForEveryone) {
                        TextButton(onClick = {
                            selectedMessageIds.toList().forEach { id ->
                                chatRef.collection("messages").document(id).delete()
                                    .addOnFailureListener { showToast(context, "Could not delete for everyone: ${it.message}") }
                            }
                            selectedMessageIds = emptySet()
                            showDeleteOptions = false
                        }) { Text("Delete for everyone", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteOptions = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AudioMessagePlayer(url: String) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(false) }
    var durationMs by remember(url) { mutableStateOf(0) }
    var progress by remember(url) { mutableStateOf(0f) }
    var seeking by remember(url) { mutableStateOf(false) }
    var playbackSpeed by remember(url) { mutableStateOf(1f) }

    DisposableEffect(url) {
        onDispose { try { player?.release() } catch (_: Exception) { }; player = null }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            if (!seeking) {
                val active = player
                if (active != null) {
                    durationMs = active.duration.coerceAtLeast(durationMs)
                    progress = (active.currentPosition.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }
    LaunchedEffect(player, playbackSpeed) {
        player?.let { active ->
            try { active.playbackParams = active.playbackParams.setSpeed(playbackSpeed) }
            catch (_: Exception) { }
        }
    }
    fun timeText(ms: Int): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    Row(
        Modifier.widthIn(max = 248.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(enabled = !loading) {
                    if (playing) {
                        player?.pause()
                        playing = false
                    } else if (player != null) {
                        if (durationMs > 0 && progress >= 0.999f) {
                            player?.seekTo(0)
                            progress = 0f
                        }
                        player?.start()
                        playing = true
                    } else {
                        loading = true
                        val created = MediaPlayer()
                        try {
                            created.setDataSource(url)
                            created.setOnPreparedListener { ready ->
                                loading = false
                                durationMs = ready.duration.coerceAtLeast(0)
                                player = ready
                                ready.start()
                                playing = true
                            }
                            created.setOnCompletionListener { done ->
                                playing = false
                                done.seekTo(0)
                                progress = 0f
                            }
                            created.setOnErrorListener { failed, _, _ ->
                                loading = false
                                playing = false
                                failed.release()
                                player = null
                                Toast.makeText(context, "Unable to play this voice message", Toast.LENGTH_SHORT).show()
                                true
                            }
                            created.prepareAsync()
                        } catch (_: Exception) {
                            loading = false
                            created.release()
                            Toast.makeText(context, "Unable to play this voice message", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.CenterStart) {
                val bars = 25
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(bars) { index ->
                        val barHeight = 7f + kotlin.math.abs(kotlin.math.sin(index * 0.79)).toFloat() * 19f
                        Box(
                            Modifier.weight(1f).height(barHeight.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (index.toFloat() / (bars - 1) <= progress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Text("🦎", Modifier.offset(x = (maxWidth - 19.dp) * progress).align(Alignment.CenterStart), fontSize = 17.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = progress,
                    onValueChange = { seeking = true; progress = it },
                    onValueChangeFinished = {
                        if (durationMs > 0) player?.seekTo((durationMs * progress).toInt())
                        seeking = false
                    },
                    enabled = durationMs > 0 && !loading,
                    modifier = Modifier.weight(1f).height(17.dp),
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onPrimary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.width(4.dp))
                Text(timeText(durationMs), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                Text(
                    when (playbackSpeed) { 1.5f -> "1.5×"; 2f -> "2×"; else -> "1×" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 6.dp).clickable {
                        playbackSpeed = when (playbackSpeed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    }
                )
            }
            Text(timeText((durationMs * progress).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MediaOpenRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    url: String,
    context: Context
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openSharedUrl(context, url) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Green)
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun isKhahishNetworkAvailable(context: Context): Boolean {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun sendChatMessage(
    chatRef: DocumentReference,
    senderId: String,
    messageText: String,
    mediaUrl: String,
    mediaType: String,
    context: Context,
    replyToMessageId: String = "",
    replyToText: String = "",
    onComplete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val completionDelivered = java.util.concurrent.atomic.AtomicBoolean(false)
    fun completeOnce() {
        if (completionDelivered.compareAndSet(false, true)) onComplete()
    }

    fun sendUsingCachedSettings(chatSnapshot: com.google.firebase.firestore.DocumentSnapshot?) {
        fun sendNow() {
            val duration = chatSnapshot?.getLong("disappearingDurationMs") ?: 0L
            val data = hashMapOf<String, Any>(
                "senderId" to senderId,
                "text" to messageText,
                "imageUrl" to mediaUrl,
                "mediaType" to mediaType,
                "timestamp" to now,
                "deliveredTo" to emptyList<String>(),
                "readBy" to listOf(senderId)
            )
            if (duration > 0L) data["expiresAt"] = now + duration
            if (replyToMessageId.isNotBlank()) {
                data["replyToMessageId"] = replyToMessageId
                data["replyToText"] = replyToText
            }

            // Firestore stores this write in its local persistent queue immediately,
            // renders it with hasPendingWrites=true, and retries it after reconnect.
            val writeTask = chatRef.collection("messages").add(data)
            completeOnce()

            writeTask.addOnSuccessListener { messageDocument ->
                KhahishPushClient.notifyMessage(chatRef.id, messageDocument.id)
                val preview = when (mediaType) {
                    "image" -> "Photo"
                    "video" -> "Video"
                    "audio" -> "Audio"
                    "document" -> "Document"
                    "location" -> "Location"
                    "contact" -> "Contact"
                    else -> messageText
                }
                chatRef.update(mapOf(
                    "lastMessage" to preview,
                    "lastMessageAt" to now,
                    "updatedAt" to now
                )).addOnCompleteListener { updateTask ->
                    if (!updateTask.isSuccessful) showToast(context, "Message sent, but chat preview could not update.")
                    completeOnce()
                }
            }.addOnFailureListener { error ->
                completeOnce()
                showToast(context, "Message could not be synced: ${error.message}")
            }
        }

        val members = chatSnapshot?.get("members") as? List<*> ?: emptyList<Any>()
        val otherUid = members.filterIsInstance<String>().firstOrNull { it != senderId }.orEmpty()
        if (chatSnapshot?.getBoolean("isGroup") != true && members.size == 2 && otherUid.isNotBlank()) {
            if (!isKhahishNetworkAvailable(context)) {
                // Rules re-check blocked users when this queued write reaches Firestore.
                sendNow()
            } else {
                FirebaseFirestore.getInstance().collection("users").document(senderId)
                    .collection("blocked").document(otherUid).get()
                    .addOnSuccessListener { myBlock ->
                        if (myBlock.exists()) {
                            completeOnce()
                            showToast(context, "Message not sent. You have blocked this account.")
                        } else sendNow()
                    }
                    .addOnFailureListener {
                        completeOnce()
                        showToast(context, "Could not verify your blocked list. Please try again.")
                    }
            }
        } else {
            sendNow()
        }
    }

    if (isKhahishNetworkAvailable(context)) {
        chatRef.get().addOnSuccessListener { sendUsingCachedSettings(it) }
            .addOnFailureListener { error ->
                completeOnce()
                showToast(context, "Could not read chat settings: ${error.message}")
            }
    } else {
        chatRef.get(com.google.firebase.firestore.Source.CACHE)
            .addOnSuccessListener { sendUsingCachedSettings(it) }
            .addOnFailureListener {
                // A first-time/offline chat may have no cached document. Still
                // enqueue the message locally; the server enforces security on sync.
                sendUsingCachedSettings(null)
            }
    }
}

private fun openSharedUrl(context: Context, url: String) {
    if (url.isBlank()) {
        showToast(context, "Media link is not available")
        return
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        showToast(context, "Unable to open this file")
    }
}

private fun startKhahishCall(
    context: Context,
    chat: Chat,
    isVideo: Boolean
) {
    showToast(
        context,
        if (isVideo) {
            "Video calling service is not connected yet."
        } else {
            "Voice calling service is not connected yet."
        }
    )
}
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid ?: ""

    var showBlocked by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf(listOf<String>()) }
    var loadingBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(uid, showBlocked) {
        if (uid.isNotBlank() && showBlocked) {
            loadingBlocked = true

            db.collection("users")
                .document(uid)
                .collection("blocked")
                .get()
                .addOnSuccessListener { snapshot ->
                    blockedUsers = snapshot.documents.map { it.id }
                    loadingBlocked = false
                }
                .addOnFailureListener {
                    loadingBlocked = false
                    showToast(context, "Unable to load blocked users")
                }
        }
    }

    if (showStorage) {
        StorageMediaPage(
            modifier = modifier,
            onBack = { showStorage = false }
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))

        SettingsGroup("ACCOUNT") {
            SettingsItem(
                Icons.Default.Person,
                "Account information",
                "Your email and account details"
            ) {
                showToast(
                    context,
                    auth.currentUser?.email ?: "Account information unavailable"
                )
            }

            SettingsItem(
                Icons.Default.Edit,
                "Edit profile",
                "Manage your public profile"
            ) {
                showToast(context, "Profile editing is not included in this update.")
            }

            SettingsItem(
                Icons.Default.Lock,
                "Privacy and security",
                "Manage your privacy"
            ) {
                showToast(context, "Privacy controls are not connected yet.")
            }

            SettingsItem(
                Icons.Default.Block,
                "Blocked accounts",
                "View and unblock accounts"
            ) {
                showBlocked = !showBlocked
            }

            if (showBlocked) {
                if (loadingBlocked) {
                    CircularProgressIndicator(color = Green)
                } else if (blockedUsers.isEmpty()) {
                    Text(
                        "No blocked accounts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    blockedUsers.forEach { blockedUid ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                blockedUid.take(12) + "...",
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = {
                                    db.collection("users")
                                        .document(uid)
                                        .collection("blocked")
                                        .document(blockedUid)
                                        .delete()
                                        .addOnSuccessListener {
                                            blockedUsers =
                                                blockedUsers.filterNot {
                                                    it == blockedUid
                                                }
                                            showToast(context, "Account unblocked")
                                        }
                                        .addOnFailureListener {
                                            showToast(
                                                context,
                                                "Unblock failed: ${it.message}"
                                            )
                                        }
                                }
                            ) {
                                Text("Unblock", color = Green)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("CHAT SETTINGS") {
            SettingsItem(
                Icons.Default.Chat,
                "Chat settings",
                "Message and conversation preferences"
            ) {
                showToast(context, "Chat preferences are not connected yet.")
            }

            SettingsItem(
                Icons.Default.Notifications,
                "Notifications",
                "Android notification settings"
            ) {
                try {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ).apply {
                        putExtra(
                            android.provider.Settings.EXTRA_APP_PACKAGE,
                            context.packageName
                        )
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    showToast(context, "Unable to open notification settings")
                }
            }

            SettingsItem(
                Icons.Default.Storage,
                "Storage and media",
                "Auto-download and storage information"
            ) {
                showStorage = true
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("HELP") {
            SettingsItem(
                Icons.Default.Help,
                "Help and support",
                "Get assistance"
            ) {
                showToast(context, "Help and support will be connected later.")
            }

            SettingsItem(
                Icons.Default.Info,
                "About Khahish",
                "Khahish (خواہش)"
            ) {
                showToast(context, "Khahish Messenger version 1.0")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE54848)
            )
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Log out")
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
internal fun StorageMediaPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("khahish_media_settings", Context.MODE_PRIVATE)
    }

    var autoPhotos by remember {
        mutableStateOf(preferences.getBoolean("auto_photos", true))
    }
    var autoVideos by remember {
        mutableStateOf(preferences.getBoolean("auto_videos", false))
    }
    var autoAudio by remember {
        mutableStateOf(preferences.getBoolean("auto_audio", false))
    }
    var autoDocuments by remember {
        mutableStateOf(preferences.getBoolean("auto_documents", false))
    }
    var saveToGallery by remember {
        mutableStateOf(preferences.getBoolean("save_gallery", false))
    }
    var storageBytes by remember { mutableStateOf(0L) }
    var calculating by remember { mutableStateOf(true) }

    fun refreshStorage() {
        calculating = true
        Thread {
            val total = directorySize(context.cacheDir) +
                directorySize(context.filesDir)

            Handler(Looper.getMainLooper()).post {
                storageBytes = total
                calculating = false
            }
        }.start()
    }

    LaunchedEffect(Unit) {
        refreshStorage()
    }

    fun savePreference(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }

            Text(
                "Storage and media",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("STORAGE USAGE") {
            Text(
                "Khahish app storage",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            if (calculating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Green
                )
            } else {
                Text(
                    formatStorageSize(storageBytes),
                    color = Green,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "App files and cache on this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { refreshStorage() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh storage information")
            }

            OutlinedButton(
                onClick = {
                    try {
                        context.cacheDir.deleteRecursively()
                        showToast(context, "Temporary cache cleared")
                        refreshStorage()
                    } catch (_: Exception) {
                        showToast(context, "Unable to clear cache")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear temporary cache")
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("MEDIA AUTO-DOWNLOAD") {
            Text(
                "Choose which media types may be automatically downloaded when auto-download is supported.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            MediaPreferenceRow(
                title = "Photos",
                subtitle = "Photo auto-download preference",
                checked = autoPhotos,
                onCheckedChange = {
                    autoPhotos = it
                    savePreference("auto_photos", it)
                }
            )

            MediaPreferenceRow(
                title = "Videos",
                subtitle = "Video auto-download preference",
                checked = autoVideos,
                onCheckedChange = {
                    autoVideos = it
                    savePreference("auto_videos", it)
                }
            )

            MediaPreferenceRow(
                title = "Audio",
                subtitle = "Audio auto-download preference",
                checked = autoAudio,
                onCheckedChange = {
                    autoAudio = it
                    savePreference("auto_audio", it)
                }
            )

            MediaPreferenceRow(
                title = "Documents",
                subtitle = "PDF and other document preference",
                checked = autoDocuments,
                onCheckedChange = {
                    autoDocuments = it
                    savePreference("auto_documents", it)
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("MEDIA VISIBILITY") {
            MediaPreferenceRow(
                title = "Save media to gallery",
                subtitle = "Gallery saving preference",
                checked = saveToGallery,
                onCheckedChange = {
                    saveToGallery = it
                    savePreference("save_gallery", it)
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Note: These preferences are saved on this device. Automatic downloading and gallery saving will take effect when those features are connected to the media system. Storage usage currently reports Khahish app files and cache, not your Cloudinary online files.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MediaPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Green
            )
        )
    }
}

private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()

    return file.listFiles()?.sumOf { child ->
        directorySize(child)
    } ?: 0L
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)

    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)

    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            title,
            color = Green,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(23.dp)
        )

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        var uploadedBytes = 0L
        var lastReported = -1
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount); uploadedBytes += byteCount
                if (totalBytes > 0L) {
                    val percent = ((uploadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    if (percent != lastReported) { lastReported = percent; onProgress(percent) }
                }
            }
        }.buffer()
        delegate.writeTo(countingSink); countingSink.flush()
    }
}

@Composable
private fun UploadProgressBadge(progress: Int, failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.78f), CircleShape).padding(6.dp), contentAlignment = Alignment.Center) {
        if (failed) {
            IconButton(onClick = onRetry, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry upload", tint = Color.White)
            }
        } else {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = progress.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxSize(), color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f), strokeWidth = 3.dp)
                Text("$progress%", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun uploadToCloudinary(
    context: Context, uri: Uri, resourceType: String,
    onSuccess: (String) -> Unit, onError: (String) -> Unit,
    onProgress: (Int) -> Unit = {}
) {
    Thread {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot read selected file")
            val bytes = inputStream.use { it.readBytes() }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "khahish_" + System.currentTimeMillis(), bytes.toRequestBody(mimeType.toMediaType()))
                .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET).build()
            val progressBody = ProgressRequestBody(requestBody) { percent ->
                Handler(Looper.getMainLooper()).post { onProgress(percent) }
            }
            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/" + resourceType + "/upload")
                .post(progressBody).build()
            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS).writeTimeout(8, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES).callTimeout(10, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true).build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("Upload error: " + response.code + " " + responseText)
                val secureUrl = JSONObject(responseText).optString("secure_url")
                if (secureUrl.isBlank()) throw Exception("Upload URL was not returned")
                Handler(Looper.getMainLooper()).post { onSuccess(secureUrl) }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Handler(Looper.getMainLooper()).post { onError("Upload took too long because of the network. Please check your internet and try again.") }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { onError(e.message ?: "Media upload failed. Please try again.") }
        }
    }.start()
}

@Composable
fun PlaceholderPage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Construction,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun KhahishUserProfileScreen(
    uid: String,
    onBack: () -> Unit,
    onStartCall: (peerUid: String, peerName: String, isVideo: Boolean) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val context = LocalContext.current
    val settingsRef = remember(currentUid, uid) {
        db.collection("users").document(currentUid).collection("appSettings").document("chat_${privateChatId(currentUid, uid)}")
    }

    var name by remember(uid) { mutableStateOf("Loading profile…") }
    var photo by remember(uid) { mutableStateOf("") }
    var username by remember(uid) { mutableStateOf("") }
    var bio by remember(uid) { mutableStateOf("") }
    var online by remember(uid) { mutableStateOf(false) }
    var loading by remember(uid) { mutableStateOf(true) }
    var showPicture by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Spam or unwanted content") }

    var notificationsMuted by remember(uid) { mutableStateOf(false) }
    var showMediaPreviews by remember(uid) { mutableStateOf(true) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showMediaDialog by remember { mutableStateOf(false) }
    var showStarredDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var chatMessages by remember(uid) { mutableStateOf(emptyList<MessageItem>()) }
    var loadingMessages by remember { mutableStateOf(false) }
    var messageLoadError by remember { mutableStateOf("") }
    var mediaStorageBytes by remember(uid) { mutableStateOf(0L) }

    val chatId = privateChatId(currentUid, uid)
    val mediaMessages = remember(chatMessages) {
        chatMessages.filter { it.mediaType != "text" || it.imageUrl.isNotBlank() || it.text.contains("http", true) }
    }
    val starredMessages = remember(chatMessages, currentUid) {
        chatMessages.filter { currentUid in it.starredBy }
    }
    val searchResults = remember(chatMessages, searchText) {
        val query = searchText.trim()
        if (query.isBlank()) emptyList()
        else chatMessages.filter { it.text.contains(query, ignoreCase = true) }
    }

    fun loadChatMessages() {
        if (currentUid.isBlank() || loadingMessages) return
        loadingMessages = true
        messageLoadError = ""
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(250)
            .get()
            .addOnSuccessListener { snapshot ->
                chatMessages = snapshot.documents.map { document ->
                    MessageItem(
                        id = document.id,
                        senderId = document.getString("senderId").orEmpty(),
                        text = document.getString("text").orEmpty(),
                        imageUrl = document.getString("imageUrl").orEmpty(),
                        mediaType = document.getString("mediaType") ?: "text",
                        timestamp = document.getLong("timestamp") ?: 0L,
                        starredBy = document.get("starredBy") as? List<String> ?: emptyList()
                    )
                }
                mediaStorageBytes = snapshot.documents.sumOf {
                    it.getLong("fileSizeBytes") ?: it.getLong("sizeBytes") ?: 0L
                }
                loadingMessages = false
            }
            .addOnFailureListener {
                loadingMessages = false
                messageLoadError = it.message ?: "Unable to load chat details."
            }
    }

    fun saveChatSetting(key: String, value: Boolean) {
        if (currentUid.isBlank()) return
        settingsRef.set(
            mapOf(key to value, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        ).addOnFailureListener {
            Toast.makeText(context, "Setting could not be saved: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(uid) {
        val listener = db.collection("publicProfiles").document(uid).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                name = snap.getString("name") ?: "Khahish User"
                photo = snap.getString("photoUrl").orEmpty()
                username = snap.getString("username").orEmpty()
                bio = snap.getString("bio").orEmpty()
                online = snap.getBoolean("isOnline") == true && snap.getBoolean("onlineStatusVisible") != false
            }
            loading = false
        }
        onDispose { listener.remove() }
    }

    DisposableEffect(uid, currentUid) {
        val registration = settingsRef.addSnapshotListener { snapshot, _ ->
            notificationsMuted = snapshot?.getBoolean("notificationsMuted") == true
            showMediaPreviews = snapshot?.getBoolean("showMediaPreviews") != false
        }
        onDispose { registration.remove() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Contact Info", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (photo.isNotBlank()) {
                    AsyncImage(
                        model = photo,
                        contentDescription = "Profile picture",
                        modifier = Modifier.size(142.dp).clip(CircleShape).clickable { showPicture = true },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.size(142.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(name, color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (username.isNotBlank()) Text("@$username", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text(if (online) "Online" else "Khahish member", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (bio.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text(bio, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onStartCall(uid, name, false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Default.Call, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Voice")
                    }
                    OutlinedButton(
                        onClick = { onStartCall(uid, name, true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                }

                Spacer(Modifier.height(18.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        ContactInfoActionRow(Icons.Default.Search, "Search in chat", "Find messages in the latest 250 records") {
                            showSearchDialog = true
                            loadChatMessages()
                        }
                        HorizontalDivider()
                        ContactInfoActionRow(Icons.Default.PhotoLibrary, "Media, links & docs", "${mediaMessages.size} items in recent messages") {
                            showMediaDialog = true
                            loadChatMessages()
                        }
                        HorizontalDivider()
                        ContactInfoActionRow(Icons.Default.Storage, "Manage storage", "Review shared media usage") {
                            showStorageDialog = true
                            loadChatMessages()
                        }
                        HorizontalDivider()
                        ContactInfoActionRow(Icons.Default.Star, "Starred messages", "${starredMessages.size} starred in recent messages") {
                            showStarredDialog = true
                            loadChatMessages()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mute notifications", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Silence message alerts from this chat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Switch(checked = notificationsMuted, onCheckedChange = {
                                notificationsMuted = it
                                saveChatSetting("notificationsMuted", it)
                            })
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Media visibility", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Show or hide photo previews in this chat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Switch(checked = showMediaPreviews, onCheckedChange = {
                                showMediaPreviews = it
                                saveChatSetting("showMediaPreviews", it)
                            })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showReportDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Report, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Report")
                    }
                    OutlinedButton(onClick = { showBlockConfirm = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Block")
                    }
                }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search in chat") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search messages") }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (loadingMessages) CircularProgressIndicator()
                    else if (messageLoadError.isNotBlank()) Text(messageLoadError, color = MaterialTheme.colorScheme.error)
                    else if (searchResults.isEmpty()) Text(if (searchText.isBlank()) "Enter a word to search recent messages." else "No matching messages.")
                    else LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(searchResults.take(30), key = { it.id }) { message ->
                            Text(message.text.ifBlank { message.mediaType }, Modifier.fillMaxWidth().padding(vertical = 7.dp), color = MaterialTheme.colorScheme.onSurface)
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearchDialog = false }) { Text("Done") } }
        )
    }

    if (showMediaDialog) {
        AlertDialog(
            onDismissRequest = { showMediaDialog = false },
            title = { Text("Media, links & docs") },
            text = {
                when {
                    loadingMessages -> CircularProgressIndicator()
                    messageLoadError.isNotBlank() -> Text(messageLoadError, color = MaterialTheme.colorScheme.error)
                    mediaMessages.isEmpty() -> Text("No media or links found in the latest 250 messages.")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(mediaMessages, key = { it.id }) { message ->
                            Text(
                                "${message.mediaType.replaceFirstChar { it.uppercase() }} • ${message.text.ifBlank { "Shared item" }}",
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMediaDialog = false }) { Text("Done") } }
        )
    }

    if (showStarredDialog) {
        AlertDialog(
            onDismissRequest = { showStarredDialog = false },
            title = { Text("Starred messages") },
            text = {
                when {
                    loadingMessages -> CircularProgressIndicator()
                    messageLoadError.isNotBlank() -> Text(messageLoadError, color = MaterialTheme.colorScheme.error)
                    starredMessages.isEmpty() -> Text("No starred messages in the latest 250 messages.")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(starredMessages, key = { it.id }) { message ->
                            Text(message.text.ifBlank { message.mediaType }, Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStarredDialog = false }) { Text("Done") } }
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Manage storage") },
            text = {
                when {
                    loadingMessages -> CircularProgressIndicator()
                    messageLoadError.isNotBlank() -> Text(messageLoadError, color = MaterialTheme.colorScheme.error)
                    else -> Column {
                        Text("Media and link items in recent messages: ${mediaMessages.size}")
                        Text(
                            if (mediaStorageBytes > 0L) "Recorded file size: ${android.text.format.Formatter.formatShortFileSize(context, mediaStorageBytes)}"
                            else "File sizes are not recorded for these uploads."
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("This page reviews usage only; it does not delete chat files.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStorageDialog = false }) { Text("Done") } }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block $name?") },
            text = { Text("This account will be added to your blocked list. You can unblock it later from Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    if (currentUid.isBlank()) return@TextButton
                    db.collection("users").document(currentUid).collection("blocked").document(uid)
                        .set(mapOf("uid" to uid, "name" to name, "createdAt" to System.currentTimeMillis()))
                        .addOnSuccessListener {
                            showBlockConfirm = false
                            Toast.makeText(context, "Account blocked", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .addOnFailureListener { Toast.makeText(context, "Block failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report account") },
            text = {
                Column {
                    Text("Why are you reporting this account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("Spam or unwanted content", "Harassment or bullying", "Fake or impersonation account", "Inappropriate content", "Other").forEach { reason ->
                        Row(Modifier.fillMaxWidth().clickable { reportReason = reason }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentUid.isBlank()) return@TextButton
                    db.collection("reports").add(mapOf(
                        "type" to "user",
                        "targetUid" to uid,
                        "targetName" to name,
                        "reporterUid" to currentUid,
                        "reason" to reportReason,
                        "status" to "pending",
                        "createdAt" to System.currentTimeMillis()
                    )).addOnSuccessListener {
                        showReportDialog = false
                        Toast.makeText(context, "Report submitted for review.", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { Toast.makeText(context, "Report failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }) { Text("Submit report") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPicture && photo.isNotBlank()) Dialog(
        onDismissRequest = { showPicture = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.85f).background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(model = photo, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { showPicture = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ContactInfoActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
