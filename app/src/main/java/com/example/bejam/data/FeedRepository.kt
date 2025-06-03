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

/**
 * Zentrale Repository-Klasse für Feed-Funktionalität.
 * Bietet Methoden, um den Song-Feed eines Nutzers von Firestore zu laden.
 */

object FeedRepository {
    /**
     * Holt die Song-Posts („DailySelections“) von heute für eine Liste von User-IDs.
     * Nutzt Firestore als Datenquelle, filtert nach Usern und Tagesdatum.
     */
    suspend fun getTodaySelectionsForUsers(userIds: List<String>): List<DailySelection> =
        withContext(Dispatchers.IO) {
            // 1) Erzeuge das heutige Datum als String (Format: yyyy-MM-dd)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val selections = mutableListOf<DailySelection>()

            // 2) Firestore unterstützt max. 10 Werte bei whereIn ⇒ daher in 10er-Blöcken abfragen
            userIds.chunked(10).forEach { chunk ->
                val result = Firebase.firestore.collection("daily_selections")
                    .whereIn("userId", chunk)
                    .get()
                    .await()

                // 3) Gehe alle Ergebnisse durch und nehme nur die von heute
                selections.addAll(result.documents.mapNotNull { doc ->
                    val selection = doc.toObject(DailySelection::class.java)
                    val docId = doc.id
                    if (docId.endsWith(today)) selection else null
                })
            }
            // 4) Rückgabe: Alle heutigen Song-Posts der gewünschten User
            selections
        }
}
