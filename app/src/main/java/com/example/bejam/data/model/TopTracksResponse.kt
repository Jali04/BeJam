package com.example.bejam.data.model

/**
 * Datenmodell für die Antwort von "GET /v1/me/top/tracks" (Spotify-API).
 * Die API liefert die Lieblings-/meistgehörten Tracks eines Nutzers als "items"-Liste zurück.
 */

data class TopTracksResponse(
    val items: List<Track>          // Die Liste der Top-Tracks als "Track"-Objekte
)
