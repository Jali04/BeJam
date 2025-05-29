package com.example.bejam.data.model

data class TopTracksResponse(
    val items: List<Track>          // Spotify’s /v1/me/top/tracks response.

)
