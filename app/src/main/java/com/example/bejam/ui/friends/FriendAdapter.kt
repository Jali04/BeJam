package com.example.bejam.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.data.model.Friend
import com.example.bejam.databinding.ItemFriendBinding

class FriendAdapter : ListAdapter<Friend, FriendAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemFriendBinding): RecyclerView.ViewHolder(b.root) {
        fun bind(f: Friend) {
            b.username.text = f.username
            Glide.with(b.root).load(f.profileImageUrl).into(b.avatar)
            // TODO: upvote/comment buttons could go here
        }
    }

    class Diff : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(a: Friend, b: Friend) = a.id == b.id
        override fun areContentsTheSame(a: Friend, b: Friend) = a == b
    }
}