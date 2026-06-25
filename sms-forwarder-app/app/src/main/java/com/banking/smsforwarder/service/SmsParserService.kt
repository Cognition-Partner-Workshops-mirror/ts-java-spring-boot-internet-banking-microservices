package com.banking.smsforwarder.service

/**
 * Service responsible for parsing SMS messages to detect OTP codes
 * and banking transaction details.
 *
 * Uses regex-based pattern matching to identify:
 * - OTP/verification codes (4-8 digit codes with common keywords)
 * - Banking transactions (debits, credits, transfers with amounts)
 *
 * All methods are stateless and accessed via companion object.
 */
object SmsParserService {

    /**
     * Enum representing the type of detected content in an SMS.
     */
    enum class MessageType {
        OTP,          // One-time password or verification code
        TRANSACTION   // Banking transaction (debit, credit, transfer)
    }

    /**
     * Data class holding the parse result with type and extracted details.
     */
    data class ParseResult(
        val type: MessageType,
        val details: String
    )

    // Regex patterns for OTP detection
    // Matches common OTP/verification code formats: "OTP is 123456", "code: 1234", etc.
    private val otpPatterns = listOf(
        Regex("""(?i)\b(?:otp|one[- ]time[- ]password)\s*(?:is|:|-|=)?\s*(\d{4,8})\b"""),
        Regex("""(?i)\b(?:verification|security|auth(?:entication)?)\s*(?:code|pin)\s*(?:is|:|-|=)?\s*(\d{4,8})\b"""),
        Regex("""(?i)\b(?:code|pin)\s*(?:is|:|-|=)\s*(\d{4,8})\b"""),
        Regex("""(?i)\b(\d{4,8})\s*(?:is\s+(?:your|the)\s+(?:otp|code|pin|verification))\b"""),
        Regex("""(?i)(?:use|enter|submit|type)\s+(\d{4,8})\s+(?:as|for|to)\b"""),
        Regex("""(?i)\b(?:2fa|two[- ]factor)\s*(?:code|token)?\s*(?:is|:|-|=)?\s*(\d{4,8})\b""")
    )

    // Regex patterns for banking transaction detection
    // Matches common bank SMS formats with amounts in various currencies
    private val transactionPatterns = listOf(
        // Debit/withdrawal pattern: "debited by Rs.1000" or "Rs 500 debited"
        Regex("""(?i)(?:debited|deducted|withdrawn|paid|spent)\s*(?:by|with|of|for)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+\.?\d*)"""),
        Regex("""(?i)(?:rs\.?|inr|usd|\$|₹)\s*([\d,]+\.?\d*)\s*(?:has\s+been\s+)?(?:debited|deducted|withdrawn)"""),
        // Credit/deposit pattern: "credited with Rs.5000" or "Rs 2000 credited"
        Regex("""(?i)(?:credited|deposited|received|refund(?:ed)?)\s*(?:by|with|of|for)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+\.?\d*)"""),
        Regex("""(?i)(?:rs\.?|inr|usd|\$|₹)\s*([\d,]+\.?\d*)\s*(?:has\s+been\s+)?(?:credited|deposited|received)"""),
        // Transfer pattern: "transferred Rs.3000"
        Regex("""(?i)(?:transfer(?:red)?)\s*(?:of|for)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+\.?\d*)"""),
        // Generic transaction with amount and account
        Regex("""(?i)(?:txn|transaction)\s*(?:of|for|:)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+\.?\d*)""")
    )

    // Pattern to extract account number references from transaction messages
    private val accountPattern = Regex("""(?i)(?:a/?c|account|acct)\s*(?:no\.?|number|#)?\s*(?:ending\s*(?:with|in))?\s*[:\s]*[xX*]*(\d{4,})""")

    // Pattern to extract available/remaining balance from messages
    private val balancePattern = Regex("""(?i)(?:(?:avail(?:able)?|remaining|current|a/?c)\s*(?:bal(?:ance)?)|bal(?:ance)?)\s*(?:is|:|-|=)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+\.?\d*)""")

    /**
     * Parses an SMS message body to detect OTP codes or transaction details.
     *
     * @param message The raw SMS message body text
     * @return ParseResult if OTP or transaction is detected, null otherwise
     */
    fun parseMessage(message: String): ParseResult? {
        // Try OTP detection first (higher priority since OTPs are time-sensitive)
        val otpResult = detectOtp(message)
        if (otpResult != null) return otpResult

        // Then try transaction detection
        val transactionResult = detectTransaction(message)
        if (transactionResult != null) return transactionResult

        // No relevant content detected
        return null
    }

    /**
     * Scans message for OTP/verification code patterns.
     * Returns ParseResult with the extracted OTP code if found.
     */
    private fun detectOtp(message: String): ParseResult? {
        for (pattern in otpPatterns) {
            val match = pattern.find(message)
            if (match != null) {
                val otpCode = match.groupValues[1]
                return ParseResult(
                    type = MessageType.OTP,
                    details = "OTP Code: $otpCode"
                )
            }
        }
        return null
    }

    /**
     * Scans message for banking transaction patterns.
     * Extracts transaction type, amount, account number, and balance if available.
     */
    private fun detectTransaction(message: String): ParseResult? {
        for (pattern in transactionPatterns) {
            val match = pattern.find(message)
            if (match != null) {
                val amount = match.groupValues[1]
                val details = buildTransactionDetails(message, amount, match.value)
                return ParseResult(
                    type = MessageType.TRANSACTION,
                    details = details
                )
            }
        }
        return null
    }

    /**
     * Builds a formatted string with all available transaction details:
     * transaction type, amount, account number, and remaining balance.
     */
    private fun buildTransactionDetails(
        message: String,
        amount: String,
        matchedText: String
    ): String {
        val details = StringBuilder()

        // Determine transaction direction (debit/credit/transfer)
        val transactionType = when {
            matchedText.contains(Regex("(?i)debit|deduct|withdraw|paid|spent")) -> "DEBIT"
            matchedText.contains(Regex("(?i)credit|deposit|receive|refund")) -> "CREDIT"
            matchedText.contains(Regex("(?i)transfer")) -> "TRANSFER"
            else -> "TRANSACTION"
        }

        details.appendLine("Transaction Type: $transactionType")
        details.appendLine("Amount: $amount")

        // Try to extract account number from the original message
        val accountMatch = accountPattern.find(message)
        if (accountMatch != null) {
            details.appendLine("Account: ****${accountMatch.groupValues[1]}")
        }

        // Try to extract available balance from the original message
        val balanceMatch = balancePattern.find(message)
        if (balanceMatch != null) {
            details.appendLine("Available Balance: ${balanceMatch.groupValues[1]}")
        }

        return details.toString().trim()
    }
}
