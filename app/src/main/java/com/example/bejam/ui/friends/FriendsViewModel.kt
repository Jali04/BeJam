package com.example.bejam.ui.friends

import android.app.Application
import androidx.lifecycle.*
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.FirestoreManager.Request
import com.example.bejam.data.FriendRepository
import com.google.firebase.auth.FirebaseAuth
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
    fun sendRequest(toUid: String) {
        FirestoreManager.sendRequest(currentUid, toUid)
            .addOnSuccessListener {
                _requestSent.value = true
            }
            .addOnFailureListener {
                _requestSent.value = false
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
            if (accept) {
                FriendRepository
                    .getInstance(getApplication())
                    .addFriend(req.fromUid)
            }
        } catch (e: Exception) {
            _error.value = e.message
        }
    }
}