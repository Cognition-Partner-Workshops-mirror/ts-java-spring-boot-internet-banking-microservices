# Payment Payload Gap Analysis

> **Scope**: Fund Transfer and Utility Payment services in the Internet Banking Microservices platform
> **Benchmark**: ISO 20022 payment message standards (pain.001, pain.002, pacs.008)
> **Date**: May 2026

---

## Table of Contents

1. [Current Payment Payload Structures](#1-current-payment-payload-structures)
2. [ISO 20022 Mapping Analysis](#2-iso-20022-mapping-analysis)
3. [Payment Validation Assessment](#3-payment-validation-assessment)
4. [Gap Inventory](#4-gap-inventory)
5. [Risk Summary](#5-risk-summary)

---

## 1. Current Payment Payload Structures

### 1.1 Fund Transfer

#### Request (`FundTransferRequest`)

| Field         | Type        | Location                         | Description                        |
|---------------|-------------|----------------------------------|------------------------------------|
| `fromAccount` | `String`    | fund-transfer-service + core     | Source bank account number         |
| `toAccount`   | `String`    | fund-transfer-service + core     | Destination bank account number    |
| `amount`      | `BigDecimal`| fund-transfer-service + core     | Transfer amount (no currency)      |
| `authID`      | `String`    | fund-transfer-service only       | Authorization identifier (unused in core) |

> **Note**: The `authID` field exists in the fund-transfer-service's `FundTransferRequest` but is **absent** from the core-banking-service's version of `FundTransferRequest`. It is never validated or used in any business logic.

#### Response (`FundTransferResponse`)

| Field           | Type     | Location                         | Description                    |
|-----------------|----------|----------------------------------|--------------------------------|
| `message`       | `String` | fund-transfer-service + core     | Human-readable status message  |
| `transactionId` | `String` | fund-transfer-service + core     | UUID-based transaction identifier |

#### Persisted Entity (`FundTransferEntity`)

| Column                 | Type                | Description                          |
|------------------------|---------------------|--------------------------------------|
| `id`                   | `Long` (auto)       | Internal surrogate key               |
| `transactionReference` | `String`            | UUID returned by core-banking        |
| `fromAccount`          | `String`            | Source account number                 |
| `toAccount`            | `String`            | Destination account number            |
| `amount`               | `BigDecimal`        | Transfer amount                       |
| `status`               | `TransactionStatus` | PENDING -> SUCCESS (no FAILED path)   |
| `createdDate`          | `Instant`           | Audit timestamp (via AuditAware)      |
| `modifiedDate`         | `Instant`           | Audit timestamp (via AuditAware)      |
| `version`              | `long`              | Optimistic locking version            |

#### Core Banking Transaction Record (`TransactionEntity`)

| Column            | Type              | Description                              |
|-------------------|-------------------|------------------------------------------|
| `id`              | `Long` (auto)     | Internal surrogate key                   |
| `amount`          | `BigDecimal`      | Signed amount (negative for debit)       |
| `transactionType` | `TransactionType`  | FUND_TRANSFER or UTILITY_PAYMENT        |
| `referenceNumber` | `String`          | Counter-party account number             |
| `transactionId`   | `String`          | UUID linking paired debit/credit entries |
| `account`         | `BankAccountEntity`| FK to the affected bank account         |

---

### 1.2 Utility Payment

#### Request (`UtilityPaymentRequest`)

| Field             | Type         | Location                          | Description                        |
|-------------------|--------------|-----------------------------------|------------------------------------|
| `providerId`      | `Long`       | utility-payment-service + core    | Utility provider identifier        |
| `amount`          | `BigDecimal` | utility-payment-service + core    | Payment amount (no currency)       |
| `referenceNumber` | `String`     | utility-payment-service + core    | Consumer/bill reference number     |
| `account`         | `String`     | utility-payment-service + core    | Payer's bank account number        |

#### Response (`UtilityPaymentResponse`)

| Field           | Type     | Location                          | Description                    |
|-----------------|----------|-----------------------------------|--------------------------------|
| `message`       | `String` | utility-payment-service + core    | Human-readable status message  |
| `transactionId` | `String` | utility-payment-service + core    | UUID-based transaction identifier |

#### Persisted Entity (`UtilityPaymentEntity`)

| Column            | Type                | Description                             |
|-------------------|---------------------|-----------------------------------------|
| `id`              | `Long` (auto)       | Internal surrogate key                  |
| `providerId`      | `Long`              | Utility provider FK                     |
| `amount`          | `BigDecimal`        | Payment amount                          |
| `referenceNumber` | `String`            | Consumer/bill reference                 |
| `account`         | `String`            | Payer's bank account number             |
| `transactionId`   | `String`            | UUID returned by core-banking           |
| `status`          | `TransactionStatus` | PROCESSING -> SUCCESS (no FAILED path)  |
| `createdDate`     | `Instant`           | Audit timestamp (via AuditAware)        |
| `modifiedDate`    | `Instant`           | Audit timestamp (via AuditAware)        |
| `version`         | `long`              | Optimistic locking version              |

---

## 2. ISO 20022 Mapping Analysis

### 2.1 pain.001 — Customer Credit Transfer Initiation

This is the standard for initiating payment instructions from a customer to a bank.

| ISO 20022 Field (pain.001)              | Current Field          | Status            | Notes                                                                 |
|-----------------------------------------|------------------------|-------------------|-----------------------------------------------------------------------|
| `MsgId` (Message Identification)        | —                      | **MISSING**       | No message-level identifier; only transaction-level UUID exists       |
| `CreDtTm` (Creation Date Time)          | `createdDate`          | PARTIAL           | Exists in AuditAware but not in the API payload                       |
| `NbOfTxs` (Number of Transactions)      | —                      | **MISSING**       | No batch/grouping support; single transaction per request             |
| `CtrlSum` (Control Sum)                 | —                      | **MISSING**       | No batch control sum                                                  |
| `InitgPty` (Initiating Party)           | —                      | **MISSING**       | No initiating party info; `authID` field exists but is unused         |
| `PmtInfId` (Payment Info Identification)| —                      | **MISSING**       | No payment information grouping                                       |
| `PmtMtd` (Payment Method)              | —                      | **MISSING**       | No payment method indicator (TRF, CHK, etc.)                          |
| `ReqdExctnDt` (Requested Execution Date)| —                      | **MISSING**       | All transfers execute immediately; no scheduling                      |
| `Dbtr` (Debtor)                         | `fromAccount` (partial)| **RENAMED/PARTIAL** | Only account number; no name, address, or BIC                       |
| `DbtrAcct` (Debtor Account)            | `fromAccount`          | **RENAMED**       | Present but named `fromAccount`; missing IBAN format, currency        |
| `DbtrAgt` (Debtor Agent / Bank)        | —                      | **MISSING**       | No BIC/bank identifier; internal-only transfers assumed               |
| `CdtrAgt` (Creditor Agent / Bank)      | —                      | **MISSING**       | No BIC/bank identifier for destination                                |
| `Cdtr` (Creditor)                       | `toAccount` (partial)  | **RENAMED/PARTIAL** | Only account number; no name, address, or BIC                       |
| `CdtrAcct` (Creditor Account)          | `toAccount`            | **RENAMED**       | Present but named `toAccount`; missing IBAN format, currency          |
| `Amt/InstdAmt` (Instructed Amount)     | `amount`               | **PARTIAL**       | Amount exists but **no currency code** (ISO 4217)                     |
| `RmtInf` (Remittance Information)       | —                      | **MISSING**       | No remittance info (structured or unstructured)                       |
| `Purp` (Purpose)                        | —                      | **MISSING**       | No purpose code for the payment                                       |
| `EndToEndId` (End-to-End Identification)| `transactionId`        | **RENAMED**       | UUID generated server-side; not client-supplied                       |
| `InstrId` (Instruction Identification)  | —                      | **MISSING**       | No instruction-level ID                                               |
| `ChrgBr` (Charge Bearer)               | —                      | **MISSING**       | No charge bearer indicator (DEBT, CRED, SHAR, SLEV)                  |

### 2.2 pain.002 — Customer Payment Status Report

This is the standard for reporting the status of a payment back to the customer.

| ISO 20022 Field (pain.002)              | Current Field            | Status            | Notes                                                              |
|-----------------------------------------|--------------------------|-------------------|--------------------------------------------------------------------|
| `OrgnlMsgId` (Original Message ID)     | —                        | **MISSING**       | No message correlation                                              |
| `OrgnlMsgNmId` (Original Message Name) | —                        | **MISSING**       | No message type tracking                                            |
| `GrpSts` (Group Status)                | —                        | **MISSING**       | No group-level status                                               |
| `TxInfAndSts` (Transaction Status)     | `status` (partial)       | **PARTIAL**       | Only PENDING/SUCCESS; no REJECTED, ACCEPTED, etc.                   |
| `StsRsnInf` (Status Reason Info)       | `message` (partial)      | **PARTIAL**       | Free-text message only; no structured reason codes (ISO 20022 reason codes) |
| `OrgnlEndToEndId`                       | `transactionId`          | **RENAMED**       | Present but named differently                                       |
| `AccptncDtTm` (Acceptance DateTime)    | `modifiedDate` (partial) | **PARTIAL**       | Exists in entity but not in API response                            |
| `InstgAgt` (Instructing Agent)         | —                        | **MISSING**       | No agent identification                                              |
| `OrgnlTxRef` (Original Tx Reference)   | —                        | **MISSING**       | No reference back to original payment details                        |

### 2.3 pacs.008 — FI to FI Customer Credit Transfer

This is the interbank settlement message standard.

| ISO 20022 Field (pacs.008)               | Current Field          | Status          | Notes                                                              |
|------------------------------------------|------------------------|-----------------|--------------------------------------------------------------------|
| `GrpHdr/MsgId`                           | —                      | **MISSING**     | No interbank message ID                                             |
| `GrpHdr/CreDtTm`                         | —                      | **MISSING**     | No creation timestamp in message header                             |
| `GrpHdr/NbOfTxs`                         | —                      | **MISSING**     | Single-transaction only                                              |
| `GrpHdr/SttlmInf` (Settlement Info)     | —                      | **MISSING**     | No settlement method or clearing system info                         |
| `GrpHdr/InstgAgt` (Instructing Agent)   | —                      | **MISSING**     | No BIC for the sending bank                                         |
| `GrpHdr/InstdAgt` (Instructed Agent)    | —                      | **MISSING**     | No BIC for the receiving bank                                       |
| `CdtTrfTxInf/PmtId/InstrId`             | —                      | **MISSING**     | No instruction ID                                                    |
| `CdtTrfTxInf/PmtId/EndToEndId`          | `transactionId`        | **RENAMED**     | UUID generated internally                                            |
| `CdtTrfTxInf/PmtId/TxId`               | `transactionId`        | **RENAMED**     | Same UUID used; no separate transaction ID                           |
| `CdtTrfTxInf/IntrBkSttlmAmt`           | `amount`               | **PARTIAL**     | Amount exists but no currency attribute                              |
| `CdtTrfTxInf/IntrBkSttlmDt`            | —                      | **MISSING**     | No settlement date; immediate execution only                         |
| `CdtTrfTxInf/ChrgBr`                    | —                      | **MISSING**     | No charge bearer                                                     |
| `CdtTrfTxInf/Dbtr`                      | `fromAccount`          | **PARTIAL**     | Account number only; no party name/address/ID                        |
| `CdtTrfTxInf/DbtrAcct/Id/IBAN`         | `fromAccount`          | **RENAMED**     | Plain account number, not IBAN format                                |
| `CdtTrfTxInf/DbtrAgt`                   | —                      | **MISSING**     | No debtor agent BIC                                                  |
| `CdtTrfTxInf/CdtrAgt`                   | —                      | **MISSING**     | No creditor agent BIC                                                |
| `CdtTrfTxInf/Cdtr`                      | `toAccount`            | **PARTIAL**     | Account number only; no party name/address/ID                        |
| `CdtTrfTxInf/CdtrAcct/Id/IBAN`         | `toAccount`            | **RENAMED**     | Plain account number, not IBAN format                                |
| `CdtTrfTxInf/RmtInf`                    | —                      | **MISSING**     | No remittance information                                            |
| `CdtTrfTxInf/RgltryRptg`               | —                      | **MISSING**     | No regulatory reporting fields                                       |

### 2.4 Extra Fields (Not in ISO 20022)

| Current Field   | Service                 | ISO 20022 Equivalent | Assessment                                            |
|-----------------|-------------------------|----------------------|-------------------------------------------------------|
| `authID`        | fund-transfer-service   | None directly        | Appears to be an authorization token; unused in logic. Could map to `InitgPty` or be removed. |
| `providerId`    | utility-payment-service | `CdtrAcct` (partial) | Utility provider identifier; proprietary field not in ISO 20022 credit transfer messages. |

---

## 3. Payment Validation Assessment

### 3.1 Amount Validation

| Rule                         | Implemented? | Details                                                                                      | Risk    |
|------------------------------|-------------|----------------------------------------------------------------------------------------------|---------|
| Amount > 0                   | **NO**      | No check for zero or negative amounts. `BigDecimal` accepts any value.                       | Critical |
| Maximum transfer limit       | **NO**      | No per-transaction or daily aggregate limits enforced.                                        | Critical |
| Minimum transfer amount      | **NO**      | No minimum threshold.                                                                         | Low     |
| Decimal precision check      | **NO**      | No validation that amount has at most 2 decimal places (for standard currencies).             | Medium  |
| Balance sufficiency           | **YES**     | `validateBalance()` checks `actualBalance >= amount && actualBalance >= 0` in `TransactionService`. | — |
| Double-deduction bug          | **YES (BUG)** | `availableBalance` is set from already-debited `actualBalance`, causing double subtraction. See `TransactionService.internalFundTransfer()` line 91 and `utilPayment()` line 64. | Critical |

### 3.2 Currency Handling

| Rule                              | Implemented? | Details                                                           | Risk     |
|-----------------------------------|-------------|-------------------------------------------------------------------|----------|
| Currency code in request          | **NO**      | No `currency` field in any payment request DTO.                   | Critical |
| Multi-currency support            | **NO**      | No currency on `BankAccountEntity`; implicit single-currency.     | High     |
| Currency mismatch validation      | **NO**      | Cannot validate since currency is not tracked.                    | High     |
| Exchange rate handling            | **NO**      | No FX conversion logic.                                           | Medium   |

### 3.3 Duplicate Detection & Idempotency

| Rule                              | Implemented? | Details                                                           | Risk     |
|-----------------------------------|-------------|-------------------------------------------------------------------|----------|
| Idempotency key in request        | **NO**      | No client-supplied idempotency key. Every POST creates a new record. | Critical |
| Duplicate transaction detection   | **NO**      | No check for identical from/to/amount within a time window.       | High     |
| Transaction ID uniqueness         | **PARTIAL** | `UUID.randomUUID()` is statistically unique but not enforced at DB level (no unique constraint on `transactionId`). | Medium |

### 3.4 Account Validation

| Rule                                   | Implemented? | Details                                                        | Risk     |
|----------------------------------------|-------------|----------------------------------------------------------------|----------|
| Account existence check                | **YES**     | `AccountService.readBankAccount()` throws `EntityNotFoundException` if not found. | — |
| Account status check (active/frozen)   | **NO**      | `AccountStatus` enum exists (values unknown) but is never checked in transfer logic. | High |
| Self-transfer prevention               | **NO**      | No check that `fromAccount != toAccount`.                      | Medium   |
| Account ownership verification         | **NO**      | No verification that the requesting user owns the source account. | Critical |

### 3.5 Input Validation

| Rule                                | Implemented? | Details                                                          | Risk     |
|-------------------------------------|-------------|------------------------------------------------------------------|----------|
| `@Valid` / Bean Validation          | **NO**      | No `@Valid` annotation on any `@RequestBody` parameter.          | Critical |
| Null field checks                   | **NO**      | `fromAccount`, `toAccount`, `amount` can all be `null`, causing NPE. | Critical |
| Account number format validation    | **NO**      | No regex or length check on account numbers.                     | High     |
| SQL injection via String fields     | **PARTIAL** | JPA parameterized queries mitigate SQL injection, but no input sanitization. | Low |

### 3.6 Transaction Integrity

| Rule                                     | Implemented? | Details                                                       | Risk     |
|------------------------------------------|-------------|---------------------------------------------------------------|----------|
| Atomic debit/credit                       | **PARTIAL** | `@Transactional` on `TransactionService` covers the core-banking DB operations, but the fund-transfer-service's entity update is in a separate transaction. | High |
| Compensation / rollback on failure        | **NO**      | If the Feign call to core-banking fails, the fund-transfer entity remains PENDING forever. No retry, no compensation saga. | Critical |
| Status never set to FAILED                | **YES (BUG)** | `TransactionStatus.FAILED` exists in the enum but is never assigned anywhere. Failed transfers stay PENDING indefinitely. | Critical |
| Concurrent modification protection       | **PARTIAL** | `@Version` on AuditAware provides optimistic locking for the orchestrating services, but `BankAccountEntity` has no versioning — concurrent transfers can cause lost updates. | High |

---

## 4. Gap Inventory

### Summary Table

| # | Gap                                          | ISO 20022 Ref      | Severity     | Business Risk                                                      |
|---|----------------------------------------------|---------------------|--------------|--------------------------------------------------------------------|
| 1 | No currency code in payment payloads         | pain.001 `InstdAmt/@Ccy` | **Critical** | Cannot operate in multi-currency environments; regulatory non-compliance |
| 2 | No idempotency key                           | pain.001 `EndToEndId` | **Critical** | Duplicate payments on network retries or user double-clicks        |
| 3 | No input validation (`@Valid`)               | —                   | **Critical** | Null/negative amounts cause server errors; potential data corruption |
| 4 | Double-deduction bug in balance calculation  | —                   | **Critical** | Customers lose double the intended amount on every transaction      |
| 5 | No compensation/rollback on Feign failure    | —                   | **Critical** | Orphaned PENDING/PROCESSING records; money debited but not tracked |
| 6 | No account ownership verification            | —                   | **Critical** | Any authenticated user can transfer from any account               |
| 7 | ~~FAILED status never assigned~~  **(RESOLVED)** | pain.002 `TxSts`    | ~~Critical~~ **Resolved** | Fixed: both `FundTransferService` and `UtilityPaymentService` now catch exceptions and set `TransactionStatus.FAILED` before re-throwing |
| 8 | No debtor/creditor party information         | pain.001 `Dbtr`/`Cdtr` | **High** | Cannot identify parties for AML/KYC compliance                     |
| 9 | No remittance information                    | pain.001 `RmtInf`   | **High**    | No payment description; poor reconciliation                        |
| 10| No account status validation                 | —                   | **High**    | Frozen/closed accounts can send or receive funds                   |
| 11| No transfer limits (per-txn or daily)        | —                   | **High**    | Unlimited transfer amounts; fraud exposure                          |
| 12| No message-level identification              | pain.001 `MsgId`    | **High**    | Cannot trace or reconcile payment batches                           |
| 13| No BIC/bank agent identification             | pacs.008 `DbtrAgt`  | **High**    | Cannot support interbank transfers or SWIFT messaging               |
| 14| No self-transfer prevention                  | —                   | **Medium**  | Allows meaningless same-account transfers                           |
| 15| No settlement date support                   | pacs.008 `IntrBkSttlmDt` | **Medium** | All payments are immediate; no future-dated payments              |
| 16| No payment method indicator                  | pain.001 `PmtMtd`   | **Medium**  | Cannot distinguish transfer types (wire, ACH, book transfer)       |
| 17| No charge bearer indicator                   | pain.001 `ChrgBr`   | **Medium**  | Cannot handle fee allocation for cross-border payments              |
| 18| No purpose code                              | pain.001 `Purp`     | **Medium**  | Reduced reporting and categorization capabilities                   |
| 19| No regulatory reporting fields               | pacs.008 `RgltryRptg` | **Medium** | Cannot satisfy regulatory reporting requirements                   |
| 20| No structured status reason codes            | pain.002 `StsRsnInf` | **Medium** | Free-text messages; not machine-parseable                          |
| 21| No decimal precision enforcement             | —                   | **Medium**  | Fractional cent amounts could be stored                             |
| 22| No `transactionId` unique constraint in DB   | —                   | **Medium**  | Theoretical (but unlikely) UUID collision undetected                |
| 23| `authID` field unused                        | —                   | **Low**     | Dead code; confusing API surface                                    |
| 24| No batch/group transaction support           | pain.001 `NbOfTxs`  | **Low**     | Single-transaction only; no bulk payment files                      |

---

## 5. Risk Summary

### Critical Gaps (Immediate Action Required)

These gaps represent active financial risk or data integrity issues:

1. **Double-deduction bug** — Every fund transfer and utility payment deducts the amount twice from `availableBalance`. This is an active production defect.
2. **No input validation** — Null or negative amounts can corrupt data and cause unhandled exceptions.
3. **No idempotency** — Network retries or UI double-submits will create duplicate financial transactions.
4. **No compensation on failure** — Feign call failures leave transactions in limbo; money may be debited in core-banking but the orchestrating service never records success.
5. **No account ownership check** — Any authenticated user can initiate transfers from any account, regardless of ownership.
6. **No currency support** — The system cannot correctly operate in any jurisdiction requiring multi-currency or explicit currency identification.
7. ~~**FAILED status never used**~~ **(RESOLVED)** — Both `FundTransferService` and `UtilityPaymentService` now wrap the Feign call in a try/catch and assign `TransactionStatus.FAILED` on any exception, preventing orphaned PENDING/PROCESSING records.

### High Gaps (Short-term Priority)

These gaps block regulatory compliance and operational maturity:

- No party identification (AML/KYC risk)
- No remittance information (reconciliation risk)
- No account status checks (frozen accounts can transact)
- No transfer limits (fraud exposure)
- No message-level IDs (traceability risk)

### Medium/Low Gaps (Roadmap Items)

These gaps affect ISO 20022 compliance completeness but are not immediate operational risks:

- Settlement dates, purpose codes, charge bearers, regulatory reporting, batch support
