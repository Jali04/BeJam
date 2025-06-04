package com.example.bejam.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.ItemFeedBinding
import com.example.bejam.ui.friends.UserProfile
import com.google.firebase.auth.FirebaseAuth

/**
 * Adapter für die Darstellung des Feeds (DailySelection-Liste) in einer RecyclerView.
 *
 * @param onLikeClicked Callback, der ausgeführt wird, wenn der Nutzer den Like-Button klickt.
 * @param onItemClick Callback, der ausgelöst wird, wenn der Nutzer auf das gesamte Item klickt.
 */
class FeedAdapter(
    private val onLikeClicked: (DailySelection) -> Unit,
    private val onItemClick: (DailySelection) -> Unit
) : ListAdapter<DailySelection, FeedAdapter.FeedVH>(Diff()) {

    // Map von userId zu UserProfile, um den Anzeigenamen und Avatar-URL für jeden Benutzer zu speichern.
    private var profileMap: Map<String, UserProfile> = emptyMap()

    /**
     * Aktualisiert die ProfileMap und benachrichtigt die RecyclerView, dass sich die Daten geändert haben.
     *
     * @param map Map von userId auf UserProfile.
     */

    fun setProfileMap(map: Map<String, UserProfile>) {
        profileMap = map
        notifyDataSetChanged()
    }

    /**
     * Erzeugt einen ViewHolder, indem das Item-Layout (ItemFeedBinding) aufgeblasen wird.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FeedVH(ItemFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Bindet das DailySelection-Objekt an den ViewHolder.
     */
    override fun onBindViewHolder(holder: FeedVH, position: Int) =
        holder.bind(getItem(position), profileMap, onLikeClicked, onItemClick)

    /**
     * ViewHolder-Klasse für ein Feed-Item.
     *
     * @param b Binding-Objekt für item_feed.xml
     */
    inner class FeedVH(private val b: ItemFeedBinding): RecyclerView.ViewHolder(b.root) {
        /**
         * Bindet die Daten aus [sel] in die UI-Komponenten des Item-Layouts.
         */
        fun bind(
            sel: DailySelection,
            profileMap: Map<String, UserProfile>,
            onLikeClicked: (DailySelection) -> Unit,
            onItemClick: (DailySelection) -> Unit
        ) {
            // Songinfo and Postername
            val me = FirebaseAuth.getInstance().currentUser?.uid
            b.songTitle.text = sel.songName
            b.artistName.text = sel.artist
            b.comment.text = sel.comment ?: ""
            val myUid = FirebaseAuth.getInstance().currentUser?.uid
            val profile = profileMap[sel.userId]
            b.username.text = when {
                sel.userId == myUid -> "You"
                profile != null     -> profile.displayName
                else                -> "Unknown User"
            }
            // Albumcover mittels Glide laden
            Glide.with(b.root).load(sel.imageUrl).into(b.albumCover)

            // Poster pic
            val avatarUrl = profile?.avatarUrl ?: profile?.spotifyId?.let { id ->
                null
            }
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(b.root)
                    .load(avatarUrl)
                    .placeholder(R.drawable.placeholder_profile)
                    .into(b.posterAvatar)
            } else {
                // Standard‐Platzhalter, falls kein Bild da ist
                b.posterAvatar.setImageResource(R.drawable.placeholder_profile)
            }

            // Like count und Buttonstatus konfigurieren
            b.likeCount.text = sel.likes.size.toString()
            b.likeButton.isEnabled = sel.userId != me  // Kann nicht den eigenen Post liken
            b.likeButton.isSelected = me != null && sel.likes.contains(me)
            b.likeButton.setOnClickListener { // Listener für Like-Button
                if (sel.userId != me) {
                    onLikeClicked(sel)
                    b.likeButton.isSelected = !b.likeButton.isSelected // Schnell visuell umschalten
                }
            }
            b.root.setOnClickListener { onItemClick(sel) }
        }
    }

    /**
     * DiffUtil-Klasse zum effizienten Vergleich von DailySelection-Objekten:
     * - areItemsTheSame vergleicht anhand der eindeutigen id
     * - areContentsTheSame vergleicht alle Felder auf Gleichheit
     */
    class Diff : DiffUtil.ItemCallback<DailySelection>() {
        override fun areItemsTheSame(a: DailySelection, b: DailySelection) = a.id == b.id // Nutze unique id!
        override fun areContentsTheSame(a: DailySelection, b: DailySelection) = a == b
    }
}
