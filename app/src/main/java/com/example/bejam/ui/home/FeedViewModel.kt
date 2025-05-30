package com.example.bejam.ui.home

import androidx.lifecycle.*
import com.example.bejam.data.model.DailySelection
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class FeedViewModel : ViewModel() {
    // LiveData to track if the current user has posted today
    private val _hasPostedToday = MutableLiveData<Boolean>(false)
    val hasPostedToday: LiveData<Boolean> = _hasPostedToday
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

    fun observeTodayFeed(userIds: List<String>): LiveData<List<DailySelection>> {
        val liveData = MutableLiveData<List<DailySelection>>()
        val selections = mutableListOf<DailySelection>()

        // Calculate start and end of the current day
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // Firestore limits whereIn to 10 items; fetch by userId then filter locally by timestamp
        userIds.chunked(10).forEach { chunk ->
            FirebaseFirestore.getInstance()
                .collection("daily_selections")
                .whereIn("userId", chunk)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    // Map documents to DailySelection and filter for today
                    val posts = snap!!.documents.mapNotNull { doc ->
                        doc.toObject(DailySelection::class.java)?.copy(id = doc.id)
                    }.filter { it.timestamp in startOfDay until endOfDay }
                    // Replace entries from this chunk and sort
                    selections.removeAll { it.userId in chunk }
                    selections.addAll(posts)
                    val sorted = selections.sortedWith(
                        compareByDescending<DailySelection> { it.likes.size }
                            .thenByDescending { it.timestamp }
                    )
                    liveData.postValue(sorted)
                }
        }
        return liveData
    }

    /** Check if user has posted a daily selection today */
    fun checkIfPostedToday(uid: String) {
        viewModelScope.launch {
            try {
                val todayId = "${uid}_${LocalDate.now()}"
                val doc = Firebase.firestore
                    .collection("daily_selections")
                    .document(todayId)
                    .get()
                    .await()
                _hasPostedToday.postValue(doc.exists())
            } catch (e: Exception) {
                _hasPostedToday.postValue(false)
            }
        }
    }
}
