package com.example.bejam.ui.friends

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bejam.R
import com.example.bejam.databinding.FragmentFriendsBinding
import com.google.firebase.auth.FirebaseAuth
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Fragment, das die Freundesliste UND eingehende Freundschaftsanfragen anzeigt.
 */
class FriendsFragment : Fragment() {

    // BroadcastReceiver für Logout-Events, um die Listen und UI zu leeren
    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Beide Adapter leeren
            reqAdapter.submitList(emptyList())
            friendAdapter.submitList(emptyList())

            // Beobachter entfernen
            if (::vm.isInitialized) {
                vm.incomingRequests.removeObservers(viewLifecycleOwner)
                vm.friends.removeObservers(viewLifecycleOwner)
            }

            // UI anpassen (Listen und Labels ausblenden)
            binding.incomingRequestsLabel.isVisible = false
            binding.requestsRecyclerView.isVisible = false
            binding.friendsRecyclerView.isVisible = false
        }
    }

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private lateinit var vm: FriendsViewModel
    private lateinit var reqAdapter: RequestAdapter
    private lateinit var friendAdapter: FriendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Registriert Receiver für Logout-Events (damit UI sofort reagiert)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(logoutReceiver, IntentFilter("com.example.bejam.USER_LOGGED_OUT"))

        // Warte auf User-Login (nicht-anonymer User), bevor UI aktiviert wird
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null || user.isAnonymous) {
            // Verzögert kurz, falls Auth noch läuft; lädt Fragment ggf. neu
            binding.root.postDelayed({
                val newUser = auth.currentUser
                if (newUser == null || newUser.isAnonymous) {
                    Toast.makeText(context, "Please wait, signing in...", Toast.LENGTH_SHORT).show()
                } else {
                    // Sobald ein echter User eingeloggt ist, lade Fragment neu
                    findNavController().navigate(R.id.navigation_friends)
                }
            }, 500) // 0.5s delay, tweak as needed
            return
        }

        // ViewModel initialisieren
        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        ).get(FriendsViewModel::class.java)

        // 1) "Freund hinzufügen"-Button
        binding.addFriendButton.setOnClickListener {
            val input = binding.addFriendEditText.text.toString().trim()
            if (input.isNotEmpty()) {
                vm.sendRequest(input)
                binding.addFriendEditText.text?.clear()
            }
        }

        // 2) Incoming-Requests RecyclerView (eingehende Freundschaftsanfragen)
        reqAdapter = RequestAdapter { req, accept ->
            vm.respond(req, accept)
        }
        binding.requestsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reqAdapter
        }
        vm.incomingRequests.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                // Keine Anfragen ⇒ ausblenden
                binding.incomingRequestsLabel.visibility = View.GONE
                binding.requestsRecyclerView.visibility    = View.GONE
            } else {
                // Anfragen vorhanden ⇒ einblenden und Liste setzen
                binding.incomingRequestsLabel.visibility = View.VISIBLE
                binding.requestsRecyclerView.visibility    = View.VISIBLE
                reqAdapter.submitList(list)
            }
        }

        // 3) Freunde-RecyclerView (akzeptierte Freunde)
        friendAdapter = FriendAdapter()
        binding.friendsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = friendAdapter
        }
        vm.friends.observe(viewLifecycleOwner) { pairs ->
            // Holt die eigene UID
            val me = FirebaseAuth.getInstance().currentUser!!.uid
            friendAdapter.submitList(pairs.map { (a, b) ->
                val otherUid = if (a == me) b else a
                com.example.bejam.data.model.Friend(
                    id = otherUid,
                    username = otherUid,
                    email = null,
                    profileImageUrl = null
                )
            })
        }

        // 4) Feedback für Anfrage gesendet / Fehler
        vm.requestSent.observe(viewLifecycleOwner) { success ->
            when (success) {
                true -> {
                    Toast.makeText(requireContext(),
                        "Friend request sent!", Toast.LENGTH_SHORT).show()
                    vm.clearRequestSent()
                }
                false -> {
                    Toast.makeText(requireContext(),
                        "Could not send request.", Toast.LENGTH_LONG).show()
                    vm.clearRequestSent()
                }
                null -> {
                    // no-op
                }
            }
        }

        // 5) Generische Fehler
        vm.error.observe(viewLifecycleOwner) { msg ->
            binding.errorText.text = msg
            binding.errorText.visibility =
                if (msg != null) View.VISIBLE else View.GONE

            if (msg != null) {
                // Fehler nach 3 Sekunden wieder ausblenden
                Handler(Looper.getMainLooper()).postDelayed({
                    vm.clearError()
                }, 6000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(logoutReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
