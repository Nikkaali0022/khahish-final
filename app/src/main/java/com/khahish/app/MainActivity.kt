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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.statusBarsPadding
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
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

data class MessageRequestItem(val id: String, val senderUid: String, val senderName: String, val senderUsername: String, val createdAt: Long)

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
    val starredBy: List<String> = emptyList()
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
        "updatedAt" to now
    )

    db.collection("users").document(uid)
        .set(privateData, SetOptions.merge())

    db.collection("publicProfiles").document(uid)
        .set(publicData, SetOptions.merge())
}

class MainActivity : ComponentActivity() {
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
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        setContent {
            KhahishApp()
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
            "lastSeenVisible" to showLastSeen
        )
        if (!foreground && showLastSeen) publicUpdates["lastSeenAt"] = now
        else if (!showLastSeen) publicUpdates["lastSeenAt"] = FieldValue.delete()
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).set(privateUpdates, SetOptions.merge())
        db.collection("publicProfiles").document(uid).set(publicUpdates, SetOptions.merge())
    }
}

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

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var tab by remember { mutableStateOf("Chats") }
    val tabOrder = listOf("Chats", "Updates", "Communities", "Calls", "Settings")
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var profileTargetUid by remember { mutableStateOf("") }
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<KhahishCall?>(null) }
    var incomingCall by remember { mutableStateOf(false) }
    val latestActiveCall by rememberUpdatedState(activeCall)

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
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
            (selectedChat != null || showNewChat || showNewGroup || profileTargetUid.isNotBlank() || tab != "Chats")
    ) {
        when {
            profileTargetUid.isNotBlank() -> profileTargetUid = ""
            activeCall != null -> {
                activeCall = null
                incomingCall = false
            }
            selectedChat != null -> selectedChat = null
            showNewGroup -> showNewGroup = false
            showNewChat -> showNewChat = false
            tab != "Chats" -> tab = "Chats"
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            background = Pale,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        AnimatedContent(
            targetState = currentUser != null,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authentication_transition"
        ) { loggedIn ->
            if (!loggedIn) {
                AuthScreen { currentUser = auth.currentUser }
            } else {
                val user = currentUser

                if (user == null) {
                    AuthScreen { currentUser = auth.currentUser }
                } else if (activeCall != null) {
                    KhahishCallScreen(
                        call = activeCall!!,
                        isIncoming = incomingCall,
                        onExit = {
                            activeCall = null
                            incomingCall = false
                        }
                    )
                } else if (profileTargetUid.isNotBlank()) {
                    KhahishUserProfileScreen(uid = profileTargetUid, onBack = { profileTargetUid = "" })
                } else if (selectedChat != null) {
                    ConversationScreen(
                        chat = selectedChat!!,
                        onBack = { selectedChat = null },
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
                            selectedChat = it
                            showNewGroup = false
                        }
                    )
                } else if (showNewChat) {
                    NewChatScreen(
                        currentUid = user.uid,
                        onBack = { showNewChat = false },
                        onCreateGroup = { showNewChat = false; showNewGroup = true },
                        onChatCreated = {
                            selectedChat = it
                            showNewChat = false
                        }
                    )
                } else {
                    Scaffold(
                        containerColor = Pale,
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
                                    Image(
                                        painter = painterResource(R.drawable.khahish_logo),
                                        contentDescription = "Khahish logo",
                                        modifier = Modifier.size(38.dp),
                                        contentScale = ContentScale.Fit
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                            NavigationBar(containerColor = Color.White) {
                                listOf(
                                    "Chats" to Icons.Default.Chat,
                                    "Updates" to Icons.Default.DonutLarge,
                                    "Communities" to Icons.Default.Groups,
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
                                        (slideInHorizontally { width -> if (movingForward) width / 3 else -width / 3 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { width -> if (movingForward) -width / 3 else width / 3 } + fadeOut())
                                    }
                                    animationsEnabled -> fadeIn() togetherWith fadeOut()
                                    else -> EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "main_tab_transition"
                        ) { selectedTab ->
                            when (selectedTab) {
                                "Chats" -> ChatList(
                                    modifier = Modifier.padding(padding),
                                    currentUid = user.uid,
                                    onOpen = { selectedChat = it },
                                    onViewProfile = { profileTargetUid = it }
                                )

                                "Updates" -> KhahishUpdatesScreen(
                                    currentUid = user.uid,
                                    currentName = user.displayName ?: "Khahish User",
                                    modifier = Modifier.padding(padding)
                                )

                                "Communities" -> KhahishCommunitiesScreen(
                                    currentUid = user.uid,
                                    modifier = Modifier.padding(padding)
                                )

                                "Calls" -> KhahishCallHistoryScreen(
                                    uid = user.uid,
                                    modifier = Modifier.padding(padding)
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
                                    onOneHandedChanged = { oneHandedMode = it }
                                )
                            }
                        }
                    }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Pale)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.khahish_logo),
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
            color = Ink
        )

        Spacer(Modifier.height(3.dp))

        Text(
            "Best Community Platform",
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
            color = Muted
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (isSignUp) "Create your account" else "Welcome back",
            color = Muted
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
                unfocusedBorderColor = Color(0xFFD8E5E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
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
                supportingText = { Text("3–20 characters; letters, numbers and _ only.", color = Muted) },
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
                unfocusedBorderColor = Color(0xFFD8E5E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
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
                unfocusedBorderColor = Color(0xFFD8E5E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
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
                                transaction.set(db.collection("users").document(newUid), profileData, SetOptions.merge())
                                transaction.set(db.collection("publicProfiles").document(newUid), profileData, SetOptions.merge())
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
            .background(Pale)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
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
            color = Muted,
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
                Text("Enter a complete username (3–20 letters, numbers or _)", color = Muted)
            }
        } else if (!loading && users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No users found", color = Muted)
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

                                db.collection("chats")
                                    .whereEqualTo(
                                        com.google.firebase.firestore.FieldPath.documentId(),
                                        chatId
                                    )
                                    .whereArrayContains("members", currentUid)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { matches ->
                                        val existing = matches.documents.firstOrNull()
                                        if (existing != null) {
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
                            Box(Modifier.size(48.dp).background(Color(0xFFDCEFE9), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(user.name.firstOrNull()?.uppercase() ?: "K", color = Green, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.width(13.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                user.name.ifBlank { "Khahish User" },
                                color = Ink,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusText = when {
                                user.isOnline && user.onlineStatusVisible -> "Online"
                                user.lastSeenVisible && user.lastSeenAt > 0L ->
                                    "Last seen " + java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                        .format(java.util.Date(user.lastSeenAt))
                                else -> "Tap to send a message request"
                            }
                            Text(statusText, color = if (statusText == "Online") Green else Muted, fontSize = 12.sp)
                            if (user.bio.isNotBlank()) {
                                Text(user.bio, color = Muted, fontSize = 11.sp, maxLines = 2)
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
                    HorizontalDivider(color = Color(0xFFEAF0ED))
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
    onViewProfile: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var chats by remember { mutableStateOf(listOf<Chat>()) }
    var incomingRequests by remember { mutableStateOf(listOf<MessageRequestItem>()) }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var profilePhotos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedProfileMenu by remember { mutableStateOf("") }
    var selectedPictureUrl by remember { mutableStateOf("") }
    var messageSearchResults by remember { mutableStateOf(emptyList<ChatMessageSearchResult>()) }
    var isSearchingMessages by remember { mutableStateOf(false) }

    DisposableEffect(currentUid) {
        val registration = db.collection("chats")
            .whereArrayContains("members", currentUid)
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
                    }.sortedByDescending { chat ->
                        docs.firstOrNull { it.id == chat.id }
                            ?.getLong("lastMessageAt") ?: 0L
                    }

                    loading = false
                }
            }

        onDispose { registration.remove() }
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
        if (queryText.length < 2) {
            messageSearchResults = emptyList()
            isSearchingMessages = false
        } else {
            kotlinx.coroutines.delay(350)
            val currentChats = chats
            if (currentChats.isEmpty()) {
                messageSearchResults = emptyList()
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
                            messageSearchResults = currentChats.flatMapIndexed { index, chat ->
                                snapshots.getOrNull(index)?.documents.orEmpty()
                                    .filter { it.getString("text").orEmpty().contains(queryText, ignoreCase = true) }
                                    .map { doc ->
                                        ChatMessageSearchResult(
                                            chat = chat.copy(initialMessageId = doc.id),
                                            messageId = doc.id,
                                            text = doc.getString("text").orEmpty(),
                                            timestamp = doc.getLong("timestamp") ?: 0L
                                        )
                                    }
                            }.sortedByDescending { it.timestamp }
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

    val filtered = chats.filter {
        it.name.contains(search, true) || it.message.contains(search, true)
    }

    if (selectedPictureUrl.isNotBlank()) Dialog(onDismissRequest = { selectedPictureUrl = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.82f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = selectedPictureUrl, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { selectedPictureUrl = "" }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
    Column(modifier.fillMaxSize()) {
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
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tap the edit button to find a user",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                if (incomingRequests.isNotEmpty()) {
                    item { Text("MESSAGE REQUESTS", modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, bottom = 4.dp), color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    items(incomingRequests, key = { "request-${it.id}" }) { request ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(request.senderName, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text("wants to start a conversation", color = Muted, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = {
                                        db.collection("messageRequests").document(request.id).update(mapOf("status" to "rejected", "handledBy" to currentUid, "rejectedAt" to System.currentTimeMillis()))
                                            .addOnSuccessListener { showToast(context, "Message request rejected.") }
                                            .addOnFailureListener { showToast(context, "Could not reject request: ${it.message}") }
                                    }) { Text("Reject", color = Muted) }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = {
                                        val reqRef = db.collection("messageRequests").document(request.id)
                                        val chatRef = db.collection("chats").document(request.id)
                                        val now = System.currentTimeMillis()
                                        val members = listOf(currentUid, request.senderUid).sorted()
                                        val batch = db.batch()
                                        batch.update(reqRef, mapOf("status" to "accepted", "acceptedBy" to currentUid, "handledBy" to currentUid, "acceptedAt" to now))
                                        batch.set(chatRef, mapOf("members" to members, "memberNames" to mapOf(currentUid to (FirebaseAuth.getInstance().currentUser?.displayName ?: "You"), request.senderUid to request.senderName), "lastMessage" to "", "lastMessageAt" to 0L, "updatedAt" to now, "createdAt" to now, "createdBy" to currentUid, "disappearingDurationMs" to 0L))
                                        batch.commit().addOnSuccessListener {
                                            showToast(context, "Request accepted. Chat is ready.")
                                            onOpen(Chat(request.id, request.senderUid, request.senderName, "", "", request.senderName.firstOrNull()?.uppercase() ?: "K"))
                                        }.addOnFailureListener { showToast(context, "Could not accept request: ${it.message}") }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Accept", color = Color.White) }
                                }
                            }
                        }
                    }
                }
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
                                Modifier.size(52.dp).background(Color(0xFFDCEFE9), CircleShape),
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
                                    Box(Modifier.size(52.dp).background(Color(0xFFDCEFE9), CircleShape)
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
                                color = Ink
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                chat.message.ifBlank { "Start your conversation" },
                                color = Muted,
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
                            tint = Muted
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFFEAF0ED),
                        modifier = Modifier.padding(start = 82.dp)
                    )
                }
                if (search.trim().length >= 2) {
                    item {
                        Text(
                            if (isSearchingMessages) "Searching recent messages…" else "Matching recent messages",
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
                            Icon(Icons.Default.Search, null, tint = Muted)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(result.chat.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(result.text, color = Muted, fontSize = 12.sp, maxLines = 2)
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
fun ConversationScreen(
    chat: Chat,
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
    var isSending by remember { mutableStateOf(false) }
    var peerTyping by remember { mutableStateOf(false) }
    var peerOnline by remember { mutableStateOf(false) }
    var peerLastSeenAt by remember { mutableStateOf(0L) }
    var peerLastSeenVisible by remember { mutableStateOf(true) }
    var peerOnlineStatusVisible by remember { mutableStateOf(true) }
    var peerPhotoUrl by remember { mutableStateOf("") }
    var peerBio by remember { mutableStateOf("") }
    var attachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
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
    var showPeerPicture by remember { mutableStateOf(false) }
    var videoToPlay by remember { mutableStateOf("") }
    var showDeleteOptions by remember { mutableStateOf(false) }
    var showDisappearingOptions by remember { mutableStateOf(false) }
    var disappearingDurationMs by remember { mutableStateOf(0L) }
    var replyTarget by remember { mutableStateOf<MessageItem?>(null) }
    var reactionTarget by remember { mutableStateOf<MessageItem?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardUsers by remember { mutableStateOf(emptyList<AppUser>()) }
    var forwardSearch by remember { mutableStateOf("") }
    var loadingForwardUsers by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf(false) }
    var chatSearchVisible by remember(chat.id) { mutableStateOf(false) }
    var chatSearchQuery by remember(chat.id) { mutableStateOf("") }

    val displayedMessages = remember(messages, chatSearchQuery) {
        val query = chatSearchQuery.trim()
        if (query.isBlank()) messages else messages.filter { message ->
            message.text.contains(query, ignoreCase = true) ||
                message.replyToText.contains(query, ignoreCase = true)
        }
    }


    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(
                context, uri, "image",
                onSuccess = { url ->
                    isUploading = false
                    isSending = true
                    sendChatMessage(
                        chatRef, currentUserId, "", url, "image", context
                    ) { isSending = false }
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
            isUploading = true
            uploadToCloudinary(
                context, uri, "video",
                onSuccess = { url ->
                    isUploading = false
                    isSending = true
                    sendChatMessage(
                        chatRef, currentUserId, "", url, "video", context
                    ) { isSending = false }
                },
                onError = {
                    isUploading = false
                    showToast(context, it)
                }
            )
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(
                context, uri, "video",
                onSuccess = { url ->
                    isUploading = false
                    isSending = true
                    sendChatMessage(
                        chatRef, currentUserId, "", url, "audio", context
                    ) { isSending = false }
                },
                onError = {
                    isUploading = false
                    showToast(context, it)
                }
            )
        }
    }

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
    ) { uri ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(
                context, uri, "raw",
                onSuccess = { url ->
                    isUploading = false
                    isSending = true
                    sendChatMessage(
                        chatRef, currentUserId, "", url, "document", context
                    ) { isSending = false }
                },
                onError = {
                    isUploading = false
                    showToast(context, it)
                }
            )
        }
    }


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
            isRecordingVoice = true
            showToast(context, "Recording… tap the mic again to send")
        } catch (error: Exception) {
            voiceRecorder?.release()
            voiceRecorder = null
            isRecordingVoice = false
            showToast(context, "Could not start recording: ${error.message}")
        }
    }

    fun stopVoiceRecordingAndSend() {
        val recorder = voiceRecorder
        val file = voiceRecordingFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
            file?.delete()
            showToast(context, "Voice recording was too short. Please try again.")
        } finally {
            recorder?.release()
            voiceRecorder = null
            isRecordingVoice = false
            voiceRecordingFile = null
        }
        if (file == null || !file.exists() || file.length() < 1000L) {
            file?.delete()
            return
        }
        isUploading = true
        uploadToCloudinary(
            context, Uri.fromFile(file), "video",
            onSuccess = { url ->
                isUploading = false
                isSending = true
                sendChatMessage(chatRef, currentUserId, "", url, "audio", context) {
                    isSending = false
                    file.delete()
                }
            },
            onError = {
                isUploading = false
                file.delete()
                showToast(context, it)
            }
        )
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecording()
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
            .addSnapshotListener { snapshot, error ->
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
                        starredBy = doc.get("starredBy") as? List<String> ?: emptyList()
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
            .background(Pale)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedMessageIds.isNotEmpty()) selectedMessageIds = emptySet() else onBack()
            }) { Icon(Icons.Default.ArrowBack, "Back") }
            if (selectedMessageIds.isNotEmpty()) {
                Text("${selectedMessageIds.size} selected", modifier = Modifier.weight(1f),
                    color = Ink, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    selectedMessageIds.forEach { id ->
                        val item = messages.firstOrNull { it.id == id } ?: return@forEach
                        val ref = chatRef.collection("messages").document(id)
                        if (currentUserId in item.starredBy) ref.update("starredBy", FieldValue.arrayRemove(currentUserId))
                        else ref.update("starredBy", FieldValue.arrayUnion(currentUserId))
                    }
                    selectedMessageIds = emptySet()
                }) { Icon(Icons.Default.Star, null); Text("Star") }
                TextButton(
                    enabled = selectedMessageIds.size == 1,
                    onClick = {
                        val item = messages.firstOrNull { it.id == selectedMessageIds.firstOrNull() }
                        if (item != null) {
                            if (item.senderId != currentUserId || item.mediaType != "text" ||
                                System.currentTimeMillis() - item.timestamp > 15 * 60 * 1000L) {
                                showToast(context, "Only your text messages from the last 15 minutes can be edited.")
                            } else {
                                editTarget = item
                                editText = item.text
                            }
                        }
                    }
                ) { Icon(Icons.Default.Edit, null); Text("Edit") }
                TextButton(
                    enabled = selectedMessageIds.size == 1,
                    onClick = {
                        val item = messages.firstOrNull { it.id == selectedMessageIds.firstOrNull() }
                        if (item != null) {
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
                ) { Icon(Icons.Default.PushPin, null); Text("Pin") }
                TextButton(onClick = { showForwardDialog = true }) { Icon(Icons.Default.Forward, null); Text("Forward") }
                TextButton(onClick = { showDeleteOptions = true }) {
                    Icon(Icons.Default.DeleteOutline, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            } else {
                if (chat.isGroup) {
                    Box(
                        Modifier.size(40.dp).background(Color(0xFFDCEFE9), CircleShape).clickable { showGroupInfo = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Groups, "Group info", tint = Green) }
                } else {
                    Box {
                        if (peerPhotoUrl.isNotBlank()) {
                            AsyncImage(model = peerPhotoUrl, contentDescription = "Profile picture",
                                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { peerMenuExpanded = true }, contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(40.dp).background(Color(0xFFDCEFE9), CircleShape).clickable { peerMenuExpanded = true }, contentAlignment = Alignment.Center) {
                                Text(chat.initials, color = Green, fontWeight = FontWeight.Bold)
                            }
                        }
                        DropdownMenu(expanded = peerMenuExpanded, onDismissRequest = { peerMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("View profile picture") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) }, enabled = peerPhotoUrl.isNotBlank(), onClick = { peerMenuExpanded = false; showPeerPicture = true })
                            DropdownMenuItem(text = { Text("View profile") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { peerMenuExpanded = false; onViewProfile(chat.peerUid) })
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (chat.isGroup) displayGroupName else chat.name, fontWeight = FontWeight.SemiBold, color = Ink)
                    val peerStatusText = when {
                        chat.isGroup -> "Group · ${chat.memberUids.size} members"
                        peerTyping -> "typing..."
                        peerOnline && peerOnlineStatusVisible -> "Online"
                        peerLastSeenVisible && peerLastSeenAt > 0L ->
                            "Last seen " + java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                .format(java.util.Date(peerLastSeenAt))
                        else -> "Khahish private chat"
                    }
                    Text(peerStatusText, color = if (peerTyping || peerStatusText == "Online") Green else Muted, fontSize = 11.sp)
                    if (peerBio.isNotBlank()) Text(peerBio, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                IconButton(onClick = { showDisappearingOptions = true }) {
                    Icon(Icons.Default.Timer, "Disappearing messages", tint = if (disappearingDurationMs > 0L) Green else Muted)
                }
                IconButton(onClick = {
                    chatSearchVisible = !chatSearchVisible
                    chatSearchQuery = ""
                }) {
                    Icon(Icons.Default.Search, "Search chat history", tint = Green)
                }
                IconButton(onClick = { showStarredDialog = true }) {
                    Icon(Icons.Default.Star, "Starred messages", tint = Green)
                }
                if (!chat.isGroup) {
                    IconButton(onClick = { onStartCall(false) }) { Icon(Icons.Default.Call, "Voice call", tint = Green) }
                    IconButton(onClick = { onStartCall(true) }) { Icon(Icons.Default.Videocam, "Video call", tint = Green) }
                }
            }
        }

        if (chatSearchVisible) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
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
                Text(displayedMessages.size.toString(), color = Muted, fontSize = 12.sp)
                IconButton(onClick = { chatSearchVisible = false; chatSearchQuery = "" }) {
                    Icon(Icons.Default.Close, "Close search")
                }
            }
        }

        if (pinnedMessageId.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFE8F5F0))
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
                    Text(pinnedMessageText, color = Ink, fontSize = 12.sp, maxLines = 1)
                }
                if (pinnedBy == currentUserId) {
                    IconButton(onClick = {
                        chatRef.update(mapOf(
                            "pinnedMessageId" to FieldValue.delete(),
                            "pinnedMessageText" to FieldValue.delete(),
                            "pinnedBy" to FieldValue.delete(),
                            "pinnedAt" to FieldValue.delete()
                        )).addOnFailureListener { showToast(context, "Could not unpin: ${it.message}") }
                    }) { Icon(Icons.Default.Close, contentDescription = "Unpin message", tint = Muted) }
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
            LazyColumn(
                state = messageListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (chatSearchVisible && displayedMessages.isEmpty()) {
                    item { Text("No matching messages", color = Muted, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
                }
                items(displayedMessages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId

                val isSelected = msg.id in selectedMessageIds
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 285.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selectedMessageIds.isNotEmpty()) {
                                        selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                    }
                                },
                                onLongClick = {
                                    selectedMessageIds = if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                                }
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (msg.text.isNotBlank()) {
                                    "Message from ${if (isMe) "you" else (groupMemberNames[msg.senderId] ?: chat.name)}: ${msg.text}"
                                } else {
                                    "Shared ${msg.mediaType} message"
                                }
                            }
                            .border(2.dp, if (isSelected) Green else Color.Transparent, RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Color.White else if (isMe) Color(0xFFD9F7EC) else Color.White,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                )
                            )
                            .padding(10.dp)
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
                            "image" -> AsyncImage(
                                model = msg.imageUrl,
                                contentDescription = "Shared photo",
                                modifier = Modifier
                                    .sizeIn(maxWidth = 250.dp, maxHeight = 250.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )

                            "video" -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF4F0))
                                    .clickable { videoToPlay = msg.imageUrl }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = Green, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play video", color = Ink, fontWeight = FontWeight.SemiBold)
                            }

                            "audio" -> AudioMessagePlayer(msg.imageUrl)

                            "document" -> MediaOpenRow(
                                "Document — tap to open",
                                Icons.Default.Description,
                                msg.imageUrl,
                                context
                            )

                            "live_location" -> {
                                val point = liveLocations[msg.senderId]
                                val active = point?.isActive == true && point.expiresAt > System.currentTimeMillis()
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEAF4F0))
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
                                        Text("Live location", color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(if (active) "Tap to view latest location" else "Sharing ended", color = Green, fontSize = 11.sp)
                                        Text(msg.text, color = Muted, fontSize = 10.sp)
                                    }
                                }
                            }

                            "location" -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEAF4F0))
                                    .clickable { openSharedUrl(context, msg.imageUrl) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Shared location", color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text("Tap to open in Maps", color = Green, fontSize = 11.sp)
                                    Text(msg.text, color = Muted, fontSize = 10.sp)
                                }
                            }
                            "contact" -> Column(Modifier.fillMaxWidth().background(Color(0xFFEAF4F0), RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContactPage, null, tint = Green)
                                    Spacer(Modifier.width(7.dp))
                                    Text("Shared contact", color = Ink, fontWeight = FontWeight.SemiBold)
                                }
                                Text(msg.text, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }

                        if (msg.replyToMessageId.isNotBlank()) {
                            Column(Modifier.fillMaxWidth().background(Color(0xFFEAF4F0), RoundedCornerShape(8.dp)).padding(7.dp)) {
                                Text("Reply", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(msg.replyToText, color = Ink, fontSize = 12.sp, maxLines = 2)
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        if (msg.text.isNotBlank() && msg.mediaType != "location" && msg.mediaType != "contact") {
                            Text(msg.text, color = if (isSelected) Color.Black else Ink, fontSize = 15.sp)
                        }
                        if (msg.reactions.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                msg.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                                    TextButton(onClick = { reactionTarget = msg }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("$emoji $count", fontSize = 12.sp) }
                                }
                            }
                        }

                        Row(
                            Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                java.text.SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(java.util.Date(msg.timestamp)),
                                fontSize = 10.sp,
                                color = Muted
                            )
                            if (msg.editedAt > 0L) {
                                Text(" · edited", fontSize = 9.sp, color = Muted)
                            }

                            TextButton(onClick = { replyTarget = msg }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Icon(Icons.Default.Reply, "Reply", tint = Green, modifier = Modifier.size(16.dp)) }
                            TextButton(onClick = { reactionTarget = msg }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("😊", fontSize = 14.sp) }
                            if (isMe) {
                                val read = msg.readBy.any { it != currentUserId }
                                val delivered =
                                    msg.deliveredTo.any { it != currentUserId }

                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when {
                                        read -> "✓✓"
                                        delivered -> "✓✓"
                                        else -> "✓"
                                    },
                                    color = if (read) Color(0xFF1686D9) else Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                    containerColor = Color.White,
                    contentColor = Green,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest message")
                }
            }
        }

        if (replyTarget != null) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFE8F5F0)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Replying to message", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(replyTarget!!.text.ifBlank { replyTarget!!.mediaType }, color = Ink, maxLines = 1, fontSize = 12.sp)
                }
                IconButton(onClick = { replyTarget = null }) { Icon(Icons.Default.Close, "Cancel reply") }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 5.dp, vertical = 7.dp),
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

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    updateTyping(it.isNotBlank())
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            IconButton(
                enabled = !isUploading && !isSending,
                onClick = {
                    if (isRecordingVoice) {
                        stopVoiceRecordingAndSend()
                    } else if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startVoiceRecording()
                    } else {
                        voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Icon(
                    if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                    if (isRecordingVoice) "Stop and send voice message" else "Record voice message",
                    tint = if (isRecordingVoice) Color(0xFFE54848) else Green
                )
            }

            IconButton(
                enabled = !isSending && !isUploading,
                onClick = {
                    val message = text.trim()
                    if (message.isNotBlank()) {
                        text = ""
                        updateTyping(false)
                        isSending = true

                        sendChatMessage(
                            chatRef,
                            currentUserId,
                            message,
                            "",
                            "text",
                            context,
                            replyToMessageId = replyTarget?.id.orEmpty(),
                            replyToText = replyTarget?.text?.ifBlank { replyTarget?.mediaType ?: "Media" }.orEmpty()
                        ) {
                            isSending = false
                            replyTarget = null
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Send", tint = Green)
            }
        }
    }

    if (showForwardDialog) {
        LaunchedEffect(showForwardDialog, forwardSearch) {
            val term = normalizeKhahishUsername(forwardSearch)
            if (!showForwardDialog || term.length < 3 || !isValidKhahishUsername(term)) { forwardUsers = emptyList(); loadingForwardUsers = false }
            else {
                loadingForwardUsers = true
                db.collection("usernames").document(term).get().addOnSuccessListener { claim ->
                    val targetUid = claim.getString("uid").orEmpty()
                    if (targetUid.isBlank() || targetUid == currentUserId) { forwardUsers = emptyList(); loadingForwardUsers = false }
                    else db.collection("publicProfiles").document(targetUid).get().addOnSuccessListener { profile ->
                        forwardUsers = listOfNotNull(profile.toObject(AppUser::class.java)?.takeIf { it.uid == targetUid }); loadingForwardUsers = false
                    }.addOnFailureListener { loadingForwardUsers = false; showToast(context, "Profile lookup failed: ${it.message}") }
                }.addOnFailureListener { loadingForwardUsers = false; showToast(context, "Username lookup failed: ${it.message}") }
            }
        }
        AlertDialog(onDismissRequest = { if (!forwarding) showForwardDialog = false }, title = { Text("Forward selected messages") }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = forwardSearch, onValueChange = { forwardSearch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recipient username") }, placeholder = { Text("Enter exact username") }, singleLine = true)
                if (loadingForwardUsers) CircularProgressIndicator(color = Green)
                else forwardUsers.forEach { target ->
                    TextButton(enabled = !forwarding, onClick = {
                        forwarding = true
                        val targetRef = db.collection("chats").document(privateChatId(currentUserId, target.uid))
                        targetRef.get().addOnSuccessListener { existing ->
                            if (!existing.exists()) { forwarding = false; showToast(context, "Send a message request and wait for acceptance before forwarding."); return@addOnSuccessListener }
                            val batch = db.batch()
                            messages.filter { it.id in selectedMessageIds }.forEach { original ->
                                val ref = targetRef.collection("messages").document()
                                batch.set(ref, mapOf("senderId" to currentUserId, "text" to original.text, "imageUrl" to original.imageUrl, "mediaType" to original.mediaType, "timestamp" to System.currentTimeMillis(), "deliveredTo" to emptyList<String>(), "readBy" to listOf(currentUserId), "forwarded" to true))
                            }
                            batch.commit().addOnSuccessListener {
                                targetRef.update(mapOf("lastMessage" to "Forwarded message", "lastMessageAt" to System.currentTimeMillis(), "updatedAt" to System.currentTimeMillis()))
                                forwarding = false; showForwardDialog = false; selectedMessageIds = emptySet()
                                showToast(context, "Messages forwarded to ${target.name}")
                            }.addOnFailureListener { forwarding = false; showToast(context, "Forward failed: ${it.message}") }
                        }.addOnFailureListener { forwarding = false; showToast(context, "Could not open chat: ${it.message}") }
                    }) { Text(target.name, color = Ink) }
                }
                if (!loadingForwardUsers && forwardUsers.isEmpty()) Text("Enter an existing user's exact username.", color = Muted)
            }
        }, confirmButton = { TextButton(onClick = { showForwardDialog = false }, enabled = !forwarding) { Text("Close") } })
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
                        Text("Enter at least 2 letters to search.", color = Muted, modifier = Modifier.padding(top = 10.dp))
                    } else if (groupSearchResults.isEmpty()) {
                        Text("No new users found.", color = Muted, modifier = Modifier.padding(top = 10.dp))
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            groupSearchResults.forEach { candidate ->
                                TextButton(onClick = {
                                    chatRef.update(
                                        "members", FieldValue.arrayUnion(candidate.uid),
                                        "memberNames.${candidate.uid}", candidate.name.ifBlank { "Khahish User" }
                                    ).addOnSuccessListener {
                                        groupMemberSearch = ""
                                        showToast(context, "${candidate.name} added to group")
                                    }.addOnFailureListener { showToast(context, "Could not add participant: ${it.message}") }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(candidate.name, color = Ink)
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
                if (starred.isEmpty()) Text("You have not starred any messages in this chat.", color = Muted)
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    starred.forEach { item ->
                        TextButton(onClick = {
                            val index = messages.indexOfFirst { it.id == item.id }
                            if (index >= 0) messageScrollScope.launch { messageListState.animateScrollToItem(index) }
                            showStarredDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.text.ifBlank { item.mediaType.replaceFirstChar { ch -> ch.uppercase() } }, color = Ink, maxLines = 2)
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
                    Text("${groupMemberUids.size} participants", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                Text(memberName, color = Ink, fontSize = 13.sp)
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
                            "groupAdmins", FieldValue.arrayRemove(currentUserId),
                            "memberNames.$currentUserId", FieldValue.delete()
                        ).addOnSuccessListener { showGroupInfo = false; onBack() }
                            .addOnFailureListener { showToast(context, "Could not leave group: ${it.message}") }
                    }) { Text("Leave group", color = MaterialTheme.colorScheme.error) }
                    if (!canLeave) Text("Promote another admin before leaving this group.", color = Muted, fontSize = 11.sp)
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
        AlertDialog(onDismissRequest = { reactionTarget = null }, title = { Text("React to message") }, text = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("❤️", "😂", "😮", "😢", "👍", "🙏").forEach { emoji ->
                    TextButton(onClick = {
                        chatRef.collection("messages").document(reactionTarget!!.id).update("reactions.$currentUserId", emoji)
                            .addOnSuccessListener { reactionTarget = null }
                            .addOnFailureListener { showToast(context, "Reaction failed: ${it.message}"); reactionTarget = null }
                    }) { Text(emoji, fontSize = 21.sp) }
                }
            }
        }, confirmButton = { TextButton(onClick = { reactionTarget = null }) { Text("Close") } })
    }
    if (showPeerPicture && peerPhotoUrl.isNotBlank()) Dialog(onDismissRequest = { showPeerPicture = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.82f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = peerPhotoUrl, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { showPeerPicture = false }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
    if (videoToPlay.isNotBlank()) Dialog(onDismissRequest = { videoToPlay = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { videoToPlay = "" }) { Icon(Icons.Default.Close, "Close video", tint = Color.White) } }
            KhahishVideoPlayer(url = videoToPlay, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
    }

    if (showLiveLocationOptions) {
        AlertDialog(
            onDismissRequest = { showLiveLocationOptions = false },
            title = { Text("Share live location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your location will update while sharing is active. A persistent Android notification lets you stop sharing at any time.", color = Muted, fontSize = 13.sp)
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

    if (showDisappearingOptions) {
        AlertDialog(
            onDismissRequest = { showDisappearingOptions = false },
            title = { Text("Disappearing messages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose how long new messages stay in this chat. This applies to both participants.", color = Muted, fontSize = 13.sp)
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
                            Text(label, color = Ink)
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
    fun timeText(ms: Int): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    Row(
        Modifier.widthIn(max = 248.dp)
            .background(Color(0xFF102B2A), RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF00E6A0), Color(0xFF00A98B))), CircleShape)
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
                                .background(if (index.toFloat() / (bars - 1) <= progress) Color(0xFF00E6A0) else Color(0xFF66827D))
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
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF00E6A0), inactiveTrackColor = Color(0xFF66827D))
                )
                Spacer(Modifier.width(4.dp))
                Text(timeText(durationMs), color = Color.White, fontSize = 10.sp)
            }
            Text(timeText((durationMs * progress).toInt()), color = Color(0xFFB8D9D0), fontSize = 9.sp)
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
        Text(label, color = Ink)
    }
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
    chatRef.get().addOnSuccessListener { chatSnapshot ->
        fun sendNow() {
            val duration = chatSnapshot.getLong("disappearingDurationMs") ?: 0L
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
            chatRef.collection("messages").add(data)
                .addOnSuccessListener {
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
                        onComplete()
                    }
                }
                .addOnFailureListener { error ->
                    onComplete()
                    showToast(context, "Message failed: ${error.message}")
                }
        }

        val members = chatSnapshot.get("members") as? List<*> ?: emptyList<Any>()
        val otherUid = members.filterIsInstance<String>().firstOrNull { it != senderId }.orEmpty()
        if (chatSnapshot.getBoolean("isGroup") != true && members.size == 2 && otherUid.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(senderId).collection("blocked").document(otherUid).get()
                .addOnSuccessListener { myBlock ->
                    if (myBlock.exists()) {
                        onComplete()
                        showToast(context, "Message not sent. You have blocked this account.")
                    } else sendNow()
                }
                .addOnFailureListener {
                    onComplete()
                    showToast(context, "Could not verify your blocked list. Please try again.")
                }
        } else {
            sendNow()
        }
    }.addOnFailureListener { error ->
        onComplete()
        showToast(context, "Could not read chat settings: ${error.message}")
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
            .background(Pale)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
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
                        color = Muted,
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
                                color = Ink
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
            .background(Pale)
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
                color = Ink
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("STORAGE USAGE") {
            Text(
                "Khahish app storage",
                color = Ink,
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
                    color = Muted,
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
                color = Muted,
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
            color = Muted,
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
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
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
            .background(Color.White, RoundedCornerShape(16.dp))
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
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Muted
        )
    }
}

internal fun uploadToCloudinary(
    context: Context,
    uri: Uri,
    resourceType: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot read selected file")

            val bytes = inputStream.use { it.readBytes() }

            val mimeType = context.contentResolver.getType(uri)
                ?: "application/octet-stream"

            val fileName = "khahish_${System.currentTimeMillis()}"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    bytes.toRequestBody(mimeType.toMediaType())
                )
                .addFormDataPart(
                    "upload_preset",
                    CLOUDINARY_UPLOAD_PRESET
                )
                .build()

            val request = Request.Builder()
                .url(
                    "https://api.cloudinary.com/v1_1/" +
                        CLOUDINARY_CLOUD_NAME +
                        "/" + resourceType + "/upload"
                )
                .post(requestBody)
                .build()

            val client = OkHttpClient.Builder()
                .callTimeout(120, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw Exception("Upload error: ${response.code} $responseText")
                }

                val json = JSONObject(responseText)
                val secureUrl = json.optString("secure_url")

                if (secureUrl.isBlank()) {
                    throw Exception("Upload URL was not returned")
                }

                Handler(Looper.getMainLooper()).post {
                    onSuccess(secureUrl)
                }
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                onError(e.message ?: "Unknown upload error")
            }
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
            .background(Pale)
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
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun KhahishUserProfileScreen(uid: String, onBack: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
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
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val context = LocalContext.current
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
    Column(Modifier.fillMaxSize().background(Pale)) {
        Row(Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Profile", color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Green) }
        else Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (photo.isNotBlank()) AsyncImage(model = photo, contentDescription = "Profile picture", modifier = Modifier.size(116.dp).clip(CircleShape).clickable { showPicture = true }, contentScale = ContentScale.Crop)
            else Box(Modifier.size(116.dp).background(Color(0xFFDCEFE9), CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(Locale.getDefault()), color = Green, fontSize = 42.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text(name, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (username.isNotBlank()) Text("@$username", color = Green)
            Text(if (online) "Online" else "Khahish member", color = Muted, fontSize = 12.sp)
            if (bio.isNotBlank()) { Spacer(Modifier.height(18.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Text(bio, Modifier.fillMaxWidth().padding(16.dp), color = Ink) } }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showReportDialog = true }) {
                    Icon(Icons.Default.Report, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Report")
                }
                OutlinedButton(onClick = { showBlockConfirm = true }) {
                    Icon(Icons.Default.Block, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Block")
                }
            }
        }
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
                    Text("Why are you reporting this account?", color = Muted)
                    listOf("Spam or unwanted content", "Harassment or bullying", "Fake or impersonation account", "Inappropriate content", "Other").forEach { reason ->
                        Row(Modifier.fillMaxWidth().clickable { reportReason = reason }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason, color = Ink, fontSize = 13.sp)
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
    if (showPicture && photo.isNotBlank()) Dialog(onDismissRequest = { showPicture = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.85f).background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(model = photo, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { showPicture = false }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}
