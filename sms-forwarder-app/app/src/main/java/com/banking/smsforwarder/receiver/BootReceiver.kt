package com.banking.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that triggers on device boot completion.
 * Ensures the SMS forwarding service remains active after device restart.
 *
 * The SmsReceiver is statically registered in AndroidManifest.xml,
 * so it auto-registers on boot. This receiver serves as a hook for
 * any additional initialization needed after restart (e.g., logging).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Log that the app's SMS listener is active after boot
            Log.d(TAG, "Device booted - SMS Forwarder receiver is active")
        }
    }
}
