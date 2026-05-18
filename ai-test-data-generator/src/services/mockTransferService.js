/**
 * Mock Fund Transfer Service
 * Simulates a banking fund transfer backend with in-memory storage.
 * Processes transfers, tracks balances, and maintains transaction history.
 */

const { v4: uuidv4 } = require('uuid');
const { createTransfer } = require('../models/transferModel');

// In-memory ledger simulating bank account balances
const accountBalances = {
  'ACC100000': 50000.00,
  'ACC100001': 25000.00,
  'ACC100002': 100000.00,
  'ACC200000': 10000.00,
  'ACC200001': 30000.00,
  'ACC200002': 75000.00,
  'ACC300001': 5000.00,
  'ACC300002': 500000.00,
  'ACC300003': 20000.00,
  'ACC400001': 15000.00,
  'ACC400002': 40000.00
};

// In-memory store for completed transfers (transaction history)
const transferHistory = [];

/**
 * Processes a mock fund transfer between two accounts.
 * Validates account existence, sufficient balance, and executes the transfer.
 * @param {Object} transferData - The transfer request payload
 * @returns {Object} Transfer result with status and transaction details
 */
function processTransfer(transferData) {
  const transfer = createTransfer({
    ...transferData,
    transactionId: transferData.transactionId || uuidv4()
  });

  // Check if source account exists in the mock ledger
  if (!(transfer.fromAccount in accountBalances)) {
    // Auto-create account with default balance for demo flexibility
    accountBalances[transfer.fromAccount] = 10000.00;
  }

  // Check if destination account exists in the mock ledger
  if (!(transfer.toAccount in accountBalances)) {
    // Auto-create account with default balance for demo flexibility
    accountBalances[transfer.toAccount] = 0.00;
  }

  // Check for sufficient balance in source account
  if (accountBalances[transfer.fromAccount] < transfer.amount) {
    transfer.status = 'FAILED';
    transferHistory.push(transfer);
    return {
      success: false,
      message: `Insufficient balance in account ${transfer.fromAccount}. Available: ${accountBalances[transfer.fromAccount]}, Requested: ${transfer.amount}`,
      transfer
    };
  }

  // Execute the mock fund transfer: debit source, credit destination
  accountBalances[transfer.fromAccount] -= transfer.amount;
  accountBalances[transfer.toAccount] += transfer.amount;
  transfer.status = 'SUCCESS';

  // Store in transaction history for auditing
  transferHistory.push(transfer);

  return {
    success: true,
    message: 'Transfer completed successfully',
    transfer,
    balances: {
      fromAccount: {
        account: transfer.fromAccount,
        newBalance: accountBalances[transfer.fromAccount]
      },
      toAccount: {
        account: transfer.toAccount,
        newBalance: accountBalances[transfer.toAccount]
      }
    }
  };
}

/**
 * Retrieves the full transfer history.
 * @returns {Array} List of all processed transfers
 */
function getTransferHistory() {
  return transferHistory;
}

/**
 * Retrieves the current balance for a given account.
 * @param {string} accountNumber - The account number to look up
 * @returns {Object} Account balance or error if not found
 */
function getAccountBalance(accountNumber) {
  if (accountNumber in accountBalances) {
    return { account: accountNumber, balance: accountBalances[accountNumber] };
  }
  return { account: accountNumber, balance: null, message: 'Account not found' };
}

module.exports = {
  processTransfer,
  getTransferHistory,
  getAccountBalance
};
