package com.example.bejam.data.model

data class DailySelection(
    val userId: String = "",
    val songId: String = "",
    val songName: String = "",
    val artist: String = "",
    val imageUrl: String? = null,
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)