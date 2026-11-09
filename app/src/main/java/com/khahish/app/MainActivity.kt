package com.khahish.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val initials: String
)

data class AppUser(
    val uid: String = "",
    val name: String = "",
    val nameLowercase: String = "",
    val photoUrl: String = "",
    val username: String = "",
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
    val hiddenFor: List<String> = emptyList()
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
        presenceUid = newUid
        if (appInForeground) updateKhahishPresence(newUid, true)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
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

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var tab by remember { mutableStateOf("Chats") }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var showNewChat by remember { mutableStateOf(false) }
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
            (selectedChat != null || showNewChat || tab != "Chats")
    ) {
        when {
            activeCall != null -> {
                activeCall = null
                incomingCall = false
            }
            selectedChat != null -> selectedChat = null
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
                } else if (selectedChat != null) {
                    ConversationScreen(
                        chat = selectedChat!!,
                        onBack = { selectedChat = null },
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
                } else if (showNewChat) {
                    NewChatScreen(
                        currentUid = user.uid,
                        onBack = { showNewChat = false },
                        onChatCreated = {
                            selectedChat = it
                            showNewChat = false
                        }
                    )
                } else {
                    Scaffold(
                        containerColor = Pale,
                        topBar = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .background(
                                                Green,
                                                RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Forum,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Khahish (خواہش)",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Ink
                                        )
                                        Text(
                                            "Connect. Share. Stay Close.",
                                            fontSize = 11.sp,
                                            color = Muted
                                        )
                                    }

                                    IconButton(onClick = { tab = "Settings" }) {
                                        Icon(Icons.Default.Settings, "Settings")
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    if (tab == "Chats") "Messages" else tab,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                            }
                        },
                        bottomBar = {
                            NavigationBar(containerColor = Color.White) {
                                listOf(
                                    "Chats" to Icons.Default.Chat,
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
                            transitionSpec = {
                                when {
                                    animationsEnabled && slideTransitionsEnabled ->
                                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it / 4 } + fadeOut())
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
                                    onOpen = { selectedChat = it }
                                )

                                "Updates" -> KhahishUpdatesScreen(
                                    currentUid = user.uid,
                                    currentName = user.displayName ?: "Khahish User",
                                    modifier = Modifier.padding(padding)
                                )

                                "Calls" -> KhahishCallHistoryScreen(
                                    uid = user.uid,
                                    modifier = Modifier.padding(padding)
                                )

                                "Settings" -> KhahishSettingsScreen(
                                    modifier = Modifier.padding(padding),
                                    onLogout = {
                                        auth.signOut()
                                        currentUser = null
                                        selectedChat = null
                                        showNewChat = false
                                        tab = "Chats"
                                    },
                                    onAnimationsChanged = { animationsEnabled = it },
                                    onSlideTransitionsChanged = { slideTransitionsEnabled = it }
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
        Box(
            Modifier
                .size(68.dp)
                .background(Green, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Forum,
                null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Khahish (خواہش)",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

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
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (6+ characters)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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

                isLoading = true

                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            saveUserToFirestore(
                                db,
                                result.user?.uid,
                                name,
                                email
                            )
                            isLoading = false
                            onLoginSuccess()
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
    onChatCreated: (Chat) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<AppUser>()) }
    var loading by remember { mutableStateOf(false) }
    var creatingUid by remember { mutableStateOf("") }

    LaunchedEffect(search) {
        val queryText = search.trim().lowercase(Locale.ROOT)

        if (queryText.length < 2) {
            users = emptyList()
            loading = false
        } else {
            loading = true
            db.collection("publicProfiles")
                .orderBy("nameLowercase")
                .startAt(queryText)
                .endAt(queryText + "\uf8ff")
                .limit(30)
                .get()
                .addOnSuccessListener { snapshot ->
                    users = snapshot.documents.mapNotNull { doc ->
                        val user = doc.toObject(AppUser::class.java)
                        if (user != null && user.uid != currentUid) user else null
                    }
                    loading = false
                }
                .addOnFailureListener { error ->
                    loading = false
                    showToast(context, "Search failed: ${error.message}")
                }
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

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search users by name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Text(
            "Search registered Khahish users",
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

        if (search.trim().length < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type at least 2 letters to search", color = Muted)
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

                                val chatData = hashMapOf<String, Any>(
                                    "members" to members,
                                    "memberNames" to mapOf(
                                        currentUid to "You",
                                        user.uid to user.name
                                    ),
                                    "lastMessage" to "",
                                    "lastMessageAt" to 0L,
                                    "updatedAt" to now
                                )

                                chatRef.get()
                                    .addOnSuccessListener { existing ->
                                        if (existing.exists()) {
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
                                            chatRef.set(chatData)
                                                .addOnSuccessListener {
                                                    creatingUid = ""
                                                    onChatCreated(
                                                        Chat(
                                                            id = chatId,
                                                            peerUid = user.uid,
                                                            name = user.name.ifBlank { "Khahish User" },
                                                            message = "",
                                                            time = "",
                                                            initials = user.name.firstOrNull()
                                                                ?.uppercase() ?: "K"
                                                        )
                                                    )
                                                }
                                                .addOnFailureListener { error ->
                                                    creatingUid = ""
                                                    showToast(
                                                        context,
                                                        "Chat create failed: ${error.message}"
                                                    )
                                                }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        creatingUid = ""
                                        showToast(
                                            context,
                                            "Chat open failed: ${error.message}"
                                        )
                                    }
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color(0xFFDCEFE9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.name.firstOrNull()?.uppercase() ?: "K",
                                color = Green,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(13.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                user.name.ifBlank { "Khahish User" },
                                color = Ink,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap to start private chat",
                                color = Muted,
                                fontSize = 12.sp
                            )
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
    onOpen: (Chat) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var chats by remember { mutableStateOf(listOf<Chat>()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }

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
                        val peerUid = members
                            ?.filterIsInstance<String>()
                            ?.firstOrNull { it != currentUid }
                            ?: return@mapNotNull null

                        val names = doc.get("memberNames") as? Map<*, *>
                        val peerName = names?.get(peerUid) as? String
                            ?: "Khahish User"

                        Chat(
                            id = doc.id,
                            peerUid = peerUid,
                            name = peerName,
                            message = doc.getString("lastMessage") ?: "",
                            time = "",
                            initials = peerName.firstOrNull()?.uppercase() ?: "K"
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

    val filtered = chats.filter {
        it.name.contains(search, true) || it.message.contains(search, true)
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

            filtered.isEmpty() -> Box(
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
                items(filtered, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(chat) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(Color(0xFFDCEFE9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                chat.initials,
                                color = Green,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
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
            }
        }
    }
}
@Composable
fun ConversationScreen(
    chat: Chat,
    onBack: () -> Unit,
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
    var isUploading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var peerTyping by remember { mutableStateOf(false) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteOptions by remember { mutableStateOf(false) }

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
                            ?: emptyList()
                    )
                }.filter { currentUserId !in it.hiddenFor }

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

        val typingRegistration = chatRef.collection("typing")
            .document(chat.peerUid)
            .addSnapshotListener { snapshot, _ ->
                peerTyping = snapshot?.getBoolean("isTyping") == true
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
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }

            Box(
                Modifier
                    .size(40.dp)
                    .background(Color(0xFFDCEFE9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.initials, color = Green, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(chat.name, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    if (peerTyping) "typing..." else "Khahish private chat",
                    color = if (peerTyping) Green else Muted,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = { onStartCall(false) }) {
                Icon(Icons.Default.Call, "Voice call", tint = Green)
            }

            IconButton(onClick = { onStartCall(true) }) {
                Icon(Icons.Default.Videocam, "Video call", tint = Green)
            }
        }

        if (isUploading || isSending) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Green
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (isMe) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 285.dp)
                            .background(
                                if (isMe) Color(0xFFD9F7EC) else Color.White,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                )
                            )
                            .padding(10.dp)
                    ) {
                        when (msg.mediaType) {
                            "image" -> AsyncImage(
                                model = msg.imageUrl,
                                contentDescription = "Shared photo",
                                modifier = Modifier
                                    .sizeIn(maxWidth = 250.dp, maxHeight = 250.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )

                            "video" -> MediaOpenRow(
                                "Video — tap to play",
                                Icons.Default.PlayCircle,
                                msg.imageUrl,
                                context
                            )

                            "audio" -> MediaOpenRow(
                                "Audio file — tap to play",
                                Icons.Default.PlayArrow,
                                msg.imageUrl,
                                context
                            )

                            "document" -> MediaOpenRow(
                                "Document — tap to open",
                                Icons.Default.Description,
                                msg.imageUrl,
                                context
                            )

                            "location" -> Text(msg.text, color = Ink)
                        }

                        if (msg.text.isNotBlank() && msg.mediaType != "location") {
                            Text(msg.text, color = Ink, fontSize = 15.sp)
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
                            showToast(
                                context,
                                "Location sharing is not available in this build."
                            )
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
                            context
                        ) {
                            isSending = false
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Send", tint = Green)
            }
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
    onComplete: () -> Unit
) {
    val now = System.currentTimeMillis()

    val data = hashMapOf<String, Any>(
        "senderId" to senderId,
        "text" to messageText,
        "imageUrl" to mediaUrl,
        "mediaType" to mediaType,
        "timestamp" to now,
        "deliveredTo" to emptyList<String>(),
        "readBy" to listOf(senderId)
    )

    chatRef.collection("messages")
        .add(data)
        .addOnSuccessListener {
            val preview = when (mediaType) {
                "image" -> "Photo"
                "video" -> "Video"
                "audio" -> "Audio"
                "document" -> "Document"
                "location" -> "Location"
                else -> messageText
            }

            chatRef.update(
                mapOf(
                    "lastMessage" to preview,
                    "lastMessageAt" to now,
                    "updatedAt" to now
                )
            ).addOnCompleteListener { updateTask ->
                if (!updateTask.isSuccessful) {
                    showToast(
                        context,
                        "Message sent, but chat preview could not update."
                    )
                }
                onComplete()
            }
        }
        .addOnFailureListener { error ->
            onComplete()
            showToast(context, "Message failed: ${error.message}")
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
