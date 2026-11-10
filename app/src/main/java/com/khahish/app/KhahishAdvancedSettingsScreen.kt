package com.khahish.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.io.File

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
    var pendingBackupJson by remember { mutableStateOf("") }

    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-device"
    }
    val deviceName = remember { Build.MANUFACTURER + " " + Build.MODEL }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingBackupJson.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pendingBackupJson.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "Khahish backup saved.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Backup save failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && uid.isNotBlank() && !busy) {
            busy = true
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read backup file")
                val root = JSONObject(jsonText)
                val backupUid = root.optJSONObject("profile")?.optString("uid").orEmpty()
                if (backupUid != uid) {
                    busy = false
                    Toast.makeText(context, "This backup belongs to a different Khahish account.", Toast.LENGTH_LONG).show()
                } else {
                    val chats = root.optJSONArray("chats") ?: JSONArray()
                    val writeTasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()
                    for (index in 0 until chats.length()) {
                        val chatJson = chats.optJSONObject(index) ?: continue
                        val chatId = chatJson.optString("chatId")
                        val memberArray = chatJson.optJSONArray("members") ?: JSONArray()
                        val members = (0 until memberArray.length()).mapNotNull { memberArray.optString(it).takeIf(String::isNotBlank) }
                        if (chatId.isBlank() || uid !in members) continue
                        val chatRef = db.collection("chats").document(chatId)
                        val chatData = hashMapOf<String, Any>(
                            "members" to members,
                            "lastMessage" to chatJson.optString("lastMessage"),
                            "lastMessageAt" to chatJson.optLong("lastMessageAt"),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        if (chatJson.has("isGroup")) chatData["isGroup"] = chatJson.optBoolean("isGroup")
                        if (chatJson.has("groupName")) chatData["groupName"] = chatJson.optString("groupName")
                        val memberNamesJson = chatJson.optJSONObject("memberNames")
                        if (memberNamesJson != null) {
                            val memberNames = mutableMapOf<String, String>()
                            memberNamesJson.keys().forEach { key -> memberNames[key] = memberNamesJson.optString(key) }
                            chatData["memberNames"] = memberNames
                        }
                        val adminsJson = chatJson.optJSONArray("groupAdmins") ?: JSONArray()
                        chatData["groupAdmins"] = (0 until adminsJson.length()).map { adminsJson.optString(it) }
                        if (chatJson.has("createdBy")) chatData["createdBy"] = chatJson.optString("createdBy")
                        if (chatJson.has("createdAt")) chatData["createdAt"] = chatJson.optLong("createdAt")
                        if (chatJson.has("disappearingDurationMs")) chatData["disappearingDurationMs"] = chatJson.optLong("disappearingDurationMs")
                        listOf("pinnedMessageId", "pinnedMessageText", "pinnedBy").forEach { key ->
                            if (chatJson.has(key)) chatData[key] = chatJson.optString(key)
                        }
                        if (chatJson.has("pinnedAt")) chatData["pinnedAt"] = chatJson.optLong("pinnedAt")
                        writeTasks.add(chatRef.set(chatData, com.google.firebase.firestore.SetOptions.merge()))
                        val messageArray = chatJson.optJSONArray("messages") ?: JSONArray()
                        var batch = db.batch()
                        var batchCount = 0
                        for (messageIndex in 0 until messageArray.length()) {
                            val message = messageArray.optJSONObject(messageIndex) ?: continue
                            val sender = message.optString("senderId")
                            if (sender !in members) continue
                            val messageData = hashMapOf<String, Any>(
                                "senderId" to sender,
                                "text" to message.optString("text"),
                                "imageUrl" to message.optString("imageUrl"),
                                "mediaType" to message.optString("mediaType", "text"),
                                "timestamp" to message.optLong("timestamp"),
                                "deliveredTo" to (message.optJSONArray("deliveredTo")?.let { array -> (0 until array.length()).map { array.optString(it) } } ?: emptyList<String>()),
                                "readBy" to (message.optJSONArray("readBy")?.let { array -> (0 until array.length()).map { array.optString(it) } } ?: listOf(sender)),
                                "hiddenFor" to (message.optJSONArray("hiddenFor")?.let { array -> (0 until array.length()).map { array.optString(it) } } ?: emptyList<String>()),
                                "expiresAt" to message.optLong("expiresAt"),
                                "replyToMessageId" to message.optString("replyToMessageId"),
                                "replyToText" to message.optString("replyToText"),
                                "editedAt" to message.optLong("editedAt"),
                                "starredBy" to (message.optJSONArray("starredBy")?.let { array -> (0 until array.length()).map { array.optString(it) } } ?: emptyList<String>())
                            )
                            val reactionsJson = message.optJSONObject("reactions")
                            if (reactionsJson != null) {
                                val reactions = mutableMapOf<String, String>()
                                reactionsJson.keys().forEach { key -> reactions[key] = reactionsJson.optString(key) }
                                if (reactions.isNotEmpty()) messageData["reactions"] = reactions
                            }
                            val messageId = message.optString("id")
                            val messageRef = if (messageId.isBlank()) chatRef.collection("messages").document() else chatRef.collection("messages").document(messageId)
                            batch.set(messageRef, messageData, com.google.firebase.firestore.SetOptions.merge())
                            batchCount++
                            if (batchCount >= 400) {
                                writeTasks.add(batch.commit())
                                batch = db.batch()
                                batchCount = 0
                            }
                        }
                        if (batchCount > 0) writeTasks.add(batch.commit())
                    }
                    com.google.android.gms.tasks.Tasks.whenAllComplete(writeTasks).addOnSuccessListener { completed ->
                        busy = false
                        val failed = completed.count { !it.isSuccessful }
                        Toast.makeText(context, if (failed == 0) "Backup restored. Existing messages were merged." else "Restore finished with $failed failed write batches.", Toast.LENGTH_LONG).show()
                    }.addOnFailureListener { error ->
                        busy = false
                        Toast.makeText(context, "Restore failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                busy = false
                Toast.makeText(context, "Invalid backup file: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
        storageText = calculateKhahishStorageBreakdown(context)
    }

    fun sendAccountRequest() {
        if (uid.isBlank() || busy) return
        busy = true
        val now = System.currentTimeMillis()
        val requestData = mutableMapOf<String, Any>(
            "uid" to uid,
            "type" to "deactivate",
            "status" to "pending",
            "createdAt" to now,
            "email" to (user?.email ?: ""),
            "note" to "User requested from Khahish Android app"
        )
        db.collection("accountRequests").document(uid).set(requestData).addOnSuccessListener {
            requestStatus = "pending"
            busy = false
            Toast.makeText(context, "Deactivation request saved. Backend enforcement will be completed in the backend phase.", Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
            busy = false
            Toast.makeText(context, "Request failed: " + (it.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    fun createChatBackup() {
        if (uid.isBlank() || busy) return
        busy = true
        db.collection("users").document(uid).get().addOnSuccessListener { profile ->
            db.collection("chats").whereArrayContains("members", uid).get().addOnSuccessListener { chats ->
                val tasks = chats.documents.map { it.reference.collection("messages").get() }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(tasks)
                    .addOnSuccessListener { snapshots ->
                        val root = JSONObject()
                        root.put("backupVersion", 1)
                        root.put("exportedAt", System.currentTimeMillis())
                        root.put("profile", JSONObject().put("uid", uid).put("email", user?.email ?: "").put("name", profile.getString("name") ?: ""))
                        val chatArray = JSONArray()
                        chats.documents.forEachIndexed { index, chatDoc ->
                            val chatJson = JSONObject()
                                .put("chatId", chatDoc.id)
                                .put("members", JSONArray(chatDoc.get("members") as? List<*> ?: emptyList<Any>()))
                                .put("lastMessage", chatDoc.getString("lastMessage") ?: "")
                                .put("lastMessageAt", chatDoc.getLong("lastMessageAt") ?: 0L)
                                .put("isGroup", chatDoc.getBoolean("isGroup") == true)
                                .put("groupName", chatDoc.getString("groupName") ?: "")
                                .put("memberNames", JSONObject(chatDoc.get("memberNames") as? Map<*, *> ?: emptyMap<Any, Any>()))
                                .put("groupAdmins", JSONArray(chatDoc.get("groupAdmins") as? List<*> ?: emptyList<Any>()))
                                .put("createdBy", chatDoc.getString("createdBy") ?: "")
                                .put("createdAt", chatDoc.getLong("createdAt") ?: 0L)
                                .put("disappearingDurationMs", chatDoc.getLong("disappearingDurationMs") ?: 0L)
                                .put("pinnedMessageId", chatDoc.getString("pinnedMessageId") ?: "")
                                .put("pinnedMessageText", chatDoc.getString("pinnedMessageText") ?: "")
                                .put("pinnedBy", chatDoc.getString("pinnedBy") ?: "")
                                .put("pinnedAt", chatDoc.getLong("pinnedAt") ?: 0L)
                            val messageArray = JSONArray()
                            snapshots.getOrNull(index)?.documents?.forEach { msg ->
                                messageArray.put(JSONObject()
                                    .put("id", msg.id)
                                    .put("senderId", msg.getString("senderId") ?: "")
                                    .put("text", msg.getString("text") ?: "")
                                    .put("mediaType", msg.getString("mediaType") ?: "text")
                                    .put("imageUrl", msg.getString("imageUrl") ?: "")
                                    .put("timestamp", msg.getLong("timestamp") ?: 0L)
                                    .put("deliveredTo", JSONArray(msg.get("deliveredTo") as? List<*> ?: emptyList<Any>()))
                                    .put("readBy", JSONArray(msg.get("readBy") as? List<*> ?: emptyList<Any>()))
                                    .put("hiddenFor", JSONArray(msg.get("hiddenFor") as? List<*> ?: emptyList<Any>()))
                                    .put("expiresAt", msg.getLong("expiresAt") ?: 0L)
                                    .put("replyToMessageId", msg.getString("replyToMessageId") ?: "")
                                    .put("replyToText", msg.getString("replyToText") ?: "")
                                    .put("editedAt", msg.getLong("editedAt") ?: 0L)
                                    .put("starredBy", JSONArray(msg.get("starredBy") as? List<*> ?: emptyList<Any>()))
                                    .put("reactions", JSONObject(msg.get("reactions") as? Map<*, *> ?: emptyMap<Any, Any>())))
                            }
                            chatJson.put("messages", messageArray)
                            chatArray.put(chatJson)
                        }
                        root.put("chats", chatArray)
                        pendingBackupJson = root.toString(2)
                        busy = false
                        backupSaveLauncher.launch("Khahish-backup-${System.currentTimeMillis()}.json")
                    }.addOnFailureListener {
                        busy = false
                        Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }.addOnFailureListener {
                busy = false
                Toast.makeText(context, "Could not load chats: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            busy = false
            Toast.makeText(context, "Could not load profile: ${it.message}", Toast.LENGTH_LONG).show()
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
                uiPrefs.edit().putBoolean("animations_enabled", !it).putBoolean("slide_transitions_enabled", !it).apply()
                onAnimationsChanged(!it)
                onSlideTransitionsChanged(!it)
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
                                    Toast.makeText(context, "Device removed. Its Khahish session will sign out when it reconnects.", Toast.LENGTH_LONG).show()
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
            AdvancedRow("Back up chats", "Save a restorable JSON backup to your device") { createChatBackup() }
            AdvancedRow("Restore chats", "Merge messages from a backup for this same account") {
                backupRestoreLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
            }
            Text(storageText, color = Muted, fontSize = 12.sp)
            AdvancedRow("Refresh storage usage", "Break down cache, files, databases and preferences") {
                storageText = calculateKhahishStorageBreakdown(context)
            }
            AdvancedRow("Clear temporary cache", "Remove cached files only; account and chats remain") {
                val cleared = context.cacheDir.deleteRecursively()
                storageText = calculateKhahishStorageBreakdown(context)
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
        AlertDialog(
            onDismissRequest = { accountRequest = "" },
            title = { Text("Request deactivation?") },
            text = {
                Text("This records your deactivation request. Account access is not automatically disabled until a trusted server-side account-management service is configured.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    sendAccountRequest()
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
            onCheckedChange = onChange
        )
    }
}

private fun calculateKhahishStorageBreakdown(context: Context): String {
    val cacheBytes = directoryBytes(context.cacheDir)
    val filesBytes = directoryBytes(context.filesDir)
    val databaseBytes = context.databaseList().sumOf { context.getDatabasePath(it).length() }
    val preferencesBytes = directoryBytes(File(context.applicationInfo.dataDir, "shared_prefs"))
    val totalBytes = cacheBytes + filesBytes + databaseBytes + preferencesBytes
    fun size(value: Long) = android.text.format.Formatter.formatShortFileSize(context, value)
    return "Cache: ${size(cacheBytes)}\\nApp files: ${size(filesBytes)}\\nDatabases: ${size(databaseBytes)}\\nPreferences: ${size(preferencesBytes)}\\nTotal measured: ${size(totalBytes)}"
}

private fun directoryBytes(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
}
