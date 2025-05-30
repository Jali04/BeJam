package com.example.bejam.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.example.bejam.AuthCallbackActivity
import fi.iki.elonen.NanoHTTPD
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

class SpotifyAuthManager(private val context: Context) {

    private val clientId = "f929decae6b84dad9fa7ce752d50c7ec"
    private val redirectUri = "http://127.0.0.1:8888/callback"
    private val authEndpoint = "https://accounts.spotify.com/authorize"
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private var server: LocalHttpServer? = null


    fun startLogin() {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        prefs.edit().putString("code_verifier", codeVerifier).apply()

        // Starte lokalen HTTP-Server
        if (server == null) {
            server = LocalHttpServer(context)
            server?.start()
            Log.d("SPOTIFY", "Lokaler HTTP-Server gestartet auf Port 8888")
        }

        val authUri = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter(
                "scope",
                "user-read-private user-read-email user-library-modify user-library-read user-follow-modify user-follow-read user-top-read"
            )
            .build()

        Log.d("SPOTIFY_AUTH_URL", "Auth URL: $authUri")

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUri)
    }
    fun logout() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expiration_time")
            .remove("code_verifier")
            .apply()

        Toast.makeText(context, "Logged out of Spotify", Toast.LENGTH_SHORT).show()
    }


    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(64)
        secureRandom.nextBytes(codeVerifier)
        return Base64.encodeToString(codeVerifier, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val CLIENT_ID = "f929decae6b84dad9fa7ce752d50c7ec"
        fun refreshAccessToken(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
            val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val refreshToken = prefs.getString("refresh_token", null)
            if (refreshToken.isNullOrEmpty()) {
                Log.e("TOKEN_REFRESH", "No refresh token available.")
                onComplete?.invoke(false, "No refresh token available.")
                return
            }

            val client = OkHttpClient()
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("TOKEN_REFRESH", "Refresh failed: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }

                override fun onResponse(call: Call, response: Response) {
                    // Use the response.use{} block to ensure the response is closed after processing
                    response.use { resp ->
                        val responseData = resp.body?.string()
                        if (resp.isSuccessful && !responseData.isNullOrEmpty()) {
                            try {
                                val json = JSONObject(responseData)
                                val newAccessToken = json.getString("access_token")
                                val expiresIn = json.getInt("expires_in")
                                val newExpirationTime = System.currentTimeMillis() + expiresIn * 1000L

                                // Update SharedPreferences with the new access token and expiration time.
                                prefs.edit().apply {
                                    putString("access_token", newAccessToken)
                                    putLong("expiration_time", newExpirationTime)
                                    apply()
                                }
                                Log.d("TOKEN_REFRESH", "New access token: $newAccessToken, expires in $expiresIn seconds.")
                                onComplete?.invoke(true, null)
                            } catch (e: Exception) {
                                Log.e("TOKEN_REFRESH", "Error parsing token refresh response: ${e.message}")
                                onComplete?.invoke(false, e.message)
                            }
                        } else {
                            Log.e("TOKEN_REFRESH", "Refresh response not successful: $responseData")
                            onComplete?.invoke(false, responseData)
                        }
                    }
                }
            })
        }
    }


    class LocalHttpServer(private val context: Context) : NanoHTTPD(8888) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val params = session.parameters

            if (uri == "/callback" && params.containsKey("code")) {
                val code = params["code"]?.firstOrNull()
                Log.d("SPOTIFY", "Code empfangen: $code")

                // Starte Activity mit Code
                val intent = Intent(context, AuthCallbackActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("code", code)
                }
                context.startActivity(intent)

                return newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html",
                    "<html><body><h2>Login erfolgreich! Du kannst zur App zurückkehren.</h2></body></html>"
                )
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }
}
