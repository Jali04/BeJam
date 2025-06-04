package com.example.bejam.ui.friends

import android.app.Application
import androidx.lifecycle.*
import com.example.bejam.data.FirestoreManager
import com.example.bejam.data.FirestoreManager.Request
import com.example.bejam.data.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Ein einfaches Datenmodell für Benutzer-Profile, das in der FeedAdapter-Map verwendet wird.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
    val spotifyId: String?
)

/**
 * ViewModel für die “Freunde”-Ansicht.
 * Hier werden eingehende Freundschaftsanfragen beobachtet,
 * die aktuelle Freundesliste geladen und Freundschaftsanfragen versendet/akzeptiert.
 */
class FriendsViewModel(app: Application) : AndroidViewModel(app) {
    // Instanz von FirebaseAuth zum Ermitteln der aktuellen User-UID
    private val auth = FirebaseAuth.getInstance()
    /**
     * Liefert die UID des aktuell angemeldeten Nutzers.
     * Wirft eine Exception, falls kein Nutzer angemeldet ist.
     */
    val currentUid: String get() = auth.currentUser?.uid
        ?: throw IllegalStateException("Must be signed in before using FriendsViewModel")

    // Livedata, das auslöst, wenn eine Anfrage erfolgreich gesendet wurde (true) oder gescheitert ist (false).
    private val _requestSent = MutableLiveData<Boolean?>()
    val requestSent: LiveData<Boolean?> = _requestSent

    /**
     * Beobachtet alle eingehenden Freundschaftsanfragen (von anderen Nutzern an currentUid).
     * Wird im IO-Dispatcher ausgeführt und als LiveData ausgegeben.
     */
    val incomingRequests: LiveData<List<Request>> = liveData(Dispatchers.IO) {
        auth.currentUser?.uid?.let { uid ->
            emitSource(
                FirestoreManager
                    .observeIncomingRequests(uid)
                    .asLiveData()
            )
        }
    }

    /**
     * Beobachtet alle bestehenden Freundschaften (Paare von UIDs) für currentUid.
     * Gibt eine Liste von Pair(userA, userB) zurück.
     */
    val friends: LiveData<List<Pair<String, String>>> = liveData(Dispatchers.IO) {
        auth.currentUser?.uid?.let { uid ->
            emitSource(
                FirestoreManager
                    .observeFriends(uid)
                    .asLiveData()
            )
        }
    }

    // UID-Liste deiner Freunde (inkl. Fehlerbehandlung falls User nicht eingeloggt)
    val friendUids: LiveData<List<String>> = friends.map { pairs ->
        val myUid = currentUid
        pairs.mapNotNull { (userA, userB) ->
            when (myUid) {
                userA -> userB
                userB -> userA
                else -> null
            }
        }
    }

    // --------- UserProfile Map für FeedAdapter ---------
    // Hier sammeln wir DisplayName & Avatar-URL für eine Menge von UIDs, um sie später im Feed anzuzeigen.
    private val _profileMap = MutableLiveData<Map<String, UserProfile>>()
    val profileMap: LiveData<Map<String, UserProfile>> = _profileMap

