# Payment Payload Gap Analysis

## 1. Executive Summary

This document analyses the current payment payload structures in the Internet Banking microservices application and compares them against **ISO 20022** payment message standards. The application supports two payment flows — **Fund Transfer** (account-to-account) and **Utility Payment** (bill payment to a utility provider). Both flows exhibit significant gaps when measured against ISO 20022 messages `pain.001` (Customer Credit Transfer Initiation), `pain.002` (Customer Payment Status Report), and `pacs.008` (FI-to-FI Customer Credit Transfer).

**Overall finding:** The payloads are minimal proof-of-concept structures carrying only the bare minimum fields required for a happy-path demo. They lack the identification, compliance, audit, currency, date/time, and error-handling fields mandated by ISO 20022 and expected by real-world payment systems.

---

## 2. Current Payment Payload Structures

### 2.1 Fund Transfer — Request

| Field | Type | Source File |
|-------|------|-------------|
| `fromAccount` | `String` | `internet-banking-fund-transfer-service/.../FundTransferRequest.java` |
| `toAccount` | `String` | (same) |
| `amount` | `BigDecimal` | (same) |
| `authID` | `String` | (same — present in fund-transfer service only, absent in core-banking copy) |

### 2.2 Fund Transfer — Response

| Field | Type | Source File |
|-------|------|-------------|
| `message` | `String` | `core-banking-service/.../FundTransferResponse.java` |
| `transactionId` | `String` | (same) |

### 2.3 Utility Payment — Request

| Field | Type | Source File |
|-------|------|-------------|
| `providerId` | `Long` | `internet-banking-utility-payment-service/.../UtilityPaymentRequest.java` |
| `amount` | `BigDecimal` | (same) |
| `referenceNumber` | `String` | (same) |
| `account` | `String` | (same) |

### 2.4 Utility Payment — Response

| Field | Type | Source File |
|-------|------|-------------|
| `message` | `String` | `internet-banking-utility-payment-service/.../UtilityPaymentResponse.java` |
| `transactionId` | `String` | (same) |

> **Note:** The fund-transfer service also carries its own copy of `UtilityPaymentRequest` / `UtilityPaymentResponse` DTOs but they are unused in that service — an artifact of shared code structure.

### 2.5 Persistence Entities

