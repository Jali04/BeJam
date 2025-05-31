
package com.example.bejam

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.bejam.data.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

        // Token tauschen und Rest in einer Coroutine erledigen
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
                    val refreshToken = json.getString("refresh_token")
                    val expiresIn = json.getInt("expires_in")
                    val expirationTime = System.currentTimeMillis() + expiresIn * 1000L

                    // Tokens in SharedPreferences speichern
                    getSharedPreferences("auth", MODE_PRIVATE).edit().apply {
                        putString("access_token", accessToken)
                        putString("refresh_token", refreshToken)
                        putLong("expiration_time", expirationTime)
                        apply()
                    }

                    // Anschließend FirebaseAuth (anonym) beenden und im richtigen Account anmelden
                    handleSpotifyLoginAndFirestore(accessToken)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AuthCallbackActivity, "Token ungültig", Toast.LENGTH_SHORT).show()
                        Log.e("SPOTIFY", "Response: $responseData")
                        finish()
                    }
                }
            }
        })
    }

    /**
     * Nachdem wir den Spotify-Access-Token haben, rufen wir die Spotify-User-API ab, um
     * das E-Mail‐Feld zu bekommen. Dann erstellen oder verwenden wir einen FirebaseAuth‐Account,
     * der exakt zu dieser E-Mail gehört. Erst danach greifen wir auf Firestore zu.
     */
    private fun handleSpotifyLoginAndFirestore(accessToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Spotify-API: Hol dir das Profil (inkl. E-Mail).
                val profile = RetrofitClient.spotifyApi
                    .getCurrentUserProfile("Bearer $accessToken")

                // 2) E-Mail extrahieren (bei Spotify immer vorhanden, aber zur Sicherheit null-check)
                val spotifyEmail = profile.email ?: ""
                if (spotifyEmail.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AuthCallbackActivity,
                            "Keine E-Mail vom Spotify-Account erhalten",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@launch
                }

                // 3) Generiere ein deterministisches Passwort aus der E-Mail (nur deine App kennt den Algorithmus).
                val salt = "EHr!t7K#F0xYn"    // Beispiel‐Salt (muss konstant bleiben!)
                val raw = spotifyEmail + salt
                val password = raw.hashSha256()

                // 4) Prüfe, ob es bereits einen FirebaseAuth-Account für diese E-Mail gibt
                val auth = FirebaseAuth.getInstance()
                val signInMethodsResult = auth.fetchSignInMethodsForEmail(spotifyEmail).await()
                val methods: List<String>? = signInMethodsResult.signInMethods
                val userProfilesRef = Firebase.firestore.collection("user_profiles")
                val firebaseUserUID: String

                if (methods.isNullOrEmpty()) {
                    // → Kein Account existiert bisher ⇒ Neuen FirebaseAuth‐User anlegen
                    val createResult = try {
                        auth.createUserWithEmailAndPassword(spotifyEmail, password).await()
                    } catch (e: FirebaseAuthUserCollisionException) {
                        // Kollision: Hole den Existing-User
                        auth.signInWithEmailAndPassword(spotifyEmail, password).await()
                    }
                    firebaseUserUID = createResult.user!!.uid
                    Log.d("AUTH", "Neuer FirebaseAuth-User angelegt: $firebaseUserUID")
                } else {
                    // → Account existiert bereits ⇒ Login in den existierenden Account
                    val signInResult = try {
                        auth.signInWithEmailAndPassword(spotifyEmail, password).await()
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AuthCallbackActivity,
                                "Anmeldefehler: Ungültiges Passwort.",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        return@launch
                    }
                    firebaseUserUID = signInResult.user!!.uid
                    Log.d("AUTH", "Mit bestehendem FirebaseAuth-User eingeloggt: $firebaseUserUID")
                }

                // 5) Firestore-Profil anlegen oder aktualisieren
                val querySnapshot = userProfilesRef
                    .whereEqualTo("email", spotifyEmail)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    // Noch kein Profil‐Dokument ⇒ neu anlegen
                    userProfilesRef.document(firebaseUserUID)
                        .set(mapOf(
                            "spotifyId" to profile.id,
                            "displayName" to (profile.display_name ?: ""),
                            "avatarUrl" to (profile.images.firstOrNull()?.url ?: ""),
                            "email" to spotifyEmail
                        ), SetOptions.merge())
                        .await()
                    Log.d("FIRESTORE", "Neues Profil angelegt: $firebaseUserUID")
                } else {
                    // Profil existiert bereits ⇒ aktualisieren
                    val existingDocId = querySnapshot.documents[0].id
                    userProfilesRef.document(existingDocId)
                        .set(mapOf(
                            "spotifyId" to profile.id,
                            "displayName" to (profile.display_name ?: ""),
                            "avatarUrl" to (profile.images.firstOrNull()?.url ?: ""),
                            "email" to spotifyEmail
                        ), SetOptions.merge())
                        .await()
                    Log.d("FIRESTORE", "Bestehendes Profil aktualisiert: $existingDocId")
                }

                // 6) Zurück ins MainActivity
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AuthCallbackActivity, "Login erfolgreich!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@AuthCallbackActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Log.e("FIRESTORE", "Fehler bei Auth/Firestore-Check", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AuthCallbackActivity,
                        "Fehler bei Authentifizierung: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    /** Hilfsfunktion: SHA-256‐Hash als Hex‐String */
    private fun String.hashSha256(): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
