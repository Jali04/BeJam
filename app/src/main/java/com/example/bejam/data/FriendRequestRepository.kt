package com.example.bejam.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Repository/ViewModel für Freundschaftsanfragen.
 * Bindeglied zwischen Firestore (Cloud) und UI/ViewModel-Schicht.
 */

class FriendRequestRepository(app: Application) : AndroidViewModel(app) {

    // Zugriff auf gespeicherte Auth-Infos
    private val prefs = app.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // Eigene Spotify/Firebase-UID
    private val myUid get() = prefs.getString("spotify_user_id","")!!

    /**
     * LiveData der eingehenden Freundschaftsanfragen (über FirestoreManager)
     */
    val incomingRequests = FirestoreManager
        .observeIncomingRequests(myUid)
        .asLiveData()

    /**
     * LiveData aller bestätigten Freunde (aus Firestore)
     * Wird in Friend-Objekte umgewandelt
     */
    val friends = FirestoreManager
        .observeFriends(myUid)
        .map { pairs ->
            // Für jedes Paar (a,b) den „anderen“ holen, Spotify-Profil abrufen und in Friend mappen
            pairs.map { (a,b) ->
                val other = if (a==myUid) b else a
                // TODO: call SpotifyApiService.getUserProfile(bearer, other)
            }
        }
        .asLiveData()

    /**
     * Schickt eine Freundschaftsanfrage an einen anderen User
     */
    fun sendRequest(toUid: String) {
        viewModelScope.launch {
            try {
                FirestoreManager.sendRequest(myUid, toUid).await()
            } catch(e:Exception) { /* ... */ }
        }
    }

    /**
     * Antwortet auf eine Freundschaftsanfrage (accept = true/false).
     * Bei Annahme wird der User auch bei Spotify gefolgt!
     */
    fun respond(req: FirestoreManager.Request, accept: Boolean) {
        viewModelScope.launch {
            FirestoreManager.respondToRequest(req, accept, myUid)
            if (accept) {
                // Bei Annahme direkt auch auf Spotify folgen!
                val token = prefs.getString("access_token","")!!
                RetrofitClient.spotifyApi.followUsers("Bearer $token", ids = req.fromUid)
            }
        }
    }
}
