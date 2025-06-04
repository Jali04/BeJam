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
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

/**
 * Adapter für die Anzeige der Top-Songs in der Profilansicht.
 * Jeder Eintrag zeigt das Album-Artwork, den Song-Titel und den Künstlernamen.
 * Klickt man auf einen Eintrag, wird die Spotify-App geöffnet und der Track angespielt.
 *
 * @param tracks Liste der Track-Objekte, die angezeigt werden.
 */
class TopSongsAdapter(
    private val tracks: List<Track>
) : RecyclerView.Adapter<TopSongsAdapter.VH>() {

    /**
     * ViewHolder, der die Views eines einzelnen Top-Song-Eintrags hält.
     * Bindet Track-Daten an das Item-Layout und färbt die Top-3-Einträge entsprechend ein.
     */
    inner class VH(private val b: ItemTopSongBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(track: Track, position: Int) {
            // Songtitel und Künstlertext setzen
            b.textSongTitle.text = track.name
            b.textSongArtist.text = track.artists.joinToString { it.name }
            // Album-Cover via Glide laden
            Glide.with(b.root)
                .load(track.album.images.firstOrNull()?.url)
                .placeholder(R.drawable.placeholder_profile)
                .into(b.imageSongArt)

            // // Hintergrund und Textfarbe je nach Platzierung der Top 3 ändern
            val card = b.root as CardView
            when (position) {
                0 -> {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(card.context, R.color.goldHighlight)
                    )
                    b.textSongTitle.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                    b.textSongArtist.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                }
                1 -> {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(card.context, R.color.silverHighlight)
                    )
                    b.textSongTitle.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                    b.textSongArtist.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                }
                2 -> {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(card.context, R.color.bronzeHighlight)
                    )
                    b.textSongTitle.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                    b.textSongArtist.setTextColor(
                        ContextCompat.getColor(card.context, R.color.black)
                    )
                }
                else -> {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(card.context, R.color.defaultCardBg)
                    )
                    b.textSongTitle.setTextColor(
                        ContextCompat.getColor(card.context, R.color.defaultTextColor)
                    )
                    b.textSongArtist.setTextColor(
                        ContextCompat.getColor(card.context, R.color.defaultTextColor)
                    )
                }
            }

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

    override fun getItemCount() = tracks.size // Anzahl der darzustellenden Tracks
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(tracks[position], position)
}
