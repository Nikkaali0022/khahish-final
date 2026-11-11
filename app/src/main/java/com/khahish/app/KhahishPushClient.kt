package com.khahish.app

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Requests authenticated push delivery from the Khahish Cloudflare Worker.
 *
 * Firebase service-account credentials stay on the server and are never
 * included in this Android application.
 */
object KhahishPushClient {
    private const val TAG = "KhahishPushClient"
    private const val WORKER_BASE_URL =
        "https://khahish-agora-token.pakistanivlogger57.workers.dev"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun notifyMessage(chatId: String, messageId: String) {
        if (chatId.isBlank() || messageId.isBlank()) return
        postAuthenticated(
            "/push/message",
            JSONObject()
                .put("chatId", chatId)
                .put("messageId", messageId)
        )
    }

    fun notifyIncomingCall(callId: String) {
        if (callId.isBlank()) return
        postAuthenticated(
            "/push/call",
            JSONObject().put("callId", callId)
        )
    }

    private fun postAuthenticated(path: String, payload: JSONObject) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val idToken = result.token
                if (idToken.isNullOrBlank()) {
                    Log.w(TAG, "Push request skipped: Firebase ID token is empty.")
                    return@addOnSuccessListener
                }

                Thread {
                    try {
                        val body = payload.toString().toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                        val request = Request.Builder()
                            .url(WORKER_BASE_URL + path)
                            .header("Authorization", "Bearer " + idToken)
                            .header("Accept", "application/json")
                            .post(body)
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.w(
                                    TAG,
                                    "Push dispatch returned HTTP " + response.code
                                )
                            }
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Push dispatch request failed.", error)
                    }
                }.start()
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Could not obtain Firebase ID token for push dispatch.", error)
            }
    }
}
