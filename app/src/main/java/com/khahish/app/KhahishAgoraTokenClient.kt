package com.khahish.app

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Requests short-lived Agora RTC credentials from the Khahish token Worker.
 *
 * The Agora App Certificate is never stored in or requested by the Android app.
 */
data class AgoraRtcCredentials(
    val token: String,
    val appId: String,
    val channelName: String,
    val uid: Int,
    val expiresIn: Int
)

object KhahishAgoraTokenClient {
    private const val TOKEN_ENDPOINT =
        "https://khahish-agora-token.pakistanivlogger57.workers.dev/rtc/token"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun requestToken(
        channelName: String,
        onSuccess: (AgoraRtcCredentials) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanChannel = channelName.trim()
        if (cleanChannel.isEmpty() || cleanChannel.toByteArray(Charsets.UTF_8).size >= 64) {
            onError("Invalid call channel.")
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError("Please sign in again before starting a call.")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val idToken = result.token
                if (idToken.isNullOrBlank()) {
                    onError("Unable to verify your account. Please try again.")
                    return@addOnSuccessListener
                }

                Thread {
                    try {
                        val body = JSONObject()
                            .put("channelName", cleanChannel)
                            .toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaType())

                        val request = Request.Builder()
                            .url(TOKEN_ENDPOINT)
                            .header("Authorization", "Bearer $idToken")
                            .header("Accept", "application/json")
                            .post(body)
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            val responseText = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val serverMessage = try {
                                    JSONObject(responseText).optString("error")
                                } catch (_: Exception) {
                                    ""
                                }
                                throw IllegalStateException(
                                    serverMessage.ifBlank {
                                        "Token service error (${response.code})."
                                    }
                                )
                            }

                            val json = JSONObject(responseText)
                            val credentials = AgoraRtcCredentials(
                                token = json.getString("token"),
                                appId = json.getString("appId"),
                                channelName = json.getString("channelName"),
                                uid = json.getInt("uid"),
                                expiresIn = json.optInt("expiresIn", 3600)
                            )

                            if (
                                credentials.token.isBlank() ||
                                credentials.appId.isBlank() ||
                                credentials.channelName != cleanChannel ||
                                credentials.uid <= 0
                            ) {
                                throw IllegalStateException("Token service returned invalid credentials.")
                            }

                            mainHandler.post { onSuccess(credentials) }
                        }
                    } catch (error: Exception) {
                        val message = error.message ?: "Unable to get calling credentials."
                        mainHandler.post { onError(message) }
                    }
                }.start()
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Unable to verify your account.")
            }
    }
}
