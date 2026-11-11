package com.khahish.app

import android.content.Context
import io.agora.rtc2.ChannelMediaOptions
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
    private val onFailure: (String) -> Unit
) {
    private val appContext = context.applicationContext
    @Volatile private var released = false
    private var engine: RtcEngine? = null

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
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
                credentials.channelName,
                onSuccess = { renewed ->
                    if (!released) {
                        engine?.renewToken(renewed.token)
                    }
                },
                onError = { message -> onFailure(message) }
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

            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.enableAudio()

            if (isVideoCall) {
                rtc.enableVideo()
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
        if (!isVideoCall) return
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

    fun setMicrophoneMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        if (isVideoCall) {
            engine?.enableLocalVideo(enabled)
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        engine?.setEnableSpeakerphone(enabled)
    }

    fun leave() {
        if (released) return
        released = true
        try {
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
            RtcEngine.destroy()
        } catch (_: Exception) {
        }
    }
}
