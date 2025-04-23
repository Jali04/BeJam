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

class DailyNotificationWorker(context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {

    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        Log.d("DailyNotificationWorker", "doWork is invoked")
        createNotificationChannel(applicationContext)

        // 1) Build your "click to open app" PendingIntent
        val clickIntent = Intent(applicationContext, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2) Build one Notification with everything attached
        val notification = NotificationCompat.Builder(applicationContext, "daily_notification_channel")
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle("Daily Song Reminder")
            .setContentText("Share your favorite Spotify song today!")
            // Pre-Oreo priority
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)                   // <-- hook up your click action
            .build()

        // 3) Fire it
        NotificationManagerCompat.from(applicationContext)
            .notify(1, notification)

        return Result.success()
    }


    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "daily_notification_channel",
                "Daily Notification Channel",
                NotificationManager.IMPORTANCE_HIGH       // heads-up channel
            ).apply {
                description = "Channel for daily song reminders"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

}
