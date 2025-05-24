package com.example.bejam.data

import com.example.bejam.data.model.Friend
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class AddFriendRequest(val query: String)      // username or email
data class AddFriendResponse(val friend: Friend)

interface BeJamApiService {
    @POST("friends")
    suspend fun addFriend(
        @Header("Authorization") bearer: String,
        @Body request: AddFriendRequest
    ): AddFriendResponse

    @GET("friends")
    suspend fun getFriends(@Header("Authorization") bearer: String): List<Friend>
}

