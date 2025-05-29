package com.example.bejam.ui.detail

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.FragmentSongDetailBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

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
            // SharedPreferences & Token
            val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", null)
            if (token.isNullOrEmpty()) {
                Toast.makeText(requireContext(),
                    "Bitte melde dich zuerst bei Spotify an.",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // DailySelection-ID
            val docId = arguments?.getString("dailySelectionId") ?: return@setOnClickListener

            // Dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Zu Favoriten hinzufügen")
                .setMessage("Möchtest du diesen Song wirklich zu deinen Spotify-Favoriten hinzufügen?")
                .setPositiveButton("Ja") { _, _ ->
                    // **Hier direkt ausführen:**
                    Firebase.firestore
                        .collection("daily_selections")
                        .document(docId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val sel = doc.toObject(DailySelection::class.java)
                            val spotifyTrackId = sel?.songId
                            if (spotifyTrackId.isNullOrEmpty()) {
                                Toast.makeText(requireContext(),
                                    "Spotify Track ID fehlt!",
                                    Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }
                            // Request bauen und senden
                            OkHttpClient().newCall(
                                Request.Builder()
                                    .url("https://api.spotify.com/v1/me/tracks?ids=$spotifyTrackId")
                                    .addHeader("Authorization", "Bearer $token")
                                    .put(RequestBody.create(null, ByteArray(0)))
                                    .build()
                            ).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    requireActivity().runOnUiThread {
                                        Toast.makeText(requireContext(),
                                            "Fehler beim Hinzufügen: ${e.localizedMessage}",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onResponse(call: Call, response: Response) {
                                    requireActivity().runOnUiThread {
                                        if (response.isSuccessful) {
                                            Toast.makeText(requireContext(),
                                                "Song wurde zu deinen Spotify Likes hinzugefügt!",
                                                Toast.LENGTH_SHORT).show()
                                            binding.likeOnSpotifyButton.isEnabled = false
                                            binding.likeOnSpotifyButton.text = "Hinzugefügt!"
                                        } else {
                                            Toast.makeText(requireContext(),
                                                "Fehler: ${response.code}",
                                                Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            })
                        }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
