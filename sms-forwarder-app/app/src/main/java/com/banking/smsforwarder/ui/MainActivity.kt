package com.banking.smsforwarder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.banking.smsforwarder.R
import com.banking.smsforwarder.data.AppDatabase
import com.banking.smsforwarder.data.ForwardedMessage
import com.banking.smsforwarder.data.PreferencesManager
import com.banking.smsforwarder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main dashboard activity displaying the forwarded message log.
 *
 * Shows a list of all SMS messages that were detected as containing
 * OTP codes or transaction details, along with their forwarding status.
 * Also handles runtime permission requests for SMS access.
 *
 * Menu options provide navigation to Settings and message log clearing.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // Request code for SMS permission dialog
        private const val SMS_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager
    private val messageList = mutableListOf<ForwardedMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "SMS Forwarder"

        prefsManager = PreferencesManager(this)

        // Set up the message log RecyclerView
        setupRecyclerView()

        // Request SMS permissions if not already granted
        checkAndRequestPermissions()

        // Configure the forwarding toggle switch
        setupForwardingToggle()

        // Show configuration reminder if email is not set up
        checkConfiguration()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the message log when returning from settings
        loadMessages()
        // Update the forwarding toggle state
        binding.switchForwarding.isChecked = prefsManager.isForwardingEnabled()
    }

    /**
     * Sets up the RecyclerView with the message log adapter.
     */
    private fun setupRecyclerView() {
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = MessageAdapter(messageList)
    }

    /**
     * Loads forwarded messages from the Room database and updates the UI.
     */
    private fun loadMessages() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(this@MainActivity)
                val messages = db.forwardedMessageDao().getAllMessages()
                messageList.clear()
                messageList.addAll(messages)
                binding.rvMessages.adapter?.notifyDataSetChanged()

                // Show empty state if no messages
                if (messages.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvMessages.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvMessages.visibility = View.VISIBLE
                }

                // Update message count in header
                binding.tvMessageCount.text = "Forwarded Messages: ${messages.size}"
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading messages", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Sets up the forwarding enable/disable toggle switch.
     */
    private fun setupForwardingToggle() {
        binding.switchForwarding.isChecked = prefsManager.isForwardingEnabled()
        binding.switchForwarding.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setForwardingEnabled(isChecked)
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "SMS forwarding $status", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks if required SMS permissions are granted and requests them if not.
     */
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Handles the result of the runtime permission request dialog.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "SMS permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                // Show explanation dialog if permissions were denied
                AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("SMS permissions are required for the app to detect and forward OTP/transaction messages. Please grant them in Settings.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Shows a reminder banner if email configuration is incomplete.
     */
    private fun checkConfiguration() {
        val recipientEmail = prefsManager.getRecipientEmail()
        val senderEmail = prefsManager.getSenderEmail()

        if (recipientEmail.isNullOrBlank() || senderEmail.isNullOrBlank()) {
            binding.cardConfigWarning.visibility = View.VISIBLE
            binding.btnGoToSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        } else {
            binding.cardConfigWarning.visibility = View.GONE
        }
    }

    // --- Options Menu ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Navigate to settings screen
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_log -> {
                // Confirm and clear the message log
                showClearLogConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Shows a confirmation dialog before clearing the message log.
     */
    private fun showClearLogConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Message Log")
            .setMessage("Are you sure you want to delete all forwarded message records?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(this@MainActivity)
                    db.forwardedMessageDao().clearAll()
                    loadMessages()
                    Toast.makeText(this@MainActivity, "Message log cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Simple RecyclerView adapter for displaying forwarded messages.
     */
    inner class MessageAdapter(
        private val messages: List<ForwardedMessage>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            // View references for the message item layout
            val tvType: android.widget.TextView = itemView.findViewById(R.id.tvMessageType)
            val tvSender: android.widget.TextView = itemView.findViewById(R.id.tvSender)
            val tvDetails: android.widget.TextView = itemView.findViewById(R.id.tvParsedDetails)
            val tvTimestamp: android.widget.TextView = itemView.findViewById(R.id.tvTimestamp)
            val tvStatus: android.widget.TextView = itemView.findViewById(R.id.tvEmailStatus)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_forwarded_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]

            // Set message type badge with appropriate color
            holder.tvType.text = message.messageType
            holder.tvType.setBackgroundResource(
                if (message.messageType == "OTP") R.drawable.badge_otp
                else R.drawable.badge_transaction
            )

            holder.tvSender.text = "From: ${message.sender}"
            holder.tvDetails.text = message.parsedDetails

            // Format timestamp for display
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            holder.tvTimestamp.text = dateFormat.format(Date(message.timestamp))

            // Show email sending status with icon
            if (message.emailSent) {
                holder.tvStatus.text = "✓ Sent to ${message.recipientEmail}"
                holder.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_success))
            } else {
                holder.tvStatus.text = "✗ Failed to send"
                holder.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
            }
        }

        override fun getItemCount(): Int = messages.size
    }
}
