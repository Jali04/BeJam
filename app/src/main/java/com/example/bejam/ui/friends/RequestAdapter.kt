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

/**
 * Adapter für die Darstellung eingehender Freundschaftsanfragen in einer RecyclerView.
 *
 * @param onRespond Callback, das ausgelöst wird, wenn der Nutzer eine Anfrage annimmt (true) oder ablehnt (false).
 */
class RequestAdapter(
    private val onRespond: (FirestoreManager.Request, Boolean) -> Unit
) : ListAdapter<FirestoreManager.Request, RequestAdapter.VH>(Diff()) {

    /**
     * Erzeugt einen ViewHolder, indem das Layout [ItemRequestBinding] aufgeblasen wird.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Bindet das Element an den ViewHolder, indem wir [bind] mit dem entsprechenden Request-Objekt aufrufen.
     */
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    /**
     * ViewHolder-Klasse für ein einzelnes Request-Item.
     *
     * @param b Binding-Objekt für item_request.xml
     */
    inner class VH(private val b: ItemRequestBinding)
        : RecyclerView.ViewHolder(b.root) {

        /**
         * Bindet die Daten aus [req] in die UI-Komponenten des Item-Layouts.
         */
        fun bind(req: FirestoreManager.Request) {
            // Setze vorläufig die UID als Text, bis wir den Display-Namen aus Firestore geladen haben.
            b.requestFrom.text = req.fromUid
            // Lade zusätzliches Profilmaterial (Name + Avatar) aus der "user_profiles"-Collection in Firestore.
            FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(req.fromUid)
                .get()
                .addOnSuccessListener { doc ->
                    // Username
                    val name = doc.getString("displayName")
                        // sonst UID
                        ?: doc.getString("spotifyId")
                        ?: req.fromUid
                    b.requestFrom.text = name

                    // Profile image URL
                    val photoUrl = doc.getString("avatarUrl")
                        ?: doc.getString("spotifyPhotoUrl")

                    if (!photoUrl.isNullOrEmpty()) {
                        // Lade das Bild mit Glide in das ImageView
                        Glide.with(b.requestAvatar.context)
                            .load(photoUrl)
                            .placeholder(R.drawable.placeholder_profile)
                            .into(b.requestAvatar)
                    } else {
                        // Falls keine URL vorhanden ist, setze eine Platzhaltergrafik
                        b.requestAvatar.setImageResource(R.drawable.placeholder_profile)
                    }
                }
                .addOnFailureListener {
                    // Bei Fehler: zeige UID und Platzhalter-Avatar
                    b.requestFrom.text = req.fromUid
                    b.requestAvatar.setImageResource(R.drawable.placeholder_profile)
                }

            // Setze Click-Listener für "Annehmen" und "Ablehnen".
            b.acceptBtn.setOnClickListener { onRespond(req, true) }
            b.rejectBtn.setOnClickListener { onRespond(req, false) }
        }

    }

    /**
     * DiffUtil-Klasse zum effizienten Vergleichen von Request-Objekten:
     * - areItemsTheSame vergleicht anhand der eindeutigen ID
     * - areContentsTheSame vergleicht alle Felder
     */
    class Diff : DiffUtil.ItemCallback<FirestoreManager.Request>() {
        override fun areItemsTheSame(a: FirestoreManager.Request, b: FirestoreManager.Request) =
            a.id == b.id
        override fun areContentsTheSame(a: FirestoreManager.Request, b: FirestoreManager.Request) =
            a == b
    }
}