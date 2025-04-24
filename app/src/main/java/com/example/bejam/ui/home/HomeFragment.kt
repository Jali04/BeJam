package com.example.bejam.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    // we'll reuse this to start login & to logout
    private lateinit var authManager: SpotifyAuthManager

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