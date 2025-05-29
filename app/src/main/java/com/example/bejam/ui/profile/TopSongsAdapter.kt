package com.example.bejam.ui.profile

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.data.model.Track
import com.example.bejam.databinding.ItemTopSongBinding

class TopSongsAdapter(
    private val tracks: List<Track>
) : RecyclerView.Adapter<TopSongsAdapter.VH>() {

    inner class VH(private val b: ItemTopSongBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(track: Track) {
            b.textSongTitle.text = track.name
            b.textSongArtist.text = track.artists.joinToString { it.name }
            Glide.with(b.root)
                .load(track.album.images.firstOrNull()?.url)
                .placeholder(R.drawable.placeholder_profile)
                .into(b.imageSongArt)

            b.root.setOnClickListener {
                // open in Spotify
                val intent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("spotify:track:${track.id}"))
                it.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTopSongBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun getItemCount() = tracks.size
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(tracks[position])
}
