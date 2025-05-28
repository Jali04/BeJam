package com.example.bejam.data

import android.content.Context
import com.example.bejam.data.model.Friend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FriendRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).friendDao()
    private val api = RetrofitClient.spotifyApi
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun getAllFriends(): Flow<List<Friend>> = dao.getAllFriends()

    suspend fun addFriend(friendUid: String): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token", "")!!
        val bearer = "Bearer $token"

        val doc = Firebase.firestore.collection("user_profiles")
            .document(friendUid)
            .get()
            .await()
        val spotifyUserId = doc.getString("spotifyId") ?: return@withContext false

        val response = api.followUsers(bearer, ids = spotifyUserId)
        if (!response.isSuccessful) return@withContext false

        val profile = api.getUserProfile(bearer, spotifyUserId)

        val friend = Friend(
            id = profile.id,
            username = profile.display_name ?: profile.id,
            profileImageUrl = profile.images.firstOrNull()?.url,
            email = null
        )
        dao.upsert(friend)
        true
    }

    suspend fun removeFriend(spotifyUserId: String) = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token","")!!
        val bearer = "Bearer $token"

        api.unfollowUsers(bearer, ids = spotifyUserId)
        dao.remove(spotifyUserId)
    }

    companion object {
        @Volatile private var INSTANCE: FriendRepository? = null
        fun getInstance(ctx: Context) = INSTANCE ?: synchronized(this) {
            FriendRepository(ctx.applicationContext).also { INSTANCE = it }
        }
    }
}
