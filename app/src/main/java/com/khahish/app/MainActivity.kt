
package com.khahish.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val CLOUDINARY_CLOUD_NAME = "koctiaes"
private const val CLOUDINARY_UPLOAD_PRESET = "ml_default"

private val Green = Color(0xFF18A889)
private val Ink = Color(0xFF101A19)
private val Pale = Color(0xFFF4F8F6)
private val Muted = Color(0xFF788681)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KhahishApp() }
    }
}

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
    val photoUrl: String = ""
)

data class MessageItem(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L
)

private fun privateChatId(uid1: String, uid2: String): String {
    return listOf(uid1, uid2).sorted().joinToString("_")
}

private fun saveUserToFirestore(
    db: FirebaseFirestore,
    uid: String?,
    name: String,
    contact: String
) {
    if (uid.isNullOrBlank()) return

    val cleanName = name.trim().ifBlank { "Khahish User" }

    val privateData = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to cleanName,
        "contact" to contact,
        "updatedAt" to System.currentTimeMillis()
    )

    val publicData = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to cleanName,
        "nameLowercase" to cleanName.lowercase(),
        "photoUrl" to "",
        "updatedAt" to System.currentTimeMillis()
    )

    db.collection("users").document(uid)
        .set(privateData, SetOptions.merge())

    db.collection("publicProfiles").document(uid)
        .set(publicData, SetOptions.merge())
}

