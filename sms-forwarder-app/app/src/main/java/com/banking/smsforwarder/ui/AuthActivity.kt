package com.banking.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.banking.smsforwarder.auth.BiometricHelper
import com.banking.smsforwarder.auth.PinManager
import com.banking.smsforwarder.databinding.ActivityAuthBinding

/**
 * Authentication gate activity - first screen shown on app launch.
 *
 * Requires the user to authenticate via PIN or biometrics before
 * accessing the main application. If no PIN is set (first launch),
 * redirects to the PIN setup screen.
 *
 * Authentication flow:
 * 1. Check if PIN is set -> if not, redirect to SetupPinActivity
 * 2. If biometric is available and enabled -> show biometric prompt
 * 3. User can also enter PIN manually via the PIN input field
 * 4. On successful auth -> navigate to MainActivity
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var pinManager: PinManager
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pinManager = PinManager(this)
        biometricHelper = BiometricHelper(this)

        // If no PIN is set, redirect to PIN setup (first-time setup)
        if (!pinManager.isPinSet()) {
            navigateToSetupPin()
            return
        }

        setupUi()
    }

    /**
     * Configures the authentication UI with PIN input and biometric button.
     */
    private fun setupUi() {
        // Show biometric button only if device supports it and user enabled it
        val prefsManager = com.banking.smsforwarder.data.PreferencesManager(this)
        if (biometricHelper.isBiometricAvailable() && prefsManager.isBiometricEnabled()) {
            binding.btnBiometric.visibility = View.VISIBLE
            // Auto-show biometric prompt on activity launch
            showBiometricPrompt()
        } else {
            binding.btnBiometric.visibility = View.GONE
        }

        // PIN submission button click handler
        binding.btnSubmitPin.setOnClickListener {
            val enteredPin = binding.etPin.text.toString()
            if (enteredPin.isEmpty()) {
                binding.tilPin.error = "Please enter your PIN"
                return@setOnClickListener
            }

            if (pinManager.verifyPin(enteredPin)) {
                // PIN matches - grant access to main screen
                navigateToMain()
            } else {
                // PIN doesn't match - show error
                binding.tilPin.error = "Incorrect PIN"
                binding.etPin.text?.clear()
            }
        }

        // Biometric button click handler
        binding.btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }
    }

    /**
     * Displays the system biometric authentication dialog.
     */
    private fun showBiometricPrompt() {
        biometricHelper.showBiometricPrompt(
            title = "Unlock SMS Forwarder",
            subtitle = "Authenticate to access the app",
            negativeButtonText = "Use PIN Instead",
            onSuccess = {
                // Biometric auth succeeded - navigate to main screen
                navigateToMain()
            },
            onError = { errorMessage ->
                // Show error but allow PIN fallback
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                // Biometric didn't match - user can retry or use PIN
                Toast.makeText(this, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * Navigates to the main dashboard and closes the auth screen.
     */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Navigates to the PIN setup screen for first-time configuration.
     */
    private fun navigateToSetupPin() {
        val intent = Intent(this, SetupPinActivity::class.java)
        intent.putExtra(SetupPinActivity.EXTRA_IS_FIRST_SETUP, true)
        startActivity(intent)
        finish()
    }
}
