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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.bejam.MainActivity
import com.example.bejam.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import com.example.bejam.data.FirestoreManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service, der eingehende Push-Benachrichtigungen verarbeitet.
 * Läuft immer im Hintergrund, wenn eine Nachricht vom Server eintrifft.
 */

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 1) Token an den eigenen Server schicken, damit er später gezielt Push-Nachrichten senden kann
        sendRegistrationToServer(token)
    }

    companion object {
        private const val CHANNEL_ID = "daily_notification_channel"

        // Stellt sicher, dass der Notification Channel existiert
        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                // Channel nur anlegen, falls er noch nicht existiert
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Daily Song Reminder",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Channel for BeJam daily reminders"
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(msg: RemoteMessage) {
        // Stellt sicher, dass der Notification Channel existiert
        ensureChannel(this)

        // Wenn die tägliche Benachrichtigung eintrifft: Tages-Posts des aktuellen Users zurücksetzen
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirestoreManager.clearTodaySelections(uid)
                } catch (e: Exception) {
                    Log.e("DailyReset", "Error clearing today's selections", e)
                }
            }
        }

        // Intent bauen, damit die App beim Klicken auf die Notification geöffnet wird
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Die eigentliche Notification zusammenbauen
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(msg.notification?.title ?: "BeJam")
            .setContentText(msg.notification?.body  ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(this).notify(1001, n)
    }

    private fun sendRegistrationToServer(token: String) {
        // 1) JSON mit Token bauen
        val json = JSONObject().apply {
            put("fcmToken", token)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        // 2) HTTP-Request bauen (ersetze URL ggf. durch eigenen Server)
        val request = Request.Builder()
            .url("http://34.34.24.86:3000/sendDaily")
            .post(body)
            .build()

        // 3) Asynchron senden
        OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("FCM_TOKEN_SEND", "Token‑Registration fehlgeschlagen", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    Log.d("FCM_TOKEN_SEND", "Token erfolgreich registriert")
                } else {
                    Log.e("FCM_TOKEN_SEND", "Server‑Error: ${response.code}")
                }
                response.close()
            }
        })
    }


}
