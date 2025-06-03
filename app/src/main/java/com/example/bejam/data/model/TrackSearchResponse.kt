package com.example.bejam.data.model

/**
 * Modelliert die Antwort auf eine Track-Suche (Spotify-API /v1/search?type=track).
 */

data class TrackSearchResponse(
    val tracks: TrackList
)

/**
 * Enthält eine Liste von einzelnen Tracks – wird bei jeder Suche benötigt.
 */
data class TrackList(
    val items: List<Track>
)

/**
 * Modell für einen einzelnen Song/Track.
 */
data class Track(
    val id: String,
    val name: String,
    val preview_url: String?,
    val artists: List<Artist>,
    val album: Album
)
data class Artist(val name: String)
data class Album(val images: List<Image>)
data class Image(val url: String)
