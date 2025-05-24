package com.example.bejam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bejam.data.model.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    /** returns the new row ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(friend: Friend): Long

    /** streams your friends */
    @Query("SELECT * FROM friends ORDER BY username")
    fun getAllFriends(): Flow<List<Friend>>

    /** returns how many rows were deleted (1 when success) */
    @Query("DELETE FROM friends WHERE id = :friendId")
    fun remove(friendId: String): Int
}
