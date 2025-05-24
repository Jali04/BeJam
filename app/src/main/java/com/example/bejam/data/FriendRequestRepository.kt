package com.example.bejam.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FriendRequestRepository(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val myUid get() = prefs.getString("spotify_user_id","")!!

    val incomingRequests = FirestoreManager
        .observeIncomingRequests(myUid)
        .asLiveData()

    val friends = FirestoreManager
        .observeFriends(myUid)
        .map { pairs ->
            // For each pair (a,b), pick the “other” ID, fetch SpotifyUserProfile, map to Friend
            pairs.map { (a,b) ->
                val other = if (a==myUid) b else a
                // TODO: call SpotifyApiService.getUserProfile(bearer, other)
                // and map into your local Friend data class
            }
        }
        .asLiveData()

    fun sendRequest(toUid: String) {
        viewModelScope.launch {
            try {
                FirestoreManager.sendRequest(myUid, toUid).await()
            } catch(e:Exception) { /* ... */ }
        }
    }

    fun respond(req: FirestoreManager.Request, accept: Boolean) {
        viewModelScope.launch {
            FirestoreManager.respondToRequest(req, accept, myUid)
            if (accept) {
                // follow on Spotify so that my feed will get their picks
                val token = prefs.getString("access_token","")!!
                RetrofitClient.spotifyApi.followUsers("Bearer $token", ids = req.fromUid)
            }
        }
    }
}
