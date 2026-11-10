package com.khahish.app

import android.net.Uri

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

private data class KhahishStory(
    val id: String,
    val ownerUid: String,
    val ownerName: String,
    val text: String,
    val mediaUrl: String,
    val mediaType: String,
    val createdAt: Long,
    val expiresAt: Long
)

@Composable
fun KhahishUpdatesScreen(
    currentUid: String,
    currentName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var stories by remember { mutableStateOf(emptyList<KhahishStory>()) }
    var loading by remember { mutableStateOf(true) }
    var showTextDialog by remember { mutableStateOf(false) }
    var storyText by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var videoPlayerUrl by remember { mutableStateOf("") }

    fun publishStory(text: String, mediaUrl: String = "", mediaType: String = "text") {
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "ownerUid" to currentUid,
            "ownerName" to currentName.ifBlank { "Khahish User" },
            "text" to text.trim(),
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to now,
            "expiresAt" to now + 24L * 60L * 60L * 1000L
        )
        db.collection("stories").add(data)
            .addOnSuccessListener { Toast.makeText(context, "Story shared for 24 hours", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(context, "Story failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    fun uploadStory(uri: Uri, type: String) {
        uploading = true
        uploadToCloudinary(
            context = context,
            uri = uri,
            resourceType = if (type == "video") "video" else "image",
            onSuccess = { url ->
                uploading = false
                publishStory("", url, type)
            },
            onError = {
                uploading = false
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadStory(uri, "image")
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadStory(uri, "video")
    }

    DisposableEffect(currentUid) {
        val listener: ListenerRegistration = db.collection("stories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loading = false
                    Toast.makeText(context, "Unable to load updates: ${error.message}", Toast.LENGTH_LONG).show()
                } else {
                    val now = System.currentTimeMillis()
                    stories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val expires = doc.getLong("expiresAt") ?: 0L
                        if (expires <= now) return@mapNotNull null
                        KhahishStory(
                            id = doc.id,
                            ownerUid = doc.getString("ownerUid").orEmpty(),
                            ownerName = doc.getString("ownerName") ?: "Khahish User",
                            text = doc.getString("text").orEmpty(),
                            mediaUrl = doc.getString("mediaUrl").orEmpty(),
                            mediaType = doc.getString("mediaType") ?: "text",
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            expiresAt = expires
                        )
                    }.sortedByDescending { it.createdAt }
                    loading = false
                }
            }
        onDispose { listener.remove() }
    }

    Column(
        modifier.fillMaxSize().background(Pale).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Updates", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Stories disappear after 24 hours", color = Muted, fontSize = 12.sp)
            }
            FilledTonalButton(onClick = { showTextDialog = true }) {
                Icon(Icons.Default.TextFields, null)
                Spacer(Modifier.width(6.dp))
                Text("Text")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !uploading) {
                Icon(Icons.Default.Image, null); Spacer(Modifier.width(5.dp)); Text("Photo")
            }
            OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !uploading) {
                Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(5.dp)); Text("Video")
            }
        }
        if (uploading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            Text("Uploading story…", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            stories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).background(Color(0xFFDCEFE9), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = Green, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("No updates yet", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Share a photo, video or text story.", color = Muted, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(stories, key = { it.id }) { story ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(story.ownerName.take(1).uppercase(Locale.getDefault()), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (story.ownerUid == currentUid) "Your story" else story.ownerName, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text("Expires in 24 hours", color = Muted, fontSize = 11.sp)
                                }
                                if (story.ownerUid == currentUid) {
                                    IconButton(onClick = {
                                        db.collection("stories").document(story.id).delete()
                                            .addOnFailureListener { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                                    }) { Icon(Icons.Default.DeleteOutline, "Delete story", tint = Muted) }
                                }
                            }
                            if (story.mediaUrl.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                if (story.mediaType == "image") {
                                    AsyncImage(
                                        model = story.mediaUrl,
                                        contentDescription = "Story image",
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                            .background(Pale).clickable { videoPlayerUrl = story.mediaUrl }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayCircle, null, tint = Green, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Play video story", color = Ink, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (story.text.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(story.text, color = Ink, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (videoPlayerUrl.isNotBlank()) {
        Dialog(onDismissRequest = { videoPlayerUrl = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { videoPlayerUrl = "" }) { Icon(Icons.Default.Close, "Close video", tint = Color.White) }
                }
                KhahishVideoPlayer(url = videoPlayerUrl, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Create text update") },
            text = {
                OutlinedTextField(
                    value = storyText,
                    onValueChange = { storyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What's on your mind?") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (storyText.isNotBlank()) {
                        publishStory(storyText)
                        storyText = ""
                        showTextDialog = false
                    }
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}


