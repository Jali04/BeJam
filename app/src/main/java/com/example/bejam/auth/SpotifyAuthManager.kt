package com.example.bejam.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.bejam.AuthCallbackActivity
import fi.iki.elonen.NanoHTTPD
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
            .appendQueryParameter("scope", "user-read-private user-read-email user-library-modify user-library-read")
            .build()

        Log.d("SPOTIFY_AUTH_URL", "Auth URL: $authUri")

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUri)
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
