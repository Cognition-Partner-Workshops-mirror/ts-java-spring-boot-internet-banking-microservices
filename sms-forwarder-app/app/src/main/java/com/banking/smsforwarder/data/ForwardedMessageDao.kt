package com.banking.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for ForwardedMessage entities.
 *
 * Provides database operations for inserting new forwarded message
 * records and querying the forwarding history log.
 */
@Dao
interface ForwardedMessageDao {

    /**
     * Inserts a new forwarded message record into the database.
     * Replaces any existing record with the same primary key (unlikely with auto-generate).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ForwardedMessage)

    /**
     * Retrieves all forwarded messages ordered by timestamp (newest first).
     * Used to populate the main dashboard's message log.
     */
    @Query("SELECT * FROM forwarded_messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<ForwardedMessage>

    /**
     * Retrieves the most recent N forwarded messages.
     * Used for displaying a limited recent history.
     *
     * @param limit Maximum number of messages to return
     */
    @Query("SELECT * FROM forwarded_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ForwardedMessage>

    /**
     * Deletes all forwarded message records from the database.
     * Used when the user chooses to clear the forwarding history.
     */
    @Query("DELETE FROM forwarded_messages")
    suspend fun clearAll()

    /**
     * Returns the total count of forwarded messages.
     * Used for displaying statistics on the dashboard.
     */
    @Query("SELECT COUNT(*) FROM forwarded_messages")
    suspend fun getMessageCount(): Int
}
