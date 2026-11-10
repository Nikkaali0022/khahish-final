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
    var showNewCallDialog by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var searchingUser by remember { mutableStateOf(false) }
    var userLookupError by remember { mutableStateOf("") }
    var callTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

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
    fun findCallUser() {
        val username = normalizeKhahishUsername(usernameInput)
        if (!isValidKhahishUsername(username)) {
            userLookupError = "Enter a valid username (3–20 letters, numbers or underscores)."
            callTarget = null
            return
        }
        searchingUser = true
        userLookupError = ""
        callTarget = null
        FirebaseFirestore.getInstance().collection("usernames").document(username).get()
            .addOnSuccessListener { claim ->
                val targetUid = claim.getString("uid").orEmpty()
                if (targetUid.isBlank() || targetUid == uid) {
                    searchingUser = false
                    userLookupError = if (targetUid == uid) "You cannot call yourself." else "No account found with this username."
                } else {
                    FirebaseFirestore.getInstance().collection("publicProfiles").document(targetUid).get()
                        .addOnSuccessListener { profile ->
                            searchingUser = false
                            if (!profile.exists()) userLookupError = "This profile is not available."
                            else callTarget = targetUid to (profile.getString("name") ?: "Khahish User")
                        }
                        .addOnFailureListener {
                            searchingUser = false
                            userLookupError = it.message ?: "Could not load this profile."
                        }
                }
            }
            .addOnFailureListener {
                searchingUser = false
                userLookupError = it.message ?: "Username search failed."
            }
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

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        Modifier.fillMaxSize().padding(16.dp)
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

    FloatingActionButton(
        onClick = { showNewCallDialog = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        containerColor = Green,
        contentColor = Color.White
    ) {
        Icon(Icons.Default.Call, contentDescription = "New call")
    }

    if (showNewCallDialog) {
        AlertDialog(
            onDismissRequest = { showNewCallDialog = false },
            title = { Text("New call") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it; callTarget = null; userLookupError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Khahish username") },
                        placeholder = { Text("@username") }
                    )
                    Button(
                        onClick = { findCallUser() },
                        enabled = !searchingUser && usernameInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (searchingUser) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Find user")
                    }
                    if (userLookupError.isNotBlank()) {
                        Text(userLookupError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    callTarget?.let { target ->
                        Text("Call ${target.second} (@${normalizeKhahishUsername(usernameInput)})", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    showNewCallDialog = false
                                    onStartCall(target.first, target.second, false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Call, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Voice")
                            }
                            OutlinedButton(
                                onClick = {
                                    showNewCallDialog = false
                                    onStartCall(target.first, target.second, true)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Videocam, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Video")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNewCallDialog = false }) { Text("Close") } }
        )
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
