package com.khahish.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import io.agora.rtc2.video.VideoCanvas


@Composable
fun KhahishCallScreen(
    call: KhahishCall,
    isIncoming: Boolean,
    isMinimized: Boolean,
    onMinimize: () -> Unit,
    onRestore: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val repository = remember { KhahishCallRepository() }

    var currentCall by remember(call.id) { mutableStateOf(call) }
    var statusText by remember(call.id) {
        mutableStateOf(if (isIncoming) "Incoming call" else "Calling…")
    }
    var errorText by remember(call.id) { mutableStateOf("") }
    var isJoined by remember(call.id) { mutableStateOf(false) }
    var isMuted by remember(call.id) { mutableStateOf(false) }
    var cameraEnabled by remember(call.id) { mutableStateOf(true) }
    var speakerEnabled by remember(call.id) { mutableStateOf(true) }
    var credentials by remember(call.id) { mutableStateOf<AgoraRtcCredentials?>(null) }
    var session by remember(call.id) { mutableStateOf<KhahishAgoraSession?>(null) }
    var connectionStarted by remember(call.id) { mutableStateOf(false) }
    var remoteUid by remember(call.id) { mutableStateOf<Int?>(null) }
    var peerPhotoUrl by remember(call.id) { mutableStateOf("") }
    var callElapsedSeconds by remember(call.id) { mutableIntStateOf(0) }
    var localVideoFullscreen by remember(call.id) { mutableStateOf(false) }
    var selectedBeautyFilter by remember(call.id) { mutableStateOf("Natural") }
    var showBeautyFilters by remember(call.id) { mutableStateOf(false) }
    fun finishCallLocally() {
        session?.leave()
        KhahishCallForegroundService.stop(context)
        onExit()
    }

    val peerUid = if (isIncoming) currentCall.callerUid else currentCall.calleeUid
    val displayName = if (isIncoming) currentCall.callerName else currentCall.calleeName
    val isConnected = currentCall.status == "accepted" || isJoined

    val requiredPermissions = remember(call.isVideo) {
        if (call.isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    fun permissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCredentialsAndJoin() {
        statusText = "Connecting…"
        KhahishCallForegroundService.start(context, currentCall, currentCall.isVideo)
        KhahishAgoraTokenClient.requestToken(
            currentCall.id,
            currentCall.channelName,
            onSuccess = { token ->
                credentials = token
                val rtc = KhahishAgoraSession(
                    context = context,
                    credentials = token,
                    isVideoCall = currentCall.isVideo,
                    onJoined = {
                        isJoined = true
                        statusText = "Connected"
                    },
                    onRemoteUserJoined = { uid -> remoteUid = uid; statusText = "Connected" },
                    onRemoteUserLeft = { statusText = "The other person left the call" },
                    onFailure = {
                        errorText = it
                        KhahishCallForegroundService.stop(context)
                    }
                )
                session = rtc
                rtc.join()
            },
            onError = {
                errorText = it
                KhahishCallForegroundService.stop(context)
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) requestCredentialsAndJoin()
        else {
            connectionStarted = false
            errorText = if (call.isVideo) {
                "Microphone and camera permissions are required."
            } else {
                "Microphone permission is required."
            }
        }
    }

    fun connectIfAllowed() {
        if (connectionStarted) return
        connectionStarted = true
        if (permissionsGranted()) {
            requestCredentialsAndJoin()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    DisposableEffect(peerUid) {
        if (peerUid.isBlank()) {
            onDispose { }
        } else {
            val registration = FirebaseFirestore.getInstance()
                .collection("publicProfiles")
                .document(peerUid)
                .addSnapshotListener { snapshot, _ ->
                    peerPhotoUrl = snapshot?.getString("photoUrl").orEmpty()
                }
            onDispose { registration.remove() }
        }
    }

    DisposableEffect(call.id) {
        val registration = repository.listenToCall(call.id) { updated ->
            if (updated != null) {
                currentCall = updated
                when (updated.status) {
                    "accepted" -> {
                        statusText = "Connected"
                        if (session == null && !connectionStarted) {
                            connectIfAllowed()
                        }
                    }
                    "rejected" -> { statusText = "Call declined"; finishCallLocally() }
                    "ended" -> { statusText = "Call ended"; finishCallLocally() }
                    "missed" -> { statusText = "Missed call"; finishCallLocally() }
                    "ringing" -> statusText = if (isIncoming) "Incoming call" else "Ringing…"
                }
            }
        }
        onDispose {
            registration.remove()
            // Session cleanup is performed only on explicit or remote call end.
        }
    }

    BackHandler(enabled = !isMinimized) {
        onMinimize()
    }

    LaunchedEffect(call.id, isIncoming, currentCall.status) {
        if (!isIncoming && currentCall.status == "ringing") {
            delay(45_000)
            if (currentCall.status == "ringing") {
                repository.markMissed(call.id) { errorText = it }
            }
        }
    }

    LaunchedEffect(call.id, currentCall.status, currentCall.acceptedAt, currentCall.updatedAt) {
        val connectedAt = currentCall.acceptedAt.takeIf { it > 0L } ?: currentCall.updatedAt
        if (currentCall.status == "accepted" && connectedAt > 0L) {
            while (currentCall.status == "accepted") {
                callElapsedSeconds = ((System.currentTimeMillis() - connectedAt)
                    .coerceAtLeast(0L) / 1000L).toInt()
                delay(1_000)
            }
        } else {
            callElapsedSeconds = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (isMinimized) {
            Surface(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE5F5EF),
                shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF18A889)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Ongoing call", tint = Color.White)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (call.isVideo) "Ongoing video call" else "Ongoing voice call",
                                color = Color(0xFF173C32), fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                            Text(
                                "$displayName · ${formatKhahishCallDuration(callElapsedSeconds)}",
                                color = Color(0xFF287A60), fontSize = 12.sp
                            )
                        }
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFD0EDE1)) {
                            Text("LIVE", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF176B51), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("↗  Return to call", color = Color(0xFF087D60), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (currentCall.isVideo) "KHAHISH  •  VIDEO CALL" else "KHAHISH  •  VOICE CALL",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )

        if (call.isVideo) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(22.dp)).background(Color.Black)
            ) {
                if (remoteUid != null && !localVideoFullscreen) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            SurfaceView(viewContext).also { session?.setupRemoteVideo(it, remoteUid!!) }
                        }
                    )
                } else if (localVideoFullscreen && credentials != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            SurfaceView(viewContext).also { session?.setupLocalVideo(it) }
                        }
                    )
                } else if (peerPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = peerPhotoUrl, contentDescription = "$displayName profile picture",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (credentials != null && !localVideoFullscreen) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .size(width = 112.dp, height = 158.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                            .clickable { localVideoFullscreen = true }
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                SurfaceView(viewContext).also {
                                    it.setZOrderMediaOverlay(true)
                                    session?.setupLocalVideo(it)
                                }
                            }
                        )
                        Text("You · tap to expand",
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Color(0x99000000)).padding(vertical = 5.dp),
                            color = Color.White, textAlign = TextAlign.Center, fontSize = 9.sp)
                    }
                } else if (localVideoFullscreen && remoteUid != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .size(width = 112.dp, height = 158.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                            .clickable { localVideoFullscreen = false }
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                SurfaceView(viewContext).also {
                                    it.setZOrderMediaOverlay(true)
                                    session?.setupRemoteVideo(it, remoteUid!!)
                                }
                            }
                        )
                        Text("$displayName · tap to expand",
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Color(0x99000000)).padding(vertical = 5.dp),
                            color = Color.White, textAlign = TextAlign.Center, fontSize = 9.sp,
                            maxLines = 1)
                    }
                }

                Row(Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0x99000000),
                        onClick = { session?.switchCamera() }) {
                        Icon(Icons.Default.Cached, "Switch camera", tint = Color.White,
                            modifier = Modifier.padding(11.dp).size(23.dp))
                    }
                    Box {
                        Surface(shape = CircleShape, color = Color(0x99000000),
                            onClick = { showBeautyFilters = true }) {
                            Text("✨ Filters", Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = showBeautyFilters,
                            onDismissRequest = { showBeautyFilters = false }) {
                            listOf("Natural", "Soft Skin", "Glow", "Rosy", "Smooth Plus").forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(filter, Modifier.weight(1f))
                                            if (selectedBeautyFilter == filter)
                                                Icon(Icons.Default.Check, "Selected")
                                        }
                                    },
                                    onClick = {
                                        selectedBeautyFilter = filter
                                        session?.setBeautyFilter(filter)
                                        showBeautyFilters = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (remoteUid == null && !localVideoFullscreen) {
                    Text(statusText, Modifier.align(Alignment.BottomStart).padding(14.dp)
                        .background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color.White, fontSize = 12.sp)
                }
            }
        } else {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(226.dp).border(4.dp, colors.primary, CircleShape)
                .padding(7.dp).clip(CircleShape).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center) {
                if (peerPhotoUrl.isNotBlank()) {
                    AsyncImage(model = peerPhotoUrl, contentDescription = "$displayName profile picture",
                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Text(displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = colors.onSurface, fontSize = 76.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(displayName.ifBlank { "Khahish user" }, color = colors.onBackground,
            fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1)
        if (isConnected) {
            Text(formatKhahishCallDuration(callElapsedSeconds), color = colors.onSurfaceVariant,
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(statusText, color = colors.onSurfaceVariant, fontSize = 13.sp)
        }

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorText,
                color = colors.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        if (isIncoming && currentCall.status == "ringing") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        repository.rejectCall(call.id) { errorText = it }
                        finishCallLocally()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                ) { Text("Decline") }
                Button(
                    onClick = {
                        repository.acceptCall(call.id) { errorText = it }
                        connectIfAllowed()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) { Text("Accept") }
            }
        } else if (currentCall.status == "accepted" || isJoined) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = colors.surfaceVariant,
                tonalElevation = 2.dp
            ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    background = colors.surfaceVariant,
                    onClick = {
                        isMuted = !isMuted
                        session?.setMicrophoneMuted(isMuted)
                    }
                )
                if (call.isVideo) {
                    CallControlButton(
                        icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = "Camera",
                        background = colors.surfaceVariant,
                        onClick = {
                            cameraEnabled = !cameraEnabled
                            session?.setCameraEnabled(cameraEnabled)
                        }
                    )
                }
                CallControlButton(
                    icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = "Speaker",
                    background = colors.surfaceVariant,
                    onClick = {
                        speakerEnabled = !speakerEnabled
                        session?.setSpeakerEnabled(speakerEnabled)
                    }
                )
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    background = colors.error,
                    iconTint = colors.onError,
                    onClick = {
                        repository.endCall(call.id) { errorText = it }
                        finishCallLocally()
                    }
                )
            }
            }
        } else {
            CircularProgressIndicator(color = colors.primary)
            Spacer(Modifier.height(16.dp))
            Text(statusText, color = colors.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    repository.endCall(call.id) { errorText = it }
                    finishCallLocally()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.error)
            ) { Text("Cancel call") }
        }
    }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    background: Color,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            color = background,
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconTint ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

private fun formatKhahishCallDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