    /**
     * Lädt aus Firestore alle Profile für die gegebenen [uids] und postet das Ergebnis in _profileMap.
     * Wird im IO-Dispatcher asynchron ausgeführt.
     */
    fun loadProfilesForUids(uids: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, UserProfile>()
            val firestore = Firebase.firestore
            for (uid in uids) {
                try {
                    val snap = firestore.collection("user_profiles").document(uid).get().await()
                    if (snap.exists()) {
                        map[uid] = UserProfile(
                            uid = uid,
                            displayName = snap.getString("displayName") ?: "Unknown User",
                            avatarUrl = snap.getString("avatarUrl"),
                            spotifyId = snap.getString("spotifyId")
                        )
                    }
                } catch (_: Exception) { /* ignore single user failure */ }
            }
            _profileMap.postValue(map)
        }
    }
    // -----------------------------------------

    // Livedata-Feld für mögliche Fehlernachrichten
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Sendet eine Freundschaftsanfrage an den Nutzer, der durch [searchInput] gefunden wird.
     * Die Suche geschieht nacheinander nach spotifyId, E-Mail und displayName.
     * Prüft außerdem, ob man sich selbst anfragt, ob es bereits Anfragen gibt oder man schon befreundet ist.
     */
    fun sendRequest(searchInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val firestore = Firebase.firestore

            // Try matching by spotifyId, then email, then displayName
            val profiles = firestore.collection("user_profiles")
            val bySpotify = profiles.whereEqualTo("spotifyId", searchInput).get().await().documents.firstOrNull()
            val byEmail   = if (bySpotify == null) profiles.whereEqualTo("email", searchInput).get().await().documents.firstOrNull() else null
            val byName    = if (bySpotify == null && byEmail == null)
                                profiles.whereEqualTo("displayName", searchInput).get().await().documents.firstOrNull()
                            else null

            // Kombiniere die drei Suchergebnisse
            val targetDoc = bySpotify ?: byEmail ?: byName
            val toUid = targetDoc?.id
            if (toUid == null) {
                _error.postValue("Kein Nutzer gefunden mit dieser Spotify-ID, Username oder E-Mail.")
                return@launch
            }

            // Verhindere Freundschaftsanfrage an sich selbst
            if (toUid == currentUid) {
                _error.postValue("Du kannst dir selbst keine Freundschaftsanfrage senden.")
                return@launch
            }

            // bereits verschickte Anfrage?
            val sentReq = firestore.collection("friend_requests")
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("toUid", toUid)
                .get().await()
            if (!sentReq.isEmpty) {
                _error.postValue("Du hast dieser Person bereits eine Freundschaftsanfrage geschickt.")
                return@launch
            }

            // Prüfe, ob toUid mir bereits eine Anfrage geschickt hat
            val receivedReq = firestore.collection("friend_requests")
                .whereEqualTo("fromUid", toUid)
                .whereEqualTo("toUid", currentUid)
                .get().await()
            if (!receivedReq.isEmpty) {
                _error.postValue("Diese Person hat dir bereits eine Freundschaftsanfrage geschickt.")
                return@launch
            }

            // Prüfe, ob wir schon befreundet sind (Freundschaftsdokument existiert)
            val sortedIds = listOf(currentUid, toUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                return@launch
            }

            // Versand der Anfrage per FirestoreManager
            FirestoreManager.sendRequest(currentUid, toUid).await()
            _requestSent.postValue(true)
        }
    }

    /** Setzt requestSent zurück, damit das Ereignis nicht wiederholt feuert. */
    fun clearRequestSent() {
        _requestSent.value = null
    }

    /** Setzt den Error‐State auf null, um Fehlermeldungen auszublenden. */
    fun clearError() {
        _error.postValue(null)
    }

    /**
     * Reagiere auf eine eingehende Freundschaftsanfrage [req].
     * Akzeptieren oder Ablehnen wird an FirestoreManager weitergegeben.
     * Wenn erfolgreich, füge den neuen Freund in die lokale DB (FriendRepository) hinzu.
     */
    fun respond(req: Request, accept: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid ?: return@launch
        try {
            val firestore = Firebase.firestore

            // Prüfe, ob wir möglicherweise schon befreundet sind
            val sortedIds = listOf(myUid, req.fromUid).sorted()
            val friendshipId = "${sortedIds[0]}_${sortedIds[1]}"
            val friendDoc = firestore.collection("friends").document(friendshipId).get().await()
            if (friendDoc.exists()) {
                // Wenn schon Freunde, lösche nur die Anfrage und zeige Fehler
                firestore.collection("friend_requests").document(req.id).delete().await()
                _error.postValue("Diese Person ist bereits in deiner Freundesliste.")
                _requestSent.postValue(false)
                return@launch
            }

            // Annehmen/Ablehnen mit FirestoreManager
            FirestoreManager.respondToRequest(req, accept, myUid)
            _error.postValue(null)

            // Falls akzeptiert: Füge Freund auch in lokale Spotify-Follow‐Datenbank ein
            val success = FriendRepository
                .getInstance(getApplication())
                .addFriend(req.fromUid)

            if (success) {
                _error.postValue(null)
                _requestSent.postValue(true)
            } else {
                _error.postValue("Fehler beim Folgen auf Spotify!")
                _requestSent.postValue(false)
            }
        } catch (e: Exception) {
            // Im Fehlerfall die Fehlermeldung anzeigen
            _error.postValue(e.message)
        }
    }
}
