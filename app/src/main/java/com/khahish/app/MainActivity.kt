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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- CLOUDINARY CONFIG ---
private const val CLOUDINARY_CLOUD_NAME = "koctiaes"
private const val CLOUDINARY_UPLOAD_PRESET = "YOUR_UNSIGNED_PRESET"

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

data class Chat(val id: String, val name: String, val message: String, val time: String, val unread: Int, val initials: String)
data class MessageItem(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L
)

private val demoChats = listOf(
    Chat("areeba_chat", "Areeba", "Theek hai, phir kal baat karte hain.", "10:42", 2, "A"),
    Chat("family_group", "Family Group", "Ammi: Sab ghar pohanch gaye?", "09:18", 4, "F"),
    Chat("hamza_chat", "Hamza", "Voice message", "Yesterday", 0, "H"),
    Chat("sana_chat", "Sana", "Photo", "Yesterday", 0, "S"),
    Chat("study_circle", "Study Circle", "Ali: Notes share kar diye hain", "Mon", 0, "SC")
)

@Composable
fun KhahishApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var tab by remember { mutableStateOf("Chats") }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Pale, surface = Color.White, onSurface = Ink)) {
        if (currentUser == null) {
            AuthScreen(onLoginSuccess = { currentUser = auth.currentUser })
        } else if (selectedChat != null) {
            ConversationScreen(selectedChat!!) { selectedChat = null }
        } else {
            Scaffold(
                containerColor = Pale,
                topBar = {
                    Column(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(Green, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Forum, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Khahish", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text("Connect. Share. Stay Close.", fontSize = 11.sp, color = Muted)
                            }
                            IconButton(onClick = { tab = "Settings" }) { Icon(Icons.Default.Settings, "Settings") }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(if (tab == "Chats") "Messages" else tab, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        listOf("Chats" to Icons.Default.Chat, "Updates" to Icons.Default.DonutLarge,
                            "Calls" to Icons.Default.Call, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                            NavigationBarItem(selected = tab == label, onClick = { tab = label },
                                icon = { Icon(icon, label) }, label = { Text(label) })
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == "Chats") FloatingActionButton(onClick = { tab = "New chat" },
                        containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Edit, "New chat") }
                }
            ) { padding ->
                when (tab) {
                    "Chats" -> ChatList(Modifier.padding(padding)) { selectedChat = it }
                    "Settings" -> SettingsPage(Modifier.padding(padding), onLogout = {
                        auth.signOut()
                        currentUser = null
                    })
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
        Box(Modifier.size(64.dp).background(Green, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Forum, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Khahish", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(if (useEmailMode) "Sign in with Email" else "Sign in with Phone Number", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name (e.g. Ali Khan)") },
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
                            Toast.makeText(context, "Enter valid phone with +92", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phone.trim())
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(context as Activity)
                            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    auth.signInWithCredential(credential).addOnSuccessListener { res ->
                                        saveUserToFirestore(db, res.user?.uid, name, phone)
                                        isLoading = false
                                        onLoginSuccess()
                                    }
                                }
                                override fun onVerificationFailed(e: FirebaseException) {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                                override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
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
                        val credential = PhoneAuthProvider.getCredential(verificationId!!, otpCode.trim())
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { res ->
                                saveUserToFirestore(db, res.user?.uid, name, phone)
                                isLoading = false
                                onLoginSuccess()
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Invalid OTP: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Enter valid email & 6+ char password", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { res ->
                            saveUserToFirestore(db, res.user?.uid, name, email)
                            isLoading = false
                            onLoginSuccess()
                        }
                        .addOnFailureListener {
                            auth.createUserWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { res ->
                                    saveUserToFirestore(db, res.user?.uid, name, email)
                                    isLoading = false
                                    onLoginSuccess()
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(context, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                if (useEmailMode) "Use Phone Number (OTP) instead" else "Use Email Login instead (No SMS needed)",
                color = Green
            )
        }
    }
}

private fun saveUserToFirestore(db: FirebaseFirestore, uid: String?, name: String, contact: String) {
    if (uid == null) return
    val userMap = hashMapOf(
        "uid" to uid,
        "name" to name.ifBlank { "Khahish User" },
        "contact" to contact,
        "updatedAt" to System.currentTimeMillis()
    )
    db.collection("users").document(uid).set(userMap)
}

@Composable
fun ChatList(modifier: Modifier = Modifier, onOpen: (Chat) -> Unit) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(value = "", onValueChange = {}, readOnly = true,
            placeholder = { Text("Search conversations") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(18.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
            items(demoChats) { chat ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(chat) }.padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Color(0xFFDCEFE9), CircleShape), contentAlignment = Alignment.Center) {
                        Text(chat.initials, color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(5.dp))
                        Text(chat.message, color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(chat.time, color = Muted, fontSize = 10.sp)
                        if (chat.unread > 0) {
                            Spacer(Modifier.height(7.dp))
                            Box(Modifier.size(21.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${chat.unread}", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFEAF0ED), modifier = Modifier.padding(start = 82.dp))
            }
        }
    }
}

@Composable
fun ConversationScreen(chat: Chat, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest_user"

    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<MessageItem>()) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(chat.id) {
        db.collection("chats").document(chat.id).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages = snapshot.documents.mapNotNull { doc ->
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
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            uploadToCloudinary(context, uri,
                onSuccess = { uploadedUrl ->
                    isUploading = false
                    val msgData = hashMapOf(
                        "senderId" to currentUserId,
                        "text" to "",
                        "imageUrl" to uploadedUrl,
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("chats").document(chat.id).collection("messages").add(msgData)
                },
                onError = { err ->
                    isUploading = false
                    Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Pale)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(chat.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {}) { Icon(Icons.Default.Call, "Voice call", tint = Green) }
            IconButton(onClick = {}) { Icon(Icons.Default.Videocam, "Video call", tint = Green) }
        }

        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Green)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .background(if (isMe) Green else Color.White, RoundedCornerShape(16.dp))
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
                            Text(msg.text, color = if (isMe) Color.White else Ink, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, "Attach Image", tint = Green)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Khahish...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    val msgData = hashMapOf(
                        "senderId" to currentUserId,
                        "text" to text.trim(),
                        "imageUrl" to "",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("chats").document(chat.id).collection("messages").add(msgData)
                    text = ""
                }
            }) {
                Icon(Icons.Default.Send, "Send", tint = Green)
            }
        }
    }
}

fun uploadToCloudinary(context: Context, uri: Uri, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
    if (bytes == null) {
        onError("File read error")
        return
    }
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", "image.jpg", bytes.toRequestBody("image/*".toMediaTypeOrNull()))
        .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
        .build()

    val request = Request.Builder()
        .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload")
        .post(requestBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            android.os.Handler(context.mainLooper).post { onError(e.message ?: "Network error") }
        }
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            android.os.Handler(context.mainLooper).post {
                if (response.isSuccessful && body != null) {
                    val secureUrl = JSONObject(body).optString("secure_url")
                    onSuccess(secureUrl)
                } else {
                    onError("Check Cloudinary Name/Preset")
                }
            }
        }
    })
}

@Composable
fun PlaceholderPage(modifier: Modifier, title: String) {
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, tint = Green, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
        Text("This section will be connected in a later development phase.", color = Muted)
    }
}

@Composable
fun SettingsPage(modifier: Modifier, onLogout: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        listOf(Icons.Default.Person to "Profile", Icons.Default.Lock to "Privacy and security",
            Icons.Default.Notifications to "Notifications", Icons.Default.Storage to "Storage and data",
            Icons.Default.Help to "Help and support").forEach { (icon, label) ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Green); Spacer(Modifier.width(14.dp)); Text(label, color = Ink)
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
