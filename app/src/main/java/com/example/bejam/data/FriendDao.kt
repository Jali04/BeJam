package com.example.bejam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bejam.data.model.Friend
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) für Freunde.
 * Definiert alle Datenbank-Operationen, die auf die "friends"-Tabelle angewendet werden können.
 */

@Dao
interface FriendDao {
    // Fügt einen Freund hinzu oder aktualisiert ihn (ersetzt, falls es einen Konflikt mit der ID gibt).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(friend: Friend): Long

    /**
     * Gibt einen Live-Stream (Flow) aller gespeicherten Freunde, sortiert nach Username.
     */
    @Query("SELECT * FROM friends ORDER BY username")
    fun getAllFriends(): Flow<List<Friend>>

    // Entfernt einen Freund anhand der ID
    /** returns how many rows were deleted (1 when success) */
    @Query("DELETE FROM friends WHERE id = :friendId")
    fun remove(friendId: String): Int
}
