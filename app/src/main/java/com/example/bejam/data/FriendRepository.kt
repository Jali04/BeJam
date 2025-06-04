package com.example.bejam.data

import android.content.Context
import com.example.bejam.data.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Repository-Klasse für Freunde. Kapselt die Logik zur Verwaltung
 * der Freundesliste lokal (Room) und in der Spotify-API.
 */

class FriendRepository(context: Context) {
    // Zugriff auf das lokale DAO für Freunde (Room DB)
    private val dao = AppDatabase.getInstance(context).friendDao()
    // Zugriff auf die Spotify-API via RetrofitClient
    private val api = RetrofitClient.spotifyApi
    // Zugriff auf gespeicherte Auth-Tokens
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    /**
     * Gibt einen Live-Stream (Flow) aller Freunde aus der lokalen DB zurück.

    fun getAllFriends(): Flow<List<Friend>> = dao.getAllFriends()*/

    //Fügt einen neuen Freund hinzu (sowohl bei Spotify als „Folge“ als auch lokal speichern)
    suspend fun addFriend(friendUid: String): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token", "")!! // Spotify-Token laden
        val bearer = "Bearer $token"

        // Hole das User-Profil aus Firestore (um Spotify-ID zu bekommen)
        val doc = Firebase.firestore.collection("user_profiles")
            .document(friendUid)
            .get()
            .await()
        val spotifyUserId = doc.getString("spotifyId") ?: return@withContext false

        // Folge dem User auf Spotify (damit tauchen seine Picks im Feed auf)
        val response = api.followUsers(bearer, ids = spotifyUserId)
        if (!response.isSuccessful) return@withContext false

        // Lade das komplette Spotify-Profil für weitere Details
        val profile = api.getUserProfile(bearer, spotifyUserId)

        // Erzeuge Friend-Objekt und speichere es lokal
        val friend = Friend(
            id = profile.id,
            username = profile.display_name ?: profile.id,
            profileImageUrl = profile.images.firstOrNull()?.url,
            email = null
        )
        dao.upsert(friend)
        true
    }

    /**
     * Entfernt einen Freund (entfolgt bei Spotify und löscht lokal).
     */
    suspend fun removeFriend(spotifyUserId: String) = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token","")!!
        val bearer = "Bearer $token"

        api.unfollowUsers(bearer, ids = spotifyUserId)
        dao.remove(spotifyUserId)
    }

    companion object {
        @Volatile private var INSTANCE: FriendRepository? = null
        // Singleton-Pattern: immer nur eine Instanz pro App
        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            FriendRepository(ctx.applicationContext).also { INSTANCE = it }
        }
    }
}
