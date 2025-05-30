package com.example.bejam.ui.home

import android.content.Context
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
import com.google.firebase.auth.FirebaseAuth
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        friendsViewModel = ViewModelProvider(requireActivity())[FriendsViewModel::class.java]

        friendsViewModel.friendUids.observe(viewLifecycleOwner) { friendUids ->
            val myUid = friendsViewModel.currentUid
            val allUids = friendUids + myUid
            friendsViewModel.loadProfilesForUids(allUids)
            // Observe today's feed (friends + self)
            feedViewModel.observeTodayFeed(allUids)
                .observe(viewLifecycleOwner) { list ->
                    feedAdapter.submitList(list)
                }
        }

        friendsViewModel.profileMap.observe(viewLifecycleOwner) { map ->
            feedAdapter.setProfileMap(map)
        }

        // Fehler/Erfolg Like-Feedback
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
        authManager = SpotifyAuthManager(requireContext())
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // SEARCH BAR & TRACKS RECYCLERVIEW SETUP
        adapter = TrackAdapter(
            onPlayClick = { track ->
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
                val action = HomeFragmentDirections
                    .actionHomeFragmentToSongDetailFragment(selection.id)
                findNavController().navigate(action)
            }
        )
        binding.feedRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.feedRecyclerView.adapter = feedAdapter

        val searchResultsContainer = binding.searchResultsContainer
        binding.searchBarCard.cardElevation = 8f
        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            binding.searchBarCard.cardElevation = if (hasFocus) 16f else 8f
        }

        // SEARCH BAR HOOK – nur diesen Block ändern!
        binding.searchEditText.addTextChangedListener(afterTextChanged = { editable ->
            val q = editable?.toString()?.trim().orEmpty()
            if (q.length < 3) {
                if (searchResultsContainer.visibility == View.VISIBLE) {
                    searchResultsContainer.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction { searchResultsContainer.visibility = View.GONE }
                        .start()
                }
                return@addTextChangedListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val token = prefs.getString("access_token", "") ?: ""
                try {
                    val resp = RetrofitClient.spotifyApi.searchTracks("Bearer $token", q)
                    withContext(Dispatchers.Main) {
                        adapter.submitList(resp.tracks.items)
                        if (searchResultsContainer.visibility != View.VISIBLE) {
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
