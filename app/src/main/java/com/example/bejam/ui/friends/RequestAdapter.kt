package com.example.bejam.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bejam.databinding.ItemRequestBinding
import com.example.bejam.data.FirestoreManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.bumptech.glide.Glide
import com.example.bejam.R

class RequestAdapter(
    private val onRespond: (FirestoreManager.Request, Boolean) -> Unit
) : ListAdapter<FirestoreManager.Request, RequestAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemRequestBinding)
        : RecyclerView.ViewHolder(b.root) {

        fun bind(req: FirestoreManager.Request) {
            // Default to UID until we load a nicer name
            b.requestFrom.text = req.fromUid
            // Load profile image & display name from Firestore
            FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(req.fromUid)
                .get()
                .addOnSuccessListener { doc ->
                    // Username
                    val name = doc.getString("displayName")
                        ?: doc.getString("spotifyId")
                        ?: req.fromUid
                    b.requestFrom.text = name

                    // Profile image URL (e.g. Spotify photo or custom)
                    val photoUrl = doc.getString("avatarUrl")
                        ?: doc.getString("spotifyPhotoUrl")

                    if (!photoUrl.isNullOrEmpty()) {
                        // Load into ImageView using Glide
                        Glide.with(b.requestAvatar.context)
                            .load(photoUrl)
                            .placeholder(R.drawable.placeholder_profile)
                            .into(b.requestAvatar)
                    } else {
                        // Optional: clear or set default avatar
                        b.requestAvatar.setImageResource(R.drawable.placeholder_profile)
                    }
                }
                .addOnFailureListener {
                    // Fallback: show UID and default avatar
                    b.requestFrom.text = req.fromUid
                    b.requestAvatar.setImageResource(R.drawable.placeholder_profile)
                }

            // Handle button clicks
            b.acceptBtn.setOnClickListener { onRespond(req, true) }
            b.rejectBtn.setOnClickListener { onRespond(req, false) }
        }

    }

    class Diff : DiffUtil.ItemCallback<FirestoreManager.Request>() {
        override fun areItemsTheSame(a: FirestoreManager.Request, b: FirestoreManager.Request) =
            a.id == b.id
        override fun areContentsTheSame(a: FirestoreManager.Request, b: FirestoreManager.Request) =
            a == b
    }
}