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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.data.RetrofitClient
import com.example.bejam.databinding.FragmentHomeBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import androidx.core.widget.addTextChangedListener
import retrofit2.HttpException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // we'll reuse this to start login & to logout
    private lateinit var authManager: SpotifyAuthManager


    private lateinit var adapter: TrackAdapter
    private var player: SimpleExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // usual ViewModel setup (if you still need it)
        ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        authManager = SpotifyAuthManager(requireContext())
        val root: View = binding.root

        // see if we're already logged in
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // 1) Setup RecyclerView + Adapter
        adapter = TrackAdapter { track ->
            // Play 10s preview
            player?.release()
            player = SimpleExoPlayer.Builder(requireContext()).build().apply {
                setMediaItem(MediaItem.fromUri(track.preview_url!!))
                prepare(); play()
                Handler(Looper.getMainLooper()).postDelayed({ stop() }, 10_000)
            }
        }
        binding.tracksRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.tracksRecyclerView.adapter = adapter

// 2) Hook search bar
        binding.searchEditText.addTextChangedListener { editable ->
            val q = editable.toString().trim()
            if (q.length < 3) {
                binding.tracksRecyclerView.visibility = View.GONE
                return@addTextChangedListener
            }
            lifecycleScope.launch {
                val token = prefs.getString("access_token","") ?: return@launch
                try {
                    val resp = RetrofitClient.spotifyApi.searchTracks("Bearer $token", q)
                    adapter.submitList(resp.tracks.items)
                    binding.tracksRecyclerView.visibility = View.VISIBLE

                } catch (e: HttpException) {
                    // log the full error body
                    val code     = e.code()
                    val errBody  = e.response()?.errorBody()?.string()
                    Log.e("SpotifySearch", "HTTP $code — $errBody")
                    Toast.makeText(
                        requireContext(),
                        "Search failed: HTTP $code\nSee logcat for details",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Search error: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            // logged in → hide Login, show Logout, fetch profile
            binding.spotifyLoginButton.visibility = View.GONE
            binding.spotifyLogoutButton.visibility = View.VISIBLE
            fetchUserProfile(accessToken)
        } else {
            // not logged in → show Login, hide Logout
            binding.spotifyLoginButton.visibility = View.VISIBLE
            binding.spotifyLogoutButton.visibility = View.GONE
        }

        // Login flow
        binding.spotifyLoginButton.setOnClickListener {
            authManager.startLogin()
        }

        // Logout flow
        binding.spotifyLogoutButton.setOnClickListener {
            authManager.logout()
            // switch UI back
            binding.spotifyLoginButton.visibility = View.VISIBLE
            binding.spotifyLogoutButton.visibility = View.GONE
            // clear profile pic
            binding.profileImageView.setImageResource(R.drawable.placeholder_profile)
        }

        return root
    }

    // unchanged
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
        _binding = null
    }
}