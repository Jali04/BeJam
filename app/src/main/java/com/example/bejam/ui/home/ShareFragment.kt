package com.example.bejam.ui.home

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.bejam.databinding.FragmentShareBinding

class ShareFragment : Fragment() {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!
    private val args: ShareFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentShareBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Populate UI
        binding.trackName.text = args.trackName
        binding.artistNames.text = args.artistNames
        Glide.with(this)
            .load(args.imageUrl)
            .into(binding.albumImage)

        binding.postButton.setOnClickListener {
            val comment = binding.commentEditText.text.toString().trim()
            // TODO: call your repository to post today’s selection
            Toast.makeText(requireContext(), "Posted “${args.trackName}”!", Toast.LENGTH_SHORT).show()
            // then pop back:
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
