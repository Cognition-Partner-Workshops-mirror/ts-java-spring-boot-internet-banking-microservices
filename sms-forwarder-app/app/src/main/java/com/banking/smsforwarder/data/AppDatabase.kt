package com.banking.smsforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the SMS Forwarder application.
 *
 * Stores forwarded message log entries in a local SQLite database.
 * Uses the singleton pattern to ensure only one database instance
 * exists across the application lifecycle.
 */
@Database(
    entities = [ForwardedMessage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Returns the DAO for forwarded message operations.
     */
    abstract fun forwardedMessageDao(): ForwardedMessageDao

    companion object {
        // Volatile ensures visibility of changes across threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance, creating it if necessary.
         * Thread-safe via double-checked locking pattern.
         *
         * @param context Application context (avoids Activity memory leaks)
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_database"
                )
                    // Allow destructive migration for simplicity in v1
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
