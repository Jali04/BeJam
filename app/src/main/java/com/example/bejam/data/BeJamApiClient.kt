package com.example.bejam.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Singleton-Objekt, das eine Retrofit-Instanz bereitstellt,
 * um mit der (Spotify-)API zu kommunizieren.
 */

object BeJamApiClient {
    private const val BASE_URL = "https://api.spotify.com/" // Basis-URL für alle API-Requests

    // Moshi ist die Library, um JSON <-> Kotlin-Objekte zu konvertieren
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .build()

    val service: BeJamApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BeJamApiService::class.java)
    }
}