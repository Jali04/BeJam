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
import java.lang.IllegalStateException

class FriendsViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    val currentUid: String get() = auth.currentUser?.uid
        ?: throw IllegalStateException("Must be signed in before using FriendsViewModel")

    private val _requestSent = MutableLiveData<Boolean?>()
    val requestSent: LiveData<Boolean?> = _requestSent

    // 1) Incoming requests – only start listening once we have a non-null UID
    val incomingRequests: LiveData<List<Request>> = liveData(Dispatchers.IO) {
        auth.currentUser?.uid?.let { uid ->
            emitSource(
                FirestoreManager
                    .observeIncomingRequests(uid)
                    .asLiveData()
            )
        }
    }

    // 2) Your accepted friends
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

    /** Send a new request */
    fun sendRequest(searchInput: String) {
        // Schritt 1: In user_profiles nach Spotify-ID, displayName ODER E-Mail suchen
        val firestore = Firebase.firestore

        // Du könntest auch displayName oder email durchsuchen, hier als Beispiel NUR spotifyId
        firestore.collection("user_profiles")
            .whereEqualTo("spotifyId", searchInput)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.documents.isNotEmpty()) {
                    // Nutzer gefunden!
                    val targetDoc = querySnapshot.documents.first()
                    val toUid = targetDoc.id // Firestore-Dokument-ID = Firebase-UID

                    // Jetzt wie gehabt FriendRequest schicken!
                    FirestoreManager.sendRequest(currentUid, toUid)
                        .addOnSuccessListener {
                            _requestSent.value = true
                        }
                        .addOnFailureListener {
                            _requestSent.value = false
                        }
                } else {
                    // Optional: Suche auch nach displayName oder email, falls kein Treffer
                    firestore.collection("user_profiles")
                        .whereEqualTo("displayName", searchInput)
                        .get()
                        .addOnSuccessListener { snapshot2 ->
                            if (snapshot2.documents.isNotEmpty()) {
                                val targetDoc2 = snapshot2.documents.first()
                                val toUid2 = targetDoc2.id
                                FirestoreManager.sendRequest(currentUid, toUid2)
                                    .addOnSuccessListener {
                                        _requestSent.value = true
                                    }
                                    .addOnFailureListener {
                                        _requestSent.value = false
                                    }
                            } else {
                                // Noch kein Treffer: Suche per E-Mail (falls du E-Mail speicherst)
                                firestore.collection("user_profiles")
                                    .whereEqualTo("email", searchInput)
                                    .get()
                                    .addOnSuccessListener { snapshot3 ->
                                        if (snapshot3.documents.isNotEmpty()) {
                                            val targetDoc3 = snapshot3.documents.first()
                                            val toUid3 = targetDoc3.id
                                            FirestoreManager.sendRequest(currentUid, toUid3)
                                                .addOnSuccessListener {
                                                    _requestSent.value = true
                                                }
                                                .addOnFailureListener {
                                                    _requestSent.value = false
                                                }
                                        } else {
                                            // Kein User gefunden!
                                            _error.value = "Kein Nutzer gefunden mit dieser Spotify-ID, Username oder E-Mail."
                                        }
                                    }
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                _error.value = "Fehler bei der Suche: ${e.message}"
            }
    }

    /** Call this after handling a success so we don’t Toast twice */
    fun clearRequestSent() {
        _requestSent.value = null
    }

    /** Accept or reject an incoming request */
    fun respond(req: Request, accept: Boolean) = viewModelScope.launch {
        val myUid = auth.currentUser?.uid ?: return@launch
        try {
            // 1) Update Firestore request status
            FirestoreManager.respondToRequest(req, accept, myUid)

            // 2) Clear any previous error
            _error.value = null

            // 3) If accepted, follow + persist via repository
            val success = FriendRepository
                .getInstance(getApplication())
                .addFriend(req.fromUid)

            if (success) {
                _error.postValue(null) // optional, um Error zu resetten
                // Erfolg nach außen geben:
                _requestSent.postValue(true)
            } else {
                _error.postValue("Fehler beim Folgen auf Spotify!")
                _requestSent.postValue(false)
            }
        } catch (e: Exception) {
            _error.value = e.message
        }
    }
}