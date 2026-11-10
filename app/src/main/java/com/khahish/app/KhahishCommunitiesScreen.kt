package com.khahish.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Add
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class KhahishCommunity(
    val id: String,
    val name: String,
    val description: String,
    val ownerUid: String,
    val members: List<String>,
    val memberCount: Long
)

private data class CommunityPost(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val text: String,
    val createdAt: Long
)

@Composable
fun KhahishCommunitiesScreen(
    currentUid: String,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val currentName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Khahish User"
    var communities by remember { mutableStateOf(emptyList<KhahishCommunity>()) }
    var selected by remember { mutableStateOf<KhahishCommunity?>(null) }
    var posts by remember { mutableStateOf(emptyList<CommunityPost>()) }
    var communityName by remember { mutableStateOf("") }
    var communityDescription by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var postText by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val registration = db.collection("communities").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    communities = snapshot.documents.map { doc ->
                        KhahishCommunity(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            description = doc.getString("description").orEmpty(),
                            ownerUid = doc.getString("ownerUid").orEmpty(),
                            members = doc.get("members") as? List<String> ?: emptyList(),
                            memberCount = doc.getLong("memberCount") ?: 0L
                        )
                    }
                    selected?.let { old -> selected = communities.firstOrNull { it.id == old.id } }
                }
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(selected?.id) {
        val communityId = selected?.id
        if (communityId.isNullOrBlank()) {
            onDispose { }
        } else {
            val registration = db.collection("communities").document(communityId)
                .collection("posts").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        posts = snapshot.documents.map { doc ->
                            CommunityPost(
                                id = doc.id,
                                authorUid = doc.getString("authorUid").orEmpty(),
                                authorName = doc.getString("authorName") ?: "Khahish User",
                                text = doc.getString("text").orEmpty(),
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }
                    }
                }
            onDispose { registration.remove() }
        }
    }

    Column(modifier.fillMaxSize().background(Pale)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected != null) {
                IconButton(onClick = { selected = null; posts = emptyList() }) { Icon(Icons.Default.ArrowBack, "Back") }
            } else {
                Icon(Icons.Default.Groups, null, tint = Green, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(selected?.name ?: "Communities", color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    selected?.let { "${it.memberCount} members" } ?: "Find people with shared interests",
                    color = Muted, fontSize = 11.sp
                )
            }
            if (selected == null) {
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Create community", tint = Green) }
            }
        }

        if (selected == null) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                placeholder = { Text("Search communities") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            val visible = communities.filter {
                it.name.contains(search, true) || it.description.contains(search, true)
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (search.isBlank()) "No communities yet. Create the first one." else "No matching communities", color = Muted)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp)) {
                    items(visible, key = { it.id }) { community ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selected = community },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(community.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                if (community.description.isNotBlank()) {
                                    Text(community.description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${community.memberCount} members", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            val ref = db.collection("communities").document(community.id)
                                            val joining = currentUid !in community.members
                                            ref.update(
                                                "members", if (joining) FieldValue.arrayUnion(currentUid) else FieldValue.arrayRemove(currentUid),
                                                "memberCount", FieldValue.increment(if (joining) 1 else -1)
                                            ).addOnFailureListener {
                                                Toast.makeText(context, "Could not update membership: ${it.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (currentUid in community.members) Color(0xFFE8F5F0) else Green),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) {
                                        Text(if (currentUid in community.members) "Joined" else "Join", color = if (currentUid in community.members) Green else Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val community = selected!!
            val joined = currentUid in community.members
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (community.description.isNotBlank()) Text(community.description, color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${community.memberCount} members", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    if (!joined) {
                        Button(onClick = {
                            db.collection("communities").document(community.id).update(
                                "members", FieldValue.arrayUnion(currentUid),
                                "memberCount", FieldValue.increment(1)
                            ).addOnFailureListener { Toast.makeText(context, "Join failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Join community") }
                    } else if (community.ownerUid != currentUid) {
                        TextButton(onClick = {
                            db.collection("communities").document(community.id).update(
                                "members", FieldValue.arrayRemove(currentUid),
                                "memberCount", FieldValue.increment(-1)
                            ).addOnSuccessListener { selected = null }
                        }) { Text("Leave") }
                    }
                }
            }
            if (joined) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Share something with the community") },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )
                    IconButton(enabled = postText.isNotBlank() && !busy, onClick = {
                        val clean = postText.trim()
                        if (clean.isNotBlank()) {
                            busy = true
                            val now = System.currentTimeMillis()
                            db.collection("communities").document(community.id).collection("posts").add(
                                mapOf("authorUid" to currentUid, "authorName" to currentName, "text" to clean, "createdAt" to now)
                            ).addOnSuccessListener {
                                postText = ""
                                busy = false
                            }.addOnFailureListener {
                                busy = false
                                Toast.makeText(context, "Post failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Icon(Icons.Default.Send, "Publish post", tint = Green) }
                }
            }
            if (posts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (joined) "No posts yet. Start the conversation." else "Join this community to see and publish posts.", color = Muted)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    items(posts, key = { it.id }) { post ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(post.authorName, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(post.createdAt)), color = Muted, fontSize = 10.sp)
                                    }
                                    IconButton(onClick = {
                                        db.collection("reports").add(mapOf(
                                            "type" to "community_post",
                                            "targetId" to post.id,
                                            "communityId" to community.id,
                                            "reporterUid" to currentUid,
                                            "createdAt" to System.currentTimeMillis(),
                                            "status" to "pending"
                                        )).addOnSuccessListener {
                                            Toast.makeText(context, "Report submitted for review.", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Icon(Icons.Default.Report, "Report post", tint = Muted) }
                                }
                                Text(post.text, color = Ink, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!busy) showCreate = false },
            title = { Text("Create community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = communityName, onValueChange = { communityName = it }, label = { Text("Community name") }, singleLine = true)
                    OutlinedTextField(value = communityDescription, onValueChange = { communityDescription = it }, label = { Text("Description") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val name = communityName.trim()
                    if (name.isBlank()) {
                        Toast.makeText(context, "Enter a community name.", Toast.LENGTH_SHORT).show()
                    } else {
                        busy = true
                        val ref = db.collection("communities").document()
                        val now = System.currentTimeMillis()
                        ref.set(mapOf(
                            "name" to name,
                            "description" to communityDescription.trim(),
                            "ownerUid" to currentUid,
                            "members" to listOf(currentUid),
                            "memberCount" to 1L,
                            "createdAt" to now
                        )).addOnSuccessListener {
                            communityName = ""
                            communityDescription = ""
                            showCreate = false
                            busy = false
                            selected = KhahishCommunity(ref.id, name, communityDescription, currentUid, listOf(currentUid), 1L)
                        }.addOnFailureListener {
                            busy = false
                            Toast.makeText(context, "Community creation failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}
