/**
 * AI Test Data Generator - Main Application Entry Point
 * A Node.js/Express server that provides:
 * - Mock fund transfer API for simulating banking transactions
 * - AI-powered test data generation using OpenAI GPT
 * - Validation APIs for verifying transfer data integrity
 *
 * This prototype demonstrates automated test data generation
 * for a banking fund transfer system using AI prompts.
 */

const express = require('express');
const transferRoutes = require('./routes/transferRoutes');
const testDataRoutes = require('./routes/testDataRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Parse incoming JSON request bodies
app.use(express.json());

// Request logging middleware for debugging and monitoring
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// Mount route handlers for transfer and test data endpoints
app.use('/api', transferRoutes);
app.use('/api', testDataRoutes);

/**
 * GET /api/health
 * Health check endpoint to verify the service is running.
 * Returns service status and available API endpoints.
 */
app.get('/api/health', (req, res) => {
  res.json({
    status: 'UP',
    service: 'AI Test Data Generator',
    version: '1.0.0',
    timestamp: new Date().toISOString(),
    endpoints: {
      transfer: {
        'POST /api/transfer': 'Process a mock fund transfer',
        'GET /api/transfer/history': 'View transfer history',
        'GET /api/transfer/balance/:accountNumber': 'Check account balance'
      },
      testData: {
        'POST /api/generate-test-data': 'Generate test data using AI',
        'POST /api/validate-test-data': 'Validate transfer test data'
      }
    }
  });
});

// Start the Express server
app.listen(PORT, () => {
  console.log('\n========================================');
  console.log('  AI Test Data Generator');
  console.log('  Running on http://localhost:' + PORT);
  console.log('========================================');
  console.log('\nAvailable endpoints:');
  console.log('  GET  /api/health                          - Health check');
  console.log('  POST /api/transfer                        - Mock fund transfer');
  console.log('  GET  /api/transfer/history                 - Transfer history');
  console.log('  GET  /api/transfer/balance/:accountNumber  - Account balance');
  console.log('  POST /api/generate-test-data               - Generate AI test data');
  console.log('  POST /api/validate-test-data               - Validate test data');
  console.log('\nOpenAI API Key: ' + (process.env.OPENAI_API_KEY ? 'Configured' : 'Not set (will use fallback data)'));
  console.log('========================================\n');
});

// Export app for testing purposes
module.exports = app;
