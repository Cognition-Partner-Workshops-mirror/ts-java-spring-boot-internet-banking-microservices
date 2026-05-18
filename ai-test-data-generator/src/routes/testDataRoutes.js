/**
 * Test Data Routes
 * Exposes API endpoints for AI-powered test data generation and validation.
 * POST /generate-test-data - Generate test data using AI (OpenAI GPT)
 * POST /validate-test-data - Validate an array of transfer test data
 */

const express = require('express');
const router = express.Router();
const { generateTestData } = require('../services/aiTestDataService');
const { validateBatch, resetDuplicateTracker } = require('../validators/transferValidator');

/**
 * POST /generate-test-data
 * Generates test data for fund transfer scenarios using OpenAI.
 * Accepts optional scenario description and count per category.
 * Falls back to static data if OpenAI is unavailable.
 *
 * Request body:
 *   - scenario (string, optional): Description of the test scenario
 *   - count (number, optional): Number of test cases per category (default: 3)
 */
router.post('/generate-test-data', async (req, res) => {
  try {
    const { scenario, count } = req.body;

    // Generate test data via AI or fallback
    const result = await generateTestData(scenario, count);

    return res.json(result);
  } catch (error) {
    console.error('[Test Data Route] Error generating test data:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Error generating test data',
      error: error.message
    });
  }
});

/**
 * POST /validate-test-data
 * Validates an array of fund transfer objects against business rules.
 * Checks for missing fields, amount limits, duplicate transactions, etc.
 *
 * Request body:
 *   - transfers (array): Array of transfer objects to validate
 *   - resetDuplicates (boolean, optional): Reset duplicate tracker before validation
 */
router.post('/validate-test-data', (req, res) => {
  try {
    const { transfers, resetDuplicates } = req.body;

    // Optionally reset the duplicate tracker for a clean validation run
    if (resetDuplicates) {
      resetDuplicateTracker();
    }

    if (!transfers) {
      return res.status(400).json({
        success: false,
        message: 'Request body must contain a "transfers" array'
      });
    }

    // Run batch validation on all provided transfers
    const result = validateBatch(transfers);

    return res.json(result);
  } catch (error) {
    console.error('[Test Data Route] Error validating test data:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Error validating test data',
      error: error.message
    });
  }
});

module.exports = router;
