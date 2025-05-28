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
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.data.FriendRepository
import com.example.bejam.data.RetrofitClient
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.FragmentHomeBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.*
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

import androidx.fragment.app.viewModels
import com.example.bejam.ui.home.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: SpotifyAuthManager
    private lateinit var adapter: TrackAdapter
    private lateinit var feedAdapter: FeedAdapter
    private var player: ExoPlayer? = null
    private var friendMap: Map<String, String> = emptyMap()

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        authManager = SpotifyAuthManager(requireContext())
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // --- SEARCH BAR & TRACKS RECYCLERVIEW SETUP ---
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
                // Navigate to ShareFragment with args
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

        // --- FEED RECYCLERVIEW SETUP ---
        feedAdapter = FeedAdapter(viewModel::onLikeClicked)
        binding.feedRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.feedRecyclerView.adapter = feedAdapter

        // --- LOAD FRIENDS AND FEED ---
        FriendRepository.getInstance(requireContext()).getAllFriends()
            .asLiveData().observe(viewLifecycleOwner) { friends ->
                friendMap = friends.associate { it.id to it.username }
                val myUid = FirebaseAuth.getInstance().currentUser?.uid
                val userIds = if (myUid != null) friendMap.keys + myUid else friendMap.keys
                observeLiveFeed(userIds)
                feedAdapter.setFriendList(friends)
            }

        // --- SEARCH BAR HOOK ---
        binding.searchEditText.addTextChangedListener(afterTextChanged = { editable ->
            val q = editable?.toString()?.trim().orEmpty()
            if (q.length < 3) {
                binding.tracksRecyclerView.visibility = View.GONE
                return@addTextChangedListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val token = prefs.getString("access_token", "") ?: ""
                try {
                    val resp = RetrofitClient.spotifyApi.searchTracks("Bearer $token", q)
                    withContext(Dispatchers.Main) {
                        adapter.submitList(resp.tracks.items)
                        binding.tracksRecyclerView.visibility = View.VISIBLE
                    }
                } catch (e: HttpException) {
                    val code = e.code()
                    val err = e.response()?.errorBody()?.string()
                    Log.e("SpotifySearch", "HTTP $code: $err")
                    if (code == 401) {
                        withContext(Dispatchers.Main) {
                            SpotifyAuthManager.refreshAccessToken(requireContext()) { success ->
                                requireActivity().runOnUiThread {
                                    if (success) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val newToken = prefs.getString("access_token", "") ?: ""
                                            val retry = RetrofitClient.spotifyApi
                                                .searchTracks("Bearer $newToken", q)
                                            withContext(Dispatchers.Main) {
                                                adapter.submitList(retry.tracks.items)
                                                binding.tracksRecyclerView.visibility = View.VISIBLE
                                            }
                                        }
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "Session expired. Please log in again.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        authManager.logout()
                                        binding.spotifyLoginButton.visibility = View.VISIBLE
                                        binding.spotifyLogoutButton.visibility = View.GONE
                                        binding.profileImageView.setImageResource(
                                            com.example.bejam.R.drawable.placeholder_profile
                                        )
                                    }
                                }
                            }
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

        // --- SPOTIFY LOGIN STATE ---
        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            binding.spotifyLoginButton.visibility = View.GONE
            binding.spotifyLogoutButton.visibility = View.VISIBLE
            fetchUserProfile(accessToken)
        } else {
            binding.spotifyLoginButton.visibility = View.VISIBLE
            binding.spotifyLogoutButton.visibility = View.GONE
        }

        binding.spotifyLoginButton.setOnClickListener {
            authManager.startLogin()
        }

        binding.spotifyLogoutButton.setOnClickListener {
            authManager.logout()
            binding.spotifyLoginButton.visibility = View.VISIBLE
            binding.spotifyLogoutButton.visibility = View.GONE
            binding.profileImageView.setImageResource(
                com.example.bejam.R.drawable.placeholder_profile
            )
        }

        return binding.root
    }

    private val feedListeners = mutableListOf<ListenerRegistration>()

    private fun observeLiveFeed(userIds: Collection<String>) {
        // Vorherige Listener entfernen (wichtig beim erneuten Aufruf!)
        feedListeners.forEach { it.remove() }
        feedListeners.clear()

        if (userIds.isEmpty()) {
            feedAdapter.submitList(emptyList())
            return
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val db = Firebase.firestore
        val selections = mutableListOf<DailySelection>()

        userIds.chunked(10).forEach { chunk ->
            val query = db.collection("daily_selections")
                .whereIn("userId", chunk)

            val listener = query.addSnapshotListener { snap, err ->
                if (err != null) {
                    // Zeige Fehler (optional)
                    feedAdapter.submitList(emptyList())
                    return@addSnapshotListener
                }
                val posts = snap!!.documents.mapNotNull { doc ->
                    val sel = doc.toObject(DailySelection::class.java)
                    val docId = doc.id
                    // Nur heutige Posts!
                    if (sel != null && doc.id.endsWith(today)) {
                        // Map the Firestore document ID into the selection model
                        sel.copy(id = doc.id)
                    } else null
                }
                // Chunk ersetzen (mehrere Listener = mehrere Chunks)
                // Trick: alle neuen Posts zusammenführen (unique by userId)
                selections.removeAll { s -> chunk.contains(s.userId) }
                selections.addAll(posts)
                feedAdapter.submitList(selections.sortedByDescending { it.timestamp })
            }
            feedListeners.add(listener)
        }
    }

    private fun fetchUserProfile(accessToken: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // log if you want
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                if (response.isSuccessful && !data.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(data)
                        val imageUrl = json.optJSONArray("images")
                            ?.takeIf { it.length() > 0 }
                            ?.getJSONObject(0)
                            ?.optString("url")

                        requireActivity().runOnUiThread {
                            if (!imageUrl.isNullOrEmpty()) {
                                Glide.with(requireContext())
                                    .load(imageUrl)
                                    .placeholder(R.drawable.placeholder_profile)
                                    .into(binding.profileImageView)
                            } else {
                                binding.profileImageView
                                    .setImageResource(R.drawable.placeholder_profile)
                            }
                        }
                    } catch (_: Exception) { /**/ }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        feedListeners.forEach { it.remove() }
        feedListeners.clear()
        _binding = null
    }

}
