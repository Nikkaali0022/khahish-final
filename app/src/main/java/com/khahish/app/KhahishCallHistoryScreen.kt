package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KhahishCallHistoryScreen(
    uid: String,
    modifier: Modifier = Modifier,
    onStartCall: (peerUid: String, peerName: String, isVideo: Boolean) -> Unit
) {
    var calls by remember(uid) { mutableStateOf(listOf<KhahishCall>()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var searchQuery by remember(uid) { mutableStateOf("") }
    var profilePhotos by remember(uid) { mutableStateOf<Map<String, String>>(emptyMap()) }

    val filteredCalls = remember(calls, searchQuery) {
        if (searchQuery.isBlank()) calls else calls.filter { call ->
            val person = if (call.callerUid == uid) call.calleeName else call.callerName
            person.contains(searchQuery, ignoreCase = true)
        }
    }

    DisposableEffect(uid) {
        val registration = KhahishCallRepository().listenForCallHistory(uid) {
            calls = it
            loading = false
        }
        onDispose { registration.remove() }
    }

    DisposableEffect(uid, calls) {
        val db = FirebaseFirestore.getInstance()
        val peerUids = calls.map { if (it.callerUid == uid) it.calleeUid else it.callerUid }
            .filter { it.isNotBlank() }.distinct()
        val registrations = peerUids.map { peerUid ->
            db.collection("publicProfiles").document(peerUid)
                .addSnapshotListener { snapshot, _ ->
                    val photo = snapshot?.getString("photoUrl").orEmpty()
                    profilePhotos = profilePhotos.toMutableMap().apply {
                        if (photo.isBlank()) remove(peerUid) else put(peerUid, photo)
                    }
                }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Calls",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search calls") },
            leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.height(14.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            filteredCalls.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Call, null, tint = Green, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (searchQuery.isBlank()) "No calls yet" else "No matching calls",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Your voice and video call history will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredCalls, key = { it.id }) { call ->
                    val isCaller = call.callerUid == uid
                    val peerUid = if (isCaller) call.calleeUid else call.callerUid
                    val person = if (isCaller) call.calleeName else call.callerName
                    val photo = profilePhotos[peerUid].orEmpty()
                    val missed = call.status == "missed"
                    val directionColor = if (missed) Color(0xFFE05A5A) else if (isCaller) Green else Color(0xFF4B9BE8)
                    val directionIcon = if (isCaller) Icons.Default.CallMade else Icons.Default.CallReceived
                    val dateText = if (call.createdAt > 0L) {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(call.createdAt))
                    } else "Time unavailable"
                    val duration = if (call.acceptedAt > 0L && call.endedAt > call.acceptedAt) {
                        formatCallHistoryDuration((call.endedAt - call.acceptedAt) / 1000L)
                    } else ""

                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCEFE9)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photo.isNotBlank()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = "$person profile picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    person.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = Green,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.ifBlank { "Khahish User" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(directionIcon, null, tint = directionColor, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when {
                                        missed -> "Missed " + (if (isCaller) "outgoing" else "incoming") + " call"
                                        isCaller -> "Outgoing • " + call.status.replaceFirstChar { it.uppercase() }
                                        else -> "Incoming • " + call.status.replaceFirstChar { it.uppercase() }
                                    },
                                    color = directionColor,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                if (duration.isNotBlank()) {
                                    Text("  •  $duration", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                            Text(dateText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { onStartCall(peerUid, person, call.isVideo) },
                            enabled = peerUid.isNotBlank()
                        ) {
                            Icon(
                                if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Call $person",
                                tint = Green
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatCallHistoryDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
