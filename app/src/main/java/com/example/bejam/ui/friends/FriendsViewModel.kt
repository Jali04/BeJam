package com.example.bejam.ui.friends

import android.app.Application
import androidx.lifecycle.*
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.FirestoreManager.Request
import com.example.bejam.data.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun sendRequest(searchInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val firestore = Firebase.firestore

            // 1. Finde die UID des Zielusers (wie gehabt)
            val targetSnapshot = firestore.collection("user_profiles")
                .whereEqualTo("spotifyId", searchInput)
                .get().await()
            val targetDoc = targetSnapshot.documents.firstOrNull()
            val toUid = targetDoc?.id
            if (toUid == null) {
                _error.postValue("Kein Nutzer gefunden mit dieser Spotify-ID, Username oder E-Mail.")
                return@launch
            }

            // 2. Prüfe auf bestehende Requests: erst "ich → ihn", dann "er → mich"
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

            // 3. Prüfe auf bestehende Freundschaft
            val sortedIds = listOf(currentUid, toUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                return@launch
            }

            // 4. Sende die Anfrage (wenn noch nicht vorhanden)
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
            // 1. Check: Existiert die Freundschaft schon?
            val firestore = Firebase.firestore
            val sortedIds = listOf(myUid, req.fromUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                // Freundschaft existiert schon → Anfrage löschen und User informieren
                firestore.collection("friend_requests").document(req.id).delete().await()
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                _requestSent.postValue(false)
                return@launch
            }


            // 2. Wenn Freundschaft NICHT existiert, dann fahre normal fort
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

}
