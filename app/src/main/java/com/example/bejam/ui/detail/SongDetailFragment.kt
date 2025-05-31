package com.example.bejam.ui.detail

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.widget.ImageButton // <--- NEU
import com.google.firebase.auth.FirebaseAuth // <--- NEU
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

    private var spotifySongId: String? = null
    private var currentSelection: DailySelection? = null // <--- NEU

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
                    val sel = doc.toObject(DailySelection::class.java)
                    if (sel != null) {
                        currentSelection = sel // <--- NEU
                        spotifySongId = sel.songId
                        binding.songTitle.text = sel.songName
                        binding.artistName.text = sel.artist
                        binding.comment.text = sel.comment ?: ""
                        Glide.with(this).load(sel.imageUrl)
                            .placeholder(com.example.bejam.R.drawable.placeholder_album)
                            .into(binding.albumCover)
                        // Play-Button nur sichtbar, wenn SongId vorhanden
                        binding.playButton.visibility = if (!sel.songId.isNullOrEmpty()) View.VISIBLE else View.GONE
                        // --- NEU: Kommentar-Bearbeiten-Button einblenden, wenn Post von mir ---
                        val myUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (sel.userId == myUid) {
                            // Stift-Icon sichtbar machen
                            binding.editCommentButton.visibility = View.VISIBLE
                        } else {
                            binding.editCommentButton.visibility = View.GONE
                        }
                    }
                }
            }

        // Playbutton (unter Albumcover)
        binding.playButton.setOnClickListener {
            val songId = spotifySongId
            if (!songId.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:$songId"))
                intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://" + requireContext().packageName))
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Kein Spotify-Link verfügbar.", Toast.LENGTH_SHORT).show()
            }
        }

        // Like-Button (Spotify Likes)
        binding.likeOnSpotifyButton.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", null)
            if (token.isNullOrEmpty()) {
                Toast.makeText(requireContext(),
                    "Bitte melde dich zuerst bei Spotify an.",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val docId = arguments?.getString("dailySelectionId") ?: return@setOnClickListener

            AlertDialog.Builder(requireContext())
                .setTitle("Zu Favoriten hinzufügen")
                .setMessage("Möchtest du diesen Song wirklich zu deinen Spotify-Favoriten hinzufügen?")
                .setPositiveButton("Ja") { _, _ ->
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

        // --- NEU: Edit-Button für Kommentar ---
        binding.editCommentButton.setOnClickListener {
            currentSelection?.let { sel ->
                showEditCommentDialog(sel)
            }
        }

        return binding.root
    }

    // --- NEU: Dialog zum Bearbeiten des Kommentars ---
    private fun showEditCommentDialog(sel: DailySelection) {
        val context = requireContext()
        val input = EditText(context)
        input.setText(sel.comment.orEmpty())
        input.setSelection(input.text.length)
        AlertDialog.Builder(context)
            .setTitle("Kommentar bearbeiten")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val newComment = input.text.toString()
                if (newComment != sel.comment) {
                    Firebase.firestore
                        .collection("daily_selections")
                        .document(sel.id)
                        .update("comment", newComment)
                        .addOnSuccessListener {
                            binding.comment.text = newComment
                            Toast.makeText(context, "Kommentar aktualisiert!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Fehler beim Aktualisieren.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
