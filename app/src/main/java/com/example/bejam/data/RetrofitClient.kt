package com.example.bejam.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.spotify.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val spotifyApi: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)                                   // add our logging client
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyApiService::class.java)
    }
}
