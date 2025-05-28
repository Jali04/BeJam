package com.example.bejam.data

import com.example.bejam.data.model.DailySelection
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FeedRepository {
    suspend fun getTodaySelectionsForUsers(userIds: List<String>): List<DailySelection> =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val selections = mutableListOf<DailySelection>()

            userIds.chunked(10).forEach { chunk ->
                val result = Firebase.firestore.collection("daily_selections")
                    .whereIn("userId", chunk)
                    .get()
                    .await()

                selections.addAll(result.documents.mapNotNull { doc ->
                    val selection = doc.toObject(DailySelection::class.java)
                    val docId = doc.id
                    if (docId.endsWith(today)) selection else null
                })
            }
            selections
        }
}
