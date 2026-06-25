package com.banking.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.banking.smsforwarder.auth.BiometricHelper
import com.banking.smsforwarder.data.PreferencesManager
import com.banking.smsforwarder.databinding.ActivitySettingsBinding

/**
 * Settings activity for configuring the SMS Forwarder application.
 *
 * Allows the user to configure:
 * - Recipient email address (where forwarded messages are sent)
 * - SMTP sender email and password (account used to send emails)
 * - SMTP server hostname and port
 * - PIN change
 * - Biometric authentication toggle
 *
 * All sensitive settings are stored in EncryptedSharedPreferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable back navigation in the toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefsManager = PreferencesManager(this)
        biometricHelper = BiometricHelper(this)

        // Load current settings into the form fields
        loadCurrentSettings()

        // Configure button click handlers
        setupClickListeners()
    }

    /**
     * Populates form fields with currently saved settings.
     * Password field shows masked value if a password is saved.
     */
    private fun loadCurrentSettings() {
        // Email configuration
        binding.etRecipientEmail.setText(prefsManager.getRecipientEmail() ?: "")
        binding.etSenderEmail.setText(prefsManager.getSenderEmail() ?: "")

        // Show masked indicator if password is set (don't reveal actual password)
        if (!prefsManager.getSenderPassword().isNullOrBlank()) {
            binding.etSenderPassword.hint = "Password saved (tap to change)"
        }

        // SMTP configuration with defaults shown as hints
        binding.etSmtpHost.setText(prefsManager.getSmtpHost() ?: "")
        binding.etSmtpHost.hint = "smtp.gmail.com (default)"
        binding.etSmtpPort.setText(prefsManager.getSmtpPort() ?: "")
        binding.etSmtpPort.hint = "587 (default)"

        // Biometric toggle - only show if device supports it
        if (biometricHelper.isBiometricAvailable()) {
            binding.switchBiometric.visibility = android.view.View.VISIBLE
            binding.tvBiometricLabel.visibility = android.view.View.VISIBLE
            binding.switchBiometric.isChecked = prefsManager.isBiometricEnabled()
        } else {
            binding.switchBiometric.visibility = android.view.View.GONE
            binding.tvBiometricLabel.visibility = android.view.View.GONE
        }
    }

    /**
     * Sets up click listeners for save and change PIN buttons.
     */
    private fun setupClickListeners() {
        // Save settings button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // Change PIN button - navigates to SetupPinActivity in change mode
        binding.btnChangePin.setOnClickListener {
            val intent = Intent(this, SetupPinActivity::class.java)
            intent.putExtra(SetupPinActivity.EXTRA_IS_FIRST_SETUP, false)
            startActivity(intent)
        }

        // Biometric toggle change handler
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setBiometricEnabled(isChecked)
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Biometric unlock $status", Toast.LENGTH_SHORT).show()
        }

        // Send test email button
        binding.btnTestEmail.setOnClickListener {
            sendTestEmail()
        }
    }

    /**
     * Validates and saves all settings to EncryptedSharedPreferences.
     */
    private fun saveSettings() {
        val recipientEmail = binding.etRecipientEmail.text.toString().trim()
        val senderEmail = binding.etSenderEmail.text.toString().trim()
        val senderPassword = binding.etSenderPassword.text.toString()
        val smtpHost = binding.etSmtpHost.text.toString().trim()
        val smtpPort = binding.etSmtpPort.text.toString().trim()

        // Validate recipient email format
        if (recipientEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(recipientEmail).matches()) {
            binding.tilRecipientEmail.error = "Invalid email format"
            return
        }

        // Validate sender email format
        if (senderEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(senderEmail).matches()) {
            binding.tilSenderEmail.error = "Invalid email format"
            return
        }

        // Save all non-empty values
        if (recipientEmail.isNotBlank()) prefsManager.setRecipientEmail(recipientEmail)
        if (senderEmail.isNotBlank()) prefsManager.setSenderEmail(senderEmail)
        if (senderPassword.isNotBlank()) prefsManager.setSenderPassword(senderPassword)
        if (smtpHost.isNotBlank()) prefsManager.setSmtpHost(smtpHost)
        if (smtpPort.isNotBlank()) prefsManager.setSmtpPort(smtpPort)

        // Clear field errors on successful save
        binding.tilRecipientEmail.error = null
        binding.tilSenderEmail.error = null

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
    }

    /**
     * Sends a test email to verify SMTP configuration is correct.
     * Runs on a background thread to avoid blocking the UI.
     */
    private fun sendTestEmail() {
        // Save settings first to ensure latest values are used
        saveSettings()

        val recipientEmail = prefsManager.getRecipientEmail()
        if (recipientEmail.isNullOrBlank()) {
            Toast.makeText(this, "Please configure recipient email first", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Sending test email...", Toast.LENGTH_SHORT).show()

        // Send test email on IO thread
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val success = com.banking.smsforwarder.service.EmailService.sendEmail(
                context = this@SettingsActivity,
                recipientEmail = recipientEmail,
                subject = "SMS Forwarder - Test Email",
                body = "This is a test email from SMS Forwarder app. If you received this, your email configuration is working correctly."
            )

            // Show result on main thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@SettingsActivity, "Test email sent successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "Failed to send test email. Check SMTP settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
