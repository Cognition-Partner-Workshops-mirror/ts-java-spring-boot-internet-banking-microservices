package com.banking.smsforwarder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a forwarded SMS message log entry.
 *
 * Each record stores the original SMS details, parsed information,
 * and the forwarding status (whether email was sent successfully).
 * Used to display forwarding history on the main dashboard.
 */
@Entity(tableName = "forwarded_messages")
data class ForwardedMessage(
    // Auto-generated primary key for each log entry
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Phone number or name of the SMS sender
    @ColumnInfo(name = "sender")
    val sender: String,

    // Original SMS message body text
    @ColumnInfo(name = "message_body")
    val messageBody: String,

    // Type of detected content: "OTP" or "TRANSACTION"
    @ColumnInfo(name = "message_type")
    val messageType: String,

    // Extracted details (OTP code or transaction info)
    @ColumnInfo(name = "parsed_details")
    val parsedDetails: String,

    // Email address the message was forwarded to
    @ColumnInfo(name = "recipient_email")
    val recipientEmail: String,

    // Timestamp when the SMS was received (epoch milliseconds)
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    // Whether the email was sent successfully
    @ColumnInfo(name = "email_sent")
    val emailSent: Boolean
)
