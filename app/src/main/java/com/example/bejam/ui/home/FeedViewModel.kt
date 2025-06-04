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

/**
 * ViewModel für den Feed und das tägliche Posting-Verhalten.
 */
class FeedViewModel : ViewModel() {
    // LiveData to track if the current user has posted today
    private val _hasPostedToday = MutableLiveData<Boolean>(false)
    val hasPostedToday: LiveData<Boolean> = _hasPostedToday
    private val db = Firebase.firestore // Referenz auf die Firestore-Datenbank

    // --------- Like-Operation: Ergebnis (erfolgreich/fehlgeschlagen) ---------
    private val _likeResult = MutableLiveData<Boolean>()
    val likeResult: LiveData<Boolean> = _likeResult

    // Like-Logik: Füge/mehrem Like hinzu oder entferne es (toggle)
    fun onLikeClicked(selection: DailySelection) {
        // Aktuelle Nutzer-ID ermitteln
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Referenz zum Firestore-Dokument „daily_selections/{selection.id}“
        val docRef = db.collection("daily_selections").document(selection.id)

        // Kopiere aktuelle Like-Liste und füge UID hinzu oder entferne sie
        val likes = selection.likes.toMutableList()
        if (likes.contains(myUid)) likes.remove(myUid)
        else likes.add(myUid)

        // // Schreibe nur das „likes“-Feld zurück in Firestore
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

    /**
     * Beobachtet Live-Daten aller DailySelection-Dokumente für die angegebenen userIds,
     * filtert sie auf die Einträge des aktuellen Tages und sortiert nach Anzahl der Likes
     * und anschließend nach Timestamp (absteigend).
     *
     * @param userIds Liste der userIds, für die der Feed geladen werden soll.
     * @return LiveData einer sortierten Liste von DailySelection-Objekten (heutige Einträge).
     */
    fun observeTodayFeed(userIds: List<String>): LiveData<List<DailySelection>> {
        val liveData = MutableLiveData<List<DailySelection>>()
        val selections = mutableListOf<DailySelection>()

        // Berechne den Beginn und das Ende des aktuellen Tages in Millisekunden
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

        // Firestore erlaubt maximal 10 Einträge in whereIn; chanke also userIds in Gruppen zu je 10
        userIds.chunked(10).forEach { chunk ->
            FirebaseFirestore.getInstance()
                .collection("daily_selections")
                .whereIn("userId", chunk)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    // Alle Dokumente in DailySelection-Objekte umwandeln und nur heutige Einträge behalten
                    val posts = snap!!.documents.mapNotNull { doc ->
                        doc.toObject(DailySelection::class.java)?.copy(id = doc.id)
                    }.filter { it.timestamp in startOfDay until endOfDay }
                    // Ersetze vorherige Einträge für diese userId-Gruppe im lokalen Cache und füge neue hinzu
                    selections.removeAll { it.userId in chunk }
                    selections.addAll(posts)
                    //Sortiere zuerst nach Anzahl der Likes (desc), dann nach Timestamp (desc)
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
                // Existiert das Dokument, hat der Nutzer heute bereits gepostet
                _hasPostedToday.postValue(doc.exists())
            } catch (e: Exception) {
                // Bei Fehler: Gehe von „nicht gepostet“ aus
                _hasPostedToday.postValue(false)
            }
        }
    }
}
