package com.banking.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.banking.smsforwarder.data.AppDatabase
import com.banking.smsforwarder.data.ForwardedMessage
import com.banking.smsforwarder.data.PreferencesManager
import com.banking.smsforwarder.service.EmailService
import com.banking.smsforwarder.service.SmsParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that triggers on incoming SMS messages.
 * Parses each message for OTP codes or banking transaction details,
 * and forwards matching messages to the configured email address.
 *
 * Registered in AndroidManifest.xml with SMS_RECEIVED intent filter
 * at high priority (999) to process messages before other apps.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Only process SMS_RECEIVED broadcasts
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefsManager = PreferencesManager(context)

        // Check if forwarding is enabled in app settings
        if (!prefsManager.isForwardingEnabled()) {
            Log.d(TAG, "SMS forwarding is disabled, ignoring message")
            return
        }

        // Extract SMS messages from the broadcast intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Combine multi-part SMS into a single message body
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        Log.d(TAG, "Received SMS from: $sender")

        // Parse the message to detect OTP codes or transaction details
        val parseResult = SmsParserService.parseMessage(fullMessage)

        // Only forward messages that contain OTP or transaction information
        if (parseResult != null) {
            Log.d(TAG, "Detected ${parseResult.type} in SMS from $sender")
            handleForwarding(context, prefsManager, sender, fullMessage, parseResult, timestamp)
        } else {
            Log.d(TAG, "No OTP or transaction detected in SMS from $sender")
        }
    }

    /**
     * Handles the email forwarding and database logging of a detected message.
     * Runs on IO dispatcher to avoid blocking the main thread.
     */
    private fun handleForwarding(
        context: Context,
        prefsManager: PreferencesManager,
        sender: String,
        messageBody: String,
        parseResult: SmsParserService.ParseResult,
        timestamp: Long
    ) {
        // Use coroutine for async email sending and database operations
        CoroutineScope(Dispatchers.IO).launch {
            val recipientEmail = prefsManager.getRecipientEmail()
            if (recipientEmail.isNullOrBlank()) {
                Log.w(TAG, "No recipient email configured, skipping forwarding")
                return@launch
            }

            // Build the email subject line based on message type
            val subject = when (parseResult.type) {
                SmsParserService.MessageType.OTP ->
                    "OTP Alert from $sender"
                SmsParserService.MessageType.TRANSACTION ->
                    "Transaction Alert from $sender"
            }

            // Build email body with parsed details and original message
            val emailBody = buildEmailBody(sender, parseResult, messageBody, timestamp)

            // Attempt to send the email via SMTP
            val emailSent = EmailService.sendEmail(
                context = context,
                recipientEmail = recipientEmail,
                subject = subject,
                body = emailBody
            )

            // Log the forwarded message to the local Room database
            val forwardedMessage = ForwardedMessage(
                sender = sender,
                messageBody = messageBody,
                messageType = parseResult.type.name,
                parsedDetails = parseResult.details,
                recipientEmail = recipientEmail,
                timestamp = timestamp,
                emailSent = emailSent
            )

            try {
                val db = AppDatabase.getInstance(context)
                db.forwardedMessageDao().insert(forwardedMessage)
                Log.d(TAG, "Message logged to database (emailSent=$emailSent)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log message to database", e)
            }
        }
    }

    /**
     * Constructs a formatted email body with message metadata and parsed details.
     */
    private fun buildEmailBody(
        sender: String,
        parseResult: SmsParserService.ParseResult,
        originalMessage: String,
        timestamp: Long
    ): String {
        val dateStr = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))

        return buildString {
            appendLine("=== SMS Forwarder Alert ===")
            appendLine()
            appendLine("Type: ${parseResult.type}")
            appendLine("From: $sender")
            appendLine("Time: $dateStr")
            appendLine()
            appendLine("--- Parsed Details ---")
            appendLine(parseResult.details)
            appendLine()
            appendLine("--- Original Message ---")
            appendLine(originalMessage)
            appendLine()
            appendLine("=== End of Alert ===")
        }
    }
}
