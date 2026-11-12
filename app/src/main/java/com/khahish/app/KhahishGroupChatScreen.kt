package com.khahish.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

@Composable
fun KhahishGroupChatScreen(
    currentUid: String,
    onBack: () -> Unit,
    onCreated: (Chat) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val currentName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Khahish User"
    var groupName by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(emptyList<AppUser>()) }
    var selected by remember { mutableStateOf(emptyMap<String, AppUser>()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(search) {
        val term = normalizeKhahishUsername(search)
        if (term.length < 3 || !isValidKhahishUsername(term)) {
            users = emptyList()
            loading = false
        } else {
            loading = true
            db.collection("usernames").document(term).get().addOnSuccessListener { claim ->
                val targetUid = claim.getString("uid").orEmpty()
                if (targetUid.isBlank() || targetUid == currentUid) {
                    users = emptyList()
                    loading = false
                } else db.collection("publicProfiles").document(targetUid).get().addOnSuccessListener { profile ->
                    users = listOfNotNull(profile.toObject(AppUser::class.java)?.takeIf { it.uid == targetUid })
                    loading = false
                }.addOnFailureListener {
                    users = emptyList(); loading = false
                    Toast.makeText(context, "Profile lookup failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                users = emptyList(); loading = false
                Toast.makeText(context, "Username lookup failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("New group", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (selected.isNotEmpty()) Text("${selected.size} selected", color = Green, modifier = Modifier.padding(end = 10.dp))
        }
        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Group name") },
            placeholder = { Text("e.g. Friends") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            label = { Text("Add participants") },
            placeholder = { Text("Search by exact username") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selected.values.take(5).forEach { user ->
                    AssistChip(onClick = { selected = selected - user.uid }, label = { Text(user.name.take(12)) })
                }
                if (selected.size > 5) Text("+${selected.size - 5}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        if (normalizeKhahishUsername(search).length < 3) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Enter a complete unique username to add someone", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!loading && users.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(users, key = { it.uid }) { user ->
                    val isSelected = selected.containsKey(user.uid)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (isSelected) selected - user.uid else selected + (user.uid to user)
                        }.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text(user.name.firstOrNull()?.uppercase() ?: "K", color = Green, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(user.name.ifBlank { "Khahish User" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            if (user.username.isNotBlank()) Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Checkbox(checked = isSelected, onCheckedChange = { checked ->
                            selected = if (checked) selected + (user.uid to user) else selected - user.uid
                        }, colors = CheckboxDefaults.colors(checkedColor = Green))
                    }
                }
            }
        }
        Button(
            onClick = {
                val cleanName = groupName.trim()
                if (cleanName.isBlank()) {
                    Toast.makeText(context, "Enter a group name first.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selected.size < 2) {
                    Toast.makeText(context, "Select at least two participants to create a group.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (creating) return@Button
                creating = true
                val members = (selected.keys + currentUid).distinct()
                val names = selected.mapValues { it.value.name }.toMutableMap().apply { put(currentUid, currentName) }
                val now = System.currentTimeMillis()
                val chatRef = db.collection("chats").document()
                val data = mapOf(
                    "members" to members,
                    "memberNames" to names,
                    "isGroup" to true,
                    "groupName" to cleanName,
                    "groupAdmins" to listOf(currentUid),
                    "createdBy" to currentUid,
                    "createdAt" to now,
                    "lastMessage" to "Group created",
                    "lastMessageAt" to now,
                    "updatedAt" to now,
                    "disappearingDurationMs" to 0L,
                    "secretLockedForUids" to emptyList<String>()
                )
                chatRef.set(data)
                    .addOnSuccessListener {
                        creating = false
                        onCreated(Chat(
                            id = chatRef.id,
                            peerUid = selected.keys.first(),
                            name = cleanName,
                            message = "Group created",
                            time = "",
                            initials = cleanName.firstOrNull()?.uppercase() ?: "G",
                            isGroup = true,
                            memberUids = members
                        ))
                    }
                    .addOnFailureListener {
                        creating = false
                        Toast.makeText(context, "Group creation failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            },
            enabled = !creating,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            if (creating) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(Icons.Default.GroupAdd, null)
            Spacer(Modifier.width(8.dp))
            Text(if (creating) "Creating group…" else "Create group")
        }
    }
}
