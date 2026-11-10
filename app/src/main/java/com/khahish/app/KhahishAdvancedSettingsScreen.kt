package com.khahish.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun KhahishAdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
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
    val prefs = remember { context.getSharedPreferences("khahish_advanced_settings", Context.MODE_PRIVATE) }
    val uiPrefs = remember { context.getSharedPreferences("khahish_ui_settings", Context.MODE_PRIVATE) }
    val privacyPrefs = remember { context.getSharedPreferences("khahish_privacy_settings", Context.MODE_PRIVATE) }

    var oneHanded by remember { mutableStateOf(prefs.getBoolean("one_handed", false)) }
    var reducedMotion by remember { mutableStateOf(!uiPrefs.getBoolean("animations_enabled", true)) }
    var language by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }
    var showLanguage by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var accountRequest by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var storageText by remember { mutableStateOf("Calculating…") }
    var requestStatus by remember { mutableStateOf("") }

    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-device"
    }
    val deviceName = remember { Build.MANUFACTURER + " " + Build.MODEL }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            val deviceRef = db.collection("users").document(uid)
                .collection("trustedDevices").document(deviceId)
            deviceRef.set(mapOf(
                "deviceName" to deviceName,
                "lastActiveAt" to System.currentTimeMillis(),
                "trusted" to true,
                "platform" to "Android"
            ), com.google.firebase.firestore.SetOptions.merge())
            db.collection("accountRequests").document(uid).get()
                .addOnSuccessListener { requestStatus = it.getString("status").orEmpty() }
            db.collection("users").document(uid).collection("trustedDevices").whereEqualTo("trusted", true).get()
                .addOnSuccessListener { snap ->
                    devices = snap.documents.map { it.id to (it.getString("deviceName") ?: "Android device") }
                }
            db.collection("users").document(uid).collection("loginHistory")
                .orderBy("time", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20).get()
                .addOnSuccessListener { snap ->
                    history = snap.documents.map { doc ->
                        val time = doc.getLong("time") ?: 0L
                        val name = doc.getString("deviceName") ?: "Android"
                        name + " • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
                    }
                }
        }
        val cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val filesBytes = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        storageText = "Cache: " + android.text.format.Formatter.formatShortFileSize(context, cacheBytes) +
            " • App files: " + android.text.format.Formatter.formatShortFileSize(context, filesBytes)
    }

    fun sendAccountRequest(type: String) {
        if (uid.isBlank() || busy) return
        busy = true
        val now = System.currentTimeMillis()
        val requestData = mutableMapOf<String, Any>(
            "uid" to uid,
            "type" to type,
            "status" to "pending",
            "createdAt" to now,
            "email" to (user?.email ?: ""),
            "note" to "User requested from Khahish Android app"
        )
        if (type == "delete") requestData["scheduledFor"] = now + 30L * 24L * 60L * 60L * 1000L
        db.collection("accountRequests").document(uid).set(requestData).addOnSuccessListener {
            requestStatus = "pending"
            busy = false
            Toast.makeText(context, if (type == "delete") "Deletion request saved. Recovery window: 30 days." else "Deactivation request saved.", Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
            busy = false
            Toast.makeText(context, "Request failed: " + (it.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    fun exportData() {
        if (uid.isBlank() || busy) return
        busy = true
        db.collection("users").document(uid).get().addOnSuccessListener { profile ->
            db.collection("chats").whereArrayContains("members", uid).get()
                .addOnSuccessListener { chats ->
                    val chatArray = JSONArray()
                    val messageTasks = chats.documents.map { chatDoc ->
                        chatDoc.reference.collection("messages").get()
                    }
                    com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(messageTasks)
                        .addOnSuccessListener { snapshots ->
                            chats.documents.forEachIndexed { index, chatDoc ->
                                val messages = JSONArray()
                                snapshots.getOrNull(index)?.documents?.forEach { msg ->
                                    val item = JSONObject()
                                    item.put("id", msg.id)
                                    item.put("senderId", msg.getString("senderId") ?: "")
                                    item.put("text", msg.getString("text") ?: "")
                                    item.put("mediaType", msg.getString("mediaType") ?: "text")
                                    item.put("imageUrl", msg.getString("imageUrl") ?: "")
                                    item.put("timestamp", msg.getLong("timestamp") ?: 0L)
                                    messages.put(item)
                                }
                                val chat = JSONObject()
                                chat.put("chatId", chatDoc.id)
                                chat.put("members", JSONArray(chatDoc.get("members") as? List<*> ?: emptyList<Any>()))
                                chat.put("messages", messages)
                                chatArray.put(chat)
                            }
                            val root = JSONObject()
                            root.put("exportedAt", System.currentTimeMillis())
                            root.put("profile", JSONObject().put("uid", uid)
                                .put("email", user?.email ?: "")
                                .put("name", profile.getString("name") ?: ""))
                            root.put("chats", chatArray)
                            busy = false
                            try {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Khahish account data export")
                                    putExtra(Intent.EXTRA_TEXT, root.toString(2))
                                }
                                context.startActivity(Intent.createChooser(share, "Export Khahish data"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export could not be opened: " + e.message, Toast.LENGTH_LONG).show()
                            }
                        }.addOnFailureListener {
                            busy = false
                            Toast.makeText(context, "Could not export messages: " + it.message, Toast.LENGTH_LONG).show()
                        }
                }.addOnFailureListener {
                    busy = false
                    Toast.makeText(context, "Could not load chats: " + it.message, Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener {
            busy = false
            Toast.makeText(context, "Could not load profile: " + it.message, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier.fillMaxSize().background(Pale).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column {
                Text("Advanced settings", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Accessibility, accounts, privacy and data", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        AdvancedGroup("ACCESSIBILITY & EXPERIENCE") {
            AdvancedSwitch("One-handed mode", "Keep important controls easier to reach on smaller screens", oneHanded) {
                oneHanded = it
                prefs.edit().putBoolean("one_handed", it).apply()
                onOneHandedChanged(it)
            }
            AdvancedSwitch("Reduced motion", "Turn off app animations and slide transitions", reducedMotion) {
                reducedMotion = it
                uiPrefs.edit().putBoolean("animations_enabled", !it).putBoolean("slide_transitions_enabled", false).apply()
                onAnimationsChanged(!it)
                onSlideTransitionsChanged(false)
            }
            AdvancedRow("Screen reader support", "Khahish uses labelled Compose controls; enable TalkBack in Android Accessibility settings") {
                try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(context, "Open Android Settings → Accessibility → TalkBack", Toast.LENGTH_LONG).show() }
            }
            AdvancedRow("Language preference", when (language) { "ur" -> "Urdu selected"; "roman" -> "Roman Urdu selected"; else -> "English selected" } + " • Full translation rollout pending") {
                showLanguage = true
            }
        }

        Spacer(Modifier.height(12.dp))
        AdvancedGroup("ACCOUNT MANAGEMENT") {
            AdvancedRow("Deactivate account", "Submit an account deactivation request") {
                accountRequest = "deactivate"
            }
            AdvancedRow("Delete account", "Request deletion with a 30-day recovery window") {
                accountRequest = "delete"
            }
            if (requestStatus == "pending") {
                Text("You have a pending account request.", color = Color(0xFFB26A00), fontSize = 12.sp)
                TextButton(onClick = {
                    db.collection("accountRequests").document(uid).update(
                        mapOf("status" to "cancelled", "cancelledAt" to System.currentTimeMillis())
                    ).addOnSuccessListener {
                        requestStatus = "cancelled"
                        Toast.makeText(context, "Account request cancelled", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Cancel failed: " + it.message, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Cancel pending request") }
            }
        }

        Spacer(Modifier.height(12.dp))
        AdvancedGroup("PRIVACY DASHBOARD & SECURITY") {
            AdvancedRow("Privacy dashboard", "Review the information and visibility preferences on this device") {
                showPrivacy = !showPrivacy
            }
            if (showPrivacy) {
                Text("Online status: " + if (privacyPrefs.getBoolean("online_status_visible", true)) "Visible" else "Hidden", color = Ink)
                Text("Last seen: " + if (privacyPrefs.getBoolean("last_seen_visible", true)) "Visible" else "Hidden", color = Ink)
                Text("Read receipts: " + if (privacyPrefs.getBoolean("read_receipts", true)) "On" else "Off", color = Ink)
                Text("Profile: " + (user?.email ?: "Guest account"), color = Muted, fontSize = 12.sp)
            }
            AdvancedRow("Trusted devices", "Review devices recently used for this account") {
                showDevices = !showDevices
                if (!showDevices && uid.isNotBlank()) db.collection("users").document(uid).collection("trustedDevices").get()
                    .addOnSuccessListener { snap -> devices = snap.documents.map { it.id to (it.getString("deviceName") ?: "Android device") } }
            }
            if (showDevices) {
                devices.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name + if (id == deviceId) " (this device)" else "", color = Ink, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        if (id != deviceId) TextButton(onClick = {
                            db.collection("users").document(uid).collection("trustedDevices").document(id)
                                .update("trusted", false)
                                .addOnSuccessListener {
                                    devices = devices.filterNot { it.first == id }
                                    Toast.makeText(context, "Device access revoked", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { Toast.makeText(context, "Could not revoke device: " + it.message, Toast.LENGTH_LONG).show() }
                        }) { Text("Revoke") }
                    }
                }
            }
            AdvancedRow("Login history", "Show recent app sessions recorded by Khahish") { showHistory = !showHistory }
            if (showHistory) history.forEach { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }

        Spacer(Modifier.height(12.dp))
        AdvancedGroup("YOUR DATA & STORAGE") {
            AdvancedRow("Export my data", "Share a JSON copy of your profile, chats and messages") { exportData() }
            Text(storageText, color = Muted, fontSize = 12.sp)
            AdvancedRow("Refresh storage usage", "Calculate local cache and app-file size") {
                val cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val filesBytes = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                storageText = "Cache: " + android.text.format.Formatter.formatShortFileSize(context, cacheBytes) +
                    " • App files: " + android.text.format.Formatter.formatShortFileSize(context, filesBytes)
            }
            AdvancedRow("Clear temporary cache", "Remove cached files only; account and chats remain") {
                val cleared = context.cacheDir.deleteRecursively()
                storageText = "Temporary cache cleared"
                Toast.makeText(context, if (cleared) "Cache cleared" else "Some cache files could not be removed", Toast.LENGTH_SHORT).show()
            }
            AdvancedRow("Manage app data", "Open Android settings to review permissions, storage and clear app data") {
                try {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    })
                } catch (_: Exception) {
                    Toast.makeText(context, "Open Android Settings → Apps → Khahish", Toast.LENGTH_LONG).show()
                }
            }
            AdvancedRow("Reset app preferences", "Reset local appearance, privacy and accessibility preferences") {
                prefs.edit().clear().apply()
                uiPrefs.edit().clear().apply()
                privacyPrefs.edit().clear().apply()
                oneHanded = false
                reducedMotion = false
                language = "en"
                onAnimationsChanged(true)
                onSlideTransitionsChanged(true)
                Toast.makeText(context, "Local preferences reset. Account data was not deleted.", Toast.LENGTH_LONG).show()
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text("Choose app language") },
            text = {
                Column {
                    listOf("en" to "English", "ur" to "اردو", "roman" to "Roman Urdu").forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth().clickable {
                            language = code
                            prefs.edit().putString("language", code).apply()
                            showLanguage = false
                            Toast.makeText(context, "Language preference saved. Full app translation will be applied as localized strings are added.", Toast.LENGTH_LONG).show()
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = language == code, onClick = {
                                language = code
                                prefs.edit().putString("language", code).apply()
                                showLanguage = false
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text("Close") } }
        )
    }

    if (accountRequest.isNotBlank()) {
        val deleting = accountRequest == "delete"
        AlertDialog(
            onDismissRequest = { accountRequest = "" },
            title = { Text(if (deleting) "Request account deletion?" else "Request deactivation?") },
            text = {
                Text(if (deleting)
                    "This records a deletion request and a 30-day recovery period. Your account is NOT automatically deleted until a trusted server-side deletion worker is configured. You can cancel the request during the recovery period."
                else
                    "This records your deactivation request. Account access is not automatically disabled until a trusted server-side account-management service is configured.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    sendAccountRequest(accountRequest)
                    accountRequest = ""
                }) { Text(if (busy) "Saving…" else "Confirm request") }
            },
            dismissButton = { TextButton(onClick = { accountRequest = "" }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdvancedGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(title, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AdvancedRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .semantics { contentDescription = title + ". " + subtitle }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun AdvancedSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title + ". " + subtitle }
        )
    }
}
