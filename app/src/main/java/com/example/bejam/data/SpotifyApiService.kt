package com.example.bejam.data

import com.example.bejam.data.model.SpotifyUserProfile
import com.example.bejam.data.model.TopTracksResponse
import com.example.bejam.data.model.TrackSearchResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20,
        @Query("market") market: String = "from_token"
    ): TrackSearchResponse

    /** Follow one or more Spotify users */
    @PUT("v1/me/following")
    suspend fun followUsers(
        @Header("Authorization") bearer: String,
        @Query("type") type: String = "user",
        @Query("ids") ids: String
    ): retrofit2.Response<Unit>

    /** Unfollow one or more Spotify users */
    @DELETE("v1/me/following")
    suspend fun unfollowUsers(
        @Header("Authorization") bearer: String,
        @Query("type") type: String = "user",
        @Query("ids") ids: String
    )

    /** Get a Spotify user’s profile (to fetch display name + avatar) */
    @GET("v1/users/{user_id}")
    suspend fun getUserProfile(
        @Header("Authorization") bearer: String,
        @Path("user_id") userId: String
    ): SpotifyUserProfile

    @GET("v1/me")
    suspend fun getCurrentUserProfile(
        @Header("Authorization") bearer: String
    ): SpotifyUserProfile

    @GET("v1/me/top/tracks")
    suspend fun getUserTopTracks(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 10
    ): TopTracksResponse

    @PUT("me/tracks")
    suspend fun likeTrack(
        @Header("Authorization") authHeader: String,
        @Query("ids") trackIds: List<String>
    ): Response<ResponseBody>
}
