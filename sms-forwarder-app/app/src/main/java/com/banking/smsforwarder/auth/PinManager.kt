package com.banking.smsforwarder.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Manages PIN-based authentication for the application.
 *
 * Uses EncryptedSharedPreferences backed by Android Keystore
 * for secure PIN storage. PINs are hashed with SHA-256 before
 * storage so the raw PIN is never persisted.
 *
 * Provides methods to set, verify, check existence, and clear the PIN.
 */
class PinManager(context: Context) {

    companion object {
        private const val PREFS_FILE = "pin_secure_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SET = "pin_is_set"
    }

    // Encrypted SharedPreferences instance backed by Android Keystore MasterKey
    private val encryptedPrefs: SharedPreferences

    init {
        // Create a MasterKey using AES256-GCM for encryption
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize EncryptedSharedPreferences with AES256 encryption
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Checks whether a PIN has been configured by the user.
     * @return true if a PIN is set, false otherwise
     */
    fun isPinSet(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PIN_SET, false)
    }

    /**
     * Stores a new PIN after hashing it with SHA-256.
     * The raw PIN is never stored - only its hash.
     *
     * @param pin The raw PIN string (typically 4-6 digits)
     */
    fun setPin(pin: String) {
        val pinHash = hashPin(pin)
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, pinHash)
            .putBoolean(KEY_PIN_SET, true)
            .apply()
    }

    /**
     * Verifies a PIN attempt against the stored hash.
     *
     * @param pin The PIN attempt to verify
     * @return true if the PIN matches, false otherwise
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null)
        if (storedHash == null) return false
        return hashPin(pin) == storedHash
    }

    /**
     * Clears the stored PIN, effectively disabling PIN authentication.
     * Used when the user wants to reset their PIN.
     */
    fun clearPin() {
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_SET, false)
            .apply()
    }

    /**
     * Hashes a PIN string using SHA-256.
     * Returns the hex-encoded hash string.
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        // Convert byte array to hex string for storage
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
