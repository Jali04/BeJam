package com.example.bejam.ui.profile

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bejam.databinding.FragmentProfileBinding
import com.example.bejam.data.RetrofitClient
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.data.model.DailySelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast // --- geändert ---
import com.example.bejam.ui.profile.TopSongsAdapter
import com.google.firebase.firestore.ktx.firestore
import com.example.bejam.auth.SpotifyAuthManager // --- geändert ---
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject
import java.util.Locale.US

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Fragment für die Profilseite des Nutzers. Zeigt Spotify-User-Informationen,
 * den heutigen ausgewählten Song und die Top-Tracks.
 * Reagiert außerdem auf Login/Logout-Events über einen BroadcastReceiver.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val b get() = _binding!!

    // Auth-Manager für Spotify-Login/Logout
    private lateinit var authManager: SpotifyAuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentProfileBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authManager = SpotifyAuthManager(requireContext())

        setupLoginLogoutUI()
        loadUserProfile()
        loadSelectedSong()
        loadTopSongs()
    }

    /**
     * BroadcastReceiver, der Logout-Events abfängt und die UI entsprechend zurücksetzt.
     */
    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Update login/logout buttons
            setupLoginLogoutUI()

            // Clear user-profile
            b.textDisplayName.text = ""
            b.textUsername.text = ""
            b.profileImageLarge.setImageResource(R.drawable.placeholder_profile)

            // Ausgewählten Song verbergen
            b.selectedSongContainer.visibility = View.GONE

            // Top-Songs-Liste leeren
            b.topSongsRecyclerView.adapter = null
        }
    }

    /**
     * Schaltet die Sichtbarkeit von Login- und Logout-Buttons abhängig vom Spotify-Token.
     * Startet Login/Logout über den SpotifyAuthManager.
     */
    private fun setupLoginLogoutUI() {
        val prefs = requireContext().getSharedPreferences("auth", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            // Token vorhanden → Logout-Button anzeigen, Login-Button verbergen
            b.spotifyLoginButton.visibility = View.GONE
            b.spotifyLogoutButton.visibility = View.VISIBLE
        } else {
            // Kein Token → Login-Button anzeigen, Logout-Button verbergen
            b.spotifyLoginButton.visibility = View.VISIBLE
            b.spotifyLogoutButton.visibility = View.GONE
        }

        // Login-Button startet Spotify-Login
        b.spotifyLoginButton.setOnClickListener {
            authManager.startLogin()
        }
        // Logout-Button führt Logout durch und aktualisiert UI
        b.spotifyLogoutButton.setOnClickListener {
            authManager.logout()
            b.spotifyLoginButton.visibility = View.VISIBLE
            b.spotifyLogoutButton.visibility = View.GONE
        }
    }

    /**
     * Lädt das aktuelle Spotify-Nutzerprofil (Name, Username, Bild) via Retrofit-Aufruf.
     * Speichert außerdem die Spotify-User-ID in SharedPreferences.
     */
    private fun loadUserProfile() {
        val prefs = requireContext().getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("access_token", "") ?: return

        // In Coroutine ausführen, um Netzwerkaufruf im IO-Thread zu machen
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    RetrofitClient.spotifyApi
                        .getCurrentUserProfile("Bearer $token")
                }

                // UI-Elemente mit erhaltenen Daten befüllen
                b.textDisplayName.text = profile.display_name.orEmpty()
                b.textUsername.text = "@${profile.id}"
                val imageUrl = profile.images?.firstOrNull()?.url
                Glide.with(this@ProfileFragment)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_profile)
                    .into(b.profileImageLarge)

                // Spotify-User-ID in SharedPreferences speichern
                requireContext()
                    .getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putString("spotify_user_id", profile.id)
                    .apply()

            } catch (e: Exception) {
                // TODO: handle error (e.g. show Toast or log out)
                // we forgor
            }
        }
    }


    /**
     * Holt aus Firestore die Today's DailySelection für den aktuellen Nutzer
     * (identifiziert durch „userId_todayDate“) und bindet sie in die UI ein.
     */
    private fun loadSelectedSong() {
        // Firebase-User-ID holen
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Schlüsse l für heute im Format „yyyy-MM-dd”
        val today = SimpleDateFormat("yyyy-MM-dd", US)
            .format(Date())

        // Asynchron Firestore abfragen
        viewLifecycleOwner.lifecycleScope.launch {
            val db = Firebase.firestore
            try {
                // Sammlung „daily_selections“ nach allen Einträgen filtern, bei denen userId == uid
                val snap = withContext(Dispatchers.IO) {
                    db.collection("daily_selections")
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()
                }
                // Dokumente in DailySelection-Objekte umwandeln und nach ID filtern (endet auf heute)
                val sel = snap.documents
                    .mapNotNull { it.toObject(DailySelection::class.java) }
                    .find { it.id.endsWith(today) }

                if (sel != null) {
                    // UI aktualisieren: Container anzeigen, Daten eintragen
                    b.selectedSongContainer.visibility = View.VISIBLE
                    b.textSelectedSong.text    = sel.songName
                    b.textSelectedArtist.text = sel.artist
                    if (sel.comment.isNullOrBlank()) {
                        b.textSelectedSongComment.visibility = View.GONE
                    } else {
                        b.textSelectedSongComment.visibility = View.VISIBLE
                        b.textSelectedSongComment.text       = sel.comment
                    }
                    Glide.with(this@ProfileFragment)
                        .load(sel.imageUrl)
                        .into(b.imageSelectedAlbum)
                } else {
                    // Kein Eintrag für heute → Container verbergen
                    b.selectedSongContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                // Fehler → Container verbergen
                b.selectedSongContainer.visibility = View.GONE
            }
        }
    }

    /**
     * Ruft die Top-Tracks des Nutzers von Spotify ab und zeigt sie in einer horizontalen RecyclerView.
     */
    private fun loadTopSongs() {
        val prefs = requireContext().getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("access_token", "") ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Netzwerk-Aufruf im IO-Thread
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.spotifyApi.getUserTopTracks("Bearer $token", limit = 10)
                }
                withContext(Dispatchers.Main) {
                    b.topSongsRecyclerView.apply {
                        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        adapter = TopSongsAdapter(resp.items)
                    }
                }
            } catch (e: Exception) {
                // TODO: error handling
                // rip we forgor this too
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // BroadcastReceiver registrieren, um auf Logout-Events zu reagieren
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(logoutReceiver, IntentFilter("com.example.bejam.USER_LOGGED_OUT"))
    }

    override fun onPause() {
        super.onPause()
        // Receiver wieder abmelden
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(logoutReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
