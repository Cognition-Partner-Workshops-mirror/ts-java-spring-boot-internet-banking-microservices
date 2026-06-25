package com.banking.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.banking.smsforwarder.auth.PinManager
import com.banking.smsforwarder.databinding.ActivitySetupPinBinding

/**
 * Activity for creating or changing the application access PIN.
 *
 * Supports two modes:
 * 1. First-time setup (EXTRA_IS_FIRST_SETUP=true): User creates initial PIN
 * 2. PIN change (EXTRA_IS_FIRST_SETUP=false): User must enter current PIN first
 *
 * PIN requirements:
 * - Must be exactly 4-6 digits
 * - Confirmation must match the new PIN
 */
class SetupPinActivity : AppCompatActivity() {

    companion object {
        // Intent extra to indicate first-time PIN setup vs PIN change
        const val EXTRA_IS_FIRST_SETUP = "is_first_setup"
    }

    private lateinit var binding: ActivitySetupPinBinding
    private lateinit var pinManager: PinManager
    private var isFirstSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pinManager = PinManager(this)
        isFirstSetup = intent.getBooleanExtra(EXTRA_IS_FIRST_SETUP, false)

        setupUi()
    }

    /**
     * Configures UI based on whether this is first-time setup or PIN change.
     */
    private fun setupUi() {
        if (isFirstSetup) {
            // First-time setup: hide current PIN field, show welcome message
            binding.tilCurrentPin.visibility = android.view.View.GONE
            binding.tvTitle.text = "Create Your PIN"
            binding.tvSubtitle.text = "Set a 4-6 digit PIN to secure this app"
        } else {
            // PIN change: show current PIN field for verification
            binding.tilCurrentPin.visibility = android.view.View.VISIBLE
            binding.tvTitle.text = "Change Your PIN"
            binding.tvSubtitle.text = "Enter your current PIN and choose a new one"

            // Enable back navigation for PIN change mode
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        // Save button click handler
        binding.btnSavePin.setOnClickListener {
            handleSavePin()
        }
    }

    /**
     * Validates input and saves the new PIN.
     */
    private fun handleSavePin() {
        // If changing PIN, verify the current PIN first
        if (!isFirstSetup) {
            val currentPin = binding.etCurrentPin.text.toString()
            if (!pinManager.verifyPin(currentPin)) {
                binding.tilCurrentPin.error = "Current PIN is incorrect"
                return
            }
        }

        val newPin = binding.etNewPin.text.toString()
        val confirmPin = binding.etConfirmPin.text.toString()

        // Validate PIN length (4-6 digits)
        if (newPin.length < 4 || newPin.length > 6) {
            binding.tilNewPin.error = "PIN must be 4-6 digits"
            return
        }

        // Validate that PIN contains only digits
        if (!newPin.all { it.isDigit() }) {
            binding.tilNewPin.error = "PIN must contain only digits"
            return
        }

        // Validate PIN confirmation matches
        if (newPin != confirmPin) {
            binding.tilConfirmPin.error = "PINs do not match"
            return
        }

        // Save the new PIN securely
        pinManager.setPin(newPin)
        Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()

        if (isFirstSetup) {
            // After first-time setup, go to main screen
            startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
