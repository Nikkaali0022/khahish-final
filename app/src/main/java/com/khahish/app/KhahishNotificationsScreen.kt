package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KhahishNotificationsScreen(
    uid: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var notifications by remember(uid) { mutableStateOf(emptyList<DocumentSnapshot>()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf("") }
    var markAllBusy by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        var active = true
        val listener: ListenerRegistration = db.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, failure ->
                if (!active) return@addSnapshotListener
                loading = false
                if (failure != null) {
                    error = failure.localizedMessage ?: "Notifications load nahi ho sakin."
                } else {
                    error = ""
                    notifications = snapshot?.documents.orEmpty()
                }
            }
        onDispose {
            active = false
            listener.remove()
        }
    }

    val unreadCount = notifications.count { it.getBoolean("isRead") != true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF075C4C))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Notifications", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (unreadCount == 0) "Your Khahish inbox" else "$unreadCount unread notification(s)",
                    color = Color(0xFFD9FFF1),
                    fontSize = 11.sp
                )
            }
            if (unreadCount > 0) {
                TextButton(
                    enabled = !markAllBusy,
                    onClick = {
                        markAllBusy = true
                        val batch = db.batch()
                        notifications.filter { it.getBoolean("isRead") != true }.forEach { doc ->
                            batch.update(
                                doc.reference,
                                mapOf("isRead" to true, "readAt" to System.currentTimeMillis())
                            )
                        }
                        batch.commit()
                            .addOnSuccessListener { markAllBusy = false }
                            .addOnFailureListener {
                                markAllBusy = false
                                error = it.localizedMessage ?: "Notifications mark as read nahi ho sakin."
                            }
                    }
                ) {
                    Icon(Icons.Default.DoneAll, null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(if (markAllBusy) "Saving…" else "Read all", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            error.isNotBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            notifications.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, null, tint = Green, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("You're all caught up", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Khahish announcements will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                items(notifications, key = { it.id }) { doc ->
                    val isRead = doc.getBoolean("isRead") == true
                    val title = doc.getString("title") ?: "Khahish notification"
                    val body = doc.getString("body").orEmpty()
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                if (!isRead) {
                                    doc.reference.update(
                                        mapOf(
                                            "isRead" to true,
                                            "readAt" to System.currentTimeMillis()
                                        )
                                    ).addOnFailureListener {
                                        error = it.localizedMessage ?: "Notification update nahi ho saki."
                                    }
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                null,
                                tint = Green,
                                modifier = Modifier.padding(top = 2.dp).size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        title,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!isRead) {
                                        Surface(color = Green, shape = RoundedCornerShape(50)) {
                                            Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text(
                                    if (createdAt > 0L) SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(createdAt)) else "Khahish",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
