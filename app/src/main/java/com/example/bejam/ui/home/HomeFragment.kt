package com.example.bejam.ui.home

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.data.RetrofitClient
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.FragmentHomeBinding
import com.example.bejam.ui.friends.FriendsViewModel
import com.example.bejam.ui.friends.UserProfile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ExoPlayer
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.isVisible

/**
 * HomeFragment zeigt je nach Status:
 * - Login-Prompt, falls der Nutzer nicht angemeldet ist
 * - Overlay, falls der Nutzer heute noch keinen DailySelection-Post abgesetzt hat
 * - Feed (Liste von DailySelection), sobald der Nutzer heute selbst einen Post erstellt hat
 * Außerdem ermöglicht es Suchanfragen in Spotify und das Abspielen von Track-Vorschauen.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: SpotifyAuthManager
    private lateinit var adapter: TrackAdapter
    private lateinit var feedAdapter: FeedAdapter
    private var player: ExoPlayer? = null
    private lateinit var friendsViewModel: FriendsViewModel
    private val feedViewModel: FeedViewModel by viewModels()

    private val feedListeners = mutableListOf<ListenerRegistration>()

    // Receiver to handle logout events and refresh UI
    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Wenn kein Nutzer angemeldet, zeige Login-Prompt und leere Feed
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid != null) {
                // Falls doch ein Nutzer vorhanden (z.B. nach Re-Login), überprüfe erneut, ob heute gepostet
                feedViewModel.checkIfPostedToday(currentUid)
            } else {
                // If no user, show login prompt and hide other containers
                binding.loginPromptContainer.isVisible = true
                binding.backgroundContent.isVisible = false
                binding.overlayContainer.isVisible = false
                // Clear the feed list to ensure the RecyclerView is empty
                feedAdapter.submitList(emptyList())
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Registriere den BroadcastReceiver für Logout-Events
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(logoutReceiver, IntentFilter("com.example.bejam.USER_LOGGED_OUT"))
    }

    override fun onPause() {
        super.onPause()
        // Deregistriere den Receiver, sobald das Fragment pausiert
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(logoutReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Prüfe beim Anzeigen des Fragments, ob ein Nutzer angemeldet ist
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Zeige Login-Prompt, wenn kein Nutzer eingeloggt
            binding.loginPromptContainer.isVisible = true
            binding.backgroundContent.isVisible = false
            binding.overlayContainer.isVisible = false
            binding.loginButton.setOnClickListener {
                // Navigiere zum Profile-Tab, um Login auszuführen
                findNavController().navigate(R.id.navigation_profile)
            }
            return
        }
        // Initialisiere FriendsViewModel, um Freunde und deren IDs zu beobachten
        friendsViewModel = ViewModelProvider(requireActivity())[FriendsViewModel::class.java]

        // Prüfe, ob der Nutzer heute schon gepostet hat
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        feedViewModel.checkIfPostedToday(currentUid)
        // Beobachte hasPostedToday LiveData, um Overlay bzw. Feed einzublenden
        feedViewModel.hasPostedToday.observe(viewLifecycleOwner) { posted ->
            //Overlay (Button „Heute posten“) nur anzeigen, falls noch kein Post vorgenommen
            binding.overlayContainer.isVisible = !posted
            // Hintergrund (Feed) anzeigen, sobald gepostet
            binding.backgroundContent.isVisible = posted
            if (posted) {
                // Sobald der Nutzer gepostet hat, lade den Feed von Freunden + eigenem Post
                friendsViewModel.friendUids.value?.let { friendUids ->
                    val allUids = friendUids + currentUid
                    feedViewModel.observeTodayFeed(allUids)
                        .observe(viewLifecycleOwner) { list ->
                            feedAdapter.submitList(list)
                        }
                }
            }
        }

        // Beobachte Änderungen an friendUids, um Profile zu laden und Feed zu aktualisieren
        friendsViewModel.friendUids.observe(viewLifecycleOwner) { friendUids ->
            val myUid = friendsViewModel.currentUid
            val allUids = friendUids + myUid
            // Lade Profile der Freunde (Avatar, Anzeigename) für FeedAdapter
            friendsViewModel.loadProfilesForUids(allUids)
            // Beobachte Live-Feed für  heute, sobald friendUids verfügbar
            feedViewModel.observeTodayFeed(allUids)
                .observe(viewLifecycleOwner) { list ->
                    feedAdapter.submitList(list)
                }
        }

        // Wenn ProfileMap aktualisiert wird, übergib sie an den Adapter
        friendsViewModel.profileMap.observe(viewLifecycleOwner) { map ->
            feedAdapter.setProfileMap(map)
        }

        // Like-Feedback: Zeige Toast, falls Like-Operation fehlgeschlagen
        feedViewModel.likeResult.observe(viewLifecycleOwner) { success ->
            if (!success) Toast.makeText(context, "Konnte Like nicht speichern", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // Wenn kein Nutzer angemeldet, zeige nur Login-Prompt und beende
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.loginPromptContainer.isVisible = true
            binding.backgroundContent.isVisible = false
            binding.overlayContainer.isVisible = false
            return binding.root
        }
        // Initialisiere SpotifyAuthManager und SharedPreferences für Token
        authManager = SpotifyAuthManager(requireContext())
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // SEARCH BAR & TRACKS RECYCLERVIEW SETUP
        adapter = TrackAdapter(
            onPlayClick = { track ->
                // Wenn Preview-URL verfügbar, starte ExoPlayer und pausiere nach 10 Sekunden
                val url = track.preview_url
                if (url.isNullOrEmpty()) {
                    Toast.makeText(requireContext(),
                        "Sorry, no preview available for “${track.name}”",
                        Toast.LENGTH_SHORT).show()
                    return@TrackAdapter
                }
                player?.release()
                player = ExoPlayer.Builder(requireContext()).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    play()
                    Handler(Looper.getMainLooper()).postDelayed({ pause() }, 10_000)
                }
            },
            onSelectClick = { track ->
                // Wenn Nutzer einen Track auswählt, navigiere zur ShareFragment
                val action = HomeFragmentDirections
                    .actionHomeFragmentToShareFragment(
                        track.id,
                        track.name,
                        track.artists.joinToString(",") { it.name },
                        track.album.images.firstOrNull()?.url
                            ?: ""
                    )
                findNavController().navigate(action)
            }
        )
        binding.tracksRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.tracksRecyclerView.adapter = adapter

        // FEED RECYCLERVIEW SETUP
        feedAdapter = FeedAdapter(
            onLikeClicked = { selection -> feedViewModel.onLikeClicked(selection) },
            onItemClick = { selection ->
                // Beim Klick auf einen Eintrag navigiere zur Detailansicht
                val action = HomeFragmentDirections
                    .actionHomeFragmentToSongDetailFragment(selection.id)
                findNavController().navigate(action)
            }
        )
        binding.feedRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.feedRecyclerView.adapter = feedAdapter

        // Alias für das Container-Layout der Suchergebnisse
        val searchResultsContainer = binding.searchResultsContainer

        // SEARCH BAR HOOK: Text-Listener für Spotify-Suche
        binding.searchEditText.addTextChangedListener(afterTextChanged = { editable ->
            val q = editable?.toString()?.trim().orEmpty()
            if (q.length < 3) {
                // Wenn weniger als 3 Zeichen, blendet Suchergebnis-Container aus
                if (searchResultsContainer.visibility == View.VISIBLE) {
                    searchResultsContainer.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction { searchResultsContainer.visibility = View.GONE }
                        .start()
                }
                return@addTextChangedListener
            }
            // Ab 3 Zeichen führe Spotify-API-Aufruf aus
            lifecycleScope.launch(Dispatchers.IO) {
                val token = prefs.getString("access_token", "") ?: ""
                try {
                    val resp = RetrofitClient.spotifyApi.searchTracks("Bearer $token", q)
                    withContext(Dispatchers.Main) {
                        adapter.submitList(resp.tracks.items)
                        if (searchResultsContainer.visibility != View.VISIBLE) {
                            // Falls noch nicht sichtbar, blende es mit Fade-In ein
                            searchResultsContainer.alpha = 0f
                            searchResultsContainer.visibility = View.VISIBLE
                            searchResultsContainer.animate().alpha(1f).setDuration(180).start()
                        }
                    }
                } catch (e: HttpException) {
                    val code = e.code()
                    val err = e.response()?.errorBody()?.string()
                    Log.e("SpotifySearch", "HTTP $code: $err")
                    if (code == 401) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Session expired. Please log in again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Search failed: HTTP $code", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Search error: ${e.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        feedListeners.forEach { it.remove() }
        feedListeners.clear()
        _binding = null
    }
}
