package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Tasks

private data class KhahishAdminSection(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val khahishAdminSections = listOf(
    KhahishAdminSection("Dashboard", "Live platform overview", Icons.Default.Dashboard),
    KhahishAdminSection("Users Management", "Review accounts and apply restrictions", Icons.Default.People),
    KhahishAdminSection("Bans & Restrictions", "Temporary and permanent account bans", Icons.Default.GppBad),
    KhahishAdminSection("Reports & Appeals", "Review user reports and moderation cases", Icons.Default.Flag),
    KhahishAdminSection("Stories Management", "Review, remove or restore reported stories", Icons.Default.Collections),
    KhahishAdminSection("Broadcast Center", "Send announcements to all or one user", Icons.Default.Campaign),
    KhahishAdminSection("In-App Notifications", "Send announcements to private user inboxes", Icons.Default.Notifications),
    KhahishAdminSection("Chat & Groups", "Review group metadata without reading private messages", Icons.Default.Chat),
    KhahishAdminSection("Support Center", "Reply to user complaints and close tickets", Icons.Default.SupportAgent),
    KhahishAdminSection("Analytics", "Live account and content totals", Icons.Default.Analytics),
    KhahishAdminSection("Admin Security", "Verify the active Owner account", Icons.Default.Security),
    KhahishAdminSection("Activity History", "Read-only record of Owner actions", Icons.Default.History),
    KhahishAdminSection("App Settings", "Save global app configuration", Icons.Default.Settings)
)

@Composable
fun KhahishAdminPasswordDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Box(Modifier.size(54.dp).background(Color(0xFFE1F8EF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = Green, modifier = Modifier.size(30.dp))
            }
        },
        title = { Text("Owner authentication", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter your account password. The panel also checks the server-issued Firebase admin permission.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Account password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotBlank(),
                onClick = {
                    val current = user
                    val email = current?.email
                    if (current == null || email.isNullOrBlank()) {
                        error = "Please sign in with your email and password account."
                        return@TextButton
                    }
                    busy = true
                    error = ""
                    current.reauthenticate(EmailAuthProvider.getCredential(email, password))
                        .addOnSuccessListener {
                            current.getIdToken(true)
                                .addOnSuccessListener { token ->
                                    busy = false
                                    if (token.claims["admin"] == true) onUnlocked()
                                    else error = "This account does not have Owner Admin permission."
                                }
                                .addOnFailureListener {
                                    busy = false
                                    error = it.localizedMessage ?: "Admin permission could not be verified."
                                }
                        }
                        .addOnFailureListener {
                            busy = false
                            error = it.localizedMessage ?: "Password verification failed."
                        }
                }
            ) { Text(if (busy) "Verifying…" else "Verify & Open") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun KhahishAdminPanelScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = auth.currentUser?.uid.orEmpty()
    val currentEmail = auth.currentUser?.email.orEmpty()

    var selectedSection by remember { mutableStateOf("Dashboard") }
    var records by remember { mutableStateOf(listOf<DocumentSnapshot>()) }
    var bannedUids by remember { mutableStateOf(setOf<String>()) }
    var metrics by remember { mutableStateOf(mapOf<String, String>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    var banUid by remember { mutableStateOf("") }
    var banName by remember { mutableStateOf("") }
    var banReason by remember { mutableStateOf("Violation of Khahish community rules") }
    var permanentBan by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }

    var composeTitle by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("all") }
    var targetUid by remember { mutableStateOf("") }
    var pendingBroadcastId by remember { mutableStateOf("") }
    var sendingMessage by remember { mutableStateOf(false) }

    var replyTicket by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var replyText by remember { mutableStateOf("") }

    var maintenanceMode by remember { mutableStateOf(false) }
    var maintenanceMessage by remember { mutableStateOf("Khahish is temporarily under maintenance. Please try again soon.") }

    fun audit(action: String, target: String = "", details: String = "") {
        db.collection("adminAuditLogs").add(
            mapOf(
                "actorUid" to currentUid,
                "actorEmail" to currentEmail,
                "action" to action,
                "targetUid" to target,
                "details" to details.take(500),
                "createdAt" to System.currentTimeMillis()
            )
        ).addOnFailureListener { error = "Action saved, but audit log failed: ${it.localizedMessage}" }
    }

    fun refreshRestrictionList() {
        db.collection("accountRestrictions").get()
            .addOnSuccessListener { snapshot -> bannedUids = snapshot.documents.map { it.id }.toSet() }
    }

    DisposableEffect(selectedSection) {
        loading = true
        error = ""
        var listener: ListenerRegistration? = null
        val extraListeners = mutableListOf<ListenerRegistration>()

        fun attach(query: Query) {
            listener = query.addSnapshotListener { snapshot, failure ->
                loading = false
                if (failure != null) {
                    error = failure.localizedMessage ?: "Could not load admin data."
                } else {
                    records = snapshot?.documents.orEmpty()
                }
            }
        }

        when (selectedSection) {
            "Dashboard", "Analytics" -> {
                val profiles = db.collection("publicProfiles").addSnapshotListener { snap, failure ->
                    if (failure == null && snap != null) {
                        metrics = metrics + mapOf(
                            "Total users" to snap.size().toString(),
                            "Online now" to snap.documents.count { it.getBoolean("isOnline") == true }.toString()
                        )
                    } else if (failure != null) error = failure.localizedMessage ?: "User metrics unavailable."
                    loading = false
                }
                val reports = db.collection("reports").addSnapshotListener { snap, failure ->
                    if (failure == null && snap != null) {
                        metrics = metrics + ("Pending reports" to snap.documents.count { it.getString("status") == "pending" }.toString())
                    }
                }
                val tickets = db.collection("supportTickets").addSnapshotListener { snap, failure ->
                    if (failure == null && snap != null) {
                        metrics = metrics + ("Open support tickets" to snap.documents.count { it.getString("status") in listOf("pending", "in_progress") }.toString())
                    }
                }
                val stories = db.collection("stories").addSnapshotListener { snap, failure ->
                    if (failure == null && snap != null) {
                        metrics = metrics + ("Stories" to snap.size().toString())
                    }
                }
                extraListeners.addAll(listOf(profiles, reports, tickets, stories))
            }
            "Users Management" -> {
                attach(db.collection("publicProfiles").orderBy("nameLowercase").limit(150))
                val restrictions = db.collection("accountRestrictions").addSnapshotListener { snap, _ ->
                    if (snap != null) bannedUids = snap.documents.map { it.id }.toSet()
                }
                extraListeners.add(restrictions)
            }
            "Bans & Restrictions" -> attach(db.collection("accountRestrictions").orderBy("createdAt", Query.Direction.DESCENDING).limit(150))
            "Reports & Appeals" -> attach(db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).limit(150))
            "Stories Management" -> attach(db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING).limit(150))
            "Broadcast Center", "In-App Notifications" -> attach(db.collection("adminBroadcasts").orderBy("createdAt", Query.Direction.DESCENDING).limit(100))
            "Chat & Groups" -> attach(db.collection("chats").orderBy("updatedAt", Query.Direction.DESCENDING).limit(150))
            "Support Center" -> attach(db.collection("supportTickets").orderBy("updatedAt", Query.Direction.DESCENDING).limit(150))
            "Activity History" -> attach(db.collection("adminAuditLogs").orderBy("createdAt", Query.Direction.DESCENDING).limit(150))
            "App Settings" -> {
                val config = db.collection("appConfig").document("global").addSnapshotListener { snap, failure ->
                    loading = false
                    if (failure != null) error = failure.localizedMessage ?: "Could not load app settings."
                    else if (snap != null && snap.exists()) {
                        maintenanceMode = snap.getBoolean("maintenanceMode") == true
                        maintenanceMessage = snap.getString("maintenanceMessage") ?: maintenanceMessage
                    }
                }
                extraListeners.add(config)
            }
            else -> loading = false
        }

        onDispose {
            listener?.remove()
            extraListeners.forEach { it.remove() }
        }
    }

    fun applyBan(uid: String, name: String) {
        if (uid.isBlank() || uid == currentUid) {
            error = "You cannot restrict the active Owner account."
            return
        }
        val now = System.currentTimeMillis()
        val expiry = if (permanentBan) 0L else now + 7L * 24L * 60L * 60L * 1000L
        db.collection("accountRestrictions").document(uid).set(
            mapOf(
                "uid" to uid,
                "displayName" to name,
                "reason" to banReason.trim().ifBlank { "Community guidelines violation" },
                "permanent" to permanentBan,
                "expiresAt" to expiry,
                "createdAt" to now,
                "updatedAt" to now,
                "createdBy" to currentUid
            )
        ).addOnSuccessListener {
            bannedUids = bannedUids + uid
            audit(if (permanentBan) "user_permanently_banned" else "user_temporarily_banned", uid, banReason)
            notice = if (permanentBan) "Permanent restriction applied." else "7-day restriction applied."
            showBanDialog = false
        }.addOnFailureListener { error = it.localizedMessage ?: "Could not apply restriction." }
    }

    fun removeBan(uid: String) {
        db.collection("accountRestrictions").document(uid).delete()
            .addOnSuccessListener {
                bannedUids = bannedUids - uid
                audit("user_unbanned", uid, "Restriction removed by Owner")
                notice = "Account restriction removed."
            }
            .addOnFailureListener { error = it.localizedMessage ?: "Could not remove restriction." }
    }

    fun updateReport(doc: DocumentSnapshot, status: String) {
        doc.reference.update(
            mapOf(
                "status" to status,
                "reviewedAt" to System.currentTimeMillis(),
                "reviewedBy" to currentUid
            )
        ).addOnSuccessListener {
            audit("report_${status}", doc.getString("targetUid").orEmpty(), "Report ${doc.id}")
            notice = "Report marked ${status}."
        }.addOnFailureListener { error = it.localizedMessage ?: "Could not update report." }
    }

    fun moderateStory(doc: DocumentSnapshot, removed: Boolean) {
        val status = if (removed) "removed" else "active"
        doc.reference.update(
            mapOf(
                "moderationStatus" to status,
                "moderationReason" to if (removed) "Removed by Khahish Owner" else "",
                "moderatedAt" to System.currentTimeMillis(),
                "moderatedBy" to currentUid
            )
        ).addOnSuccessListener {
            audit(if (removed) "story_removed" else "story_restored", doc.getString("ownerUid").orEmpty(), "Story ${doc.id}")
            notice = if (removed) "Story removed from viewer feeds." else "Story restored."
        }.addOnFailureListener { error = it.localizedMessage ?: "Could not moderate story." }
    }

    fun sendAdminMessage(kind: String) {
        val title = composeTitle.trim()
        val body = composeBody.trim()
        if (title.isBlank() || body.isBlank()) {
            error = "Title aur message dono likhein."
            return
        }
        if (targetType == "user" && targetUid.trim().isBlank()) {
            error = "Specific user ke liye UID zaroor likhein."
            return
        }
        if (sendingMessage) return

        sendingMessage = true
        error = ""
        val broadcastId = pendingBroadcastId.ifBlank {
            db.collection("adminBroadcasts").document().id.also { pendingBroadcastId = it }
        }
        val createdAt = System.currentTimeMillis()

        fun deliverToRecipients(recipientUids: List<String>) {
            val recipients = recipientUids.distinct().filter { it.isNotBlank() }
            if (recipients.isEmpty()) {
                sendingMessage = false
                error = "Is audience ke liye koi Khahish user nahi mila."
                return
            }

            val commits = recipients.chunked(450).map { chunk ->
                val batch = db.batch()
                chunk.forEach { recipientUid ->
                    val inboxRef = db.collection("users").document(recipientUid)
                        .collection("notifications").document(broadcastId)
                    batch.set(
                        inboxRef,
                        mapOf(
                            "broadcastId" to broadcastId,
                            "recipientUid" to recipientUid,
                            "title" to title,
                            "body" to body,
                            "kind" to kind,
                            "createdBy" to currentUid,
                            "createdAt" to createdAt,
                            "isRead" to false,
                            "readAt" to 0L,
                            "deliveryMode" to "in_app"
                        )
                    )
                }
                batch.commit()
            }

            Tasks.whenAll(commits).addOnSuccessListener {
                val history = mapOf(
                    "title" to title,
                    "body" to body,
                    "kind" to kind,
                    "targetType" to targetType,
                    "targetUid" to if (targetType == "user") recipients.first() else "",
                    "createdBy" to currentUid,
                    "createdAt" to createdAt,
                    "deliveryMode" to "in_app",
                    "dispatchStatus" to "delivered",
                    "recipientCount" to recipients.size
                )
                db.collection("adminBroadcasts").document(broadcastId).set(history)
                    .addOnSuccessListener {
                        audit(
                            if (kind == "notification") "in_app_notification_sent" else "announcement_sent",
                            if (targetType == "user") recipients.first() else "",
                            "$title • ${recipients.size} inboxes"
                        )
                        composeTitle = ""
                        composeBody = ""
                        pendingBroadcastId = ""
                        sendingMessage = false
                        notice = "Delivered to ${recipients.size} private inbox(es). Users will see it when they open Khahish."
                    }
                    .addOnFailureListener {
                        sendingMessage = false
                        error = "Inbox messages delivered, but broadcast history could not be saved: ${it.localizedMessage}"
                    }
            }.addOnFailureListener {
                sendingMessage = false
                error = "Some inbox writes failed. Tap Send again to safely retry this same broadcast: ${it.localizedMessage}"
            }
        }

        if (targetType == "user") {
            val recipientUid = targetUid.trim()
            db.collection("publicProfiles").document(recipientUid).get()
                .addOnSuccessListener { profile ->
                    if (!profile.exists()) {
                        sendingMessage = false
                        error = "Target UID ka Khahish profile nahi mila."
                    } else {
                        deliverToRecipients(listOf(recipientUid))
                    }
                }
                .addOnFailureListener {
                    sendingMessage = false
                    error = it.localizedMessage ?: "Target user verify nahi ho saka."
                }
        } else {
            db.collection("publicProfiles").get()
                .addOnSuccessListener { profiles ->
                    deliverToRecipients(profiles.documents.map { it.id })
                }
                .addOnFailureListener {
                    sendingMessage = false
                    error = it.localizedMessage ?: "User list load nahi ho saki."
                }
        }
    }

    fun replyToTicket(doc: DocumentSnapshot) {
        val response = replyText.trim()
        if (response.isBlank()) {
            error = "Reply likhein."
            return
        }
        doc.reference.update(
            mapOf(
                "adminReply" to response.take(2000),
                "status" to "resolved",
                "updatedAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            audit("support_ticket_replied", doc.getString("userUid").orEmpty(), "Ticket ${doc.id}")
            notice = "Support reply saved."
            replyTicket = null
            replyText = ""
        }.addOnFailureListener { error = it.localizedMessage ?: "Could not send support reply." }
    }

    fun saveMaintenanceSettings(enabled: Boolean) {
        maintenanceMode = enabled
        db.collection("appConfig").document("global").set(
            mapOf(
                "maintenanceMode" to enabled,
                "maintenanceMessage" to maintenanceMessage,
                "updatedAt" to System.currentTimeMillis(),
                "updatedBy" to currentUid
            )
        ).addOnSuccessListener {
            audit("maintenance_mode_changed", "", enabled.toString())
            notice = "Global setting saved. Maintenance mode applies to non-owner signed-in users immediately."
        }.addOnFailureListener { error = it.localizedMessage ?: "Could not save setting." }
    }

    val selected = khahishAdminSections.first { it.title == selectedSection }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF075C4E), Color(0xFF079B78), Color(0xFF18A889))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            ).statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("KHAHISH ADMIN", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Owner Control Center", color = Color(0xFFD9FFF1), fontSize = 11.sp)
                    }
                    Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("OWNER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Welcome back, ${auth.currentUser?.displayName ?: "Owner"}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Secure community controls and live Firebase data.", color = Color(0xFFE5FFF6), fontSize = 12.sp)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (notice.isNotBlank()) {
                Surface(color = Color(0xFFE6F8EF), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Green)
                        Spacer(Modifier.width(8.dp))
                        Text(notice, color = Color(0xFF075C4E), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { notice = "" }) { Text("OK") }
                    }
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(selected.icon, null, tint = Green, modifier = Modifier.size(25.dp))
                Column(Modifier.weight(1f)) {
                    Text(selected.title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(selected.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                IconButton(onClick = { refreshRestrictionList() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }

            when (selectedSection) {
                "Dashboard", "Analytics" -> {
                    val labels = if (selectedSection == "Dashboard") {
                        listOf("Total users", "Online now", "Pending reports", "Open support tickets")
                    } else {
                        listOf("Total users", "Online now", "Pending reports", "Open support tickets", "Stories")
                    }
                    labels.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { label ->
                                AdminMetricCard(label, metrics[label] ?: "…", Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("Live data updates automatically while this screen is open.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                "Users Management" -> {
                    if (records.isEmpty() && !loading) Text("No public profiles found.")
                    records.forEach { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val name = doc.getString("name") ?: "Khahish User"
                        AdminRecordCard(
                            title = name,
                            subtitle = "@${doc.getString("username").orEmpty()} • ${uid.take(12)}",
                            trailing = if (uid in bannedUids) "Unban" else "Ban",
                            onAction = {
                                if (uid in bannedUids) removeBan(uid)
                                else {
                                    banUid = uid
                                    banName = name
                                    banReason = "Violation of Khahish community rules"
                                    permanentBan = false
                                    showBanDialog = true
                                }
                            }
                        )
                    }
                }

                "Bans & Restrictions" -> {
                    if (records.isEmpty() && !loading) Text("No active restrictions.")
                    records.forEach { doc ->
                        AdminRecordCard(
                            title = doc.getString("displayName") ?: doc.getString("uid") ?: doc.id,
                            subtitle = "${doc.getString("reason").orEmpty()} • ${if (doc.getBoolean("permanent") == true) "Permanent" else "Temporary"}",
                            trailing = "Unban",
                            onAction = { removeBan(doc.id) }
                        )
                    }
                }

                "Reports & Appeals" -> {
                    if (records.isEmpty() && !loading) Text("No reports found.")
                    records.forEach { doc ->
                        AdminRecordCard(
                            title = "${doc.getString("targetName") ?: "Reported user"} • ${doc.getString("status") ?: "pending"}",
                            subtitle = "${doc.getString("reason").orEmpty()} • Reporter: ${doc.getString("reporterUid").orEmpty().take(12)}",
                            trailing = "Resolve",
                            onAction = { updateReport(doc, "resolved") },
                            secondaryAction = { updateReport(doc, "dismissed") },
                            secondaryLabel = "Dismiss"
                        )
                    }
                }

                "Stories Management" -> {
                    if (records.isEmpty() && !loading) Text("No stories found.")
                    records.forEach { doc ->
                        val removed = doc.getString("moderationStatus") == "removed"
                        AdminRecordCard(
                            title = doc.getString("ownerName") ?: doc.getString("ownerUid") ?: "Story",
                            subtitle = "${doc.getString("text").orEmpty().take(110)} • ${doc.getString("mediaType").orEmpty()}",
                            trailing = if (removed) "Restore" else "Remove",
                            onAction = { moderateStory(doc, !removed) }
                        )
                    }
                }

                "Broadcast Center", "In-App Notifications" -> {
                    Text("Target audience", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = targetType == "all", onClick = { targetType = "all" })
                        Text("All users", modifier = Modifier.clickable { targetType = "all" })
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = targetType == "user", onClick = { targetType = "user" })
                        Text("One user", modifier = Modifier.clickable { targetType = "user" })
                    }
                    if (targetType == "user") {
                        OutlinedTextField(
                            value = targetUid,
                            onValueChange = { targetUid = it },
                            label = { Text("Target user UID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = composeTitle,
                        onValueChange = { composeTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = composeBody,
                        onValueChange = { composeBody = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = { sendAdminMessage(if (selectedSection == "In-App Notifications") "notification" else "announcement") },
                        enabled = !sendingMessage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (sendingMessage) "Sending…" else "Send in-app ${if (selectedSection == "In-App Notifications") "notification" else "announcement"}")
                    }
                    Text("Recent inbox deliveries", fontWeight = FontWeight.SemiBold)
                    records.forEach { doc ->
                        AdminRecordCard(
                            title = doc.getString("title") ?: "Admin message",
                            subtitle = "${doc.getString("dispatchStatus") ?: "legacy record"} • ${doc.getString("deliveryMode") ?: "legacy"} • Inbox recipients: ${doc.getLong("recipientCount") ?: 0}",
                            trailing = doc.getString("kind") ?: "view",
                            onAction = { notice = "In-app delivery status: ${doc.getString("dispatchStatus") ?: "legacy record"} • Recipients: ${doc.getLong("recipientCount") ?: 0}" }
                        )
                    }
                }

                "Chat & Groups" -> {
                    Text("Group metadata only. Private message contents are not exposed in this moderation view.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    records.filter { it.getBoolean("isGroup") == true }.forEach { doc ->
                        val members = (doc.get("members") as? List<*>)?.size ?: 0
                        AdminRecordCard(
                            title = doc.getString("groupName") ?: "Khahish group",
                            subtitle = "$members members • ${doc.getString("createdBy").orEmpty().take(12)}",
                            trailing = "Details",
                            onAction = { notice = "Group ID: ${doc.id} • Members: $members" }
                        )
                    }
                }

                "Support Center" -> {
                    if (records.isEmpty() && !loading) Text("No support tickets found.")
                    records.forEach { doc ->
                        AdminRecordCard(
                            title = doc.getString("subject") ?: "Support request",
                            subtitle = "${doc.getString("status") ?: "pending"} • ${doc.getString("userEmail").orEmpty()} • ${doc.getString("message").orEmpty().take(100)}",
                            trailing = "Reply",
                            onAction = {
                                replyTicket = doc
                                replyText = doc.getString("adminReply").orEmpty()
                            }
                        )
                    }
                }

                "Admin Security" -> {
                    AdminRecordCard(
                        title = "Signed in as Owner",
                        subtitle = currentEmail,
                        trailing = "Verified",
                        onAction = { notice = "Admin access is checked using the Firebase admin custom claim." }
                    )
                    Text("This build does not create additional admins or moderators. Only accounts with the server-issued admin claim can access owner data.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                "Activity History" -> {
                    if (records.isEmpty() && !loading) Text("No admin actions recorded yet.")
                    records.forEach { doc ->
                        AdminRecordCard(
                            title = doc.getString("action") ?: "Admin action",
                            subtitle = "${doc.getString("details").orEmpty()} • ${doc.getString("actorEmail").orEmpty()} • ${doc.getLong("createdAt") ?: 0}",
                            trailing = "Log",
                            onAction = { notice = "Audit entry ${doc.id}" }
                        )
                    }
                }

                "App Settings" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Maintenance mode", fontWeight = FontWeight.SemiBold)
                            Text("Immediately blocks non-owner app access while maintenance is enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Switch(checked = maintenanceMode, onCheckedChange = { saveMaintenanceSettings(it) })
                    }
                    OutlinedTextField(
                        value = maintenanceMessage,
                        onValueChange = { maintenanceMessage = it },
                        label = { Text("Maintenance message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Button(onClick = { saveMaintenanceSettings(maintenanceMode) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save app settings")
                    }
                    Text("The Owner account keeps access to the app and Admin Panel while maintenance mode is enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            Divider()
            Text("Admin modules", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            khahishAdminSections.forEach { section ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selectedSection == section.title) Color(0xFFE4F8EF) else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedSection = section.title }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(section.icon, null, tint = if (selectedSection == section.title) Green else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(section.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(section.subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text("Restrict $banName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = banReason,
                        onValueChange = { banReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permanentBan, onCheckedChange = { permanentBan = it })
                        Text("Permanent restriction")
                    }
                    if (!permanentBan) Text("Temporary ban duration: 7 days.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { applyBan(banUid, banName) }) { Text("Apply restriction") } },
            dismissButton = { TextButton(onClick = { showBanDialog = false }) { Text("Cancel") } }
        )
    }

    replyTicket?.let { ticket ->
        AlertDialog(
            onDismissRequest = { replyTicket = null },
            title = { Text("Reply to ${ticket.getString("subject") ?: "support ticket"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ticket.getString("message").orEmpty(), fontSize = 12.sp)
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Admin reply") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = { TextButton(onClick = { replyToTicket(ticket) }) { Text("Send reply") } },
            dismissButton = { TextButton(onClick = { replyTicket = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Default.Analytics, null, tint = Green, modifier = Modifier.size(21.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminRecordCard(
    title: String,
    subtitle: String,
    trailing: String,
    onAction: () -> Unit,
    secondaryAction: (() -> Unit)? = null,
    secondaryLabel: String = ""
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAction) { Text(trailing) }
                if (secondaryAction != null) TextButton(onClick = secondaryAction) { Text(secondaryLabel) }
            }
        }
    }
}
