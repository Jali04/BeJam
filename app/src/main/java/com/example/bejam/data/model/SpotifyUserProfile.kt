package com.example.bejam.data.model

/**
 * Datenmodell für ein Spotify-Benutzerprofil.
 * Wird verwendet, um Daten von der Spotify-API (z. B. „v1/me“ oder „v1/users/{id}“) abzubilden
 */

data class SpotifyUserProfile(
    val id: String,
    val display_name: String?,
    val images: List<Image>,
    val email: String? = null
)
