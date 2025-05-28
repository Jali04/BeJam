package com.example.bejam.ui.friends

import android.app.Application
import androidx.lifecycle.*
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.FirestoreManager.Request
import com.example.bejam.data.FriendRepository
import com.example.bejam.data.model.DailySelection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
    val spotifyId: String?
)

class FriendsViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    val currentUid: String get() = auth.currentUser?.uid
        ?: throw IllegalStateException("Must be signed in before using FriendsViewModel")

    private val _requestSent = MutableLiveData<Boolean?>()
    val requestSent: LiveData<Boolean?> = _requestSent

    val incomingRequests: LiveData<List<Request>> = liveData(Dispatchers.IO) {
        auth.currentUser?.uid?.let { uid ->
            emitSource(
                FirestoreManager
                    .observeIncomingRequests(uid)
                    .asLiveData()
            )
        }
    }

    val friends: LiveData<List<Pair<String, String>>> = liveData(Dispatchers.IO) {
        auth.currentUser?.uid?.let { uid ->
            emitSource(
                FirestoreManager
                    .observeFriends(uid)
                    .asLiveData()
            )
        }
    }

    // UID-Liste deiner Freunde (inkl. Fehlerbehandlung falls User nicht eingeloggt)
    val friendUids: LiveData<List<String>> = friends.map { pairs ->
        val myUid = currentUid
        pairs.mapNotNull { (userA, userB) ->
            when (myUid) {
                userA -> userB
                userB -> userA
                else -> null
            }
        }
    }

    // --------- UserProfile Map für FeedAdapter ---------
    private val _profileMap = MutableLiveData<Map<String, UserProfile>>()
    val profileMap: LiveData<Map<String, UserProfile>> = _profileMap

    fun loadProfilesForUids(uids: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, UserProfile>()
            val firestore = Firebase.firestore
            for (uid in uids) {
                try {
                    val snap = firestore.collection("user_profiles").document(uid).get().await()
                    if (snap.exists()) {
                        map[uid] = UserProfile(
                            uid = uid,
                            displayName = snap.getString("displayName") ?: "Unknown User",
                            avatarUrl = snap.getString("avatarUrl"),
                            spotifyId = snap.getString("spotifyId")
                        )
                    }
                } catch (_: Exception) { /* ignore single user failure */ }
            }
            _profileMap.postValue(map)
        }
    }
    // -----------------------------------------

    // Fehlerhandling
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun sendRequest(searchInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val firestore = Firebase.firestore

            val targetSnapshot = firestore.collection("user_profiles")
                .whereEqualTo("spotifyId", searchInput)
                .get().await()
            val targetDoc = targetSnapshot.documents.firstOrNull()
            val toUid = targetDoc?.id
            if (toUid == null) {
                _error.postValue("Kein Nutzer gefunden mit dieser Spotify-ID, Username oder E-Mail.")
                return@launch
            }

            val sentReq = firestore.collection("friend_requests")
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("toUid", toUid)
                .get().await()
            if (!sentReq.isEmpty) {
                _error.postValue("Du hast dieser Person bereits eine Freundschaftsanfrage geschickt.")
                return@launch
            }

            val receivedReq = firestore.collection("friend_requests")
                .whereEqualTo("fromUid", toUid)
                .whereEqualTo("toUid", currentUid)
                .get().await()
            if (!receivedReq.isEmpty) {
                _error.postValue("Diese Person hat dir bereits eine Freundschaftsanfrage geschickt.")
                return@launch
            }

            val sortedIds = listOf(currentUid, toUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                return@launch
            }

            FirestoreManager.sendRequest(currentUid, toUid).await()
            _requestSent.postValue(true)
        }
    }

    fun clearRequestSent() {
        _requestSent.value = null
    }

    fun respond(req: Request, accept: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid ?: return@launch
        try {
            val firestore = Firebase.firestore
            val sortedIds = listOf(myUid, req.fromUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                firestore.collection("friend_requests").document(req.id).delete().await()
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                _requestSent.postValue(false)
                return@launch
            }

            FirestoreManager.respondToRequest(req, accept, myUid)
            _error.postValue(null)

            val success = FriendRepository
                .getInstance(getApplication())
                .addFriend(req.fromUid)

            if (success) {
                _error.postValue(null)
                _requestSent.postValue(true)
            } else {
                _error.postValue("Fehler beim Folgen auf Spotify!")
                _requestSent.postValue(false)
            }
        } catch (e: Exception) {
            _error.postValue(e.message)
        }
    }

    // ------ Like-Button Unterstützung für FeedAdapter ------
    fun onLikeClicked(sel: DailySelection) {
        val myUid = currentUid
        if (sel.userId == myUid) return // <-- VERHINDERN!
        val db = Firebase.firestore
        val docRef = db.collection("daily_selections").document(sel.id)
        viewModelScope.launch(Dispatchers.IO) {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    val likes = (snap.get("likes") as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
                    if (likes.contains(myUid)) {
                        likes.remove(myUid)
                    } else {
                        likes.add(myUid)
                    }
                    tx.update(docRef, "likes", likes)
                }
            }.await()
        }
    }

    // -------------------------------------------------------
}
