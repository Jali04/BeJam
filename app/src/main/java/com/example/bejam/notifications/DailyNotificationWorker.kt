// DailyNotificationWorker.kt
package com.example.bejam.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bejam.MainActivity
import com.example.bejam.R

/**
 * Worker, der eine tägliche Notification auslöst.
 * Wird über WorkManager geplant und ausgeführt.
 */

class DailyNotificationWorker(context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {

    /**
     * Hauptfunktion des Workers: Baut und zeigt die tägliche Notification an.
     */
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        Log.d("DailyNotificationWorker", "doWork is invoked")
        // 1) Notification-Channel anlegen
        createNotificationChannel(applicationContext)

        // 2) PendingIntent erstellen, der die MainActivity öffnet, wenn auf die Notification geklickt wird
        val clickIntent = Intent(applicationContext, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3) Die eigentliche Notification bauen (mit Icon, Titel, Text, Kategorie usw.)
        val notification = NotificationCompat.Builder(applicationContext, "daily_notification_channel")
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle("Daily Song Reminder")
            .setContentText("Share your favorite Spotify song today!")
            // Pre-Oreo priority
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)                   //öffnet App bei Klick
            .build()

        // 4) Notification anzeigen
        NotificationManagerCompat.from(applicationContext)
            .notify(1, notification)

        // 5) Worker war erfolgreich
        return Result.success()
    }

    /**
     * Erstellt den Notification-Channel (ab Android O Pflicht).
     * Der Channel bleibt erhalten, muss also nur einmalig angelegt werden.
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "daily_notification_channel",           // Channel-ID
                "Daily Notification Channel",        //Channel-Name
                NotificationManager.IMPORTANCE_HIGH        // Heads-Up-Notification
            ).apply {
                description = "Channel for daily song reminders"
            }
            // Channel registrieren (wird ignoriert, wenn schon vorhanden)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

}
