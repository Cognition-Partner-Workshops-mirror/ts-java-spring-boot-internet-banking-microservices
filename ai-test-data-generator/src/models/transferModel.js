/**
 * Transfer Model
 * Defines the structure and constants for fund transfer transactions.
 * Used by the mock transfer API, validation, and test data generation modules.
 */

// Minimum allowed transfer amount (in currency units)
const MIN_TRANSFER_AMOUNT = 1;

// Maximum allowed transfer amount (in currency units)
const MAX_TRANSFER_AMOUNT = 1000000;

// Valid transaction status values
const TRANSFER_STATUSES = ['PENDING', 'SUCCESS', 'FAILED'];

// Required fields for a valid fund transfer request
const REQUIRED_FIELDS = ['fromAccount', 'toAccount', 'amount'];

/**
 * Creates a transfer object with default values.
 * @param {Object} data - Partial transfer data
 * @returns {Object} A complete transfer object with defaults applied
 */
function createTransfer(data = {}) {
  return {
    fromAccount: data.fromAccount || null,
    toAccount: data.toAccount || null,
    amount: data.amount || null,
    currency: data.currency || 'USD',
    description: data.description || '',
    transactionId: data.transactionId || null,
    timestamp: data.timestamp || new Date().toISOString(),
    status: data.status || 'PENDING'
  };
}

module.exports = {
  MIN_TRANSFER_AMOUNT,
  MAX_TRANSFER_AMOUNT,
  TRANSFER_STATUSES,
  REQUIRED_FIELDS,
  createTransfer
};
