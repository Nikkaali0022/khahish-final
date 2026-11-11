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
import androidx.compose.material.icons.Icons
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

private val CallScreenBackground = Color(0xFF0B1513)
private val CallScreenGreen = Color(0xFF18A889)
private val CallScreenMuted = Color(0xFF9BAEAA)

@Composable
fun KhahishCallScreen(
    call: KhahishCall,
    isIncoming: Boolean,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { KhahishCallRepository() }

    var currentCall by remember(call.id) { mutableStateOf(call) }
    var statusText by remember(call.id) {
        mutableStateOf(if (isIncoming) "Incoming call" else "Calling…")
    }
    var errorText by remember(call.id) { mutableStateOf("") }
    var isJoined by remember(call.id) { mutableStateOf(false) }
    var isMuted by remember(call.id) { mutableStateOf(false) }
    var cameraEnabled by remember(call.id) { mutableStateOf(true) }
    var speakerEnabled by remember(call.id) { mutableStateOf(false) }
    var credentials by remember(call.id) { mutableStateOf<AgoraRtcCredentials?>(null) }
    var session by remember(call.id) { mutableStateOf<KhahishAgoraSession?>(null) }
    var connectionStarted by remember(call.id) { mutableStateOf(false) }
    var remoteUid by remember(call.id) { mutableStateOf<Int?>(null) }
    var peerPhotoUrl by remember(call.id) { mutableStateOf("") }
    var callElapsedSeconds by remember(call.id) { mutableIntStateOf(0) }
    val latestSession by rememberUpdatedState(session)

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
        KhahishAgoraTokenClient.requestToken(
            currentCall.channelName,
            onSuccess = { token ->
                credentials = token
                val rtc = KhahishAgoraSession(
                    context = context,
                    credentials = token,
                    isVideoCall = currentCall.isVideo,
                    onJoined = { isJoined = true; statusText = "Connected"; errorText = "" },
                    onRemoteUserJoined = { uid -> remoteUid = uid; statusText = "Connected"; errorText = "" },
                    onRemoteUserLeft = { statusText = "The other person left the call" },
                    onFailure = { errorText = it }
                )
                session = rtc
                rtc.join()
            },
            onError = { errorText = it }
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
                        if (!isIncoming && session == null && !connectionStarted) {
                            connectIfAllowed()
                        }
                    }
                    "rejected" -> { statusText = "Call declined"; onExit() }
                    "ended" -> { statusText = "Call ended"; onExit() }
                    "missed" -> { statusText = "Missed call"; onExit() }
                    "ringing" -> statusText = if (isIncoming) "Incoming call" else "Ringing…"
                }
            }
        }
        onDispose {
            registration.remove()
            latestSession?.leave()
        }
    }

    BackHandler(enabled = true) {
        repository.endCall(call.id) { errorText = it }
        session?.leave()
        onExit()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (currentCall.isVideo) "KHAHISH  •  VIDEO CALL" else "KHAHISH  •  VOICE CALL",
            color = CallScreenMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(42.dp))

        Box(
            modifier = Modifier
                .size(226.dp)
                .border(4.dp, CallScreenGreen, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B302B)),
            contentAlignment = Alignment.Center
        ) {
            if (peerPhotoUrl.isNotBlank()) {
                AsyncImage(
                    model = peerPhotoUrl,
                    contentDescription = "$displayName profile picture",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            displayName.ifBlank { "Khahish user" },
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(Modifier.height(12.dp))

        if (isConnected) {
            Text(
                formatKhahishCallDuration(callElapsedSeconds),
                color = Color(0xFFB8C9C4),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text("Connected", color = CallScreenGreen, fontSize = 13.sp)
        } else {
            Text(statusText, color = CallScreenMuted, fontSize = 15.sp)
        }

        if (call.isVideo && credentials != null) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AndroidView(
                    modifier = Modifier.weight(1f).height(190.dp),
                    factory = { viewContext ->
                        SurfaceView(viewContext).also { surface ->
                            session?.setupLocalVideo(surface)
                        }
                    }
                )
                if (remoteUid != null) {
                    AndroidView(
                        modifier = Modifier.weight(1f).height(190.dp),
                        factory = { viewContext ->
                            SurfaceView(viewContext).also { surface ->
                                session?.setupRemoteVideo(surface, remoteUid!!)
                            }
                        }
                    )
                }
            }
        }

        if (errorText.isNotBlank() && !isConnected) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorText,
                color = Color(0xFFFFB4AB),
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
                        onExit()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("Decline") }
                Button(
                    onClick = {
                        repository.acceptCall(call.id) { errorText = it }
                        connectIfAllowed()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CallScreenGreen)
                ) { Text("Accept") }
            }
        } else if (currentCall.status == "accepted" || isJoined) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    background = Color(0xFF263A35),
                    onClick = {
                        isMuted = !isMuted
                        session?.setMicrophoneMuted(isMuted)
                    }
                )
                if (call.isVideo) {
                    CallControlButton(
                        icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = "Camera",
                        background = Color(0xFF263A35),
                        onClick = {
                            cameraEnabled = !cameraEnabled
                            session?.setCameraEnabled(cameraEnabled)
                        }
                    )
                }
                CallControlButton(
                    icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = "Speaker",
                    background = Color(0xFF263A35),
                    onClick = {
                        speakerEnabled = !speakerEnabled
                        session?.setSpeakerEnabled(speakerEnabled)
                    }
                )
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    background = Color(0xFFB3261E),
                    iconTint = Color.White,
                    onClick = {
                        repository.endCall(call.id) { errorText = it }
                        session?.leave()
                        onExit()
                    }
                )
            }
        } else {
            CircularProgressIndicator(color = CallScreenGreen)
            Spacer(Modifier.height(16.dp))
            Text(statusText, color = CallScreenMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    repository.endCall(call.id) { errorText = it }
                    session?.leave()
                    onExit()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) { Text("Cancel call") }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    background: Color,
    iconTint: Color = Color.White,
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
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = CallScreenMuted, fontSize = 11.sp)
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
