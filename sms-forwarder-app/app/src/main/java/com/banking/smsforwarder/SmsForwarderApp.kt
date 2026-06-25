package com.banking.smsforwarder

import android.app.Application

/**
 * Application class for SMS Forwarder.
 * Serves as the entry point for the application lifecycle.
 * Initializes the Room database singleton on first access.
 */
class SmsForwarderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Store application instance for global access (e.g., database, preferences)
        instance = this
    }

    companion object {
        // Singleton application instance used by services and receivers
        // that need application context without an Activity reference
        lateinit var instance: SmsForwarderApp
            private set
    }
}
