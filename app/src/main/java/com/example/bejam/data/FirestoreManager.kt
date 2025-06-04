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

/**
 * Hilfsklasse für alle direkten Operationen mit Firestore (Datenbank in der Cloud).
 * Stellt Methoden bereit für Friend-Requests, Freundschaften, Likes und DailySelections.
 */

object FirestoreManager {

    // Firestore-Instanz
    private val fs = Firebase.firestore

    // Sammlungsnamen als Konstanten
    private const val REQ = "friend_requests"
    private const val FRI = "friends"
    private const val SEL = "daily_selections"

    /**
     * Datenklasse für eine Freundschaftsanfrage.
     * Enthält Absender, Empfänger, Status und Zeitstempel.
     */
    data class Request(
        val id: String = "",
        val fromUid: String = "",
        val toUid: String = "",
        val status: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    /** Erstellt/verschickt eine Freundschaftsanfrage von fromUid → toUid */
    fun sendRequest(fromUid: String, toUid: String): Task<Void> {
        // Eindeutige ID für die Anfrage: verhindert doppelte Anfragen
        val requestId = "${fromUid}_$toUid"
        val docRef = fs.collection(REQ).document(requestId)

        // Anfrage-Dokument bauen
        val req = Request(
            id        = docRef.id,
            fromUid   = fromUid,
            toUid     = toUid,
            status    = "PENDING",
            timestamp = System.currentTimeMillis()
        )

        // Anfrage speichern (ersetzt vorhandene Anfrage gleichen Typs)
        return docRef.set(req)
    }

    /** Liefert alle eingehenden, noch nicht beantworteten Freundschaftsanfragen als Flow (Live-Stream) */
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

    /** Beantwortet eine Anfrage (accept/reject), schreibt im Erfolgsfall auch die Freundschaft */
    suspend fun respondToRequest(
        req: Request,
        accept: Boolean,
        myUid: String
    ) {
        if (!accept) {
            // Anfrage abgelehnt: Anfrage löschen (kann erneut geschickt werden)
            fs.collection(REQ).document(req.id)
                .delete().await()
            return
        }

        // Anfrage angenommen: Status updaten
        fs.collection(REQ).document(req.id)
            .update("status", "ACCEPTED").await()

        // Freundschaft anlegen (Eintrag in "friends"-Collection, für beide User)
        val sortedIds = listOf(req.fromUid, myUid).sorted()
        val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
        fs.collection(FRI).document(friendshipId).set(mapOf(
            "userA"     to sortedIds[0],
            "userB"     to sortedIds[1],
            "timestamp" to System.currentTimeMillis()
        )).await()
    }

    /** Streamt alle Freundschaften, bei denen der aktuelle User beteiligt ist */
    fun observeFriends(myUid: String): Flow<List<Pair<String, String>>> = callbackFlow {
        var latestA = emptyList<Pair<String, String>>()
        var latestB = emptyList<Pair<String, String>>()

        // Freundschaften, bei denen der User „userA“ ist
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

        // Freundschaften, bei denen der User „userB“ ist
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
     * Fügt die userId zur Like-Liste eines DailySelection-Posts hinzu.

    fun likeSelection(selectionId: String, userId: String): Task<Void> {
        return fs.collection(SEL)
            .document(selectionId)
            .update("likes", FieldValue.arrayUnion(userId))
    }

    /**
     * Entfernt die userId aus der Like-Liste eines DailySelection-Posts.
     */
    fun unlikeSelection(selectionId: String, userId: String): Task<Void> {
        return fs.collection(SEL)
            .document(selectionId)
            .update("likes", FieldValue.arrayRemove(userId))
    }*/

    /**
     * Löscht alle DailySelections eines Users, die heute erstellt wurden.
     * Vermeidet den Bedarf für einen Composite Index in Firestore.
     */
    suspend fun clearTodaySelections(uid: String) {
        // Tagesanfang/-ende berechnen (Millisekunden)
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

        // Alle Posts des Users holen, die im Zeitraum heute liegen
        val allSnapshot = fs.collection(SEL)
            .whereEqualTo("userId", uid)
            .get()
            .await()
        val snapshotDocs = allSnapshot.documents.filter { doc ->
            val ts = doc.getLong("timestamp") ?: 0L
            ts in startOfDay until endOfDay
        }

        // Alle passenden Posts im Batch löschen
        val batch = fs.batch()
        for (doc in snapshotDocs) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
