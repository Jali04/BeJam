package com.example.bejam

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.bejam.data.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class AuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent.getStringExtra("code")
        val error = intent?.data?.getQueryParameter("error")

        if (error != null) {
            Toast.makeText(this, "Login abgelehnt: $error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val codeVerifier = getSharedPreferences("auth", MODE_PRIVATE)
            .getString("code_verifier", null)

        if (code == null || codeVerifier == null) {
            Toast.makeText(this, "Fehlender Code oder Verifier", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Jetzt: manuell Token anfordern
        exchangeToken(code, codeVerifier)
    }

    private fun exchangeToken(code: String, codeVerifier: String) {
        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
            .add("client_id", "f929decae6b84dad9fa7ce752d50c7ec")
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", "http://127.0.0.1:8888/callback")
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(requestBody)
            .build()

        Log.d("DEBUG", "CODE: $code")
        Log.d("DEBUG", "CODE_VERIFIER: $codeVerifier")
        Log.d("DEBUG", "REDIRECT_URI: http://127.0.0.1:8888/callback")


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@AuthCallbackActivity, "Fehler bei Token-Request", Toast.LENGTH_SHORT).show()
                    Log.e("SPOTIFY", "Token-Abruf fehlgeschlagen: ${e.message}")
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    val json = JSONObject(responseData)
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.getString("refresh_token") // sometimes refresh_token is only returned the first time
                    val expiresIn = json.getInt("expires_in")  // seconds until expiration
                    val expirationTime = System.currentTimeMillis() + expiresIn * 1000L  // store in milliseconds

                    // Log the tokens for debugging purposes
                    Log.d("SPOTIFY_TOKEN", "Access token: $accessToken")
                    Log.d("SPOTIFY_TOKEN", "Refresh token: $refreshToken")

                    // Save tokens and expiration in SharedPreferences
                    getSharedPreferences("auth", MODE_PRIVATE).edit().apply {
                        putString("access_token", accessToken)
                        putString("refresh_token", refreshToken)
                        putLong("expiration_time", expirationTime)
                        apply()
                    }

                    val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (firebaseUid != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val profile = RetrofitClient.spotifyApi
                                    .getCurrentUserProfile("Bearer $accessToken")

                                Firebase.firestore
                                    .collection("user_profiles")
                                    .document(firebaseUid)
                                    .set(mapOf(
                                        "spotifyId" to profile.id,
                                        "displayName" to (profile.display_name ?: ""),
                                        "avatarUrl" to (profile.images.firstOrNull()?.url ?: "")
                                    ), SetOptions.merge())
                                    .await()
                                Log.d("FIRESTORE", "Profile mapping saved")
                            } catch (e: Exception) {
                                Log.e("FIRESTORE", "Failed to save mapping", e)
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@AuthCallbackActivity,
                                    "Login successful!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivity(
                                    Intent(this@AuthCallbackActivity, MainActivity::class.java)
                                )
                                finish()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@AuthCallbackActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@AuthCallbackActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AuthCallbackActivity, "Token ungültig", Toast.LENGTH_SHORT).show()
                        Log.e("SPOTIFY", "Response: $responseData")
                        Log.e("SPOTIFY_TOKEN_ERROR", "Response: ${response.body?.string()}")
                        finish()
                    }
                }
            }
        })
    }
}
