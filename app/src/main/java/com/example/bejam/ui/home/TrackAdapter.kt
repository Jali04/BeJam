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

/**
 * Adapter für die Darstellung von Suchergebnissen (Track-Objekten) in einer RecyclerView.
 *
 * @param onPlayClick Callback, der ausgeführt wird, wenn der Benutzer auf den Play-Button klickt.
 * @param onSelectClick Callback, der ausgeführt wird, wenn der Benutzer das gesamte Item antippt (Auswahl).
 */

class TrackAdapter(
    private val onPlayClick: (Track) -> Unit,
    private val onSelectClick: (Track) -> Unit
): ListAdapter<Track, TrackAdapter.ViewHolder>(DiffCallback()) {

    /**
     * Erzeugt eine neue ViewHolder-Instanz, indem das Layout für ein einzelnes Track-Item
     * (item_track.xml) inflatiert wird.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false))

    /**
     * ViewHolder für ein Track-Item. Enthält Referenzen auf die UI-Elemente
     * und bindet die Daten eines Track-Objekts an diese.
     */
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
            // Wenn das gesamte Item angetippt wird, rufe onSelectClick mit dem Track-Objekt auf
            v.setOnClickListener {
                val track = getItem(bindingAdapterPosition)
                onSelectClick(track)
            }
        }

        /**
         * Bindet die Daten aus dem übergebenen Track-Modell an die UI-Elemente:
         * - Setzt Name und Künstler
         * - Lädt Album-Cover via Glide
         * - Zeigt entweder Play-Button oder Open-Button je nach Verfügbarkeit der Preview-URL
         */
        fun bind(track: Track) {
            // Songtitel und Künstler anzeigen
            name.text = track.name
            artist.text = track.artists.joinToString { it.name }
            Glide.with(itemView)
                // Lade das Album-Cover per Glide (falls vorhanden) oder Platzhalter
                .load(track.album.images.firstOrNull()?.url)
                .placeholder(R.drawable.placeholder_profile)
                .into(album)
            if (track.preview_url != null) {
                // Falls eine Preview-URL existiert:
                // - Zeige Play-Button, verstecke Open-Button
                playButton.visibility = View.VISIBLE
                openButton.visibility = View.GONE
                playButton.isEnabled = true
                playButton.setOnClickListener { onPlayClick(track) }
            } else {
                // Falls keine Preview verfügbar:
                // - Zeige Open-Button, verstecke Play-Button
                playButton.visibility = View.GONE
                openButton.visibility = View.VISIBLE

                // Open-Button aktivieren und öffnet den Track in der Spotify-App per Deep Link
                openButton.isEnabled = true
                openButton.setOnClickListener {
                    val context = itemView.context
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
