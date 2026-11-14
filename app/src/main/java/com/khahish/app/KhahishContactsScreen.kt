package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun KhahishContactsScreen(
    currentUid: String,
    modifier: Modifier = Modifier,
    onOpenChat: (Chat) -> Unit,
    onViewProfile: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var contacts by remember(currentUid) { mutableStateOf(emptyList<Chat>()) }
    var photos by remember(currentUid) { mutableStateOf(emptyMap<String, String>()) }
    var search by remember(currentUid) { mutableStateOf("") }
    var loading by remember(currentUid) { mutableStateOf(true) }

    DisposableEffect(currentUid) {
        val registration = db.collection("chats")
            .whereArrayContains("members", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loading = false
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()

                // Backfill legacy private chats whose privacy Worker has not yet
                // populated visibleToUids. The Worker recalculates visibility for
                // both participants, preserving Secret Locked Chats privacy.
                documents.forEach { doc ->
                    if (doc.getBoolean("isGroup") != true) {
                        val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                        val visibleToUids = (doc.get("visibleToUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                        if (members.size == 2 && currentUid in members && visibleToUids.isEmpty()) {
                            KhahishSecretChatApi.syncChatVisibility(
                                chatId = doc.id,
                                onSuccess = {},
                                onError = {}
                            )
                        }
                    }
                }

                contacts = documents.mapNotNull { doc ->
                    if (doc.getBoolean("isGroup") == true) return@mapNotNull null
                    val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    if (members.size != 2 || currentUid !in members) return@mapNotNull null
                    val peerUid = members.firstOrNull { it != currentUid } ?: return@mapNotNull null
                    val names = doc.get("memberNames") as? Map<*, *>
                    val name = names?.get(peerUid) as? String ?: "Khahish User"
                    Chat(
                        id = doc.id,
                        peerUid = peerUid,
                        name = name,
                        message = doc.getString("lastMessage").orEmpty(),
                        time = "",
                        initials = name.firstOrNull()?.uppercase() ?: "K",
                        isGroup = false,
                        memberUids = members
                    )
                }.distinctBy { it.peerUid }.sortedBy { it.name.lowercase() }
                loading = false
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(contacts.map { it.peerUid }.joinToString("|")) {
        val registrations = contacts.map { contact ->
            db.collection("publicProfiles").document(contact.peerUid)
                .addSnapshotListener { snapshot, _ ->
                    val photo = snapshot?.getString("photoUrl").orEmpty()
                    photos = photos.toMutableMap().apply {
                        if (photo.isBlank()) remove(contact.peerUid) else put(contact.peerUid, photo)
                    }
                }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    val filtered = remember(contacts, search) {
        contacts.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }

    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Contacts", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("People from your existing private chats", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search contacts") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (contacts.isEmpty()) "No contacts yet" else "No matching contacts", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Start a private chat to see a person here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.peerUid }) { contact ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .clickable { onOpenChat(contact) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = photos[contact.peerUid].orEmpty()
                            if (photo.isNotBlank()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = "${contact.name} profile picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(contact.initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("Open conversation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onViewProfile(contact.peerUid) }) {
                            Icon(Icons.Default.Info, contentDescription = "View contact info", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
