package com.example.bejam.ui.home

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.data.model.Track

class TrackAdapter(
    private val onPlayClick: (Track) -> Unit,
    private val onSelectClick: (Track) -> Unit
): ListAdapter<Track, TrackAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(v: View): RecyclerView.ViewHolder(v) {
        private val album = v.findViewById<ImageView>(R.id.albumImage)
        private val name = v.findViewById<TextView>(R.id.trackName)
        private val artist = v.findViewById<TextView>(R.id.artistName)
        private val playButton = v.findViewById<ImageButton>(R.id.playPreviewBtn)
        private val openButton = v.findViewById<ImageButton>(R.id.openButton)

        init {
            v.setOnClickListener {
                val track = getItem(bindingAdapterPosition)
                onSelectClick(track)
            }
        }

        fun bind(track: Track) {
            name.text = track.name
            artist.text = track.artists.joinToString { it.name }
            Glide.with(itemView)
                .load(track.album.images.firstOrNull()?.url)
                .placeholder(R.drawable.placeholder_profile)
                .into(album)
            if (track.preview_url != null) {
                playButton.visibility = View.VISIBLE
                openButton.visibility = View.GONE
                playButton.isEnabled = true
                playButton.setOnClickListener { onPlayClick(track) }
            } else {
                playButton.visibility = View.GONE
                openButton.visibility = View.VISIBLE
                openButton.isEnabled = true
                openButton.setOnClickListener {
                    val context = itemView.context
                    // deep-link into the Spotify app
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("spotify:track:${track.id}")
                    )
                    context.startActivity(intent)
                }
            }
        }
    }

    class DiffCallback: DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(a: Track, b: Track) = a.id == b.id
        override fun areContentsTheSame(a: Track, b: Track) = a == b
    }
}
