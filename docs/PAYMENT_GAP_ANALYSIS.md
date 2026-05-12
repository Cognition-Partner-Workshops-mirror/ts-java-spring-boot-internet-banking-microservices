# Payment Payload Gap Analysis

> Comparison of the current internet-banking payment payloads against **ISO 20022** message standards and industry best practices for payment validation.

---

## Table of Contents

1. [Current Payment Payload Structure](#1-current-payment-payload-structure)
2. [ISO 20022 Standard Reference](#2-iso-20022-standard-reference)
3. [Field-Level Gap Analysis — Fund Transfer](#3-field-level-gap-analysis--fund-transfer)
4. [Field-Level Gap Analysis — Utility Payment](#4-field-level-gap-analysis--utility-payment)
5. [Payment Validation Rules Assessment](#5-payment-validation-rules-assessment)
6. [Gap Severity Summary](#6-gap-severity-summary)

---

## 1. Current Payment Payload Structure

### 1.1 Fund Transfer

**Request** (`FundTransferRequest` — fund-transfer-service):

| Field         | Type        | Description                          |
|---------------|-------------|--------------------------------------|
| `fromAccount` | `String`    | Source account number                |
| `toAccount`   | `String`    | Destination account number           |
| `amount`      | `BigDecimal`| Transfer amount (no currency field)  |
| `authID`      | `String`    | Authenticated user identifier        |

**Response** (`FundTransferResponse` — fund-transfer-service):

| Field           | Type     | Description                |
|-----------------|----------|----------------------------|
| `message`       | `String` | Human-readable status text |
| `transactionId` | `String` | UUID assigned by core      |

**Core Banking Request** (`FundTransferRequest` — core-banking-service):

| Field         | Type        | Description                          |
|---------------|-------------|--------------------------------------|
| `fromAccount` | `String`    | Source account number                |
| `toAccount`   | `String`    | Destination account number           |
| `amount`      | `BigDecimal`| Transfer amount                      |

> Note: `authID` is stripped when the fund-transfer-service calls core-banking. The core-banking version has only 3 fields.

**Core Banking Response** (`FundTransferResponse` — core-banking-service):

| Field           | Type     | Description                |
|-----------------|----------|----------------------------|
| `message`       | `String` | Human-readable status text |
| `transactionId` | `String` | UUID assigned by core      |

### 1.2 Utility Payment

**Request** (`UtilityPaymentRequest` — utility-payment-service):

| Field             | Type        | Description                            |
|-------------------|-------------|----------------------------------------|
| `providerId`      | `Long`      | Internal ID of the utility provider    |
| `amount`          | `BigDecimal`| Payment amount (no currency field)     |
| `referenceNumber` | `String`    | Customer/bill reference                |
| `account`         | `String`    | Debtor account number                  |

**Response** (`UtilityPaymentResponse` — utility-payment-service):

| Field           | Type     | Description                |
|-----------------|----------|----------------------------|
| `message`       | `String` | Human-readable status text |
| `transactionId` | `String` | UUID assigned by core      |

### 1.3 Persisted Entities

**`fund_transfer` table** (fund-transfer-service DB):

| Column                | Type        | Notes                         |
|-----------------------|-------------|-------------------------------|
| `id`                  | `Long`      | Auto-generated PK             |
| `transactionReference`| `String`    | UUID from core-banking        |
| `fromAccount`         | `String`    | Source account number         |
| `toAccount`           | `String`    | Destination account number    |
| `amount`              | `BigDecimal`| Transfer amount               |
| `status`              | `Enum`      | PENDING / SUCCESS (no FAILED) |
| `createdDate`         | `Instant`   | AuditAware field              |
| `createdBy`           | `String`    | AuditAware field              |
| `modifiedDate`        | `Instant`   | AuditAware field              |
| `modifiedBy`          | `String`    | AuditAware field              |
| `version`             | `long`      | Optimistic locking            |

**`utility_payment` table** (utility-payment-service DB):

| Column                | Type        | Notes                              |
|-----------------------|-------------|------------------------------------|
| `id`                  | `Long`      | Auto-generated PK                  |
| `providerId`          | `Long`      | FK to utility provider             |
| `amount`              | `BigDecimal`| Payment amount                     |
| `referenceNumber`     | `String`    | Customer/bill reference            |
| `account`             | `String`    | Debtor account number              |
| `transactionId`       | `String`    | UUID from core-banking             |
| `status`              | `Enum`      | PROCESSING / SUCCESS (no FAILED)   |
| `createdDate`         | `Instant`   | AuditAware field                   |
| `createdBy`           | `String`    | AuditAware field                   |
| `modifiedDate`        | `Instant`   | AuditAware field                   |
| `modifiedBy`          | `String`    | AuditAware field                   |
| `version`             | `long`      | Optimistic locking                 |

**`banking_core_transaction` table** (core-banking-service DB):

| Column             | Type           | Notes                              |
|--------------------|----------------|------------------------------------|
| `id`               | `bigint(20)`   | Auto-generated PK                  |
| `amount`           | `decimal(19,2)`| Signed amount (negative for debits)|
| `transaction_type` | `varchar(30)`  | FUND_TRANSFER / UTILITY_PAYMENT    |
| `reference_number` | `varchar(50)`  | Counter-party account or bill ref  |
| `transaction_id`   | `varchar(50)`  | UUID                               |
| `account_id`       | `bigint(20)`   | FK to banking_core_account         |

---

## 2. ISO 20022 Standard Reference

The following ISO 20022 message types are relevant to this application:

| Message   | Name                          | Purpose                                           |
|-----------|-------------------------------|---------------------------------------------------|
| `pain.001`| CustomerCreditTransferInitiation | Submitted by the customer to initiate a payment |
| `pain.002`| CustomerPaymentStatusReport    | Status feedback on a submitted payment            |
| `pacs.008`| FI-to-FI Customer Credit Transfer | Interbank message for executing the transfer   |

### Key ISO 20022 Data Groups

| Data Group                     | pain.001 | pacs.008 | Description                                              |
|--------------------------------|----------|----------|----------------------------------------------------------|
| **MessageIdentification**      | ✅        | ✅        | Unique ID for the entire message                         |
| **CreationDateTime**           | ✅        | ✅        | ISO 8601 timestamp of message creation                   |
| **NumberOfTransactions**       | ✅        | ✅        | Count of payment instructions in the batch               |
| **ControlSum**                 | ✅        | ✅        | Sum of all instructed amounts for integrity check        |
| **InitiatingParty**            | ✅        | —        | Name/ID of the party requesting the payment              |
| **InstructionIdentification**  | ✅        | ✅        | Unique ID per payment instruction                        |
| **EndToEndIdentification**     | ✅        | ✅        | Customer-assigned end-to-end reference                   |
| **InstructedAmount + Currency**| ✅        | ✅        | Amount with mandatory ISO 4217 currency code             |
| **DebtorAccount (IBAN)**       | ✅        | ✅        | Source account in IBAN or proprietary format              |
| **DebtorAgent (BIC)**          | ✅        | ✅        | BIC of the debtor's financial institution                |
| **CreditorAccount (IBAN)**     | ✅        | ✅        | Target account in IBAN or proprietary format             |
| **CreditorAgent (BIC)**        | ✅        | ✅        | BIC of the creditor's financial institution              |
| **CreditorName**               | ✅        | ✅        | Name of the beneficiary                                  |
| **RemittanceInformation**      | ✅        | ✅        | Structured or unstructured payment description           |
| **ChargeBearer**               | ✅        | ✅        | Who bears the charges (DEBT, CRED, SHAR, SLEV)          |
| **RequestedExecutionDate**     | ✅        | —        | When the payment should be executed                      |
| **PaymentMethod**              | ✅        | —        | Transfer method (CHK, TRF, TRA)                         |
| **CategoryPurpose**            | ✅        | ✅        | High-level purpose code (SALA, SUPP, TAXS, etc.)        |
| **TransactionStatus**          | —        | —        | In pain.002: ACCP, ACTC, ACSP, RJCT, etc.              |
| **StatusReasonCode**           | —        | —        | In pain.002: coded reason for rejection                 |

---

## 3. Field-Level Gap Analysis — Fund Transfer

### 3.1 Fields Present but Named Differently

| ISO 20022 Field                | Current Field    | ISO Element Path                       | Notes                                                  |
|--------------------------------|------------------|----------------------------------------|--------------------------------------------------------|
| Debtor Account                 | `fromAccount`    | `PmtInf/DbtrAcct/Id/Othr/Id`          | Functionally equivalent; non-standard naming           |
| Creditor Account               | `toAccount`      | `PmtInf/CdtTrfTxInf/CdtrAcct/Id/Othr/Id` | Functionally equivalent; non-standard naming       |
| Instructed Amount              | `amount`         | `PmtInf/CdtTrfTxInf/Amt/InstdAmt`     | Present but missing mandatory currency code (Ccy attr) |
| End-to-End Identification      | `transactionId`  | `PmtInf/CdtTrfTxInf/PmtId/EndToEndId` | Generated server-side (UUID); not client-assigned      |

### 3.2 ISO 20022 Fields Missing Entirely

| ISO 20022 Field                  | Element Path                                  | Severity     | Business Risk                                                   |
|----------------------------------|-----------------------------------------------|--------------|-----------------------------------------------------------------|
| **Currency Code (Ccy)**          | `InstdAmt/@Ccy`                               | 🔴 Critical  | All amounts assumed single-currency; multi-currency is impossible; no FX conversion possible |
| **MessageIdentification**        | `GrpHdr/MsgId`                                | 🔴 Critical  | No message-level traceability; impossible to correlate batch/request origin |
| **CreationDateTime**             | `GrpHdr/CreDtTm`                              | 🟠 High      | No client-side timestamp; audit trail depends solely on server-side `createdDate` |
| **InstructionIdentification**    | `PmtInf/CdtTrfTxInf/PmtId/InstrId`           | 🟠 High      | No instruction-level ID; client cannot track individual payment instructions |
| **EndToEndId (client-assigned)** | `PmtInf/CdtTrfTxInf/PmtId/EndToEndId`        | 🟠 High      | Currently server-generated UUID; ISO 20022 requires client-provided idempotency key |
| **DebtorName**                   | `PmtInf/Dbtr/Nm`                              | 🟡 Medium    | Cannot display originator name in statements or notifications |
| **DebtorAgent (BIC)**            | `PmtInf/DbtrAgt/FinInstnId/BICFI`             | 🟡 Medium    | Single-institution assumption; blocks interbank/SWIFT integration |
| **CreditorName**                 | `PmtInf/CdtTrfTxInf/Cdtr/Nm`                 | 🟡 Medium    | Cannot display beneficiary name for confirmation screens |
| **CreditorAgent (BIC)**          | `PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/BICFI`| 🟡 Medium    | Blocks external/interbank transfers |
| **RemittanceInformation**        | `PmtInf/CdtTrfTxInf/RmtInf/Ustrd`            | 🟡 Medium    | No payment description or reference for reconciliation |
| **RequestedExecutionDate**       | `PmtInf/ReqdExctnDt`                          | 🟡 Medium    | No support for scheduled/future-dated payments |
| **ChargeBearer**                 | `PmtInf/CdtTrfTxInf/ChrgBr`                  | 🟢 Low       | Single-institution; no fee routing needed today |
| **CategoryPurpose**              | `PmtInf/CdtTrfTxInf/Purp/Cd`                 | 🟢 Low       | No purpose classification; blocks regulatory reporting (AML/CFT) |
| **PaymentMethod**                | `PmtInf/PmtMtd`                               | 🟢 Low       | Only one method (credit transfer) supported today |
| **NumberOfTransactions**         | `GrpHdr/NbOfTxs`                              | 🟢 Low       | Only single-transaction requests; batch not supported |
| **ControlSum**                   | `GrpHdr/CtrlSum`                               | 🟢 Low       | Relevant only for batch payments |

### 3.3 Extra Fields (Not in ISO 20022)

| Current Field | Location               | Notes                                                          |
|---------------|------------------------|----------------------------------------------------------------|
| `authID`      | Fund Transfer Request  | Application-level auth header; not part of ISO payment schema  |
| `message`     | Fund Transfer Response | Free-text human message; ISO uses structured status codes      |

---

## 4. Field-Level Gap Analysis — Utility Payment

### 4.1 Fields Present but Named Differently

| ISO 20022 Field        | Current Field      | Notes                                                                |
|------------------------|--------------------|----------------------------------------------------------------------|
| Debtor Account         | `account`          | Functionally equivalent; generic naming                              |
| Creditor Reference     | `referenceNumber`  | Maps loosely to `RmtInf/Strd/CdtrRefInf/Ref` (structured remittance)|
| Instructed Amount      | `amount`           | Present but missing mandatory currency code                          |

### 4.2 ISO 20022 Fields Missing Entirely

| ISO 20022 Field                    | Severity     | Business Risk                                                         |
|------------------------------------|--------------|-----------------------------------------------------------------------|
| **Currency Code (Ccy)**            | 🔴 Critical  | Same as fund transfer — no multi-currency support                     |
| **MessageIdentification**          | 🔴 Critical  | No message traceability                                               |
| **CreditorName**                   | 🟠 High      | Provider identified only by internal DB ID (`providerId`); no human-readable name in payload |
| **CreditorAccount (IBAN/Other)**   | 🟠 High      | No creditor account in payment request; resolved internally from `providerId` |
| **EndToEndId (client-assigned)**   | 🟠 High      | No client idempotency key; duplicate payments possible                |
| **CreationDateTime**               | 🟠 High      | No client timestamp                                                   |
| **DebtorName**                     | 🟡 Medium    | Not included in payload                                               |
| **RemittanceInformation (struct)** | 🟡 Medium    | `referenceNumber` is unstructured; no type code or issuer             |
| **RequestedExecutionDate**         | 🟡 Medium    | No scheduled payments                                                 |
| **CategoryPurpose**                | 🟢 Low       | Would help classify utility vs. other bill payments                   |
| **ChargeBearer**                   | 🟢 Low       | Not applicable for same-institution utility payments today            |

### 4.3 Extra Fields (Not in ISO 20022)

| Current Field | Notes                                                                       |
|---------------|-----------------------------------------------------------------------------|
| `providerId`  | Internal surrogate key; ISO uses creditor name + account for identification |

---

## 5. Payment Validation Rules Assessment

### 5.1 Amount Limits

| Check                        | Status         | Details                                                                    |
|------------------------------|----------------|----------------------------------------------------------------------------|
| Minimum amount > 0           | ❌ **Missing** | No validation. `BigDecimal` accepts zero, negative, and null values.       |
| Maximum single-transaction   | ❌ **Missing** | No per-transaction ceiling. A single request could transfer the entire balance. |
| Daily aggregate limit        | ❌ **Missing** | No daily cumulative limit per account or per user.                         |
| Amount precision/scale       | ⚠️ **Partial** | Database uses `decimal(19,2)` but no DTO-level scale validation.           |

**Severity: 🔴 Critical** — A malicious or buggy client can submit zero, negative, or astronomically large amounts.

### 5.2 Currency Handling

| Check                        | Status         | Details                                                                    |
|------------------------------|----------------|----------------------------------------------------------------------------|
| Currency field present       | ❌ **Missing** | No currency attribute on any payment DTO or entity.                        |
| Cross-currency validation    | ❌ **Missing** | Impossible since currency is not tracked.                                  |
| FX rate lookup               | ❌ **Missing** | No foreign exchange capability.                                            |
| Currency match (debit/credit)| ❌ **Missing** | Cannot verify source and destination are same currency.                    |

**Severity: 🔴 Critical** — The system implicitly assumes a single currency with no way to enforce it.

### 5.3 Duplicate Detection / Idempotency

| Check                             | Status         | Details                                                               |
|-----------------------------------|----------------|-----------------------------------------------------------------------|
| Client-provided idempotency key   | ❌ **Missing** | No idempotency key in any request DTO.                                |
| Server-side duplicate detection   | ❌ **Missing** | No unique constraint on (fromAccount, toAccount, amount, timestamp).  |
| Retry-safe semantics              | ❌ **Missing** | Re-submitting the same request creates a new transaction every time.  |
| Transaction reference uniqueness  | ⚠️ **Partial** | UUID is generated server-side; prevents accidental collision but not intentional duplicates. |

**Severity: 🔴 Critical** — Network retries, user double-clicks, or malicious replay can create duplicate financial transactions.

### 5.4 Account Validation

| Check                              | Status         | Details                                                              |
|------------------------------------|----------------|----------------------------------------------------------------------|
| Account existence                  | ✅ **Present** | `AccountService.readBankAccount()` throws `EntityNotFoundException`. |
| Insufficient funds check           | ✅ **Present** | `validateBalance()` checks `actualBalance >= amount`.                |
| Account status check (ACTIVE)      | ❌ **Missing** | PENDING, DORMANT, or BLOCKED accounts can transact.                  |
| Self-transfer prevention           | ❌ **Missing** | `fromAccount == toAccount` is not checked; creates debit+credit on same account. |
| Account ownership (authorization)  | ❌ **Missing** | Any authenticated user can transfer from any account.                |

**Severity: 🟠 High** — DORMANT/BLOCKED accounts should not be transactable; self-transfers waste ledger entries; authorization gap is a security risk.

### 5.5 Transaction Integrity

| Check                                | Status         | Details                                                              |
|--------------------------------------|----------------|----------------------------------------------------------------------|
| Atomic debit/credit                  | ⚠️ **Partial** | `@Transactional` on `TransactionService` covers core-banking. But fund-transfer-service saves PENDING first, then makes a Feign call with no rollback on failure. |
| Status management                    | ❌ **Broken**  | `fund_transfer.status` and `utility_payment.status` never set to FAILED. If Feign call fails, records stay PENDING/PROCESSING forever. |
| Double-deduction bug                 | ❌ **Bug**     | `internalFundTransfer()` sets `availableBalance = actualBalance - amount` AFTER `actualBalance` was already debited, causing double subtraction. Same bug in `utilPayment()`. |
| Compensation/rollback on failure     | ❌ **Missing** | No saga, compensation transaction, or retry mechanism.               |
| Concurrent access protection         | ⚠️ **Partial** | AuditAware has `@Version` for optimistic locking on fund_transfer/utility_payment entities, but core-banking `BankAccountEntity` has NO version field — concurrent transfers can corrupt balances. |

**Severity: 🔴 Critical** — The double-deduction bug causes financial loss. Missing compensation means orphaned transactions.

### 5.6 Input Sanitization

| Check                     | Status         | Details                                                               |
|---------------------------|----------------|-----------------------------------------------------------------------|
| Bean Validation (`@Valid`)| ❌ **Missing** | No `@Valid` on any `@RequestBody` in any controller.                  |
| Null checks               | ❌ **Missing** | Null `fromAccount`, `toAccount`, or `amount` cause `NullPointerException`. |
| String length limits      | ❌ **Missing** | Account numbers have no max-length validation.                        |
| SQL injection protection  | ✅ **Present** | JPA parameterized queries prevent SQL injection.                      |
| XSS protection            | ✅ **Present** | JSON API without HTML rendering.                                      |

**Severity: 🟠 High** — Missing input validation causes unhandled NPEs returned as HTTP 400 with stack traces.

---

## 6. Gap Severity Summary

| # | Gap                                          | Severity     | Category            | Business Risk                                                |
|---|----------------------------------------------|--------------|---------------------|--------------------------------------------------------------|
| 1 | No currency code on any payment              | 🔴 Critical  | ISO 20022 Compliance| Multi-currency impossible; no FX; regulatory non-compliance  |
| 2 | No idempotency / duplicate detection         | 🔴 Critical  | Validation          | Duplicate financial transactions on retry or replay          |
| 3 | Double-deduction bug in balance updates      | 🔴 Critical  | Transaction Integrity| Direct financial loss — customers lose money                |
| 4 | No amount limits (min/max/daily)             | 🔴 Critical  | Validation          | Zero/negative/unbounded transfers possible                   |
| 5 | No compensation / rollback on Feign failure  | 🔴 Critical  | Transaction Integrity| Orphaned PENDING/PROCESSING records; funds debited but transfer not completed |
| 6 | No message identification                    | 🔴 Critical  | ISO 20022 Compliance| No end-to-end traceability across systems                    |
| 7 | Missing Bean Validation (`@Valid`)           | 🟠 High      | Input Validation    | Null/invalid inputs cause unhandled NPEs                     |
| 8 | No account status check                      | 🟠 High      | Validation          | DORMANT/BLOCKED accounts can transact                        |
| 9 | No account ownership authorization           | 🟠 High      | Security            | Any user can transfer from any account                       |
| 10| No client-assigned end-to-end ID             | 🟠 High      | ISO 20022 Compliance| Cannot trace payment from originator through settlement      |
| 11| No creditor name in payloads                 | 🟡 Medium    | ISO 20022 Compliance| Cannot confirm beneficiary before execution                  |
| 12| No debtor name in payloads                   | 🟡 Medium    | ISO 20022 Compliance| Cannot display originator in statements                      |
| 13| No remittance information                    | 🟡 Medium    | ISO 20022 Compliance| No payment description for reconciliation                    |
| 14| No scheduled/future-dated payments           | 🟡 Medium    | ISO 20022 Compliance| Only immediate execution supported                           |
| 15| No self-transfer prevention                  | 🟡 Medium    | Validation          | Wastes ledger entries; potential abuse vector                 |
| 16| Status never set to FAILED                   | 🟡 Medium    | Transaction Integrity| No visibility into failed transactions                      |
| 17| No BIC/SWIFT agent identifiers               | 🟡 Medium    | ISO 20022 Compliance| Blocks interbank integration                                 |
| 18| No concurrent access protection (core)       | 🟡 Medium    | Transaction Integrity| Race conditions can corrupt account balances                |
| 19| No category/purpose codes                    | 🟢 Low       | ISO 20022 Compliance| Blocks regulatory reporting (AML/CFT)                        |
| 20| No charge bearer field                       | 🟢 Low       | ISO 20022 Compliance| Not needed for single-institution today                      |
| 21| No batch payment support                     | 🟢 Low       | ISO 20022 Compliance| Only single-transaction requests                             |

---

### Recommended Priority Actions

1. **Immediate (P0):** Fix the double-deduction bug in `TransactionService.internalFundTransfer()` and `utilPayment()`.
2. **Immediate (P0):** Add Bean Validation with `@NotNull`, `@Positive`, `@Size` constraints on all payment DTOs; add `@Valid` to controllers.
3. **Short-term (P1):** Introduce a `currency` field (ISO 4217) to all payment requests, entities, and database columns.
4. **Short-term (P1):** Implement client-provided idempotency keys with server-side unique constraint checking.
5. **Short-term (P1):** Add compensation/retry logic or a saga pattern for Feign call failures; implement FAILED status transitions.
6. **Medium-term (P2):** Add account status and ownership validation before processing payments.
7. **Medium-term (P2):** Expand payload to include debtor/creditor names, remittance info, and message identifiers.
8. **Long-term (P3):** Add BIC/SWIFT identifiers, scheduled payments, category purpose codes for full ISO 20022 alignment.
