package com.example.bejam.data

import com.example.bejam.data.model.DailySelection
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FeedRepository {
    suspend fun getTodaySelectionsForUsers(userIds: List<String>): List<DailySelection> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val selections = mutableListOf<DailySelection>()

        // Firestore 'whereIn' allows max 10 IDs per query; if more, split into chunks
        userIds.chunked(10).forEach { chunk ->
            val result = Firebase.firestore.collection("daily_selections")
                .whereIn("userId", chunk)
                .get()
                .await()

            selections.addAll(result.documents.mapNotNull { doc ->
                val selection = doc.toObject(DailySelection::class.java)
                // Only today’s selections (if you store date in docId or add a 'date' field)
                val docId = doc.id // format: userId_yyyy-MM-dd
                if (docId.endsWith(today)) selection else null
            })
        }
        return selections
    }
}