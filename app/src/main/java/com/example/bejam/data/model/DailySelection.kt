package com.example.bejam.data.model

data class DailySelection(
    val id: String = "",
    val userId: String = "",
    val songId: String = "",
    val songName: String = "",
    val artist: String = "",
    val imageUrl: String? = null,
    val comment: String? = null,
    val likes: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)