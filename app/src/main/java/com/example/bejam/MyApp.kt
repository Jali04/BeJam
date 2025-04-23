package com.example.bejam

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hier wird der einmalige WorkManager‐Job geplant,
        // egal ob und wann eine Activity gestartet wird:
        FirebaseMessaging.getInstance()
            .subscribeToTopic("daily_reminder")
            .addOnSuccessListener { Log.d("TOPIC","subscribed to daily_reminder") }
            .addOnFailureListener { Log.e("TOPIC","subscribe failed",it) }
    }
}
