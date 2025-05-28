package com.example.bejam.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.ItemFeedBinding
import com.example.bejam.ui.friends.UserProfile
import com.google.firebase.auth.FirebaseAuth

class FeedAdapter(
    private val onLikeClicked: (DailySelection) -> Unit,
    private val onItemClick: (DailySelection) -> Unit
) : ListAdapter<DailySelection, FeedAdapter.FeedVH>(Diff()) {

    private var profileMap: Map<String, UserProfile> = emptyMap()

    fun setProfileMap(map: Map<String, UserProfile>) {
        profileMap = map
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FeedVH(ItemFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FeedVH, position: Int) =
        holder.bind(getItem(position), profileMap, onLikeClicked, onItemClick)

    inner class FeedVH(private val b: ItemFeedBinding): RecyclerView.ViewHolder(b.root) {
        fun bind(
            sel: DailySelection,
            profileMap: Map<String, UserProfile>,
            onLikeClicked: (DailySelection) -> Unit,
            onItemClick: (DailySelection) -> Unit
        ) {
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
            Glide.with(b.root).load(sel.imageUrl).into(b.albumCover)
            b.likeCount.text = sel.likes.size.toString()
            b.likeButton.isEnabled = sel.userId != me
            b.likeButton.isSelected = me != null && sel.likes.contains(me)
            b.likeButton.setOnClickListener {
                if (sel.userId != me) {
                    onLikeClicked(sel)
                }
            }
            b.root.setOnClickListener { onItemClick(sel) }
        }
    }

    class Diff : DiffUtil.ItemCallback<DailySelection>() {
        override fun areItemsTheSame(a: DailySelection, b: DailySelection) = a.id == b.id // Nutze unique id!
        override fun areContentsTheSame(a: DailySelection, b: DailySelection) = a == b
    }
}
