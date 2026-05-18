/**
 * AI Test Data Generation Service
 * Uses OpenAI GPT to generate realistic test data for banking fund transfers.
 * Generates valid, invalid, and edge-case test scenarios based on AI prompts.
 */

const OpenAI = require('openai');
const { v4: uuidv4 } = require('uuid');

// Initialize OpenAI client using API key from environment variables
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY
});

/**
 * System prompt that instructs the AI model to generate banking test data.
 * Defines the expected JSON schema and test data categories.
 */
const SYSTEM_PROMPT = `You are a test data generator for a banking fund transfer system.
Generate realistic test data for fund transfer transactions in JSON format.

Each transfer object must have these fields:
- fromAccount: string (alphanumeric, 6-20 characters, e.g., "ACC123456")
- toAccount: string (alphanumeric, 6-20 characters, e.g., "ACC789012")
- amount: number (positive, between 1 and 1,000,000)
- currency: string (3-letter ISO code, e.g., "USD")
- description: string (brief transfer description)
- transactionId: string (UUID format)

Generate test data in three categories:
1. "valid" - Correct, well-formed transfers that should pass all validations
2. "invalid" - Transfers with intentional errors (missing fields, invalid amounts, bad formats)
3. "edge_cases" - Boundary conditions (min/max amounts, same account transfers, special characters)

Return ONLY valid JSON with this structure:
{
  "valid": [...],
  "invalid": [...],
  "edge_cases": [...]
}`;

/**
 * Generates test data using the OpenAI API based on a user-provided scenario.
 * Falls back to static test data if the API call fails.
 * @param {string} scenario - Description of the test scenario to generate data for
 * @param {number} count - Number of test cases to generate per category
 * @returns {Object} Generated test data organized by valid/invalid/edge_cases
 */
async function generateTestData(scenario = 'standard fund transfer', count = 3) {
  const userPrompt = `Generate ${count} test cases per category for the following scenario: "${scenario}".
Include ${count} valid transfers, ${count} invalid transfers, and ${count} edge case transfers.
Make sure each test case has a unique transactionId (UUID format).
Return ONLY the JSON object, no additional text.`;

  try {
    console.log(`[AI Service] Generating test data for scenario: "${scenario}" with ${count} cases per category`);

    // Call OpenAI API to generate test data based on the scenario
    const response = await openai.chat.completions.create({
      model: 'gpt-4o-mini',
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: userPrompt }
      ],
      temperature: 0.7,
      max_tokens: 2000,
      response_format: { type: 'json_object' }
    });

    // Parse the AI-generated JSON response
    const content = response.choices[0].message.content;
    const testData = JSON.parse(content);

    console.log('[AI Service] Successfully generated test data via OpenAI');

    return {
      success: true,
      source: 'ai-generated',
      scenario,
      data: testData,
      metadata: {
        model: 'gpt-4o-mini',
        generatedAt: new Date().toISOString(),
        tokensUsed: response.usage?.total_tokens || 0
      }
    };
  } catch (error) {
    console.error(`[AI Service] OpenAI API error: ${error.message}`);
    console.log('[AI Service] Falling back to static test data generation');

    // Fallback to locally-generated static test data if AI is unavailable
    return {
      success: true,
      source: 'fallback-static',
      scenario,
      data: generateFallbackTestData(count),
      metadata: {
        generatedAt: new Date().toISOString(),
        note: 'Generated using fallback static logic (OpenAI unavailable)'
      }
    };
  }
}

/**
 * Generates static fallback test data when the OpenAI API is unavailable.
 * Produces valid, invalid, and edge-case transfers with predictable patterns.
 * @param {number} count - Number of test cases per category
 * @returns {Object} Test data with valid, invalid, and edge_cases arrays
 */
function generateFallbackTestData(count = 3) {
  const valid = [];
  const invalid = [];
  const edgeCases = [];

  // Generate valid transfer test cases with realistic data
  for (let i = 0; i < count; i++) {
    valid.push({
      fromAccount: `ACC${100000 + i}`,
      toAccount: `ACC${200000 + i}`,
      amount: parseFloat((Math.random() * 9999 + 1).toFixed(2)),
      currency: 'USD',
      description: `Valid test transfer #${i + 1}`,
      transactionId: uuidv4()
    });
  }

  // Generate invalid transfer test cases with intentional errors
  const invalidTemplates = [
    // Missing fromAccount field
    { toAccount: 'ACC200001', amount: 500, currency: 'USD', description: 'Missing fromAccount', transactionId: uuidv4() },
    // Negative amount value
    { fromAccount: 'ACC100001', toAccount: 'ACC200001', amount: -100, currency: 'USD', description: 'Negative amount', transactionId: uuidv4() },
    // Amount exceeds maximum limit
    { fromAccount: 'ACC100002', toAccount: 'ACC200002', amount: 9999999, currency: 'USD', description: 'Amount exceeds limit', transactionId: uuidv4() },
    // Amount is a string instead of number
    { fromAccount: 'ACC100003', toAccount: 'ACC200003', amount: 'not-a-number', currency: 'USD', description: 'String amount', transactionId: uuidv4() },
    // Missing all required fields
    { currency: 'USD', description: 'Missing all required fields', transactionId: uuidv4() },
    // Invalid account format with special characters
    { fromAccount: '!@#$%', toAccount: 'ACC200004', amount: 100, currency: 'USD', description: 'Invalid account format', transactionId: uuidv4() }
  ];

  for (let i = 0; i < count; i++) {
    invalid.push(invalidTemplates[i % invalidTemplates.length]);
  }

  // Generate edge-case transfer test cases for boundary conditions
  const edgeCaseTemplates = [
    // Minimum allowed transfer amount (exactly 1)
    { fromAccount: 'ACC300001', toAccount: 'ACC400001', amount: 1, currency: 'USD', description: 'Minimum amount', transactionId: uuidv4() },
    // Maximum allowed transfer amount (exactly 1,000,000)
    { fromAccount: 'ACC300002', toAccount: 'ACC400002', amount: 1000000, currency: 'USD', description: 'Maximum amount', transactionId: uuidv4() },
    // Same source and destination account (self-transfer)
    { fromAccount: 'ACC300003', toAccount: 'ACC300003', amount: 500, currency: 'USD', description: 'Same account transfer', transactionId: uuidv4() },
    // Zero amount transfer
    { fromAccount: 'ACC300004', toAccount: 'ACC400004', amount: 0, currency: 'USD', description: 'Zero amount', transactionId: uuidv4() },
    // Very small decimal amount (fractional cents)
    { fromAccount: 'ACC300005', toAccount: 'ACC400005', amount: 0.01, currency: 'USD', description: 'Smallest decimal', transactionId: uuidv4() },
    // Amount at boundary just above maximum
    { fromAccount: 'ACC300006', toAccount: 'ACC400006', amount: 1000001, currency: 'USD', description: 'Just above max limit', transactionId: uuidv4() }
  ];

  for (let i = 0; i < count; i++) {
    edgeCases.push(edgeCaseTemplates[i % edgeCaseTemplates.length]);
  }

  return { valid, invalid, edge_cases: edgeCases };
}

module.exports = {
  generateTestData,
  generateFallbackTestData
};
