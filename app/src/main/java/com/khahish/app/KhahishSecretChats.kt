package com.khahish.app

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object KhahishSecretChatLock {
    private const val PREFS = "khahish_secret_chat_lock"
    private const val ITERATIONS = 160_000
    private const val KEY_BITS = 256

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(prefix: String, uid: String) = "${prefix}_${uid}"

    fun hasPassword(context: Context, uid: String): Boolean =
        uid.isNotBlank() &&
            prefs(context).contains(key("salt", uid)) &&
            prefs(context).contains(key("hash", uid))

    fun setPassword(context: Context, uid: String, password: String): Boolean {
        if (uid.isBlank() || password.length < 8) return false
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val algorithm = algorithmName()
            val hash = derive(password.toCharArray(), salt, algorithm)
            prefs(context).edit()
                .putString(key("salt", uid), Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(key("hash", uid), Base64.encodeToString(hash, Base64.NO_WRAP))
                .putString(key("algorithm", uid), algorithm)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun verifyPassword(context: Context, uid: String, password: String): Boolean {
        if (!hasPassword(context, uid)) return false
        return try {
            val p = prefs(context)
            val salt = Base64.decode(p.getString(key("salt", uid), "") ?: "", Base64.NO_WRAP)
            val expected = Base64.decode(p.getString(key("hash", uid), "") ?: "", Base64.NO_WRAP)
            val algorithm = p.getString(key("algorithm", uid), algorithmName()) ?: algorithmName()
            val actual = derive(password.toCharArray(), salt, algorithm)
            if (actual.size != expected.size) false
            else actual.indices.fold(true) { same, i -> same && (actual[i] == expected[i]) }
        } catch (_: Exception) {
            false
        }
    }

    fun lockedUids(context: Context, uid: String): Set<String> =
        prefs(context).getStringSet(key("contacts", uid), emptySet())?.toSet() ?: emptySet()

    fun saveLockedUids(context: Context, uid: String, values: Set<String>) {
        prefs(context).edit()
            .putStringSet(key("contacts", uid), HashSet(values.filter { it.isNotBlank() && it != uid }))
            .apply()
    }

    private fun algorithmName(): String = try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        "PBKDF2WithHmacSHA256"
    } catch (_: Exception) {
        "PBKDF2WithHmacSHA1"
    }

    private fun derive(password: CharArray, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }
}

@Composable
internal fun SecretChatPasswordDialog(
    uid: String,
    createMode: Boolean,
    onDismiss: () -> Unit,
    onUnlocked: (Long) -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Box(
                Modifier.size(58.dp).background(
                    Brush.linearGradient(listOf(Color(0xFFB8F3DE), Color(0xFF18A889))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        },
        title = {
            Text(
                if (createMode) "Create Your Unique Password" else "Enter Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (createMode) "Create a password to protect your Locked Chats. You will use the same password every time."
                    else "Enter your secret password to open your Locked Chats.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text(if (createMode) "Create password (8+ characters)" else "Secret password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (createMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = "" },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && password.isNotBlank() && (!createMode || confirmPassword.isNotBlank()),
                onClick = {
                    if (createMode) {
                        when {
                            password.length < 8 -> error = "Password must be at least 8 characters."
                            password != confirmPassword -> error = "Passwords do not match."
                            else -> {
                                busy = true
                                KhahishSecretChatApi.createPassword(
                                    password,
                                    onSuccess = { expiresAt ->
                                        busy = false
                                        Toast.makeText(context, "Secret password created.", Toast.LENGTH_SHORT).show()
                                        onUnlocked(expiresAt)
                                    },
                                    onError = { message ->
                                        busy = false
                                        error = message
                                    }
                                )
                            }
                        }
                    } else {
                        busy = true
                        KhahishSecretChatApi.unlock(
                            password,
                            onSuccess = { expiresAt ->
                                busy = false
                                onUnlocked(expiresAt)
                            },
                            onError = { message ->
                                busy = false
                                error = message
                            }
                        )
                    }
                }
            ) {
                Text(if (busy) "Please wait…" else if (createMode) "Create Password" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel") }
        }
    )
}

@Composable
internal fun LockChatPasswordSettingsDialog(uid: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        KhahishSecretChatApi.loadConfig(
            onSuccess = { hasPassword = it.hasPassword },
            onError = { message = it }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Lock Chat Passwords") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (hasPassword)
                        "Change the password used to open your Locked Chats."
                    else
                        "No secret password has been created yet. Tap the Khahish logo or name four times on the main page to create it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (hasPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; message = "" },
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; message = "" },
                        label = { Text("New password (8+ characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; message = "" },
                        label = { Text("Confirm new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            if (hasPassword) {
                Button(
                    enabled = !busy && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                    onClick = {
                        when {
                            newPassword.length < 8 -> message = "New password must be at least 8 characters."
                            newPassword != confirmPassword -> message = "New passwords do not match."
                            else -> {
                                busy = true
                                KhahishSecretChatApi.changePassword(
                                    currentPassword,
                                    newPassword,
                                    onSuccess = {
                                        busy = false
                                        Toast.makeText(context, "Password changed successfully.", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    onError = { error ->
                                        busy = false
                                        message = error
                                    }
                                )
                            }
                        }
                    }
                ) { Text(if (busy) "Saving…" else "Change Password") }
            }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close") } }
    )
}

@Composable
internal fun LockedChatsHub(
    modifier: Modifier = Modifier,
    currentUid: String,
    lockedContactUids: Set<String>,
    onBack: () -> Unit,
    onAddContact: (String, (Set<String>?, String?) -> Unit) -> Unit,
    onOpenChat: (Chat) -> Unit,
    onChangePassword: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF031D1B), Color(0xFF087F68), Color(0xFF003B42)))
            ).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Icon(Icons.Default.Lock, null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Locked Chats", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddContact = true }) { Icon(Icons.Default.PersonAdd, "Add contact", tint = Color.White) }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More options", tint = Color.White) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Change Password") },
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        onClick = { menuExpanded = false; onChangePassword() }
                    )
                    DropdownMenuItem(
                        text = { Text("Add Contact") },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                        onClick = { menuExpanded = false; showAddContact = true }
                    )
                }
            }
        }

        if (lockedContactUids.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, tint = Green, modifier = Modifier.size(76.dp))
                    Text("No Chats or Contacts Yet", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Add contacts to your Locked Chats and keep them hidden.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showAddContact = true }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add Contact")
                    }
                }
            }
        } else {
            ChatList(
                modifier = Modifier.weight(1f),
                currentUid = currentUid,
                onOpen = onOpenChat,
                onViewProfile = {}
            )
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { showAddContact = true }) {
                    Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(7.dp)); Text("Add Contact")
                }
            }
        }
    }

    if (showAddContact) {
        AddLockedContactDialog(
            currentUid = currentUid,
            existingLockedUids = lockedContactUids,
            onDismiss = { showAddContact = false },
            onAdd = { targetUid, onResult ->
                onAddContact(targetUid) { updated, error ->
                    if (updated != null) {
                        showAddContact = false
                    } else {
                        onResult(error ?: "Could not add this contact.")
                    }
                }
            }
        )
    }
}

