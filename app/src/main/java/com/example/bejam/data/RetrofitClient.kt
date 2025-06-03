package com.example.bejam.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Singleton-Objekt, das die Retrofit-Instanz für die Spotify-API bereitstellt.
 */

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

    /**
     * SpotifyApiService ist die Kotlin-Interface-Beschreibung aller Spotify-Endpunkte.
     * Retrofit erstellt daraus automatisch eine Implementierung.
     */
    val spotifyApi: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyApiService::class.java)
    }
}
