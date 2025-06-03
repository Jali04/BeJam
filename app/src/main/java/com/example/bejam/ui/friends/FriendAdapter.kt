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

/**
 * Adapter für die RecyclerView, die alle Freunde im UI anzeigt.
 * ListAdapter: vereinfacht diff-basierte Updates für RecyclerViews.
 */

class FriendAdapter : ListAdapter<Friend, FriendAdapter.VH>(Diff()) {

    // Erstellt einen neuen ViewHolder für ein Freund-Item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    // Bindet einen Freund an einen ViewHolder
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemFriendBinding): RecyclerView.ViewHolder(b.root) {
        fun bind(f: Friend) {

            // Die Friend-ID ist eigentlich die UID des Freundes
            val friendUid = f.id

            // Standardanzeige (Username aus lokalem Modell oder UID, falls leer)
            b.username.text = f.username.ifBlank { friendUid }
            b.avatar.setImageResource(R.drawable.placeholder_profile)

            // Holt den aktuellen Anzeigenamen und Profilbild aus Firestore (user_profiles)
            FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(friendUid)
                .get()
                .addOnSuccessListener { doc ->
                    val displayName = doc.getString("displayName") ?: friendUid
                    b.username.text = displayName

                    // Profilbild-URL holen (avatarUrl oder spotifyPhotoUrl)
                    val photoUrl = doc.getString("avatarUrl")
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
        // Prüft, ob die Items dieselbe ID haben (Unique Key)
        override fun areItemsTheSame(a: Friend, b: Friend) = a.id == b.id
        // Prüft, ob die Inhalte identisch sind (vollständiger Vergleich)
        override fun areContentsTheSame(a: Friend, b: Friend) = a == b
    }
}