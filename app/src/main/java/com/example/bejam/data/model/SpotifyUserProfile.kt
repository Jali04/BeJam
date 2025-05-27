package com.example.bejam.data.model

data class SpotifyUserProfile(
    val id: String,
    val display_name: String?,
    val images: List<Image>,
    val email: String
)
