/**
 * Transfer Validator Module
 * Provides validation logic for fund transfer transactions including:
 * - Required field checks
 * - Amount limit enforcement
 * - Duplicate transaction detection
 * - Account number format validation
 */

const {
  MIN_TRANSFER_AMOUNT,
  MAX_TRANSFER_AMOUNT,
  REQUIRED_FIELDS
} = require('../models/transferModel');

// In-memory store for processed transaction IDs to detect duplicates
const processedTransactionIds = new Set();

/**
 * Validates a single fund transfer request.
 * Checks for missing fields, amount limits, same-account transfers,
 * invalid account formats, and duplicate transactions.
 * @param {Object} transfer - The transfer object to validate
 * @returns {Object} Validation result with isValid flag and array of errors
 */
function validateTransfer(transfer) {
  const errors = [];

  // Check for missing required fields
  for (const field of REQUIRED_FIELDS) {
    if (transfer[field] === undefined || transfer[field] === null || transfer[field] === '') {
      errors.push({
        field,
        message: `Missing required field: ${field}`,
        type: 'MISSING_FIELD'
      });
    }
  }

  // Validate amount limits (only if amount is present)
  if (transfer.amount !== undefined && transfer.amount !== null) {
    if (typeof transfer.amount !== 'number') {
      errors.push({
        field: 'amount',
        message: `Amount must be a number, got ${typeof transfer.amount}`,
        type: 'INVALID_TYPE'
      });
    } else {
      if (transfer.amount < MIN_TRANSFER_AMOUNT) {
        errors.push({
          field: 'amount',
          message: `Amount ${transfer.amount} is below minimum limit of ${MIN_TRANSFER_AMOUNT}`,
          type: 'AMOUNT_BELOW_MINIMUM'
        });
      }
      if (transfer.amount > MAX_TRANSFER_AMOUNT) {
        errors.push({
          field: 'amount',
          message: `Amount ${transfer.amount} exceeds maximum limit of ${MAX_TRANSFER_AMOUNT}`,
          type: 'AMOUNT_EXCEEDS_MAXIMUM'
        });
      }
      // Negative or zero amounts
      if (transfer.amount <= 0) {
        errors.push({
          field: 'amount',
          message: 'Amount must be a positive number',
          type: 'INVALID_AMOUNT'
        });
      }
    }
  }

  // Check for same-account transfer (fromAccount == toAccount)
  if (transfer.fromAccount && transfer.toAccount &&
      transfer.fromAccount === transfer.toAccount) {
    errors.push({
      field: 'fromAccount/toAccount',
      message: 'Source and destination accounts cannot be the same',
      type: 'SAME_ACCOUNT_TRANSFER'
    });
  }

  // Validate account number format (expect alphanumeric, 6-20 chars)
  const accountPattern = /^[A-Za-z0-9]{6,20}$/;
  if (transfer.fromAccount && !accountPattern.test(transfer.fromAccount)) {
    errors.push({
      field: 'fromAccount',
      message: `Invalid account number format: ${transfer.fromAccount}`,
      type: 'INVALID_ACCOUNT_FORMAT'
    });
  }
  if (transfer.toAccount && !accountPattern.test(transfer.toAccount)) {
    errors.push({
      field: 'toAccount',
      message: `Invalid account number format: ${transfer.toAccount}`,
      type: 'INVALID_ACCOUNT_FORMAT'
    });
  }

  // Check for duplicate transactions using transactionId
  if (transfer.transactionId) {
    if (processedTransactionIds.has(transfer.transactionId)) {
      errors.push({
        field: 'transactionId',
        message: `Duplicate transaction detected: ${transfer.transactionId}`,
        type: 'DUPLICATE_TRANSACTION'
      });
    } else {
      // Track this transaction ID for future duplicate checks
      processedTransactionIds.add(transfer.transactionId);
    }
  }

  return {
    isValid: errors.length === 0,
    transfer,
    errors
  };
}

/**
 * Validates an array of fund transfer requests.
 * Returns per-item results and an overall summary.
 * @param {Array} transfers - Array of transfer objects to validate
 * @returns {Object} Summary with total counts and per-item validation results
 */
function validateBatch(transfers) {
  if (!Array.isArray(transfers)) {
    return {
      success: false,
      message: 'Input must be an array of transfer objects',
      results: []
    };
  }

  const results = transfers.map((transfer, index) => ({
    index,
    ...validateTransfer(transfer)
  }));

  const validCount = results.filter(r => r.isValid).length;
  const invalidCount = results.filter(r => !r.isValid).length;

  return {
    success: true,
    summary: {
      total: transfers.length,
      valid: validCount,
      invalid: invalidCount
    },
    results
  };
}

/**
 * Resets the duplicate transaction tracker.
 * Useful for testing or resetting state between batches.
 */
function resetDuplicateTracker() {
  processedTransactionIds.clear();
}

module.exports = {
  validateTransfer,
  validateBatch,
  resetDuplicateTracker
};
