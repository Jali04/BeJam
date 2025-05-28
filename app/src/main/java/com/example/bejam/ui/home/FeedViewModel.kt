package com.example.bejam.ui.home

import androidx.lifecycle.*
import com.example.bejam.data.model.DailySelection
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

class FeedViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _likeResult = MutableLiveData<Boolean>()
    val likeResult: LiveData<Boolean> = _likeResult

    // Like-Logik: Füge/mehrem Like hinzu oder entferne es (toggle)
    fun onLikeClicked(selection: DailySelection) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = db.collection("daily_selections").document(selection.id)
        val likes = selection.likes.toMutableList()
        if (likes.contains(myUid)) likes.remove(myUid)
        else likes.add(myUid)

        // Schreibe nur das likes-Feld
        viewModelScope.launch {
            try {
                docRef.update("likes", likes).addOnSuccessListener {
                    _likeResult.postValue(true)
                }.addOnFailureListener {
                    _likeResult.postValue(false)
                }
            } catch (e: Exception) {
                _likeResult.postValue(false)
            }
        }
    }
}
