package com.example.bejam.ui.friends

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bejam.R
import com.example.bejam.databinding.FragmentFriendsBinding
import com.google.firebase.auth.FirebaseAuth

class FriendsFragment : Fragment() {
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

        // Wait until user is signed in before enabling UI or initializing ViewModel
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            binding.root.postDelayed({
                if (auth.currentUser == null) {
                    Toast.makeText(context, "Please wait, signing in...", Toast.LENGTH_SHORT).show()
                } else {
                    // Now signed in, re-navigate to this fragment or refresh
                    findNavController().navigate(R.id.navigation_friends)
                }
            }, 500) // 0.5s delay, tweak as needed
            return
        }

        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        ).get(FriendsViewModel::class.java)

        // 1) “Send request” button
        binding.addFriendButton.setOnClickListener {
            val toUid = binding.addFriendEditText.text.toString().trim()
            if (toUid.isNotEmpty()) {
                vm.sendRequest(toUid)
                binding.addFriendEditText.text?.clear()
            }
        }

        // 2) Incoming-requests RecyclerView
        reqAdapter = RequestAdapter { req, accept ->
            vm.respond(req, accept)
        }
        binding.requestsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reqAdapter
        }
        vm.incomingRequests.observe(viewLifecycleOwner) { list ->
            reqAdapter.submitList(list)
        }

        // 3) “Accepted friends” RecyclerView
        friendAdapter = FriendAdapter()
        binding.friendsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = friendAdapter
        }
        vm.friends.observe(viewLifecycleOwner) { pairs ->
            // grab your own UID directly from FirebaseAuth
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

        // 4) Toast on sendRequest success/failure
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
                    // no-op: this just resets the event so it won’t fire again
                }
            }
        }

        // 5) Any generic error
        vm.error.observe(viewLifecycleOwner) { msg ->
            binding.errorText.text = msg
            binding.errorText.visibility =
                if (msg != null) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
