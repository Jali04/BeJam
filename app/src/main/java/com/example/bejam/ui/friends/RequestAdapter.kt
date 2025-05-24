package com.example.bejam.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bejam.databinding.ItemRequestBinding
import com.example.bejam.data.FirestoreManager

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
            b.requestFrom.text = req.fromUid
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