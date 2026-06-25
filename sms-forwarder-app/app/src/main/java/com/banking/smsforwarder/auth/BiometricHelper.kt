package com.banking.smsforwarder.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class for biometric authentication (fingerprint/face unlock).
 *
 * Wraps AndroidX BiometricPrompt API to provide a simple interface
 * for checking biometric availability and showing the authentication dialog.
 *
 * Falls back gracefully when biometric hardware is not available
 * or no biometrics are enrolled - the app uses PIN-only auth in that case.
 */
class BiometricHelper(private val activity: FragmentActivity) {

    /**
     * Checks if the device supports and has enrolled biometric authentication.
     *
     * @return true if biometric auth can be used, false if hardware is missing
     *         or no biometrics are enrolled
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        // Check for strong biometric authentication (fingerprint, face, iris)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Shows the system biometric authentication dialog.
     *
     * @param title Dialog title text
     * @param subtitle Dialog subtitle text
     * @param negativeButtonText Text for the cancel/fallback button
     * @param onSuccess Callback invoked when authentication succeeds
     * @param onError Callback invoked on authentication error with error message
     * @param onFailed Callback invoked when biometric is recognized but doesn't match
     */
    fun showBiometricPrompt(
        title: String = "Biometric Authentication",
        subtitle: String = "Use your fingerprint or face to unlock",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        // Execute callbacks on the main thread
        val executor = ContextCompat.getMainExecutor(activity)

        // Define authentication result callbacks
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Biometric matched - grant access
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // System error or user cancelled - report error message
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometric recognized but didn't match enrolled data
                onFailed()
            }
        }

        // Build and display the biometric prompt dialog
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
