package com.example.bejam.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.databinding.FragmentHomeBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Check if user is logged in by seeing if an access token is stored.
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            // User already logged in, so hide the login button and fetch the profile.
            binding.spotifyLoginButton.visibility = View.GONE
            fetchUserProfile(accessToken)
        } else {
            // Show the login button if no token is available.
            binding.spotifyLoginButton.visibility = View.VISIBLE
        }

        binding.spotifyLoginButton.setOnClickListener {
            val authManager = SpotifyAuthManager(requireContext())
            authManager.startLogin() // Launches the Custom Tab for Spotify login.
        }

        return root
    }

    // Place the fetchUserProfile function below onCreateView.
    private fun fetchUserProfile(accessToken: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Optionally log the error
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    try {
                        val json = JSONObject(responseData)
                        // Check if there's at least one profile image.
                        val imageUrl = if (json.has("images") && json.getJSONArray("images").length() > 0) {
                            json.getJSONArray("images").getJSONObject(0).getString("url")
                        } else {
                            null
                        }
                        // Update UI on the main thread
                        requireActivity().runOnUiThread {
                            if (!imageUrl.isNullOrEmpty()) {
                                Glide.with(requireContext())
                                    .load(imageUrl)
                                    .placeholder(R.drawable.placeholder_profile)
                                    .into(binding.profileImageView)
                            } else {
                                // Set a default image if no profile picture is found.
                                binding.profileImageView.setImageResource(R.drawable.placeholder_profile)
                            }
                        }
                    } catch (e: Exception) {
                        // Log exception if needed
                    }
                } else {
                    // Log error response if needed.
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
