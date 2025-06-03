package com.example.bejam.auth

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Automatische, anonyme User-Identität für ALLE Firebase-Funktionen (Feed, Upvotes, Freunde)
 * Verbindung zum Push-Benachrichtigungs-System
 */

class MyApp : Application() {

    // automatisch aufgerufen, sobald App startet
    override fun onCreate() {
        super.onCreate()

        //Anonymer Login bei Firebase
        FirebaseAuth.getInstance()
            .signInAnonymously() // anonyme Anmeldung bei Firebase
            .addOnSuccessListener { Log.d("AUTH","signed in as ${it.user?.uid}") } // ausgeführt, wenn Anmelden erfolgreich - gibt uid ins Log aus
            .addOnFailureListener { e -> Log.e("AUTH","anonymous sign-in failed",e) } // bei error wird Fehler ins Log geschrieben

        // 2) FCM subscription
        FirebaseMessaging.getInstance()
            .subscribeToTopic("daily_reminder") // Gerät wird auf FCM-Topic "daily_reminder" registriert
            .addOnSuccessListener { Log.d("TOPIC", "subscribed to daily_reminder") } // loggt bei erfolgreichem abonnieren
            .addOnFailureListener { Log.e("TOPIC", "subscribe failed", it) } // loggt Fehler beim Abonnieren
    }
}