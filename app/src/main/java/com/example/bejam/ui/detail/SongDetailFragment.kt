package com.example.bejam.ui.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.bejam.databinding.FragmentSongDetailBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SongDetailFragment : Fragment() {
    private var _binding: FragmentSongDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongDetailBinding.inflate(inflater, container, false)

        // Argument holen
        val id = arguments?.getString("dailySelectionId") ?: return binding.root

        // Firestore holen
        Firebase.firestore.collection("daily_selections").document(id)
            .get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val sel = doc.toObject(com.example.bejam.data.model.DailySelection::class.java)
                    if (sel != null) {
                        binding.songTitle.text = sel.songName
                        binding.artistName.text = sel.artist
                        binding.comment.text = sel.comment ?: ""
                        Glide.with(this).load(sel.imageUrl)
                            .placeholder(com.example.bejam.R.drawable.placeholder_album)
                            .into(binding.albumCover)
                        // Button-Logik zum Spotify-Liken einbauen (kommt noch)
                    }
                }
            }

        // Button click → später Spotify-Logik hier!
        binding.likeOnSpotifyButton.setOnClickListener {
            // Access Token holen
            val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", null)
            if (token.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Bitte melde dich zuerst bei Spotify an.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Song-ID holen
            val id = arguments?.getString("dailySelectionId") ?: return@setOnClickListener

            // Lade DailySelection um die Spotify Track-ID zu bekommen!
            Firebase.firestore.collection("daily_selections").document(id)
                .get().addOnSuccessListener { doc ->
                    val sel = doc.toObject(com.example.bejam.data.model.DailySelection::class.java)
                    val spotifyTrackId = sel?.songId
                    if (spotifyTrackId.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Spotify Track ID fehlt!", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    // Mache den API Call zu Spotify
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder()
                        .url("https://api.spotify.com/v1/me/tracks?ids=$spotifyTrackId")
                        .addHeader("Authorization", "Bearer $token")
                        .put(okhttp3.RequestBody.create(null, "")) // PUT-Body muss leer sein
                        .build()

                    client.newCall(req).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Fehler beim Hinzufügen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            requireActivity().runOnUiThread {
                                if (response.isSuccessful) {
                                    Toast.makeText(requireContext(), "Song wurde zu deinen Spotify Likes hinzugefügt!", Toast.LENGTH_SHORT).show()
                                    binding.likeOnSpotifyButton.isEnabled = false
                                    binding.likeOnSpotifyButton.text = "Hinzugefügt!"
                                } else {
                                    Toast.makeText(requireContext(), "Fehler: ${response.code}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    })
                }
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
