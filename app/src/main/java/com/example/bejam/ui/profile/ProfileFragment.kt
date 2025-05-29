package com.example.bejam.ui.profile

import android.content.Context
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
import com.example.bejam.ui.profile.TopSongsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
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


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val b get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentProfileBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserProfile()
        loadSelectedSong()
        loadTopSongs()
    }

    private fun loadUserProfile() {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", "") ?: return

        // launch on Main-safe coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    RetrofitClient.spotifyApi
                        .getCurrentUserProfile("Bearer $token")
                }

                // bind to UI
                b.textDisplayName.text = profile.display_name.orEmpty()
                b.textUsername.text = "@${profile.id}"
                val imageUrl = profile.images?.firstOrNull()?.url
                Glide.with(this@ProfileFragment)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_profile)
                    .into(b.profileImageLarge)

                requireContext()
                    .getSharedPreferences("auth", Context.MODE_PRIVATE)
                    .edit()
                    .putString("spotify_user_id", profile.id)
                    .apply()

            } catch (e: Exception) {
                // TODO: handle error (e.g. show Toast or log out)
            }
        }
    }


    private fun loadSelectedSong() {
        // 1) get FirebaseAuth uid
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 2) build today’s key
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date())

        // 3) hit Firestore once
        viewLifecycleOwner.lifecycleScope.launch {
            val db = Firebase.firestore
            try {
                val snap = withContext(Dispatchers.IO) {
                    db.collection("daily_selections")
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()
                }
                // 4) filter for today’s doc
                val sel = snap.documents
                    .mapNotNull { it.toObject(DailySelection::class.java) }
                    .find { it.id.endsWith(today) }

                if (sel != null) {
                    // 5) bind into your profile UI
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
                    // no selection today
                    b.selectedSongContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                // optionally log or show error
                b.selectedSongContainer.visibility = View.GONE
            }
        }
    }



    private fun loadTopSongs() {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", "") ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // fetch top tracks
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
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
