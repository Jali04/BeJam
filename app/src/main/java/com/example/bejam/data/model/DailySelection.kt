package com.example.bejam.data.model

/**
 * Ein Datenmodell für einen "Song des Tages"-Post
 * Objekte die für einen Song stehen, diese Songs werden von Nutzern im Feed geteilt
 */

data class DailySelection(
    val id: String = "",                                // Die eindeutige ID des Posts
    val userId: String = "",                            // UID des Users, der den Song gepostet hat
    val songId: String = "",                            // Spotify-Track-ID
    val songName: String = "",                          // Name des Songs
    val artist: String = "",                            // Name des Interpreten/der Band
    val imageUrl: String? = null,                       // URL zum Albumcover oder Songbild
    val comment: String? = null,                        // Optionaler Kommentar des Nutzers
    val likes: List<String> = emptyList(),              // Liste von User-UIDs, die diesen Post geliked haben
    val timestamp: Long = System.currentTimeMillis(),   // Wann wurde der Song gepostet?
)