package com.example.bejam.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.data.model.DailySelection
import com.example.bejam.data.model.Friend
import com.example.bejam.databinding.ItemFeedBinding
import com.google.firebase.auth.FirebaseAuth

class FeedAdapter : ListAdapter<DailySelection, FeedAdapter.FeedVH>(Diff()) {
    private var friendMap: Map<String, String> = emptyMap()

    fun setFriendList(friends: List<Friend>) {
        friendMap = friends.associate { it.id to it.username }
    }

    fun friendNameForUserId(userId: String): String {
        // Also check if it's me
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == myUid) return "You"
        return friendMap[userId] ?: "Unknown User"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FeedVH(ItemFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FeedVH, position: Int) =
        holder.bind(getItem(position))

    inner class FeedVH(private val b: ItemFeedBinding): RecyclerView.ViewHolder(b.root) {
        fun bind(sel: DailySelection) {
            b.songTitle.text = sel.songName
            b.artistName.text = sel.artist
            b.comment.text = sel.comment ?: ""
            b.username.text = friendNameForUserId(sel.userId)
            Glide.with(b.root).load(sel.imageUrl).into(b.albumCover)
        }
    }
    class Diff : DiffUtil.ItemCallback<DailySelection>() {
        override fun areItemsTheSame(a: DailySelection, b: DailySelection) = a.userId == b.userId
        override fun areContentsTheSame(a: DailySelection, b: DailySelection) = a == b
    }
}