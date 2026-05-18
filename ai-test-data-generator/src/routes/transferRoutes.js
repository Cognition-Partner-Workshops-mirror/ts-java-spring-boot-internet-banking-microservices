/**
 * Transfer Routes
 * Defines the REST API endpoints for the mock fund transfer system.
 * POST /transfer - Process a fund transfer
 * GET /transfer/history - View transfer history
 * GET /transfer/balance/:accountNumber - Check account balance
 */

const express = require('express');
const router = express.Router();
const { processTransfer, getTransferHistory, getAccountBalance } = require('../services/mockTransferService');

/**
 * POST /transfer
 * Processes a mock fund transfer between two accounts.
 * Expects JSON body with fromAccount, toAccount, amount, and optional fields.
 */
router.post('/transfer', (req, res) => {
  try {
    const transferData = req.body;

    // Basic request body check before processing
    if (!transferData || Object.keys(transferData).length === 0) {
      return res.status(400).json({
        success: false,
        message: 'Request body is required'
      });
    }

    const result = processTransfer(transferData);

    // Return 200 for successful transfers, 400 for failed ones
    const statusCode = result.success ? 200 : 400;
    return res.status(statusCode).json(result);
  } catch (error) {
    console.error('[Transfer Route] Error processing transfer:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Internal server error while processing transfer',
      error: error.message
    });
  }
});

/**
 * GET /transfer/history
 * Returns the complete list of processed transfers.
 */
router.get('/transfer/history', (req, res) => {
  const history = getTransferHistory();
  return res.json({
    success: true,
    count: history.length,
    transfers: history
  });
});

/**
 * GET /transfer/balance/:accountNumber
 * Returns the current balance for the specified account.
 */
router.get('/transfer/balance/:accountNumber', (req, res) => {
  const balance = getAccountBalance(req.params.accountNumber);
  return res.json(balance);
});

module.exports = router;
