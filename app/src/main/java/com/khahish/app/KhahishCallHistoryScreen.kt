package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KhahishCallHistoryScreen(
    uid: String,
    modifier: Modifier = Modifier
) {
    var calls by remember(uid) { mutableStateOf(listOf<KhahishCall>()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var searchQuery by remember(uid) { mutableStateOf("") }
    val filteredCalls = remember(calls, searchQuery) {
        if (searchQuery.isBlank()) calls else calls.filter { call ->
            (if (call.callerUid == uid) call.calleeName else call.callerName)
                .contains(searchQuery, ignoreCase = true)
        }
    }

    DisposableEffect(uid) {
        val registration = KhahishCallRepository().listenForCallHistory(uid) {
            calls = it
            loading = false
        }
        onDispose { registration.remove() }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text("Calls", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            calls.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Call, null, tint = Green, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No calls yet", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    Text("Your voice and video call history will appear here.", color = Muted, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredCalls, key = { it.id }) { call ->
                    val isCaller = call.callerUid == uid
                    val person = if (isCaller) call.calleeName else call.callerName
                    val icon = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call
                    val dateText = if (call.createdAt > 0L) {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(call.createdAt))
                    } else {
                        "Time unavailable"
                    }

                    Row(
                        Modifier.fillMaxWidth()
                             .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = Green, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.ifBlank { "Khahish User" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (isCaller) "Outgoing" else "Incoming") + " • " + call.status.replaceFirstChar { it.uppercase() },
                                color = Muted,
                                fontSize = 12.sp
                            )
                            Text(dateText, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