@Composable
fun KhahishApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var tab by remember { mutableStateOf("Chats") }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            background = Pale,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        val user = currentUser

        if (user == null) {
            AuthScreen(onLoginSuccess = {
                currentUser = auth.currentUser
            })
        } else if (selectedChat != null) {
            ConversationScreen(
                chat = selectedChat!!,
                onBack = { selectedChat = null }
            )
        } else {
            Scaffold(
                containerColor = Pale,
                topBar = {
                    Column(
                        Modifier.background(Color.White)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp)
                                    .background(Green, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Forum, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Khahish",
                                    fontSize = 25.sp,
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
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (tab == "Chats") "Messages" else
                                if (tab == "New chat") "New conversation" else tab,
                            fontSize = 23.sp,
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
                            onClick = { tab = "New chat" },
                            containerColor = Green,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Edit, "New chat")
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    "Chats" -> ChatList(
                        modifier = Modifier.padding(padding),
                        currentUid = user.uid,
                        onOpen = { selectedChat = it }
                    )
                    "New chat" -> NewChatScreen(
                        modifier = Modifier.padding(padding),
                        currentUid = user.uid,
                        onBack = { tab = "Chats" },
                        onChatCreated = {
                            selectedChat = it
                        }
                    )
                    "Settings" -> SettingsPage(
                        Modifier.padding(padding),
                        onLogout = {
                            auth.signOut()
                            currentUser = null
                            selectedChat = null
                            tab = "Chats"
                        }
                    )
                    else -> PlaceholderPage(Modifier.padding(padding), tab)
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
    var phone by remember { mutableStateOf("+92") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useEmailMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Pale).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(Green, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Forum, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Khahish", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            if (useEmailMode) "Sign in with Email" else "Sign in with Phone Number",
            fontSize = 13.sp,
            color = Muted
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        if (!useEmailMode) {
            if (verificationId == null) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (+923...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (phone.length < 10) {
                            Toast.makeText(context, "Enter valid phone number", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phone.trim())
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(context as Activity)
                            .setCallbacks(object :
                                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    auth.signInWithCredential(credential)
                                        .addOnSuccessListener { result ->
                                            saveUserToFirestore(db, result.user?.uid, name, phone)
                                            isLoading = false
                                            onLoginSuccess()
                                        }
                                        .addOnFailureListener {
                                            isLoading = false
                                            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                                        }
                                }

                                override fun onVerificationFailed(e: FirebaseException) {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }

                                override fun onCodeSent(
                                    verId: String,
                                    token: PhoneAuthProvider.ForceResendingToken
                                ) {
                                    isLoading = false
                                    verificationId = verId
                                    Toast.makeText(context, "OTP Sent!", Toast.LENGTH_SHORT).show()
                                }
                            }).build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text(if (isLoading) "Sending OTP..." else "Send OTP Code")
                }
            } else {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("Enter 6-Digit OTP") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (otpCode.length < 6) return@Button
                        isLoading = true
                        val credential = PhoneAuthProvider.getCredential(
                            verificationId!!,
                            otpCode.trim()
                        )
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { result ->
                                saveUserToFirestore(db, result.user?.uid, name, phone)
                                isLoading = false
                                onLoginSuccess()
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Invalid OTP: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text(if (isLoading) "Verifying..." else "Verify & Login")
                }
            }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (6+ chars)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 6) {
                        Toast.makeText(context, "Enter valid email & password", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            saveUserToFirestore(db, result.user?.uid, name, email)
                            isLoading = false
                            onLoginSuccess()
                        }
                        .addOnFailureListener {
                            auth.createUserWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { result ->
                                    saveUserToFirestore(db, result.user?.uid, name, email)
                                    isLoading = false
                                    onLoginSuccess()
                                }
                                .addOnFailureListener { error ->
                                    isLoading = false
                                    Toast.makeText(context, "Auth failed: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Text(if (isLoading) "Signing in..." else "Continue with Email")
            }
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { useEmailMode = !useEmailMode }) {
            Text(
                if (useEmailMode) "Use Phone Number (OTP) instead"
                else "Use Email Login instead (No SMS needed)",
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
    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<AppUser>()) }
    var loading by remember { mutableStateOf(false) }
    var creatingUid by remember { mutableStateOf("") }

    LaunchedEffect(search) {
        val queryText = search.trim().lowercase()
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
                .addOnFailureListener {
                    loading = false
                }
        }
    }

    Column(modifier.fillMaxSize().background(Pale)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
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
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
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
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (creatingUid.isNotEmpty()) return@clickable
                                creatingUid = user.uid
                                val chatId = privateChatId(currentUid, user.uid)
                                val members = listOf(currentUid, user.uid).sorted()
                                val memberNames = mapOf(
                                    currentUid to "You",
                                    user.uid to user.name
                                )
                                val chatData = hashMapOf<String, Any>(
                                    "members" to members,
                                    "memberNames" to memberNames,
                                    "lastMessage" to "",
                                    "lastMessageAt" to 0L,
                                    "updatedAt" to System.currentTimeMillis()
                                )

                                db.collection("chats").document(chatId)
                                    .get()
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
                                                    initials = user.name.firstOrNull()?.uppercase() ?: "K"
                                                )
                                            )
                                        } else {
                                            db.collection("chats").document(chatId)
                                                .set(chatData)
                                                .addOnSuccessListener {
                                                    creatingUid = ""
                                                    onChatCreated(
                                                        Chat(
                                                            id = chatId,
                                                            peerUid = user.uid,
                                                            name = user.name.ifBlank { "Khahish User" },
                                                            message = "",
                                                            time = "",
                                                            initials = user.name.firstOrNull()?.uppercase() ?: "K"
                                                        )
                                                    )
                                                }
                                                .addOnFailureListener { error ->
                                                    creatingUid = ""
                                                    Toast.makeText(
                                                        LocalContext.current,
                                                        "Chat create failed: ${error.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        creatingUid = ""
                                        Toast.makeText(
                                            LocalContext.current,
                                            "Error: ${error.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).background(Color(0xFFDCEFE9), CircleShape),
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
                            Text(user.name.ifBlank { "Khahish User" }, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Tap to start private chat", color = Muted, fontSize = 12.sp)
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
                    val chatDocs = snapshot.documents
                    val mapped = chatDocs.mapNotNull { doc ->
                        val members = doc.get("members") as? List<*>
                        val peerUid = members
                            ?.filterIsInstance<String>()
                            ?.firstOrNull { it != currentUid }
                            ?: return@mapNotNull null

                        val names = doc.get("memberNames") as? Map<*, *>
                        val peerName = names?.get(peerUid) as? String ?: "Khahish User"

                        Chat(
                            id = doc.id,
                            peerUid = peerUid,
                            name = peerName,
                            message = doc.getString("lastMessage") ?: "",
                            time = "",
                            initials = peerName.firstOrNull()?.uppercase() ?: "K"
                        )
                    }.sortedByDescending { chat ->
                        chatDocs.firstOrNull { it.id == chat.id }
                            ?.getLong("lastMessageAt") ?: 0L
                    }
                    chats = mapped
                    loading = false
                }
            }

        onDispose { registration.remove() }
    }

    val filtered = chats.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.message.contains(search, ignoreCase = true)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search conversations") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            filtered.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Green, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (search.isBlank()) "No private chats yet" else "No matching conversations",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Tap the edit button to find a user", color = Muted, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                items(filtered, key = { it.id }) { chat ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onOpen(chat) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(52.dp).background(Color(0xFFDCEFE9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(chat.initials, color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.name, fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                chat.message.ifBlank { "Start your conversation" },
                                color = Muted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                    HorizontalDivider(color = Color(0xFFEAF0ED), modifier = Modifier.padding(start = 82.dp))
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(chat: Chat, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<MessageItem>()) }
    var isUploading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    DisposableEffect(chat.id) {
        val registration = db.collection("chats").document(chat.id)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages = snapshot.documents.map { doc ->
                        MessageItem(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            text = doc.getString("text") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                }
            }
        onDispose { registration.remove() }
    }

    fun sendMessage(messageText: String, imageUrl: String = "") {
        if (messageText.isBlank() && imageUrl.isBlank()) return

        isSending = true
        val now = System.currentTimeMillis()
        val msgData = hashMapOf<String, Any>(
            "senderId" to currentUserId,
            "text" to messageText,
            "imageUrl" to imageUrl,
            "timestamp" to now
        )

        val chatRef = db.collection("chats").document(chat.id)

        chatRef.collection("messages").add(msgData)
            .addOnSuccessListener {
                val preview = if (imageUrl.isNotBlank()) "Photo" else messageText
                chatRef.update(
                    mapOf(
                        "lastMessage" to preview,
                        "lastMessageAt" to now,
                        "updatedAt" to now
                    )
                ).addOnCompleteListener {
                    isSending = false
                }
            }
            .addOnFailureListener { error ->
                isSending = false
                Toast.makeText(context, "Message failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(
                context,
                uri,
                onSuccess = { uploadedUrl ->
                    isUploading = false
                    sendMessage("", uploadedUrl)
                },
                onError = { error ->
                    isUploading = false
                    Toast.makeText(context, "Upload failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Pale)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Box(
                Modifier.size(38.dp).background(Color(0xFFDCEFE9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.initials, color = Green, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(chat.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink)
            IconButton(onClick = {
                Toast.makeText(context, "Voice calling will be added in the calling phase", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Call, "Voice call", tint = Green)
            }
            IconButton(onClick = {
                Toast.makeText(context, "Video calling will be added in the calling phase", Toast.LENGTH_SHORT).show()
            }) {
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
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                if (isMe) Green else Color.White,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                            .widthIn(max = 260.dp)
                    ) {
                        if (msg.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = msg.imageUrl,
                                contentDescription = "Uploaded Photo",
                                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (msg.text.isNotEmpty()) {
                            Text(
                                msg.text,
                                color = if (isMe) Color.White else Ink,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, "Attach Image", tint = Green)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            IconButton(
                enabled = !isSending && !isUploading,
                onClick = {
                    val message = text.trim()
                    if (message.isNotBlank()) {
                        text = ""
                        sendMessage(message)
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Send", tint = Green)
            }
        }
    }
}

fun uploadToCloudinary(
    context: Context,
    uri: Uri,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    if (bytes == null) {
        onError("File read error")
        return
    }

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            "image.jpg",
            bytes.toRequestBody("image/*".toMediaTypeOrNull())
        )
        .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
        .build()

    val request = Request.Builder()
        .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload")
        .post(requestBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            android.os.Handler(context.mainLooper).post {
                onError(e.message ?: "Network error")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            android.os.Handler(context.mainLooper).post {
                if (response.isSuccessful && body != null) {
                    val secureUrl = JSONObject(body).optString("secure_url")
                    if (secureUrl.isNotBlank()) onSuccess(secureUrl)
                    else onError("Image URL missing")
                } else {
                    onError("Check Cloudinary configuration")
                }
            }
        }
    })
}

@Composable
fun PlaceholderPage(modifier: Modifier, title: String) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = Green, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
        Text("This section will be connected in a later development phase.", color = Muted)
    }
}

@Composable
fun SettingsPage(modifier: Modifier, onLogout: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        listOf(
            Icons.Default.Person to "Profile",
            Icons.Default.Lock to "Privacy and security",
            Icons.Default.Notifications to "Notifications",
            Icons.Default.Storage to "Storage and data",
            Icons.Default.Help to "Help and support"
        ).forEach { (icon, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Green)
                Spacer(Modifier.width(14.dp))
                Text(label, color = Ink)
            }
            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9534F)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Logout, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Log Out", color = Color.White)
        }
    }
}
