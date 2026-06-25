package com.banking.smsforwarder.service

import android.content.Context
import android.util.Log
import com.banking.smsforwarder.data.PreferencesManager
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Service responsible for sending emails via SMTP using JavaMail API.
 *
 * Supports configurable SMTP settings (server, port, credentials)
 * stored in EncryptedSharedPreferences. Defaults to Gmail SMTP
 * with TLS on port 587.
 *
 * All methods are stateless and accessed via companion object.
 * Email sending must be called from a background thread (IO dispatcher).
 */
object EmailService {

    private const val TAG = "EmailService"

    // Default SMTP configuration for Gmail
    private const val DEFAULT_SMTP_HOST = "smtp.gmail.com"
    private const val DEFAULT_SMTP_PORT = "587"

    /**
     * Sends an email to the specified recipient using configured SMTP settings.
     *
     * @param context Application context for reading preferences
     * @param recipientEmail Target email address to receive the forwarded message
     * @param subject Email subject line
     * @param body Email body text
     * @return true if email was sent successfully, false on any error
     */
    fun sendEmail(
        context: Context,
        recipientEmail: String,
        subject: String,
        body: String
    ): Boolean {
        val prefsManager = PreferencesManager(context)

        // Read SMTP configuration from encrypted preferences
        val smtpHost = prefsManager.getSmtpHost() ?: DEFAULT_SMTP_HOST
        val smtpPort = prefsManager.getSmtpPort() ?: DEFAULT_SMTP_PORT
        val senderEmail = prefsManager.getSenderEmail()
        val senderPassword = prefsManager.getSenderPassword()

        // Validate that sender credentials are configured
        if (senderEmail.isNullOrBlank() || senderPassword.isNullOrBlank()) {
            Log.e(TAG, "Sender email or password not configured")
            return false
        }

        return try {
            // Configure SMTP session properties with TLS encryption
            val properties = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort)
                put("mail.smtp.ssl.trust", smtpHost)
                // Set connection and read timeouts to 30 seconds
                put("mail.smtp.connectiontimeout", "30000")
                put("mail.smtp.timeout", "30000")
                put("mail.smtp.writetimeout", "30000")
            }

            // Create authenticated SMTP session
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            })

            // Compose and send the email message
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
                setSubject(subject)
                setText(body)
            }

            Transport.send(message)
            Log.d(TAG, "Email sent successfully to $recipientEmail")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email to $recipientEmail", e)
            false
        }
    }
}
