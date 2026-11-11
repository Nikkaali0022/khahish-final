package com.khahish.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
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
    val expiresAt: Long,
    val audience: String = "everyone"
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
    var showStoryChooser by remember { mutableStateOf(false) }
    var showMediaEditor by remember { mutableStateOf(false) }
    var showVoiceEditor by remember { mutableStateOf(false) }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMediaType by remember { mutableStateOf("image") }
    var storyCaption by remember { mutableStateOf("") }
    var storyText by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    var voicePreviewFile by remember { mutableStateOf<File?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var videoPlayerUrl by remember { mutableStateOf("") }
    var storyAudience by remember { mutableStateOf("everyone") }
    var audienceMenu by remember { mutableStateOf(false) }
    var viewerStoryId by remember { mutableStateOf("") }
    var storyViewers by remember { mutableStateOf(emptyList<String>()) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var updatesMenu by remember { mutableStateOf(false) }
    var viewedStoryIds by remember { mutableStateOf(setOf<String>()) }
    var currentPhotoUrl by remember(currentUid) { mutableStateOf("") }
    var ownerPhotos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun publishStory(text: String, mediaUrl: String = "", mediaType: String = "text") {
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "ownerUid" to currentUid,
            "ownerName" to currentName.ifBlank { "Khahish User" },
            "text" to text.trim(),
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "createdAt" to now,
            "expiresAt" to now + 24L * 60L * 60L * 1000L,
            "audience" to storyAudience
        )
        db.collection("stories").add(data)
            .addOnSuccessListener { Toast.makeText(context, "Story shared for 24 hours", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(context, "Story failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    fun uploadStory(uri: Uri, type: String, caption: String = storyCaption) {
        uploading = true
        uploadToCloudinary(
            context = context,
            uri = uri,
            resourceType = if (type == "video") "video" else "image",
            onSuccess = { url ->
                uploading = false
                publishStory(caption, url, type)
                storyCaption = ""
                pendingMediaUri = null
                showMediaEditor = false
            },
            onError = {
                uploading = false
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { pendingMediaUri = uri; pendingMediaType = "image"; storyCaption = ""; showMediaEditor = true }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { pendingMediaUri = uri; pendingMediaType = "video"; storyCaption = ""; showMediaEditor = true }
    }

    fun createVoiceRecorder() {
        try {
            val file = File(context.cacheDir, "khahish_story_voice_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceRecorder = recorder
            voiceRecordingFile = file
            isRecordingVoice = true
        } catch (error: Exception) {
            voiceRecorder?.release()
            voiceRecorder = null
            isRecordingVoice = false
            Toast.makeText(context, "Could not start voice story: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) createVoiceRecorder()
        else Toast.makeText(context, "Microphone permission is required for a voice story.", Toast.LENGTH_LONG).show()
    }

    fun requestVoiceRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            createVoiceRecorder()
        } else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopVoiceRecording() {
        try { voiceRecorder?.stop() } catch (_: Exception) { voiceRecordingFile?.delete() }
        voiceRecorder?.release()
        voiceRecorder = null
        isRecordingVoice = false
        voicePreviewFile = voiceRecordingFile
        voiceRecordingFile = null
    }

    fun cancelVoiceRecording() {
        try { voiceRecorder?.stop() } catch (_: Exception) { }
        voiceRecorder?.release()
        voiceRecorder = null
        voiceRecordingFile?.delete()
        voiceRecordingFile = null
        isRecordingVoice = false
    }

    fun publishVoiceStory() {
        val file = voicePreviewFile ?: return
        uploading = true
        uploadToCloudinary(context, Uri.fromFile(file), "video", onSuccess = { url ->
            uploading = false
            publishStory(storyCaption, url, "voice")
            storyCaption = ""
            showVoiceEditor = false
            voicePreviewFile?.delete()
            voicePreviewFile = null
        }, onError = { message ->
            uploading = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        })
    }

    DisposableEffect(currentUid) {
        val registration = db.collection("publicProfiles").document(currentUid)
            .addSnapshotListener { snapshot, _ ->
                currentPhotoUrl = snapshot?.getString("photoUrl").orEmpty()
            }
        onDispose { registration.remove() }
    }

    DisposableEffect(currentUid, stories) {
        val owners = stories.map { it.ownerUid }.filter { it.isNotBlank() && it != currentUid }.distinct()
        val photoRegistrations = owners.map { ownerUid ->
            db.collection("publicProfiles").document(ownerUid).addSnapshotListener { snapshot, _ ->
                val url = snapshot?.getString("photoUrl").orEmpty()
                ownerPhotos = ownerPhotos.toMutableMap().apply {
                    if (url.isBlank()) remove(ownerUid) else put(ownerUid, url)
                }
            }
        }
        onDispose { photoRegistrations.forEach { it.remove() } }
    }

    DisposableEffect(currentUid, stories) {
        val registrations = stories.filter { it.ownerUid != currentUid }.map { story ->
            db.collection("stories").document(story.id).collection("views").document(currentUid)
                .addSnapshotListener { snapshot, _ ->
                    viewedStoryIds = if (snapshot?.exists() == true) {
                        viewedStoryIds + story.id
                    } else {
                        viewedStoryIds - story.id
                    }
                }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    DisposableEffect(currentUid) {
        val publicDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
        val ownerDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
        val allowedDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
        fun refreshStories() {
            val now = System.currentTimeMillis()
            stories = (publicDocs.values + ownerDocs.values + allowedDocs.values)
                .distinctBy { it.id }
                .mapNotNull { doc ->
                    val expires = doc.getLong("expiresAt") ?: 0L
                    if (expires <= now) return@mapNotNull null
                    val ownerUid = doc.getString("ownerUid").orEmpty()
                    val audience = doc.getString("audience") ?: "everyone"
                    KhahishStory(
                        id = doc.id,
                        ownerUid = ownerUid,
                        ownerName = doc.getString("ownerName") ?: "Khahish User",
                        text = doc.getString("text").orEmpty(),
                        mediaUrl = doc.getString("mediaUrl").orEmpty(),
                        mediaType = doc.getString("mediaType") ?: "text",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        expiresAt = expires,
                        audience = audience
                    )
                }.sortedByDescending { it.createdAt }
            loading = false
        }
        val publicListener = db.collection("stories").whereEqualTo("audience", "everyone")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loading = false
                    Toast.makeText(context, "Unable to load public updates: ${error.message}", Toast.LENGTH_LONG).show()
                } else {
                    publicDocs.clear()
                    snapshot?.documents?.forEach { publicDocs[it.id] = it }
                    refreshStories()
                }
            }
        val ownerListener = db.collection("stories").whereEqualTo("ownerUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error == null) {
                    ownerDocs.clear()
                    snapshot?.documents?.forEach { ownerDocs[it.id] = it }
                    refreshStories()
                }
            }
        val allowedListener = db.collection("stories").whereArrayContains("allowedUids", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error == null) {
                    allowedDocs.clear()
                    snapshot?.documents?.forEach { allowedDocs[it.id] = it }
                    refreshStories()
                }
            }
        onDispose {
            publicListener.remove()
            ownerListener.remove()
            allowedListener.remove()
        }
    }

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Updates", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Stories disappear after 24 hours", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                Icon(Icons.Default.Search, "Search updates", tint = MaterialTheme.colorScheme.onSurface)
            }
            Box {
                IconButton(onClick = { updatesMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Update options", tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(expanded = updatesMenu, onDismissRequest = { updatesMenu = false }) {
                    listOf("close_friends" to "Close Friends", "my_contacts" to "My Contacts", "my_friends" to "My Friends", "everyone" to "Everyone").forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(if (storyAudience == value) "✓ $label" else label) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { storyAudience = value; updatesMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Status Privacy") },
                        leadingIcon = { Icon(Icons.Default.People, null) },
                        onClick = { audienceMenu = true; updatesMenu = false }
                    )
                }
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search people or updates") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Status privacy: " + when (storyAudience) {
                    "close_friends" -> "Close Friends"
                    "my_contacts" -> "My Contacts"
                    "my_friends" -> "My Friends"
                    else -> "Everyone"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { audienceMenu = true }) {
                Icon(Icons.Default.People, null, tint = Green)
                Text("Change", color = Green)
            }
            DropdownMenu(expanded = audienceMenu, onDismissRequest = { audienceMenu = false }) {
                listOf("close_friends" to "Close Friends", "my_contacts" to "My Contacts", "my_friends" to "My Friends", "everyone" to "Everyone").forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { storyAudience = value; audienceMenu = false })
                }
            }
        }
        if (uploading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            Text("Uploading story…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        val ownStories = stories.filter { it.ownerUid == currentUid }
        val recentStories = stories.filter { it.ownerUid != currentUid }.filter { story ->
            searchQuery.isBlank() || story.ownerName.contains(searchQuery, true) || story.text.contains(searchQuery, true)
        }
        Card(
            Modifier.fillMaxWidth().clickable { showStoryChooser = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(58.dp)
                        .border(2.dp, if (ownStories.isEmpty()) MaterialTheme.colorScheme.outline else Green, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentPhotoUrl.isNotBlank()) {
                        AsyncImage(
                            model = currentPhotoUrl,
                            contentDescription = "My profile picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(currentName.take(1).uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(21.dp).background(Green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("My status", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ownStories.isEmpty()) "Tap to add a status" else "Updated ${relativeStoryTime(ownStories.maxByOrNull { it.createdAt }?.createdAt ?: 0L)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Green).clickable { showStoryChooser = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Add story", tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Recent updates", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
            recentStories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = Green, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("No updates yet", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Share a photo, video, text or voice story.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentStories, key = { it.id }) { story ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp)
                                        .border(
                                            2.dp,
                                            if (story.id in viewedStoryIds) MaterialTheme.colorScheme.outline else Green,
                                            CircleShape
                                        )
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val ownerPhoto = ownerPhotos[story.ownerUid].orEmpty()
                                    if (ownerPhoto.isNotBlank()) {
                                        AsyncImage(
                                            model = ownerPhoto,
                                            contentDescription = "${story.ownerName} profile picture",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(story.ownerName.take(1).uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).clickable {
                                    if (story.ownerUid != currentUid) {
                                        db.collection("stories").document(story.id).collection("views").document(currentUid)
                                            .set(mapOf("uid" to currentUid, "name" to currentName, "viewedAt" to System.currentTimeMillis()))
                                    }
                                }) {
                                    Text(if (story.ownerUid == currentUid) "Your story" else story.ownerName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (story.ownerUid == currentUid) "Audience: ${if (story.audience == "everyone") "Everyone" else "Only me"}" else relativeStoryTime(story.createdAt),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                    )
                                }
                                if (story.ownerUid == currentUid) {
                                    IconButton(onClick = {
                                        db.collection("stories").document(story.id).collection("views").get()
                                            .addOnSuccessListener { snapshot ->
                                                storyViewers = snapshot.documents.map { doc ->
                                                    doc.getString("name") ?: doc.getString("uid") ?: "Khahish user"
                                                }
                                                viewerStoryId = story.id
                                            }
                                            .addOnFailureListener { Toast.makeText(context, "Could not load viewers: ${it.message}", Toast.LENGTH_LONG).show() }
                                    }) { Icon(Icons.Default.Visibility, "View story viewers", tint = Green) }

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
                                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { videoPlayerUrl = story.mediaUrl }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayCircle, null, tint = Green, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Play video story", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (story.text.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(story.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerStoryId.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { viewerStoryId = "" },
            title = { Text("Story viewers") },
            text = {
                if (storyViewers.isEmpty()) Text("No views recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column {
                    storyViewers.forEach { viewer -> Text(viewer, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { viewerStoryId = "" }) { Text("Close") } }
        )
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

    if (showStoryChooser) {
        Dialog(
            onDismissRequest = { showStoryChooser = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Create a Story", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple(Icons.Default.Image, "Photo", "Add a photo"),
                        Triple(Icons.Default.Videocam, "Video", "Add a video"),
                        Triple(Icons.Default.TextFields, "Text", "Create a text story"),
                        Triple(Icons.Default.Mic, "Voice", "Record a voice story")
                    ).forEach { (icon, label, description) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showStoryChooser = false
                                    when (label) {
                                        "Photo" -> imagePicker.launch("image/*")
                                        "Video" -> videoPicker.launch("video/*")
                                        "Text" -> { storyText = ""; showTextDialog = true }
                                        "Voice" -> { storyCaption = ""; voicePreviewFile = null; showVoiceEditor = true }
                                    }
                                }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Green.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(icon, null, tint = Green, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showMediaEditor && pendingMediaUri != null) {
        Dialog(
            onDismissRequest = { showMediaEditor = false; pendingMediaUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF101010)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showMediaEditor = false; pendingMediaUri = null }) {
                        Icon(Icons.Default.Close, "Close editor", tint = Color.White)
                    }
                    Text("Edit story", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { audienceMenu = true }) {
                        Text(
                            when (storyAudience) {
                                "close_friends" -> "Close Friends"
                                "my_contacts" -> "My Contacts"
                                "my_friends" -> "My Friends"
                                else -> "Everyone"
                            },
                            color = Color(0xFF70D7B8)
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (pendingMediaType == "image") {
                        AsyncImage(
                            model = pendingMediaUri,
                            contentDescription = "Story preview",
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        Text("Video preview selected", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { Toast.makeText(context, "Music tool selected", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White); Text("Music", color = Color.White)
                    }
                    TextButton(onClick = { Toast.makeText(context, "Crop and rotate tool selected", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.CropRotate, null, tint = Color.White); Text("Crop", color = Color.White)
                    }
                    TextButton(onClick = { storyCaption += " ❤️" }) {
                        Icon(Icons.Default.EmojiEmotions, null, tint = Color.White); Text("Sticker", color = Color.White)
                    }
                    TextButton(onClick = { storyCaption += " " }) {
                        Icon(Icons.Default.TextFields, null, tint = Color.White); Text("Text", color = Color.White)
                    }
                    TextButton(onClick = { Toast.makeText(context, "Drawing tool selected", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Draw, null, tint = Color.White); Text("Draw", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = storyCaption,
                        onValueChange = { storyCaption = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a caption...") },
                        singleLine = false,
                        maxLines = 3
                    )
                    TextButton(onClick = { storyCaption += "@" }) { Text("@", color = Color(0xFF70D7B8), fontSize = 20.sp) }
                    IconButton(
                        enabled = !uploading,
                        onClick = { pendingMediaUri?.let { uploadStory(it, pendingMediaType, storyCaption) } }
                    ) { Icon(Icons.Default.Send, "Publish story", tint = Color(0xFF70D7B8)) }
                }
                if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            }
        }
    }

    if (showVoiceEditor) {
        AlertDialog(
            onDismissRequest = {
                if (isRecordingVoice) cancelVoiceRecording()
                voicePreviewFile?.delete()
                voicePreviewFile = null
                showVoiceEditor = false
            },
            title = { Text("Voice story") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when {
                            isRecordingVoice -> "Recording your voice story…"
                            voicePreviewFile != null -> "Voice recording is ready to publish."
                            else -> "Record a voice story, then preview and publish it."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = storyCaption,
                        onValueChange = { storyCaption = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add a caption...") },
                        maxLines = 2
                    )
                    if (isRecordingVoice) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                        Button(onClick = { stopVoiceRecording() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(8.dp)); Text("Stop recording")
                        }
                    } else if (voicePreviewFile == null) {
                        Button(onClick = { requestVoiceRecording() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("Start recording")
                        }
                    } else {
                        Text("Recording saved on this device until you publish.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Button(onClick = { publishVoiceStory() }, enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Publish voice story")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isRecordingVoice) cancelVoiceRecording()
                    voicePreviewFile?.delete()
                    voicePreviewFile = null
                    showVoiceEditor = false
                }) { Text("Cancel") }
            }
        )
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



private fun relativeStoryTime(timestamp: Long): String {
    if (timestamp <= 0L) return "just now"
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "${minutes / (24L * 60L)}d ago"
    }
}
