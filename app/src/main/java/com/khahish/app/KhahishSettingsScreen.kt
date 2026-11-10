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

@Composable
fun KhahishSettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onSlideTransitionsChanged: (Boolean) -> Unit,
    onOneHandedChanged: (Boolean) -> Unit
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
    var editBio by remember { mutableStateOf("") }
    var profilePhotoUrl by remember { mutableStateOf(user?.photoUrl?.toString().orEmpty()) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var savingProfile by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
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

    Column(
        modifier.fillMaxSize().background(Pale)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Settings", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Manage your Khahish account and app experience", color = Muted, fontSize = 12.sp)
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
                    blockedUsers.isEmpty() -> Text("No blocked accounts", color = Muted, modifier = Modifier.padding(10.dp))
                    else -> blockedUsers.forEach { blockedUid ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(blockedUid.take(16) + "…", color = Ink, modifier = Modifier.weight(1f))
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
                Text("Your online status, last seen and read-receipt preferences are now connected to your profile and chat behavior.", color = Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        KhahishSettingsGroup("CHAT & APPEARANCE") {
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
                Toast.makeText(context, "Khahish support will be connected in a later update.", Toast.LENGTH_LONG).show()
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
            color = Muted,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(28.dp))
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
                    OutlinedTextField(value = editBio, onValueChange = { if (it.length <= 160) editBio = it },
                        label = { Text("Bio") }, placeholder = { Text("Write something about yourself…") },
                        minLines = 3, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    Text("${editBio.length}/160", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = editName.isNotBlank() && !savingProfile && !uploadingPhoto, onClick = {
                    val cleanName = editName.trim()
                    val current = auth.currentUser
                    if (current == null || uid.isBlank()) return@TextButton
                    savingProfile = true
                    val profileRequest = UserProfileChangeRequest.Builder().setDisplayName(cleanName).setPhotoUri(if (profilePhotoUrl.isBlank()) null else Uri.parse(profilePhotoUrl)).build()
                    current.updateProfile(profileRequest).addOnCompleteListener { authTask ->
                        val updates = mapOf(
                            "name" to cleanName,
                            "nameLowercase" to cleanName.lowercase(Locale.ROOT),
                            "bio" to editBio.trim(),
                            "photoUrl" to profilePhotoUrl,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        db.collection("users").document(uid).set(updates, SetOptions.merge())
                            .addOnSuccessListener {
                                db.collection("publicProfiles").document(uid).set(updates, SetOptions.merge())
                                savingProfile = false
                                showEditProfile = false
                                Toast.makeText(context, if (authTask.isSuccessful) "Profile updated" else "Profile saved; account display name may need refresh", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener {
                                savingProfile = false
                                Toast.makeText(context, "Profile update failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
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
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
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
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
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
            Text(title, color = if (enabled) Ink else Muted, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
