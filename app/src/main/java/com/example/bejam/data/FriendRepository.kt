package com.example.bejam.data

import android.content.Context
import com.example.bejam.data.model.Friend
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FriendRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).friendDao()
    private val api = RetrofitClient.spotifyApi
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    /** Local stream of your “friends” */
    fun getAllFriends(): Flow<List<Friend>> = dao.getAllFriends()

    /** Follow on Spotify, fetch profile, save as Friend */
    suspend fun addFriend(friendUid: String) {
        val token = prefs.getString("access_token", "")!!
        val bearer = "Bearer $token"

        // 1. UserProfile-Dokument holen, Spotify-ID extrahieren
        val doc = Firebase.firestore.collection("user_profiles")
            .document(friendUid)
            .get()
            .await()
        val spotifyUserId = doc.getString("spotifyId") ?: return

        // 2. Folgen auf Spotify
        api.followUsers(bearer, ids = spotifyUserId)

        // 3. Profil von Spotify laden
        val profile = api.getUserProfile(bearer, spotifyUserId)

        // 4. Lokal speichern
        val friend = Friend(
            id = profile.id,
            username = profile.display_name ?: profile.id,
            profileImageUrl = profile.images.firstOrNull()?.url,
            email = profile.email
        )
        withContext(Dispatchers.IO) { dao.upsert(friend) }
    }


    /** Unfollow on Spotify, then delete locally */
    suspend fun removeFriend(spotifyUserId: String) {
        val token = prefs.getString("access_token","")!!
        val bearer = "Bearer $token"

        api.unfollowUsers(bearer, ids = spotifyUserId)
        withContext(Dispatchers.IO) {
            dao.remove(spotifyUserId)
        }
    }

    companion object {
        @Volatile private var INSTANCE: FriendRepository? = null
        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            FriendRepository(ctx.applicationContext).also { INSTANCE = it }
        }
    }
}