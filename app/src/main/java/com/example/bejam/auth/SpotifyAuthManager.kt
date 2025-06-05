package com.example.bejam.auth

import com.google.firebase.auth.FirebaseAuth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.bejam.AuthCallbackActivity
import fi.iki.elonen.NanoHTTPD
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * zentraler Manager für die komplette Spotify-Authenfizierung
 * OAuth2 mit Spotify (Empfang und Weiterleitung des Auth-Codes)
 * Spotify-Login-Flow
 * Nach Login wird zur App weitergeleitet
 */

class SpotifyAuthManager(private val context: Context) {

    // Spotify-Client-ID der App (von Spotify Developer Dashboard)
    private val clientId = "f929decae6b84dad9fa7ce752d50c7ec"

    // Die Adresse, auf die Spotify nach erfolgreichem Login umleitet (lokaler Server)
    private val redirectUri = "http://127.0.0.1:8888/callback"

    // Spotify-URL zum Starten des OAuth2-Flows
    private val authEndpoint = "https://accounts.spotify.com/authorize"

    // SharedPreferences, um Tokens etc. dauerhaft im Handy zu speichern
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // Referenz auf den lokalen HTTP-Server, um bei Bedarf zu starten/stoppen
    private var server: LocalHttpServer? = null

    // startet den kompletten Spotify-Loginprozess
    fun startLogin() {

        // 1) PKCE-Codeverifier und -challenge generieren
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        // 2) Code-Verifier in SharedPreferences ablegen, um ihn nachher beim Token-Tausch wiederzuholen
        prefs.edit().putString("code_verifier", codeVerifier).apply()

        // 3) Starte lokalen HTTP-Server
        server?.stop()
        server = LocalHttpServer(context)
        server?.start()
        Log.d("SPOTIFY", "Lokaler HTTP-Server gestartet auf Port 8888")

        // 4) Die vollständige Auth-URL mit allen Query-Parametern aufbauen
        val authUri = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter(
                "scope",
                "user-read-private user-read-email user-library-modify user-library-read user-follow-modify user-follow-read user-top-read"
            ) // Welche Rechte die App beim User anfragt
            .build()

        Log.d("SPOTIFY_AUTH_URL", "Auth URL: $authUri")

        // 5) Öffnet den Browser mit der Auth-URL → User sieht Spotify-Login
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUri)
    }

    // loggt user komplett aus Spotify und Firebase aus, löscht gespeicherte tokens
    fun logout() {

        // 1) Logge User auch aus Firebase aus
        FirebaseAuth.getInstance().signOut()

        // 2) Entferne Access Token, Refresh Token, Expiration Time, Code Verifier aus Storage
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expiration_time")
            .remove("code_verifier")
            .apply()

        // 3) Lokalen HTTP-Server stoppen, falls er noch läuft
        server?.stop()
        server = null

        // App-intern Logout kommunizieren
        val intent = Intent("com.example.bejam.USER_LOGGED_OUT")
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

        // 4) Info-Toast für User
        Toast.makeText(context, "Logged out of Spotify", Toast.LENGTH_SHORT).show()
    }

    // generiert kryptografisch sichere zufällige Zeichenkette
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(64)
        secureRandom.nextBytes(codeVerifier)
        return Base64.encodeToString(codeVerifier, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // Erzeugt die Code-Challenge (SHA256 Hash des Verifiers, dann base64-URL-encoded)
    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // Companion Object mit statischer Methode für das Token-Refresh
    companion object {
        private const val CLIENT_ID = "f929decae6b84dad9fa7ce752d50c7ec"

        // Holt sich mit einem Refresh Token ein neues Access Token von Spotify
        fun refreshAccessToken(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
            val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val refreshToken = prefs.getString("refresh_token", null)
            if (refreshToken.isNullOrEmpty()) {
                Log.e("TOKEN_REFRESH", "No refresh token available.")
                onComplete?.invoke(false, "No refresh token available.")
                return
            }

            val client = OkHttpClient()

            // 1) Request-Body für Token-Refresh zusammenbauen
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            // 2) POST-Request an Spotify Token Endpoint
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(requestBody)
                .build()

            // 3) Request asynchron abschicken
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("TOKEN_REFRESH", "Refresh failed: ${e.message}")
                    onComplete?.invoke(false, e.message)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val responseData = resp.body?.string()
                        if (resp.isSuccessful && !responseData.isNullOrEmpty()) {
                            try {
                                val json = JSONObject(responseData)
                                val newAccessToken = json.getString("access_token")
                                val expiresIn = json.getInt("expires_in")
                                val newExpirationTime = System.currentTimeMillis() + expiresIn * 1000L

                                // 4) Neues Access Token & Ablaufzeit in den SharedPreferences speichern
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

    // Server lauscht auf Port 8888 und empfängt Anfrage an /callback mit code als URL-Parameter
    inner class LocalHttpServer(private val context: Context) : NanoHTTPD(8888) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val params = session.parameters

            if (uri == "/callback" && params.containsKey("code")) {
                val code = params["code"]?.firstOrNull() //extrahiert den Autorisierungscode
                Log.d("SPOTIFY", "Code empfangen: $code")

                // Weiterleiten in die App (z.B. zur AuthCallbackActivity)
                val intent = Intent(context, AuthCallbackActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("code", code)
                }
                context.startActivity(intent)

                // Server wird nicht mehr benötigt
                stop()
                server = null

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
