package com.example.bejam.data

import com.google.android.gms.tasks.Task
import java.util.Calendar
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
    private const val SEL = "daily_selections"

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
    fun observeFriends(myUid: String): Flow<List<Pair<String, String>>> = callbackFlow {
        var latestA = emptyList<Pair<String, String>>()
        var latestB = emptyList<Pair<String, String>>()

        val subA = fs.collection(FRI)
            .whereEqualTo("userA", myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    latestA = snap.documents.mapNotNull { doc ->
                        val userA = doc.getString("userA")
                        val userB = doc.getString("userB")
                        if (userA != null && userB != null) Pair(userA, userB) else null
                    }
                    trySend(latestA + latestB)
                }
            }

        val subB = fs.collection(FRI)
            .whereEqualTo("userB", myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    latestB = snap.documents.mapNotNull { doc ->
                        val userA = doc.getString("userA")
                        val userB = doc.getString("userB")
                        if (userA != null && userB != null) Pair(userA, userB) else null
                    }
                    trySend(latestA + latestB)
                }
            }

        awaitClose {
            subA.remove()
            subB.remove()
        }
    }

    /**
     * Add the given userId to the likes array of a daily selection.
     */
    fun likeSelection(selectionId: String, userId: String): Task<Void> {
        return fs.collection(SEL)
            .document(selectionId)
            .update("likes", FieldValue.arrayUnion(userId))
    }

    /**
     * Remove the given userId from the likes array of a daily selection.
     */
    fun unlikeSelection(selectionId: String, userId: String): Task<Void> {
        return fs.collection(SEL)
            .document(selectionId)
            .update("likes", FieldValue.arrayRemove(userId))
    }

    /**
     * Deletes all daily selections for the given user that were created today.
     * This version fetches all selections for the user and filters by timestamp in Kotlin,
     * avoiding the need for a composite index in Firestore.
     */
    suspend fun clearTodaySelections(uid: String) {
        // Calculate the start and end of the current day in milliseconds
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // Fetch all selections for this user, then filter locally by today's timestamp range
        val allSnapshot = fs.collection(SEL)
            .whereEqualTo("userId", uid)
            .get()
            .await()
        val snapshotDocs = allSnapshot.documents.filter { doc ->
            val ts = doc.getLong("timestamp") ?: 0L
            ts in startOfDay until endOfDay
        }

        // Batch delete all matching documents
        val batch = fs.batch()
        for (doc in snapshotDocs) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
