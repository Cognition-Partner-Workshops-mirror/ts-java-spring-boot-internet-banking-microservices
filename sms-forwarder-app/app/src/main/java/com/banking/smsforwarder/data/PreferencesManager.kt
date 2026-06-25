package com.banking.smsforwarder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages application preferences using EncryptedSharedPreferences.
 *
 * Stores sensitive configuration such as SMTP credentials and
 * email settings in Android Keystore-backed encrypted storage.
 * Non-sensitive preferences (like forwarding toggle) are also stored
 * here for consistency.
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_FILE = "app_secure_prefs"

        // Keys for email/SMTP configuration
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"

        // Key for forwarding enable/disable toggle
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"

        // Key for biometric preference
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }

    // EncryptedSharedPreferences instance - all values are AES256 encrypted
    private val prefs: SharedPreferences

    init {
        // Create MasterKey backed by Android Keystore for encryption
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Recipient Email (where forwarded messages are sent) ---

    fun getRecipientEmail(): String? = prefs.getString(KEY_RECIPIENT_EMAIL, null)

    fun setRecipientEmail(email: String) {
        prefs.edit().putString(KEY_RECIPIENT_EMAIL, email).apply()
    }

    // --- Sender Email (SMTP account used to send emails) ---

    fun getSenderEmail(): String? = prefs.getString(KEY_SENDER_EMAIL, null)

    fun setSenderEmail(email: String) {
        prefs.edit().putString(KEY_SENDER_EMAIL, email).apply()
    }

    // --- Sender Password (SMTP account password / app password) ---

    fun getSenderPassword(): String? = prefs.getString(KEY_SENDER_PASSWORD, null)

    fun setSenderPassword(password: String) {
        prefs.edit().putString(KEY_SENDER_PASSWORD, password).apply()
    }

    // --- SMTP Host (email server hostname, defaults to Gmail) ---

    fun getSmtpHost(): String? = prefs.getString(KEY_SMTP_HOST, null)

    fun setSmtpHost(host: String) {
        prefs.edit().putString(KEY_SMTP_HOST, host).apply()
    }

    // --- SMTP Port (email server port, defaults to 587 for TLS) ---

    fun getSmtpPort(): String? = prefs.getString(KEY_SMTP_PORT, null)

    fun setSmtpPort(port: String) {
        prefs.edit().putString(KEY_SMTP_PORT, port).apply()
    }

    // --- Forwarding Toggle (enable/disable SMS forwarding) ---

    fun isForwardingEnabled(): Boolean = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, enabled).apply()
    }

    // --- Biometric Preference (whether to offer biometric unlock) ---

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
}
