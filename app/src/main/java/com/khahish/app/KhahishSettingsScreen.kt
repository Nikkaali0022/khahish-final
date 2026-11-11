package com.khahish.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.Locale

private data class KhahishSupportTicket(val id: String, val subject: String, val status: String, val adminReply: String)

@Composable
fun KhahishSettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onSlideTransitionsChanged: (Boolean) -> Unit,
    onOneHandedChanged: (Boolean) -> Unit,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid.orEmpty()
    val prefs = remember { context.getSharedPreferences("khahish_ui_settings", Context.MODE_PRIVATE) }
    val privacyPrefs = remember { context.getSharedPreferences("khahish_privacy_settings", Context.MODE_PRIVATE) }

    var showAdvanced by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var editUsername by remember { mutableStateOf("") }
    var originalUsername by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var profilePhotoUrl by remember { mutableStateOf(user?.photoUrl?.toString().orEmpty()) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var savingProfile by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var supportSubject by remember { mutableStateOf("") }
    var supportMessage by remember { mutableStateOf("") }
    var submittingSupport by remember { mutableStateOf(false) }
    var supportTickets by remember { mutableStateOf(emptyList<KhahishSupportTicket>()) }
    var blockedUsers by remember { mutableStateOf(emptyList<String>()) }
    var loadingBlocked by remember { mutableStateOf(false) }
    var lastSeenVisible by remember { mutableStateOf(privacyPrefs.getBoolean("last_seen_visible", true)) }
    var onlineStatusVisible by remember { mutableStateOf(privacyPrefs.getBoolean("online_status_visible", true)) }
    var readReceipts by remember { mutableStateOf(privacyPrefs.getBoolean("read_receipts", true)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("animations_enabled", true)) }
    var slideTransitions by remember { mutableStateOf(prefs.getBoolean("slide_transitions_enabled", true)) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            db.collection("publicProfiles").document(uid).get()
                .addOnSuccessListener { profile ->
                    editBio = profile.getString("bio").orEmpty()
                    editUsername = profile.getString("username").orEmpty()
                    originalUsername = profile.getString("username").orEmpty()
                    profilePhotoUrl = profile.getString("photoUrl") ?: user?.photoUrl?.toString().orEmpty()
                    editName = profile.getString("name") ?: user?.displayName.orEmpty()
                }
        }
    }

    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uid.isNotBlank()) {
            uploadingPhoto = true
            uploadToCloudinary(context, uri, "image",
                onSuccess = { url ->
                    profilePhotoUrl = url
                    val updates = mapOf("photoUrl" to url, "updatedAt" to System.currentTimeMillis())
                    db.collection("users").document(uid).set(updates, SetOptions.merge())
                    db.collection("publicProfiles").document(uid).set(updates, SetOptions.merge())
                    user?.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(url)).build())
                    uploadingPhoto = false
                    Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
                },
                onError = { uploadingPhoto = false; Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            )
        }
    }

    LaunchedEffect(uid, showBlocked) {
        if (uid.isNotBlank() && showBlocked) {
            loadingBlocked = true
            db.collection("users").document(uid).collection("blocked").get()
                .addOnSuccessListener {
                    blockedUsers = it.documents.map { doc -> doc.id }
                    loadingBlocked = false
                }
                .addOnFailureListener {
                    loadingBlocked = false
                    Toast.makeText(context, "Unable to load blocked accounts: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    if (showAdvanced) {
        KhahishAdvancedSettingsScreen(
            modifier = modifier,
            onBack = { showAdvanced = false },
            onLogout = onLogout,
            onAnimationsChanged = onAnimationsChanged,
            onSlideTransitionsChanged = onSlideTransitionsChanged,
            onOneHandedChanged = onOneHandedChanged
        )
        return
    }

    if (showStorage) {
        StorageMediaPage(modifier = modifier, onBack = { showStorage = false })
        return
    }

    DisposableEffect(uid, showSupport) {
        if (uid.isBlank() || !showSupport) {
            onDispose { }
        } else {
            val registration = db.collection("supportTickets")
                .whereEqualTo("userUid", uid)
                .addSnapshotListener { snapshot, _ ->
                    supportTickets = snapshot?.documents.orEmpty().map { doc ->
                        KhahishSupportTicket(doc.id, doc.getString("subject").orEmpty(), doc.getString("status") ?: "pending", doc.getString("adminReply").orEmpty())
                    }.sortedByDescending { it.first }
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Settings", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Manage your Khahish account and app experience", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))

        KhahishSettingsGroup("ACCOUNT") {
            KhahishSettingsItem(Icons.Default.Person, "Account information", user?.email ?: "Email unavailable") {
                Toast.makeText(context, user?.email ?: "Account information unavailable", Toast.LENGTH_LONG).show()
            }
            KhahishSettingsItem(Icons.Default.Edit, "Edit profile", "Change your photo, name and bio") {
                showEditProfile = true
            }
            KhahishSettingsItem(Icons.Default.Block, "Blocked accounts", "Review and unblock people") {
                showBlocked = !showBlocked
            }
            if (showBlocked) {
                when {
                    loadingBlocked -> CircularProgressIndicator(color = Green)
                    blockedUsers.isEmpty() -> Text("No blocked accounts", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                    else -> blockedUsers.forEach { blockedUid ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(blockedUid.take(16) + "…", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                db.collection("users").document(uid).collection("blocked").document(blockedUid).delete()
                                    .addOnSuccessListener {
                                        blockedUsers = blockedUsers.filterNot { it == blockedUid }
                                        Toast.makeText(context, "Account unblocked", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }) { Text("Unblock", color = Green) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        KhahishSettingsGroup("PRIVACY & SECURITY") {
            KhahishSettingsItem(Icons.Default.Security, "Privacy controls", "Manage privacy preferences") {
                showPrivacy = !showPrivacy
            }
            if (showPrivacy) {
                KhahishSettingsSwitch(
                    "Show online status",
                    "Let contacts see when you are online",
                    onlineStatusVisible
                ) { enabled ->
                    onlineStatusVisible = enabled
                    privacyPrefs.edit().putBoolean("online_status_visible", enabled).apply()
                    if (uid.isNotBlank()) {
                        db.collection("users").document(uid).set(
                            mapOf("privacy" to mapOf("onlineStatusVisible" to enabled, "lastSeenVisible" to lastSeenVisible, "readReceipts" to readReceipts)),
                            SetOptions.merge()
                        )
                        db.collection("publicProfiles").document(uid).set(
                            mapOf("onlineStatusVisible" to enabled, "isOnline" to enabled), SetOptions.merge()
                        )
                    }
                }
                KhahishSettingsSwitch(
                    "Show last seen",
                    "Let contacts see the time you were last active",
                    lastSeenVisible
                ) { enabled ->
                    lastSeenVisible = enabled
                    privacyPrefs.edit().putBoolean("last_seen_visible", enabled).apply()
                    if (uid.isNotBlank()) {
                        db.collection("users").document(uid).set(
                            mapOf("privacy" to mapOf("onlineStatusVisible" to onlineStatusVisible, "lastSeenVisible" to enabled, "readReceipts" to readReceipts)),
                            SetOptions.merge()
                        )
                        val publicUpdate = mutableMapOf<String, Any>(
                            "lastSeenVisible" to enabled,
                            "onlineStatusVisible" to onlineStatusVisible
                        )
                        if (!enabled) publicUpdate["lastSeenAt"] = FieldValue.delete()
                        db.collection("publicProfiles").document(uid).set(publicUpdate, SetOptions.merge())
                    }
                }
                KhahishSettingsSwitch(
                    "Read receipts",
                    "Let contacts know when you have read their messages",
                    readReceipts
                ) { enabled ->
                    readReceipts = enabled
                    privacyPrefs.edit().putBoolean("read_receipts", enabled).apply()
                    if (uid.isNotBlank()) db.collection("users").document(uid).set(
                        mapOf("privacy" to mapOf("onlineStatusVisible" to onlineStatusVisible, "lastSeenVisible" to lastSeenVisible, "readReceipts" to enabled)),
                        SetOptions.merge()
                    )
                }
                Text("Your online status, last seen and read-receipt preferences are now connected to your profile and chat behavior.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        KhahishSettingsGroup("CHAT & APPEARANCE") {
            var themeMenuExpanded by remember { mutableStateOf(false) }
            KhahishSettingsItem(
                Icons.Default.DarkMode,
                "App theme",
                when (themeMode) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "System default"
                }
            ) { themeMenuExpanded = true }
            DropdownMenu(expanded = themeMenuExpanded, onDismissRequest = { themeMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("System default") }, onClick = {
                    onThemeModeChanged("system"); themeMenuExpanded = false
                })
                DropdownMenuItem(text = { Text("Light") }, onClick = {
                    onThemeModeChanged("light"); themeMenuExpanded = false
                })
                DropdownMenuItem(text = { Text("Dark") }, onClick = {
                    onThemeModeChanged("dark"); themeMenuExpanded = false
                })
            }
            KhahishSettingsItem(Icons.Default.Tune, "Animations and transitions", "Smooth movement and slide effects") {
                showAppearance = !showAppearance
            }
            if (showAppearance) {
                KhahishSettingsSwitch("Smooth animations", "Enable screen and content animations", animations) {
                    animations = it
                    prefs.edit().putBoolean("animations_enabled", it).apply()
                    onAnimationsChanged(it)
                    if (!it) {
                        slideTransitions = false
                        prefs.edit().putBoolean("slide_transitions_enabled", false).apply()
                        onSlideTransitionsChanged(false)
                    }
                }
                KhahishSettingsSwitch("Slide transitions", "Slide between Chats, Updates, Calls and Settings", slideTransitions, enabled = animations) {
                    slideTransitions = it
                    prefs.edit().putBoolean("slide_transitions_enabled", it).apply()
                    onSlideTransitionsChanged(it)
                }
            }
            KhahishSettingsItem(Icons.Default.Notifications, "Notifications", "Open Android notification controls") {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
                }
            }
            KhahishSettingsItem(Icons.Default.Storage, "Storage and media", "Manage downloads and clear temporary cache") {
                showStorage = true
            }
        }

        Spacer(Modifier.height(14.dp))
        KhahishSettingsGroup("ADVANCED FEATURES") {
            KhahishSettingsItem(Icons.Default.Tune, "Advanced settings", "Accessibility, account controls, trusted devices, privacy and data tools") {
                showAdvanced = true
            }
        }

        KhahishSettingsGroup("HELP & ABOUT") {
            KhahishSettingsItem(Icons.Default.Help, "Help and support", "Contact and support information") {
                showSupport = true
            }
            KhahishSettingsItem(Icons.Default.Info, "About Khahish", "Khahish Messenger • Version 1.0") {
                Toast.makeText(context, "Khahish Messenger version 1.0", Toast.LENGTH_SHORT).show()
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE54848))
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Log out")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Developed by Nikka Ali",
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showSupport) {
        AlertDialog(
            onDismissRequest = { if (!submittingSupport) showSupport = false },
            title = { Text("Help and support") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send a complaint or question. Your ticket and any support reply will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    OutlinedTextField(
                        value = supportSubject,
                        onValueChange = { if (it.length <= 100) supportSubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supportMessage,
                        onValueChange = { if (it.length <= 2000) supportMessage = it },
                        label = { Text("Describe your issue") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Your previous tickets", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (supportTickets.isEmpty()) {
                        Text("No support tickets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        supportTickets.take(5).forEach { ticket ->
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text(ticket.subject, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                Text("Status: ${ticket.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                if (ticket.adminReply.isNotBlank()) Text("Support reply: ${ticket.adminReply}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submittingSupport && supportSubject.trim().length >= 3 && supportMessage.trim().length >= 10,
                    onClick = {
                        if (uid.isBlank()) return@TextButton
                        submittingSupport = true
                        val now = System.currentTimeMillis()
                        db.collection("supportTickets").add(mapOf(
                            "userUid" to uid,
                            "userEmail" to (user?.email ?: ""),
                            "subject" to supportSubject.trim(),
                            "message" to supportMessage.trim(),
                            "status" to "pending",
                            "adminReply" to "",
                            "createdAt" to now,
                            "updatedAt" to now
                        )).addOnSuccessListener {
                            submittingSupport = false
                            supportSubject = ""
                            supportMessage = ""
                            Toast.makeText(context, "Support ticket submitted.", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener { error ->
                            submittingSupport = false
                            Toast.makeText(context, "Ticket could not be submitted: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text(if (submittingSupport) "Sending…" else "Send ticket") }
            },
            dismissButton = {
                TextButton(onClick = { if (!submittingSupport) showSupport = false }) { Text("Close") }
            }
        )
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { if (!savingProfile) showEditProfile = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (profilePhotoUrl.isNotBlank()) {
                            AsyncImage(model = profilePhotoUrl, contentDescription = "Profile picture",
                                modifier = Modifier.size(88.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(88.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(42.dp))
                            }
                        }
                    }
                    OutlinedButton(onClick = { profilePhotoPicker.launch("image/*") }, enabled = !uploadingPhoto, modifier = Modifier.fillMaxWidth()) {
                        if (uploadingPhoto) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uploadingPhoto) "Uploading photo…" else "Choose profile picture")
                    }
                    OutlinedTextField(value = editName, onValueChange = { editName = it },
                        label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editUsername,
                        onValueChange = { editUsername = it.lowercase(Locale.ROOT).filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }.take(20) },
                        label = { Text("Unique username") }, placeholder = { Text("your_username") },
                        leadingIcon = { Text("@", color = Green) },
                        supportingText = { Text("3–20 characters. Letters, numbers and underscore only.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editBio, onValueChange = { if (it.length <= 160) editBio = it },
                        label = { Text("Bio") }, placeholder = { Text("Write something about yourself…") },
                        minLines = 3, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    Text("${editBio.length}/160", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = editName.isNotBlank() && isValidKhahishUsername(editUsername) && !savingProfile && !uploadingPhoto, onClick = {
                    val cleanName = editName.trim()
                    val cleanUsername = normalizeKhahishUsername(editUsername)
                    val current = auth.currentUser
                    if (current == null || uid.isBlank()) return@TextButton
                    savingProfile = true
                    val usernameRef = db.collection("usernames").document(cleanUsername)
                    val oldUsername = normalizeKhahishUsername(originalUsername)
                    val oldRef = oldUsername.takeIf { it.isNotBlank() && it != cleanUsername }?.let { db.collection("usernames").document(it) }
                    db.runTransaction { transaction ->
                        val claimed = transaction.get(usernameRef)
                        val oldClaim = oldRef?.let { transaction.get(it) }
                        if (claimed.exists() && claimed.getString("uid") != uid) throw IllegalStateException("That username is already taken.")
                        val now = System.currentTimeMillis()
                        transaction.set(usernameRef, mapOf("uid" to uid, "username" to cleanUsername, "updatedAt" to now))
                        transaction.set(db.collection("usernamesByUid").document(uid), mapOf("uid" to uid, "username" to cleanUsername, "updatedAt" to now), SetOptions.merge())
                        if (oldRef != null && oldClaim?.getString("uid") == uid) transaction.delete(oldRef)
                        val updates = mapOf<String, Any>("name" to cleanName, "nameLowercase" to cleanName.lowercase(Locale.ROOT),
                            "username" to cleanUsername, "bio" to editBio.trim(), "photoUrl" to profilePhotoUrl, "updatedAt" to now)
                        transaction.set(db.collection("users").document(uid), updates, SetOptions.merge())
                        transaction.set(db.collection("publicProfiles").document(uid), updates, SetOptions.merge())
                        null
                    }.addOnSuccessListener {
                        val profileRequest = UserProfileChangeRequest.Builder().setDisplayName(cleanName)
                            .setPhotoUri(if (profilePhotoUrl.isBlank()) null else Uri.parse(profilePhotoUrl)).build()
                        current.updateProfile(profileRequest).addOnCompleteListener { authTask ->
                            savingProfile = false; originalUsername = cleanUsername; showEditProfile = false
                            Toast.makeText(context, if (authTask.isSuccessful) "Profile and username updated" else "Profile saved; account display name may need refresh", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener { error ->
                        savingProfile = false
                        Toast.makeText(context, error.message ?: "Username update failed", Toast.LENGTH_LONG).show()
                    }
                }) { Text(if (savingProfile) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun KhahishSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(title, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun KhahishSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KhahishSettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