**FundTransferEntity** stores: `id`, `transactionReference`, `fromAccount`, `toAccount`, `amount`, `status` (enum: PENDING, PROCESSING, SUCCESS, FAILED), plus audit fields (`createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, `version`).

**UtilityPaymentEntity** stores: `id`, `providerId`, `amount`, `referenceNumber`, `account`, `transactionId`, `status`, plus audit fields.

**TransactionEntity** (core-banking): `id`, `amount`, `transactionType` (FUND_TRANSFER, UTILITY_PAYMENT), `referenceNumber`, `transactionId`, `account` (FK to BankAccountEntity).

---

## 3. ISO 20022 Comparison

### 3.1 pain.001 — Customer Credit Transfer Initiation

This message is sent by the debtor (initiating party) to instruct a credit transfer.

| ISO 20022 Field | Status | Current Mapping | Gap Detail |
|-----------------|--------|----------------|------------|
| `MsgId` (Message Identification) | **MISSING** | — | No message-level identifier. There is no way to trace or deduplicate an incoming request |
| `CreDtTm` (Creation Date Time) | **MISSING** | — | No request timestamp. Audit fields exist on persisted entities but not on the API payload |
| `NbOfTxs` (Number of Transactions) | **MISSING** | — | System processes single transactions; no batch concept |
| `CtrlSum` (Control Sum) | **MISSING** | — | No checksum for batch integrity |
| `InitgPty` (Initiating Party) | **MISSING** | `authID` field exists in fund-transfer request but is not validated or forwarded | Initiating party identification (name, ID, address) is absent |
| `PmtInfId` (Payment Information ID) | **MISSING** | — | No payment information grouping identifier |
| `PmtMtd` (Payment Method) | **MISSING** | — | Always implied as credit transfer; not explicit |
| `ReqdExctnDt` (Requested Execution Date) | **MISSING** | — | No scheduled/future-dated payment support |
| `Dbtr` (Debtor) | **PARTIAL** | `fromAccount` (string) | Only account number; no debtor name, address, or identification |
| `DbtrAcct` (Debtor Account) | **PARTIAL** | `fromAccount` (string) | No IBAN, BIC, currency, or account type |
| `DbtrAgt` (Debtor Agent / Bank) | **MISSING** | — | No BIC or bank identification |
| `CdtrAgt` (Creditor Agent / Bank) | **MISSING** | — | No receiving bank identification |
| `Cdtr` (Creditor) | **PARTIAL** | `toAccount` (string) | Only account number; no creditor name or identification |
| `CdtrAcct` (Creditor Account) | **PARTIAL** | `toAccount` (string) | No IBAN, BIC, or currency |
| `Amt/InstdAmt` (Instructed Amount) | **PARTIAL** | `amount` (BigDecimal) | Amount present but **no currency code** (ISO 4217). System is single-currency by assumption |
| `RmtInf` (Remittance Information) | **MISSING** | — | No payment reference, invoice number, or free-text description |
| `Purp` (Purpose) | **MISSING** | — | No purpose code (e.g. SALA, SUPP) |
| `ChrgBr` (Charge Bearer) | **MISSING** | — | No indication of who bears charges |
| `EndToEndId` (End-to-End Identification) | **MISSING** | — | Critical for payment tracing; completely absent from request |
| `InstrId` (Instruction Identification) | **MISSING** | — | No point-to-point instruction ID |
| `RgltryRptg` (Regulatory Reporting) | **MISSING** | — | No AML/CFT or regulatory reporting fields |

### 3.2 pain.002 — Customer Payment Status Report

This message reports the status of a previously submitted payment instruction.

| ISO 20022 Field | Status | Current Mapping | Gap Detail |
|-----------------|--------|----------------|------------|
| `OrgnlMsgId` (Original Message ID) | **MISSING** | — | No reference back to original request |
| `OrgnlMsgNmId` (Original Message Name) | **MISSING** | — | No message type identification |
| `GrpSts` (Group Status) | **MISSING** | — | No group-level status |
| `TxInfAndSts/TxSts` (Transaction Status) | **PARTIAL** | `message` (free text) | Status is a human-readable string, not a codified status (ACCP, ACSP, RJCT, etc.) |
| `TxInfAndSts/OrgnlEndToEndId` | **MISSING** | — | No original end-to-end ID echo |
| `TxInfAndSts/OrgnlTxId` | **PARTIAL** | `transactionId` (UUID) | Present but format does not follow ISO standards |
| `StsRsnInf` (Status Reason Information) | **MISSING** | — | No structured reason codes for rejections |
| `OrgnlTxRef` (Original Transaction Reference) | **MISSING** | — | No echo of original payment details |
| `AccptncDtTm` (Acceptance Date Time) | **MISSING** | — | No timestamp of when payment was accepted |

### 3.3 pacs.008 — FI-to-FI Customer Credit Transfer

This inter-bank message is relevant because the core-banking service processes the actual transfer.

| ISO 20022 Field | Status | Current Mapping | Gap Detail |
|-----------------|--------|----------------|------------|
| `GrpHdr/MsgId` | **MISSING** | — | No inter-service message identification |
| `GrpHdr/CreDtTm` | **MISSING** | — | No creation timestamp on inter-service calls |
| `GrpHdr/SttlmInf` (Settlement Information) | **MISSING** | — | No settlement method or clearing system info |
| `GrpHdr/InstgAgt` (Instructing Agent) | **MISSING** | — | Fund-transfer service does not identify itself |
| `GrpHdr/InstdAgt` (Instructed Agent) | **MISSING** | — | Core-banking service not identified |
| `CdtTrfTxInf/PmtId/InstrId` | **MISSING** | — | No instruction ID |
| `CdtTrfTxInf/PmtId/EndToEndId` | **MISSING** | — | No end-to-end tracing |
| `CdtTrfTxInf/PmtId/TxId` | **PARTIAL** | UUID generated server-side | Present but not provided by initiator |
| `CdtTrfTxInf/IntrBkSttlmAmt` | **PARTIAL** | `amount` | No currency attribute |
| `CdtTrfTxInf/IntrBkSttlmDt` | **MISSING** | — | No settlement date |
| `CdtTrfTxInf/ChrgBr` | **MISSING** | — | No charge bearer |
| `CdtTrfTxInf/Dbtr` | **PARTIAL** | Account number only | No name, address, identification |
| `CdtTrfTxInf/Cdtr` | **PARTIAL** | Account number only | No name, address, identification |

---

## 4. Payment Validation Rules Assessment

### 4.1 Amount Validation

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Positive amount check | **NO** | No validation that `amount > 0`. Negative or zero amounts would be processed |
| Maximum amount limit | **NO** | No per-transaction or daily limits enforced |
| Minimum amount limit | **NO** | No minimum threshold |
| Sufficient funds check | **YES** | `TransactionService.validateBalance()` checks `actualBalance >= amount` and `actualBalance >= 0` |
| Decimal precision validation | **NO** | No check on decimal places (e.g. 2 decimal places for USD) |

### 4.2 Currency Handling

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Currency code on request | **NO** | No currency field exists anywhere in the payload |
| Cross-currency transfers | **NO** | Not supported; no FX rate or target currency |
| Currency mismatch detection | **NO** | Accounts lack currency fields — `BankAccountEntity` has no currency column |

### 4.3 Duplicate Detection

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Request deduplication | **NO** | No message ID or idempotency key on requests |
| Idempotency header | **NO** | No `Idempotency-Key` header support |
| Duplicate transaction detection | **NO** | Same transfer can be submitted multiple times, creating duplicate records each time |

### 4.4 Idempotency

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Idempotency key support | **NO** | Not implemented |
| Retry safety | **NO** | The fund transfer flow: (1) saves PENDING entity, (2) calls core-banking via Feign, (3) updates to SUCCESS. If step 3 fails after step 2 succeeds, the transfer is completed but marked as PENDING — no retry/recovery mechanism |
| Transaction atomicity | **PARTIAL** | Core-banking `TransactionService` is `@Transactional`, but the fund-transfer service orchestration is **not** transactional across the Feign call boundary |

### 4.5 Account Validation

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Account existence check | **YES** | `AccountService.readBankAccount()` throws `EntityNotFoundException` if account not found |
| Account status check | **NO** | No validation that accounts are ACTIVE (vs. DORMANT, BLOCKED, PENDING) |
| Self-transfer prevention | **NO** | No check that `fromAccount != toAccount` |
| Account ownership verification | **NO** | No check that the authenticated user owns the debtor account |

### 4.6 Input Validation

| Check | Implemented? | Detail |
|-------|-------------|--------|
| Bean Validation annotations | **NO** | No `@NotNull`, `@NotBlank`, `@Min`, `@Size` annotations on any DTO |
| Request body null checks | **NO** | No null-safety checks in service layer |
| SQL injection protection | **YES** | JPA parameterized queries provide implicit protection |

---

## 5. Gap Severity & Business Risk Rating

| # | Gap | Severity | Business Risk | Notes |
|---|-----|----------|--------------|-------|
| 1 | No currency field | **CRITICAL** | **HIGH** | Cannot operate in multi-currency environments; ambiguous amounts |
| 2 | No idempotency / duplicate detection | **CRITICAL** | **HIGH** | Duplicate payments can occur on retries; financial loss |
| 3 | No input validation annotations | **CRITICAL** | **HIGH** | Null/negative amounts can corrupt data and balances |
| 4 | No amount limits | **HIGH** | **HIGH** | Unlimited transfer amounts; fraud risk |
| 5 | No account status validation | **HIGH** | **HIGH** | Transfers from/to blocked or dormant accounts |
| 6 | No account ownership check | **HIGH** | **HIGH** | Any authenticated user can transfer from any account |
| 7 | No end-to-end tracing ID | **HIGH** | **MEDIUM** | Cannot trace payments across service boundaries |
| 8 | Distributed transaction inconsistency | **HIGH** | **HIGH** | Fund-transfer service and core-banking can become out of sync |
| 9 | No remittance information | **MEDIUM** | **MEDIUM** | Cannot attach payment references or descriptions |
| 10 | No debtor/creditor identification | **MEDIUM** | **MEDIUM** | ISO 20022 requires party names and IDs |
| 11 | No regulatory reporting fields | **MEDIUM** | **HIGH** | AML/CFT compliance impossible |
| 12 | No timestamp on requests | **MEDIUM** | **MEDIUM** | Cannot detect stale or replayed requests |
| 13 | No settlement date / execution date | **MEDIUM** | **LOW** | Cannot support future-dated payments |
| 14 | No payment purpose code | **LOW** | **LOW** | Statistical and regulatory use only |
| 15 | No batch payment support | **LOW** | **LOW** | Single-transaction processing only |
| 16 | No charge bearer field | **LOW** | **LOW** | Relevant for cross-border only |
| 17 | Response uses free-text status | **MEDIUM** | **MEDIUM** | Should use codified status values per pain.002 |
| 18 | `UtilityPaymentResponse` empty in fund-transfer service | **LOW** | **LOW** | Dead code / DTO mismatch |

---

## 6. Recommendations Summary

1. **Immediate (P0):** Add `@Valid` / Bean Validation constraints on all DTOs. Add currency code field. Implement idempotency keys.
2. **Short-term (P1):** Add account status checks, ownership verification, amount limits, and self-transfer prevention. Implement end-to-end tracing with correlation IDs.
3. **Medium-term (P2):** Adopt ISO 20022–aligned payload structures with proper party identification, remittance information, and codified status responses. Add a saga/outbox pattern for distributed transaction consistency.
4. **Long-term (P3):** Add regulatory reporting fields, batch payment support, scheduled payments, and full ISO 20022 message compliance.
