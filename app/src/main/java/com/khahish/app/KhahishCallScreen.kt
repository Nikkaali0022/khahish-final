package com.khahish.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
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


@Composable
fun KhahishCallScreen(
    call: KhahishCall,
    isIncoming: Boolean,
    onExit: () -> Unit,
    autoAcceptIncoming: Boolean = false
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
    var selectedAudioRoute by remember(call.id) { mutableStateOf(if (call.isVideo) "speaker" else "earpiece") }
    var audioRouteMenuExpanded by remember(call.id) { mutableStateOf(false) }
    var callMoreMenuExpanded by remember(call.id) { mutableStateOf(false) }
    var callInfoDialog by remember(call.id) { mutableStateOf<String?>(null) }
    var credentials by remember(call.id) { mutableStateOf<AgoraRtcCredentials?>(null) }
    var session by remember(call.id) { mutableStateOf<KhahishAgoraSession?>(null) }
    var connectionStarted by remember(call.id) { mutableStateOf(false) }
    var remoteUid by remember(call.id) { mutableStateOf<Int?>(null) }
    var peerPhotoUrl by remember(call.id) { mutableStateOf("") }
    var callElapsedSeconds by remember(call.id) { mutableIntStateOf(0) }
    val latestSession by rememberUpdatedState(session)

    val peerUid = if (isIncoming) currentCall.callerUid else currentCall.calleeUid
    val displayName = if (isIncoming) currentCall.callerName else currentCall.calleeName
    val isConnected = isJoined

    val requiredPermissions = remember(call.isVideo) {
        if (call.isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    fun permissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCredentialsAndJoin() {
        statusText = "Connecting securely…"
        errorText = ""
        KhahishAgoraTokenClient.requestToken(
            currentCall.id,
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
                    onFailure = { message -> statusText = "Connection failed"; errorText = message },
                    onTokenRenewalFailure = { message ->
                        if (isJoined) {
                            statusText = "Connected"
                            errorText = if (message.contains("server configuration", ignoreCase = true)) {
                                "Your call is still connected, but the server could not renew its calling credentials. Check the Cloudflare Worker configuration to support longer calls."
                            } else {
                                "Your call is still connected, but its calling credentials could not be renewed: ${message}"
                            }
                        } else {
                            statusText = "Connection failed"
                            errorText = message
                        }
                    }
                )
                session = rtc
                rtc.setAudioRoute(selectedAudioRoute)
                rtc.join()
            },
            onError = { message -> statusText = "Connection failed"; errorText = message }
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

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            selectedAudioRoute = "bluetooth"
            session?.setAudioRoute("bluetooth")
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
                        KhahishMessagingService.cancelIncomingCallNotification(context, call.id)
                        statusText = if (isJoined) "Connected" else "Connecting audio…"
                        if (!isIncoming && session == null && !connectionStarted) connectIfAllowed()
                    }
                    "rejected" -> { KhahishMessagingService.cancelIncomingCallNotification(context, call.id); errorText = ""; statusText = "Call declined"; onExit() }
                    "ended" -> { KhahishMessagingService.cancelIncomingCallNotification(context, call.id); errorText = ""; statusText = "Call ended"; onExit() }
                    "missed" -> { KhahishMessagingService.cancelIncomingCallNotification(context, call.id); errorText = ""; statusText = "Missed call"; onExit() }
                    "ringing" -> statusText = if (isIncoming) "Incoming call" else "Ringing…"
                }
            }
        }
        onDispose {
            registration.remove()
            latestSession?.leave()
        }
    }

    LaunchedEffect(call.id, autoAcceptIncoming, currentCall.status) {
        if (autoAcceptIncoming && isIncoming &&
            (currentCall.status == "ringing" || currentCall.status == "accepted")
        ) {
            if (currentCall.status == "ringing") {
                repository.acceptCall(call.id) { message ->
                    errorText = message
                    statusText = "Could not answer"
                }
            }
            connectIfAllowed()
        }
    }

    DisposableEffect(call.id, selectedAudioRoute, isJoined, call.isVideo) {
        var proximityLock: PowerManager.WakeLock? = null
        if (!call.isVideo && isJoined && selectedAudioRoute == "earpiece") {
            try {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Khahish:CallProximity").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
            } catch (_: Exception) { proximityLock = null }
        }
        onDispose {
            try { if (proximityLock?.isHeld == true) proximityLock?.release() } catch (_: Exception) { }
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

    LaunchedEffect(call.id, currentCall.status, currentCall.acceptedAt, currentCall.updatedAt, isJoined) {
        val connectedAt = currentCall.acceptedAt.takeIf { it > 0L } ?: currentCall.updatedAt
        if (isJoined && currentCall.status == "accepted" && connectedAt > 0L) {
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
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (currentCall.isVideo) "KHAHISH  •  VIDEO CALL" else "KHAHISH  •  VOICE CALL",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
            }
            Box {
                IconButton(onClick = { callMoreMenuExpanded = true }) {
                    Text("⋮", color = colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = callMoreMenuExpanded,
                    onDismissRequest = { callMoreMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Screen Share") },
                        leadingIcon = { Text("▣", color = colors.primary, fontSize = 18.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Screen Share"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add Participant") },
                        leadingIcon = { Text("＋", color = colors.primary, fontSize = 18.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Add Participant"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open Chat") },
                        leadingIcon = { Text("▤", color = colors.primary, fontSize = 18.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Open Chat"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Audio Devices") },
                        leadingIcon = { Text("◖))", color = colors.primary, fontSize = 16.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            audioRouteMenuExpanded = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Live Captions") },
                        leadingIcon = { Text("CC", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Live Captions"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Call Security") },
                        leadingIcon = { Text("◆", color = colors.primary, fontSize = 18.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Call Security"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Call Settings") },
                        leadingIcon = { Text("⚙", color = colors.primary, fontSize = 18.sp) },
                        onClick = {
                            callMoreMenuExpanded = false
                            callInfoDialog = "Call Settings"
                        }
                    )
                }
            }
        }

        if (callInfoDialog != null) {
            val selectedOption = callInfoDialog!!
            AlertDialog(
                onDismissRequest = { callInfoDialog = null },
                title = { Text(selectedOption) },
                text = {
                    Text(
                        when (selectedOption) {
                            "Screen Share" -> "Screen sharing ka option yahan add kar diya gaya hai. Actual screen broadcast ke liye Android screen-capture permission aur Agora screen-track integration abhi required hai."
                            "Add Participant" -> "Multi-person calling abhi is one-to-one call engine mein enabled nahi hai. Is option ko group-call support ke saath activate karna hoga."
                            "Open Chat" -> "Call ke dauran chat panel abhi connected nahi hai. Is waqt call ko band kiye baghair chat panel open nahi kiya ja sakta."
                            "Live Captions" -> "Live captions ke liye speech-to-text service aur language support integration abhi required hai."
                            "Call Security" -> "Call security details ko verify karne ke liye secure-call verification flow abhi integrate nahi hua."
                            else -> "Call settings ke advanced controls ko aglay implementation step mein activate kiya jayega. Audio Devices se abhi Earpiece, Phone Speaker aur connected Bluetooth select kar sakte hain."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { callInfoDialog = null }) { Text("OK") }
                }
            )
        }

        Spacer(Modifier.height(42.dp))

        Box(
            modifier = Modifier
                .size(226.dp)
                .border(4.dp, colors.primary, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
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
                    color = colors.onSurface,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            displayName.ifBlank { "Khahish user" },
            color = colors.onBackground,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(Modifier.height(12.dp))

        if (isConnected) {
            Text(
                formatKhahishCallDuration(callElapsedSeconds),
                color = colors.onSurfaceVariant,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text("Connected", color = colors.primary, fontSize = 13.sp)
        } else {
            Text(statusText, color = colors.onSurfaceVariant, fontSize = 15.sp)
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
                        onExit()
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
        } else if (isJoined) {
            Row(
                Modifier.fillMaxWidth(),
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
                Box {
                    CallControlButton(
                        icon = when (selectedAudioRoute) {
                            "speaker" -> Icons.Default.VolumeUp
                            "bluetooth" -> Icons.Default.Bluetooth
                            else -> Icons.Default.VolumeOff
                        },
                        label = "Audio output",
                        background = colors.surfaceVariant,
                        onClick = { audioRouteMenuExpanded = true }
                    )
                    DropdownMenu(expanded = audioRouteMenuExpanded, onDismissRequest = { audioRouteMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Earpiece") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                            trailingIcon = { if (selectedAudioRoute == "earpiece") Icon(Icons.Default.Check, null, tint = colors.primary) },
                            onClick = { selectedAudioRoute = "earpiece"; session?.setAudioRoute("earpiece"); audioRouteMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Phone Speaker") },
                            leadingIcon = { Icon(Icons.Default.VolumeUp, null) },
                            trailingIcon = { if (selectedAudioRoute == "speaker") Icon(Icons.Default.Check, null, tint = colors.primary) },
                            onClick = { selectedAudioRoute = "speaker"; session?.setAudioRoute("speaker"); audioRouteMenuExpanded = false }
                        )
                        if (hasKhahishBluetoothAudio(context)) {
                            DropdownMenuItem(
                                text = { Text("Bluetooth") },
                                leadingIcon = { Icon(Icons.Default.Bluetooth, null) },
                                trailingIcon = { if (selectedAudioRoute == "bluetooth") Icon(Icons.Default.Check, null, tint = colors.primary) },
                                onClick = {
                                audioRouteMenuExpanded = false
                                if (Build.VERSION.SDK_INT >= 31 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    selectedAudioRoute = "bluetooth"
                                    session?.setAudioRoute("bluetooth")
                                }
                            }
                            )
                        }
                    }
                }
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    background = colors.error,
                    iconTint = colors.onError,
                    onClick = {
                        repository.endCall(call.id) { errorText = it }
                        session?.leave()
                        onExit()
                    }
                )
            }
        } else {
            CircularProgressIndicator(color = colors.primary)
            Spacer(Modifier.height(16.dp))
            Text(statusText, color = colors.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    repository.endCall(call.id) { errorText = it }
                    session?.leave()
                    onExit()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.error)
            ) { Text("Cancel call") }
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

private fun hasKhahishBluetoothAudio(context: Context): Boolean = try {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
} catch (_: Exception) { false }

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
