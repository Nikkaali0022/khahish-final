package com.khahish.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.google.firebase.firestore.SetOptions
import java.util.Locale

@Composable
fun KhahishSettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onSlideTransitionsChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid.orEmpty()
    val prefs = remember { context.getSharedPreferences("khahish_ui_settings", Context.MODE_PRIVATE) }
    val privacyPrefs = remember { context.getSharedPreferences("khahish_privacy_settings", Context.MODE_PRIVATE) }

    var showEditProfile by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var savingProfile by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf(emptyList<String>()) }
    var loadingBlocked by remember { mutableStateOf(false) }
    var lastSeenVisible by remember { mutableStateOf(privacyPrefs.getBoolean("last_seen_visible", true)) }
    var readReceipts by remember { mutableStateOf(privacyPrefs.getBoolean("read_receipts", true)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("animations_enabled", true)) }
    var slideTransitions by remember { mutableStateOf(prefs.getBoolean("slide_transitions_enabled", true)) }

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
            KhahishSettingsItem(Icons.Default.Edit, "Edit profile", "Change your display name") {
                editName = user?.displayName.orEmpty()
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
                    "Show last seen preference",
                    "Save whether you want last-seen visibility enabled",
                    lastSeenVisible
                ) {
                    lastSeenVisible = it
                    privacyPrefs.edit().putBoolean("last_seen_visible", it).apply()
                    if (uid.isNotBlank()) db.collection("users").document(uid)
                        .set(mapOf("privacy" to mapOf("lastSeenVisible" to it, "readReceipts" to readReceipts)), SetOptions.merge())
                }
                KhahishSettingsSwitch(
                    "Read receipts preference",
                    "Save whether read receipts should be enabled",
                    readReceipts
                ) {
                    readReceipts = it
                    privacyPrefs.edit().putBoolean("read_receipts", it).apply()
                    if (uid.isNotBlank()) db.collection("users").document(uid)
                        .set(mapOf("privacy" to mapOf("lastSeenVisible" to lastSeenVisible, "readReceipts" to it)), SetOptions.merge())
                }
                Text("These preferences are saved; chat presence and receipt enforcement will be connected to these controls in the next integration pass.", color = Muted, fontSize = 11.sp)
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
        Spacer(Modifier.height(28.dp))
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { if (!savingProfile) showEditProfile = false },
            title = { Text("Edit profile") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Display name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = editName.isNotBlank() && !savingProfile, onClick = {
                    val cleanName = editName.trim()
                    val current = auth.currentUser
                    if (current == null || uid.isBlank()) return@TextButton
                    savingProfile = true
                    val profileRequest = UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                    current.updateProfile(profileRequest).addOnCompleteListener { authTask ->
                        val updates = mapOf(
                            "name" to cleanName,
                            "nameLowercase" to cleanName.lowercase(Locale.ROOT),
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
