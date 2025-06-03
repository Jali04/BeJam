package com.example.bejam.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Datenmodell für einen Freund eines Nutzers
 * Wird lokal in einer SQLite/Room-Datenbank gespeichert
 */

// Markiert diese Klasse als Entity für die Room-DB, Tabelle heißt "friends"
@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val id: String,                 // Die eindeutige ID des Freundes
    val username: String,                       // Anzeigename oder Username
    val email: String? = null,                  // E-Mail-Adresse
    val profileImageUrl: String? = null         // Link zum Profilbild/Avatar
)