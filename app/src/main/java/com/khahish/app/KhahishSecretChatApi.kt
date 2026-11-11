package com.khahish.app

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class SecretChatLockConfig(
    val hasPassword: Boolean,
    val lockedContactUids: Set<String>
)

internal object KhahishSecretChatApi {
    private const val BASE_URL =
        "https://khahish-agora-token.pakistanivlogger57.workers.dev/secret-chats"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun loadConfig(
        onSuccess: (SecretChatLockConfig) -> Unit,
        onError: (String) -> Unit
    ) {
        request("GET", "/config", null, { json ->
            val values = json.optJSONArray("lockedContactUids") ?: JSONArray()
            val contacts = buildSet {
                for (index in 0 until values.length()) {
                    values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            onSuccess(SecretChatLockConfig(json.optBoolean("hasPassword"), contacts))
        }, onError)
    }

    fun createPassword(password: String, onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        request(
            "POST", "/password",
            JSONObject().put("action", "create").put("password", password),
            { onSuccess(parseExpiry(it)) }, onError
        )
    }

    fun unlock(password: String, onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        request(
            "POST", "/unlock",
            JSONObject().put("password", password),
            { onSuccess(parseExpiry(it)) }, onError
        )
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        request(
            "POST", "/password",
            JSONObject()
                .put("action", "change")
                .put("currentPassword", currentPassword)
                .put("newPassword", newPassword),
            { onSuccess() }, onError
        )
    }

    fun updateContact(
        action: String,
        contactUid: String,
        onSuccess: (Set<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        request(
            "POST", "/contacts",
            JSONObject().put("action", action).put("contactUid", contactUid),
            { json ->
                val values = json.optJSONArray("lockedContactUids") ?: JSONArray()
                val contacts = buildSet {
                    for (index in 0 until values.length()) {
                        values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                onSuccess(contacts)
            },
            onError
        )
    }

    fun lock(onComplete: () -> Unit = {}) {
        request("POST", "/lock", JSONObject(), { onComplete() }, { onComplete() })
    }

    private fun parseExpiry(json: JSONObject): Long {
        val raw = json.optString("expiresAt")
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis() + 20 * 60_000L
        }
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject?,
        onSuccess: (JSONObject) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError("Please sign in again.")
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token
                if (token.isNullOrBlank()) {
                    onError("Could not verify your Khahish account.")
                    return@addOnSuccessListener
                }
                Thread {
                    try {
                        val builder = Request.Builder()
                            .url(BASE_URL + path)
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/json")
                        val request = if (method == "GET") {
                            builder.get().build()
                        } else {
                            val body = (payload ?: JSONObject()).toString()
                                .toRequestBody("application/json; charset=utf-8".toMediaType())
                            builder.post(body).build()
                        }
                        client.newCall(request).execute().use { response ->
                            val raw = response.body?.string().orEmpty()
                            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                            if (!response.isSuccessful || json.optBoolean("ok", method == "GET").not() && method != "GET") {
                                val message = json.optString("error")
                                    .ifBlank { "Secret Locked Chats service error (${response.code})." }
                                throw IllegalStateException(message)
                            }
                            mainHandler.post { onSuccess(json) }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            onError(error.message ?: "Secret Locked Chats service unavailable.")
                        }
                    }
                }.start()
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Could not verify your Khahish account.")
            }
    }
}
