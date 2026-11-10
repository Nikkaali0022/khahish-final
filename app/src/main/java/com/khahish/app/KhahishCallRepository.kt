package com.khahish.app

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

data class KhahishCall(
    val id: String = "",
    val callerUid: String = "",
    val calleeUid: String = "",
    val callerName: String = "",
    val calleeName: String = "",
    val channelName: String = "",
    val callType: String = "voice",
    val status: String = "ringing",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val acceptedAt: Long = 0L,
    val endedAt: Long = 0L
) {
    val isVideo: Boolean get() = callType == "video"

    companion object {
        fun from(document: DocumentSnapshot): KhahishCall = KhahishCall(
            id = document.id,
            callerUid = document.getString("callerUid").orEmpty(),
            calleeUid = document.getString("calleeUid").orEmpty(),
            callerName = document.getString("callerName").orEmpty(),
            calleeName = document.getString("calleeName").orEmpty(),
            channelName = document.getString("channelName").orEmpty(),
            callType = document.getString("callType") ?: "voice",
            status = document.getString("status") ?: "ringing",
            createdAt = document.getLong("createdAt") ?: 0L,
            updatedAt = document.getLong("updatedAt") ?: 0L,
            acceptedAt = document.getLong("acceptedAt") ?: 0L,
            endedAt = document.getLong("endedAt") ?: 0L
        )
    }
}

/**
 * Firestore signaling for one-to-one calls.
 *
 * IMPORTANT: Firestore security rules must restrict each call document to its
 * callerUid/calleeUid. Do not deploy calling until those rules are in place.
 */
class KhahishCallRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val calls = db.collection("calls")

    fun createOutgoingCall(
        callerUid: String,
        callerName: String,
        calleeUid: String,
        calleeName: String,
        isVideo: Boolean,
        onSuccess: (KhahishCall) -> Unit,
        onError: (String) -> Unit
    ) {
        if (callerUid.isBlank() || calleeUid.isBlank() || callerUid == calleeUid) {
            onError("Invalid call participants.")
            return
        }

        val callId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val call = KhahishCall(
            id = callId,
            callerUid = callerUid,
            calleeUid = calleeUid,
            callerName = callerName.ifBlank { "Khahish User" },
            calleeName = calleeName.ifBlank { "Khahish User" },
            channelName = "khahish_${callId.replace("-", "")}",
            callType = if (isVideo) "video" else "voice",
            status = "ringing",
            createdAt = now,
            updatedAt = now
        )

        calls.document(callId).set(
            mapOf(
                "callerUid" to call.callerUid,
                "calleeUid" to call.calleeUid,
                "callerName" to call.callerName,
                "calleeName" to call.calleeName,
                "participants" to listOf(call.callerUid, call.calleeUid),
                "channelName" to call.channelName,
                "callType" to call.callType,
                "status" to call.status,
                "createdAt" to call.createdAt,
                "updatedAt" to call.updatedAt
            )
        ).addOnSuccessListener {
            KhahishPushClient.notifyIncomingCall(call.id)
            onSuccess(call)
        }.addOnFailureListener { error ->
            onError(error.message ?: "Unable to start the call.")
        }
    }

    fun listenToCall(
        callId: String,
        onUpdate: (KhahishCall?) -> Unit
    ): ListenerRegistration = calls.document(callId)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                onUpdate(null)
            } else {
                onUpdate(KhahishCall.from(snapshot))
            }
        }

    fun listenForIncomingCalls(
        uid: String,
        onIncoming: (KhahishCall?) -> Unit
    ): ListenerRegistration = calls
        .whereEqualTo("calleeUid", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onIncoming(null)
                return@addSnapshotListener
            }
            val incoming = snapshot?.documents
                ?.map(KhahishCall::from)
                ?.filter { it.status == "ringing" }
                ?.maxByOrNull { it.createdAt }
            onIncoming(incoming)
        }

    fun listenForCallHistory(
        uid: String,
        onCalls: (List<KhahishCall>) -> Unit
    ): ListenerRegistration = calls
        .whereArrayContains("participants", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                onCalls(emptyList())
            } else {
                onCalls(
                    snapshot.documents
                        .map(KhahishCall::from)
                        .sortedByDescending { it.createdAt }
                )
            }
        }

    fun acceptCall(callId: String, onError: (String) -> Unit = {}) {
        updateStatus(callId, "accepted", onError)
    }

    fun rejectCall(callId: String, onError: (String) -> Unit = {}) {
        updateStatus(callId, "rejected", onError)
    }

    fun endCall(callId: String, onError: (String) -> Unit = {}) {
        updateStatus(callId, "ended", onError)
    }

    fun markMissed(callId: String, onError: (String) -> Unit = {}) {
        updateStatus(callId, "missed", onError)
    }

    private fun updateStatus(
        callId: String,
        status: String,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to now
        )
        if (status == "accepted") updates["acceptedAt"] = now
        if (status == "ended") updates["endedAt"] = now

        calls.document(callId).update(updates)
            .addOnFailureListener { error ->
            onError(error.message ?: "Unable to update call status.")
        }
    }
}
