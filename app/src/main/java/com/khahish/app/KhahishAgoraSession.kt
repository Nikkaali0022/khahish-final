package com.khahish.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.ScreenCaptureParameters
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import android.view.SurfaceView
import io.agora.rtc2.video.VideoCanvas

/**
 * Owns a single Agora RTC engine instance for an active Khahish call.
 * Runtime microphone/camera permissions must be granted by the UI before join().
 */
class KhahishAgoraSession(
    context: Context,
    private val credentials: AgoraRtcCredentials,
    private val isVideoCall: Boolean,
    private val onJoined: () -> Unit,
    private val onRemoteUserJoined: (Int) -> Unit,
    private val onRemoteUserLeft: (Int) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onTokenRenewalFailure: (String) -> Unit = onFailure
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var released = false
    @Volatile private var joined = false
    @Volatile private var screenSharing = false
    @Volatile private var cameraEnabled = true
    private var engine: RtcEngine? = null

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            joined = true
            onJoined()
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            onRemoteUserJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            onRemoteUserLeft(uid)
        }

        override fun onError(err: Int) {
            onFailure("Agora call error: $err")
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            KhahishAgoraTokenClient.requestToken(
                credentials.callId,
                credentials.channelName,
                onSuccess = { renewed ->
                    if (!released) {
                        engine?.renewToken(renewed.token)
                    }
                },
                onError = { message ->
                    android.util.Log.e("KhahishAgoraSession", "RTC token renewal failed: $message")
                    onTokenRenewalFailure(message)
                }
            )
        }
    }

    fun join() {
        if (released) {
            onFailure("Call session has already ended.")
            return
        }

        try {
            val config = RtcEngineConfig().apply {
                mContext = appContext
                mAppId = credentials.appId
                mEventHandler = eventHandler
            }

            val rtc = RtcEngine.create(config)
            engine = rtc

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.enableAudio()

            if (isVideoCall) {
                rtc.enableVideo()
                rtc.enableLocalVideo(true)
                rtc.muteLocalVideoStream(false)
            } else {
                rtc.disableVideo()
            }

            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = isVideoCall
                autoSubscribeAudio = true
                autoSubscribeVideo = isVideoCall
            }

            val result = rtc.joinChannel(
                credentials.token,
                credentials.channelName,
                credentials.uid,
                options
            )

            if (result < 0) {
                onFailure("Unable to join Agora channel (code $result).")
            }
        } catch (error: Exception) {
            onFailure(error.message ?: "Unable to initialize Agora calling.")
        }
    }

    fun setupLocalVideo(surfaceView: SurfaceView) {
        if (!isVideoCall || released) return
        engine?.enableVideo()
        engine?.enableLocalVideo(true)
        engine?.setupLocalVideo(
            VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, credentials.uid)
        )
        engine?.startPreview()
    }

    fun setupRemoteVideo(surfaceView: SurfaceView, remoteUid: Int) {
        if (!isVideoCall) return
        engine?.setupRemoteVideo(
            VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid)
        )
    }

    /**
     * Starts an Android screen-capture stream in the existing call.
     * The MediaProjection permission must be granted by the system UI first.
     */
    fun startScreenShare(projection: MediaProjection): Int {
        val rtc = engine ?: return -1
        if (released || !joined) return -2
        if (screenSharing) return 0

        return try {
            rtc.enableVideo()
            rtc.setExternalMediaProjection(projection)
            val parameters = ScreenCaptureParameters().apply {
                captureVideo = true
                captureAudio = false
                videoCaptureParameters.width = 720
                videoCaptureParameters.height = 1280
                videoCaptureParameters.framerate = 15
            }
            val startResult = rtc.startScreenCapture(parameters)
            if (startResult < 0) {
                rtc.setExternalMediaProjection(null)
                return startResult
            }

            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = false
                publishScreenCaptureVideo = true
                publishScreenCaptureAudio = false
                autoSubscribeAudio = true
                autoSubscribeVideo = true
            }
            val updateResult = rtc.updateChannelMediaOptions(options)
            if (updateResult < 0) {
                rtc.stopScreenCapture()
                rtc.setExternalMediaProjection(null)
                return updateResult
            }
            screenSharing = true
            0
        } catch (error: Exception) {
            try { rtc.stopScreenCapture() } catch (_: Exception) { }
            try { rtc.setExternalMediaProjection(null) } catch (_: Exception) { }
            -3
        }
    }

    fun stopScreenShare(): Int {
        val rtc = engine ?: return -1
        if (!screenSharing) return 0
        return try {
            val stopResult = rtc.stopScreenCapture()
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = isVideoCall && cameraEnabled
                publishScreenCaptureVideo = false
                publishScreenCaptureAudio = false
                autoSubscribeAudio = true
                autoSubscribeVideo = true
            }
            val updateResult = rtc.updateChannelMediaOptions(options)
            rtc.setExternalMediaProjection(null)
            if (!isVideoCall) rtc.disableVideo()
            screenSharing = false
            if (stopResult < 0) stopResult else updateResult
        } catch (_: Exception) {
            screenSharing = false
            -3
        }
    }

    fun setMicrophoneMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
        if (isVideoCall && !released) {
            if (enabled) {
                engine?.enableVideo()
                engine?.enableLocalVideo(true)
                engine?.muteLocalVideoStream(false)
                engine?.startPreview()
            } else {
                engine?.muteLocalVideoStream(true)
                engine?.enableLocalVideo(false)
                engine?.stopPreview()
            }
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        setAudioRoute(if (enabled) "speaker" else "earpiece")
    }

    fun setAudioRoute(route: String) {
        if (released) return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            when (route) {
                "speaker" -> {
                    engine?.setEnableSpeakerphone(true)
                    if (Build.VERSION.SDK_INT >= 31) {
                        val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speaker != null) audioManager.setCommunicationDevice(speaker)
                    }
                }
                "bluetooth" -> {
                    engine?.setEnableSpeakerphone(false)
                    if (Build.VERSION.SDK_INT >= 31) {
                        val bluetooth = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        }
                        if (bluetooth != null && audioManager.setCommunicationDevice(bluetooth)) return
                    }
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    run { audioManager.isBluetoothScoOn = true }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    run { audioManager.isBluetoothScoOn = false }
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
                    engine?.setEnableSpeakerphone(false)
                }
            }
        } catch (_: SecurityException) {
            if (route == "speaker") engine?.setEnableSpeakerphone(true)
            else engine?.setEnableSpeakerphone(false)
        } catch (_: Exception) {
            if (route == "speaker") engine?.setEnableSpeakerphone(true)
            else engine?.setEnableSpeakerphone(false)
        }
    }

    fun leave() {
        if (released) return
        released = true
        try {
            if (screenSharing) engine?.stopScreenCapture()
            screenSharing = false
            engine?.leaveChannel()
        } catch (_: Exception) {
        }
        try {
            engine?.disableVideo()
            engine?.disableAudio()
        } catch (_: Exception) {
        }
        engine = null
        try {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) { }
        try {
            RtcEngine.destroy()
        } catch (_: Exception) {
        }
    }
}
