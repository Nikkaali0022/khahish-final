package com.khahish.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import android.view.SurfaceView
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.BeautyOptions

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
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                credentials.callId,
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

    fun switchCamera() {
        if (isVideoCall) engine?.switchCamera()
    }

    fun setBeautyFilter(filter: String) {
        if (!isVideoCall) return
        val rtc = engine ?: return
        when (filter) {
            "Natural" -> rtc.setBeautyEffectOptions(false, BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, 0.0f, 0.0f, 0.0f, 0.0f))
            "Soft Skin" -> rtc.setBeautyEffectOptions(true,
                BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, 0.45f, 0.68f, 0.12f, 0.35f))
            "Glow" -> rtc.setBeautyEffectOptions(true,
                BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_HIGH, 0.82f, 0.52f, 0.18f, 0.42f))
            "Rosy" -> rtc.setBeautyEffectOptions(true,
                BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, 0.52f, 0.48f, 0.38f, 0.34f))
            "Smooth Plus" -> rtc.setBeautyEffectOptions(true,
                BeautyOptions(BeautyOptions.LIGHTENING_CONTRAST_NORMAL, 0.58f, 0.90f, 0.16f, 0.30f))
        }
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
            engine?.setEnableSpeakerphone(route == "speaker")
        } catch (_: Exception) {
            engine?.setEnableSpeakerphone(route == "speaker")
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        engine?.setEnableSpeakerphone(enabled)
    }

    fun leave() {
        if (released) return
        released = true
        try {
            @Suppress("DEPRECATION")
            run { audioManager.isBluetoothScoOn = false }
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) { }
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
