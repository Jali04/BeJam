package com.example.bejam.auth

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1) Anonymous Firebase Auth
        FirebaseAuth.getInstance()
            .signInAnonymously()
            .addOnSuccessListener { Log.d("AUTH","signed in as ${it.user?.uid}") }
            .addOnFailureListener { e -> Log.e("AUTH","anonymous sign-in failed",e) }

        // 2) FCM subscription
        FirebaseMessaging.getInstance()
            .subscribeToTopic("daily_reminder")
            .addOnSuccessListener { Log.d("TOPIC", "subscribed to daily_reminder") }
            .addOnFailureListener { Log.e("TOPIC", "subscribe failed", it) }
    }
}