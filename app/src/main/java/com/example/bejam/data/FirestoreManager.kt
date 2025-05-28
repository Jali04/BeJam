package com.example.bejam.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirestoreManager {
    private val fs = Firebase.firestore
    private const val REQ = "friend_requests"
    private const val FRI = "friends"

    data class Request(
        val id: String = "",
        val fromUid: String = "",
        val toUid: String = "",
        val status: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    /** Send a PENDING request from currentUser → toUid */
    fun sendRequest(fromUid: String, toUid: String): Task<Void> {
        // Deterministic request ID: prevent duplicate requests
        val requestId = "${fromUid}_$toUid"
        val docRef = fs.collection(REQ).document(requestId)

        // now build your Request including the ID and a timestamp
        val req = Request(
            id        = docRef.id,
            fromUid   = fromUid,
            toUid     = toUid,
            status    = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        // write it
        return docRef.set(req)
    }

    /** Listen to incoming PENDING requests for you */
    fun observeIncomingRequests(myUid: String): Flow<List<Request>> = callbackFlow {
        val sub = fs.collection(REQ)
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status","PENDING")
            .addSnapshotListener { snap, err ->
                if (err!=null) { close(err); return@addSnapshotListener }
                val list = snap!!.documents.map { doc ->
                    Request(
                        id         = doc.id,
                        fromUid    = doc.getString("fromUid")!!,
                        toUid      = doc.getString("toUid")!!,
                        status     = doc.getString("status")!!,
                        timestamp  = doc.getLong("timestamp")!!
                    )
                }
                trySend(list)
            }
        awaitClose { sub.remove() }
    }

    /** Respond to a request (ACCEPT or REJECT).  If ACCEPT, also write to `friends`. */
    suspend fun respondToRequest(
        req: Request,
        accept: Boolean,
        myUid: String
    ) {
        if (!accept) {
            // Declined: remove the pending request so it can be resent
            fs.collection(REQ).document(req.id)
                .delete().await()
            return
        }

        // Accepted: update the request status
        fs.collection(REQ).document(req.id)
            .update("status", "ACCEPTED").await()

        // Create the mutual friendship entry
        val sortedIds = listOf(req.fromUid, myUid).sorted()
        val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
        fs.collection(FRI).document(friendshipId).set(mapOf(
            "userA"     to sortedIds[0],
            "userB"     to sortedIds[1],
            "timestamp" to System.currentTimeMillis()
        )).await()
    }

    /** Stream all friendships where userA == me OR userB == me */
    fun observeFriends(myUid: String): Flow<List<Pair<String,String>>> = callbackFlow {
        var latestA = emptyList<Pair<String, String>>()
        var latestB = emptyList<Pair<String, String>>()

        val subA = fs.collection(FRI)
            .whereEqualTo("userA", myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                latestA = snap!!.documents.map { doc ->
                    Pair(doc.getString("userA")!!, doc.getString("userB")!!)
                }
                trySend(latestA + latestB)
            }

        val subB = fs.collection(FRI)
            .whereEqualTo("userB", myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                latestB = snap!!.documents.map { doc ->
                    Pair(doc.getString("userA")!!, doc.getString("userB")!!)
                }
                trySend(latestA + latestB)
            }

        awaitClose {
            subA.remove()
            subB.remove()
        }
    }
}
