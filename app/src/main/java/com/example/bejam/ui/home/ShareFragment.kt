package com.example.bejam.ui.home

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.bejam.data.model.DailySelection
import com.example.bejam.databinding.FragmentShareBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShareFragment ermöglicht es dem Nutzer, einen ausgewählten Spotify-Track
 * als täglichen „DailySelection“-Beitrag zu posten. Dabei wird sichergestellt,
 * dass jeder Nutzer pro Tag nur einen einzigen Beitrag veröffentlichen kann.
 */
class ShareFragment : Fragment() {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!

    // Übergabewerte aus der Navigation: trackId, trackName, artistNames und imageUrl
    private val args: ShareFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentShareBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Setze UI-Elemente mit den Werten aus den NavArgs
        binding.trackName.text = args.trackName
        binding.artistNames.text = args.artistNames
        Glide.with(this)
            .load(args.imageUrl)
            .into(binding.albumImage)

        // Hole aktuellen Firebase-User
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            // Erzeuge Dokument-Id für „today’s post“ im Format "<userId>_yyyy-MM-dd"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val docId = "${firebaseUser.uid}_$today"
            // Prüfe, ob der Nutzer heute bereits gepostet hat
            Firebase.firestore.collection("daily_selections")
                .document(docId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        // Falls bereits existiert: Button deaktivieren und Hinweis anzeigen
                        binding.postButton.isEnabled = false
                        binding.postButton.text = "Du hast heute schon einen Song gepostet!"
                        Toast.makeText(requireContext(), "Du hast heute schon einen Song gepostet!", Toast.LENGTH_LONG).show()
                    } else {
                        // Noch kein Post heute: Button aktivieren
                        binding.postButton.isEnabled = true
                        binding.postButton.text = "Post"
                        binding.postButton.setOnClickListener {
                            postSong(docId)
                        }
                    }
                }
                .addOnFailureListener {
                    // Bei Fehlern (z.B. Netzwerkausfall) trotzdem erlauben, zu posten
                    binding.postButton.isEnabled = true
                    binding.postButton.text = "Post"
                    binding.postButton.setOnClickListener {
                        postSong(docId)
                    }
                }
        }
    }

    /**
     * Legt das DailySelection-Objekt an und speichert es in Firestore.
     * Anschließend wird der Nutzer per Toast informiert und zum vorherigen Screen zurückgebracht.
     */
    private fun postSong(docId: String) {
        val comment = binding.commentEditText.text.toString().trim()
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            // Erzeuge DailySelection mit allen nötigen Feldern
            val selection = DailySelection(
                id = docId,
                userId = firebaseUser.uid,
                songId = args.trackId,
                songName = args.trackName,
                artist = args.artistNames,
                imageUrl = args.imageUrl,
                comment = comment,
                likes = emptyList(),
                timestamp = System.currentTimeMillis()
            )

            // Speichere in Firestore
            Firebase.firestore.collection("daily_selections")
                .document(docId)
                .set(selection)
                .addOnSuccessListener {
                    // Bei Erfolg: Toast, Button deaktivieren und zurück navigieren
                    Toast.makeText(requireContext(), "Posted “${args.trackName}”!", Toast.LENGTH_SHORT).show()
                    binding.postButton.isEnabled = false
                    binding.postButton.text = "Du hast heute schon einen Song gepostet!"
                    findNavController().popBackStack()
                }
                .addOnFailureListener {
                    // Bei Fehler: Fehlermeldung per Toast
                    Toast.makeText(requireContext(), "Failed to post selection.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
