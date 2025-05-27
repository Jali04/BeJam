package com.example.bejam.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val id: String,            // e.g. user’s unique ID from your backend
    val username: String,
    val email: String? = null,
    val profileImageUrl: String? = null
)