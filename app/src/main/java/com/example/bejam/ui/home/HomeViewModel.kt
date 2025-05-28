package com.example.bejam.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.model.DailySelection

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    //Toggle like/unlike for a daily selection.

    fun onLikeClicked(selection: DailySelection) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (selection.likes.contains(me)) {
            FirestoreManager.unlikeSelection(selection.id, me)
        } else {
            FirestoreManager.likeSelection(selection.id, me)
        }
    }
}
