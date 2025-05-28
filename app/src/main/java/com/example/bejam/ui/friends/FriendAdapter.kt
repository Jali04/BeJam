package com.example.bejam.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bejam.R
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.FriendDao
import com.example.bejam.data.FriendRepository
import com.example.bejam.data.model.Friend
import com.example.bejam.databinding.ItemFriendBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendAdapter : ListAdapter<Friend, FriendAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemFriendBinding): RecyclerView.ViewHolder(b.root) {
        fun bind(f: Friend) {
            // The Friend model’s primary key is the friend's UID
            val friendUid = f.id

            // Default to showing the locally cached username or UID until we load the real profile
            b.username.text = f.username.ifBlank { friendUid }
            b.avatar.setImageResource(R.drawable.placeholder_profile)

            // Fetch displayName and photoUrl from user_profiles
            FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(friendUid)
                .get()
                .addOnSuccessListener { doc ->
                    val displayName = doc.getString("displayName") ?: friendUid
                    b.username.text = displayName

                    val photoUrl = doc.getString("photoUrl")
                        ?: doc.getString("spotifyPhotoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(b.avatar.context)
                            .load(photoUrl)
                            .placeholder(R.drawable.placeholder_profile)
                            .into(b.avatar)
                    }
                }
                .addOnFailureListener {
                    // leave defaults if loading fails
                }
        }
    }

    class Diff : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(a: Friend, b: Friend) = a.id == b.id
        override fun areContentsTheSame(a: Friend, b: Friend) = a == b
    }
}