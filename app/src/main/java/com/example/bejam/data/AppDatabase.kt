package com.example.bejam.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.bejam.data.model.Friend

/**
 * Definition der lokalen Room-Datenbank.
 * Speichert alle Freunde des aktuellen Nutzers (Tabelle: "friends").
 */

@Database(entities = [Friend::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    // Gibt Zugriff auf das DAO-Interface für Freunde
    abstract fun friendDao(): FriendDao

    companion object {
        // Singleton-Instanz (stellt sicher, dass es nur eine Datenbankinstanz gibt)
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,           // Welche DB-Klasse
                    "bejam.db"                   // Name der DB-Datei
                ).build().also { INSTANCE = it }
            }
    }
}