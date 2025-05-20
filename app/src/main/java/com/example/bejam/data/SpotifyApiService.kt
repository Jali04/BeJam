package com.example.bejam.data

import com.example.bejam.data.model.TrackSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): TrackSearchResponse
}
