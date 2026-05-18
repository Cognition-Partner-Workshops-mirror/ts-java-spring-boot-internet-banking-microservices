# AI Test Data Generator

An AI-powered test data generator prototype for the banking fund transfer system. This service uses OpenAI GPT to generate realistic test data (valid, invalid, and edge cases) for fund transfer transactions, and provides validation logic to verify the generated data.

## Features

- **Mock Fund Transfer API** - Simulates banking fund transfers with in-memory account balances
- **AI-Powered Test Data Generation** - Uses OpenAI GPT to generate contextual test data
- **Comprehensive Validation** - Validates transfers for amount limits, missing fields, duplicate transactions, and account format
- **Fallback Mode** - Generates static test data when OpenAI API is unavailable

## Quick Start

### Prerequisites
- Node.js 18+
- OpenAI API key (optional, falls back to static data generation)

### Installation

```bash
cd ai-test-data-generator
npm install
```

### Running the Server

```bash
# With OpenAI API key
OPENAI_API_KEY=your-key-here npm start

# Without API key (uses fallback static data)
npm start
```

The server starts on `http://localhost:3000`.

## API Endpoints

### Health Check
```bash
GET /api/health
```

### Mock Fund Transfer
```bash
POST /api/transfer
Content-Type: application/json

{
  "fromAccount": "ACC100000",
  "toAccount": "ACC200000",
  "amount": 500.00,
  "currency": "USD",
  "description": "Test transfer"
}
```

### Generate Test Data (AI-powered)
```bash
POST /api/generate-test-data
Content-Type: application/json

{
  "scenario": "international wire transfer with currency conversion",
  "count": 5
}
```

### Validate Test Data
```bash
POST /api/validate-test-data
Content-Type: application/json

{
  "resetDuplicates": true,
  "transfers": [
    {
      "fromAccount": "ACC100000",
      "toAccount": "ACC200000",
      "amount": 500,
      "transactionId": "uuid-here"
    },
    {
      "amount": -100,
      "transactionId": "uuid-here"
    }
  ]
}
```

### Transfer History
```bash
GET /api/transfer/history
```

### Account Balance
```bash
GET /api/transfer/balance/ACC100000
```

## Validation Rules

| Rule | Description |
|------|-------------|
| Missing Fields | Checks for required fields: fromAccount, toAccount, amount |
| Amount Minimum | Amount must be >= 1 |
| Amount Maximum | Amount must be <= 1,000,000 |
| Invalid Amount | Amount must be a positive number |
| Same Account | Source and destination accounts cannot be the same |
| Account Format | Account numbers must be alphanumeric, 6-20 characters |
| Duplicate Transaction | Detects duplicate transactionId values |

## Project Structure

```
ai-test-data-generator/
├── src/
│   ├── app.js                        # Express server entry point
│   ├── models/
│   │   └── transferModel.js          # Transfer data model and constants
│   ├── routes/
│   │   ├── transferRoutes.js         # Mock transfer API endpoints
│   │   └── testDataRoutes.js         # Test data generation/validation endpoints
│   ├── services/
│   │   ├── aiTestDataService.js      # OpenAI integration for test data generation
│   │   └── mockTransferService.js    # Mock banking transfer logic
│   └── validators/
│       └── transferValidator.js      # Transfer validation rules engine
├── package.json
├── .eslintrc.json
└── README.md
```
