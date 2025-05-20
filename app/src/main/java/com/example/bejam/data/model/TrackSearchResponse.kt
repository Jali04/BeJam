package com.example.bejam.data.model

data class TrackSearchResponse(
    val tracks: TrackList
)
data class TrackList(
    val items: List<Track>
)
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
