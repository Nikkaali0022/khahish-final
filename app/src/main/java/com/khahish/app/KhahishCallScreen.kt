package com.khahish.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import io.agora.rtc2.video.VideoCanvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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
    var speakerEnabled by remember(call.id) { mutableStateOf(true) }
    var credentials by remember(call.id) { mutableStateOf<AgoraRtcCredentials?>(null) }
    var session by remember(call.id) { mutableStateOf<KhahishAgoraSession?>(null) }
    var connectionStarted by remember(call.id) { mutableStateOf(false) }
    var remoteUid by remember(call.id) { mutableStateOf<Int?>(null) }
    val latestSession by rememberUpdatedState(session)

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
                    onJoined = { isJoined = true; statusText = "Connected" },
                    onRemoteUserJoined = { uid -> remoteUid = uid; statusText = "Connected" },
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

    DisposableEffect(call.id) {
        val registration = KhahishCallRepository().listenToCall(call.id) { updated ->
            if (updated != null) {
                currentCall = updated
                when (updated.status) {
                    "accepted" -> {
                        statusText = "Connecting…"
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

    val displayName = if (isIncoming) call.callerName else call.calleeName

    Column(
        Modifier.fillMaxSize().background(Color(0xFF10231F)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (call.isVideo) "Video call" else "Voice call", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(30.dp))
        Box(
            Modifier.size(148.dp).background(Color(0xFF1B594B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 58.sp
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            displayName.ifBlank { "Khahish user" },
            color = Color.White,
            fontSize = 27.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(statusText, color = Color(0xFFB7D8CE), fontSize = 14.sp)

        if (call.isVideo && credentials != null) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AndroidView(
                    modifier = Modifier.weight(1f).height(280.dp),
                    factory = { viewContext ->
                        SurfaceView(viewContext).also { surface ->
                            session?.setupLocalVideo(surface)
                        }
                    }
                )
                if (remoteUid != null) {
                    AndroidView(
                        modifier = Modifier.weight(1f).height(280.dp),
                        factory = { viewContext ->
                            SurfaceView(viewContext).also { surface ->
                                session?.setupRemoteVideo(surface, remoteUid!!)
                            }
                        }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(50.dp))
            Box(
                Modifier.size(112.dp).background(Color(0xFF1B594B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(errorText, color = Color(0xFFFFB4AB), fontSize = 12.sp)
        }

        Spacer(Modifier.height(42.dp))

        if (isIncoming && currentCall.status == "ringing") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(
                    onClick = {
                        repository.rejectCall(call.id) { errorText = it }
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("Decline") }
                Button(
                    onClick = {
                        repository.acceptCall(call.id) { errorText = it }
                        connectIfAllowed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18A889))
                ) { Text("Accept") }
            }
        } else if (currentCall.status == "accepted" || isJoined) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = {
                    isMuted = !isMuted
                    session?.setMicrophoneMuted(isMuted)
                }) {
                    Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
                }
                if (call.isVideo) {
                    IconButton(onClick = {
                        cameraEnabled = !cameraEnabled
                        session?.setCameraEnabled(cameraEnabled)
                    }) {
                        Icon(if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, null, tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    speakerEnabled = !speakerEnabled
                    session?.setSpeakerEnabled(speakerEnabled)
                }) {
                    Icon(if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, tint = Color.White)
                }
                IconButton(onClick = {
                    repository.endCall(call.id) { errorText = it }
                    session?.leave()
                    onExit()
                }) {
                    Icon(Icons.Default.CallEnd, "End call", tint = Color(0xFFFF6B6B), modifier = Modifier.size(34.dp))
                }
            }
        } else {
            CircularProgressIndicator(color = Color(0xFF18A889))
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    repository.endCall(call.id) { errorText = it }
                    onExit()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) { Text("Cancel call") }
        }
    }
}