@Composable
private fun AddLockedContactDialog(
    currentUid: String,
    existingLockedUids: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var targetUid by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Add to Locked Chats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20); error = ""; targetUid = ""; name = "" },
                    label = { Text("Contact username") },
                    leadingIcon = { Text("@", color = Green) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (name.isNotBlank()) Text("Add $name to your private list?", color = MaterialTheme.colorScheme.onSurface)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && username.trim().length >= 3,
                onClick = {
                    loading = true
                    error = ""
                    val clean = normalizeKhahishUsername(username)
                    db.collection("usernames").document(clean).get()
                        .addOnSuccessListener { claim ->
                            val foundUid = claim.getString("uid").orEmpty()
                            if (!claim.exists() || foundUid.isBlank() || foundUid == currentUid) {
                                loading = false
                                error = if (foundUid == currentUid) "You cannot add yourself." else "Username not found."
                            } else if (foundUid in existingLockedUids) {
                                loading = false
                                error = "This contact is already in Locked Chats."
                            } else {
                                db.collection("publicProfiles").document(foundUid).get()
                                    .addOnSuccessListener { profile ->
                                        loading = false
                                        if (!profile.exists()) error = "Profile is unavailable."
                                        else {
                                            targetUid = foundUid
                                            name = profile.getString("name") ?: "Khahish User"
                                        }
                                    }
                                    .addOnFailureListener { e -> loading = false; error = e.message ?: "Could not load profile." }
                            }
                        }
                        .addOnFailureListener { e -> loading = false; error = e.message ?: "Username lookup failed." }
                }
            ) { Text(if (loading) "Searching…" else "Find Contact") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (targetUid.isNotBlank()) {
                    TextButton(onClick = {
                        loading = true
                        onAdd(targetUid) { resultError ->
                            loading = false
                            if (resultError.isBlank()) onDismiss()
                            else error = resultError
                        }
                    }) { Text(if (loading) "Adding…" else "Add") }
                }
            }
        }
    )
}
