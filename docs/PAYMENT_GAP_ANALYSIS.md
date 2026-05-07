# Payment Payload Gap Analysis

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Scope:** Fund Transfer Service, Utility Payment Service, Core Banking Service (transaction processing layer)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Payment Payload Structures](#2-current-payment-payload-structures)
3. [ISO 20022 Mapping & Gap Analysis](#3-iso-20022-mapping--gap-analysis)
4. [Payment Validation Rules Assessment](#4-payment-validation-rules-assessment)
5. [Gap Severity & Business Risk Matrix](#5-gap-severity--business-risk-matrix)
6. [Recommendations](#6-recommendations)

---

## 1. Executive Summary

This analysis evaluates the payment message structures used in the internet banking microservices against ISO 20022 payment message standards. The application currently implements two payment flows — **fund transfers** (peer-to-peer between bank accounts) and **utility payments** (bank account to utility provider). Both flows use minimal payload structures that lack the majority of fields required by ISO 20022 standards (pain.001, pain.002, pacs.008), presenting significant gaps in regulatory compliance, interoperability, and operational risk management.

**Key Findings:**
- Payment payloads carry only 3-4 fields each; ISO 20022 equivalents require 20-40+ fields
- No currency field exists anywhere in the system — all amounts are implicitly single-currency
- Zero input validation annotations on any payment request DTO
- No duplicate detection or idempotency mechanism
- No transaction date/time, remittance information, or party identification
- Error handling on the fund-transfer service silently marks transactions as SUCCESS even if downstream fails partially

---

## 2. Current Payment Payload Structures

### 2.1 Fund Transfer Request

**Source:** `internet-banking-fund-transfer-service/.../dto/request/FundTransferRequest.java`

| Field | Type | Description |
|-------|------|-------------|
| `fromAccount` | `String` | Source account number |
| `toAccount` | `String` | Destination account number |
| `amount` | `BigDecimal` | Transfer amount |
| `authID` | `String` | Authorization identifier (present only in fund-transfer-service, NOT in core-banking-service's equivalent DTO) |

**Note:** The core-banking-service defines its own `FundTransferRequest` with only 3 fields (`fromAccount`, `toAccount`, `amount`) — the `authID` field is dropped during the Feign call from the fund-transfer-service to core-banking-service.

### 2.2 Fund Transfer Response

**Source:** `internet-banking-fund-transfer-service/.../dto/response/FundTransferResponse.java`

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` | Human-readable status message |
| `transactionId` | `String` | UUID-based transaction identifier |

### 2.3 Utility Payment Request

**Source:** `internet-banking-utility-payment-service/.../rest/request/UtilityPaymentRequest.java`

| Field | Type | Description |
|-------|------|-------------|
| `providerId` | `Long` | Utility provider identifier (internal DB ID) |
| `amount` | `BigDecimal` | Payment amount |
| `referenceNumber` | `String` | Customer reference / bill number |
| `account` | `String` | Source bank account number |

### 2.4 Utility Payment Response

**Source:** `internet-banking-utility-payment-service/.../rest/response/UtilityPaymentResponse.java`

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` | Human-readable status message |
| `transactionId` | `String` | UUID-based transaction identifier |

**Note:** The fund-transfer-service also defines a `UtilityPaymentResponse` class that is **completely empty** (no fields), suggesting an incomplete or abandoned implementation path.

### 2.5 Core Banking Transaction Entity

**Source:** `core-banking-service/.../entity/TransactionEntity.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Auto-generated primary key |
| `amount` | `BigDecimal` | Transaction amount (negative for debits) |
| `transactionType` | `TransactionType` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `referenceNumber` | `String` | Counterparty account number or bill reference |
| `transactionId` | `String` | UUID correlation identifier |
| `account` | `BankAccountEntity` | Associated bank account (OneToOne) |

---

## 3. ISO 20022 Mapping & Gap Analysis

### 3.1 Fund Transfer vs. pain.001 (Customer Credit Transfer Initiation)

| ISO 20022 Field (pain.001) | XPath | Current Field | Status | Notes |
|---|---|---|---|---|
| **Message Identification** | `GrpHdr/MsgId` | — | MISSING | No message-level identifier; only transaction-level UUID exists |
| **Creation Date Time** | `GrpHdr/CreDtTm` | — | MISSING | No request timestamp captured |
| **Number of Transactions** | `GrpHdr/NbOfTxs` | — | MISSING | Single-transaction model; no batch support |
| **Control Sum** | `GrpHdr/CtrlSum` | — | MISSING | No checksum for batch integrity |
| **Initiating Party Name** | `GrpHdr/InitgPty/Nm` | — | MISSING | No payer identity beyond account number |
| **Initiating Party ID** | `GrpHdr/InitgPty/Id` | — | MISSING | `authID` exists but is dropped at core-banking boundary |
| **Payment Information ID** | `PmtInf/PmtInfId` | — | MISSING | No payment instruction grouping |
| **Payment Method** | `PmtInf/PmtMtd` | — | MISSING | Implicitly "TRF" (transfer) but not specified |
| **Requested Execution Date** | `PmtInf/ReqdExctnDt` | — | MISSING | All transfers execute immediately; no scheduling |
| **Debtor Name** | `PmtInf/Dbtr/Nm` | — | MISSING | Only account number used (`fromAccount`) |
| **Debtor Account IBAN** | `PmtInf/DbtrAcct/Id/IBAN` | `fromAccount` | PARTIAL | Account number present but not in IBAN format |
| **Debtor Agent BIC** | `PmtInf/DbtrAgt/FinInstnId/BIC` | — | MISSING | No bank/branch identification |
| **Creditor Name** | `CdtTrfTxInf/Cdtr/Nm` | — | MISSING | Only destination account number |
| **Creditor Account IBAN** | `CdtTrfTxInf/CdtrAcct/Id/IBAN` | `toAccount` | PARTIAL | Account number present but not in IBAN format |
| **Creditor Agent BIC** | `CdtTrfTxInf/CdtrAgt/FinInstnId/BIC` | — | MISSING | No receiving bank identification |
| **Amount + Currency** | `CdtTrfTxInf/Amt/InstdAmt` | `amount` | PARTIAL | Amount present but **no currency code** (e.g., `Ccy="USD"`) |
| **End-to-End ID** | `CdtTrfTxInf/PmtId/EndToEndId` | — | MISSING | Critical for reconciliation across systems |
| **Instruction ID** | `CdtTrfTxInf/PmtId/InstrId` | — | MISSING | No instruction-level identifier |
| **Transaction ID** | `CdtTrfTxInf/PmtId/TxId` | `transactionId` (response) | PARTIAL | Generated server-side only; not in request |
| **Remittance Information** | `CdtTrfTxInf/RmtInf/Ustrd` | — | MISSING | No payment description or memo field |
| **Purpose Code** | `CdtTrfTxInf/Purp/Cd` | — | MISSING | No classification of payment purpose |
| **Charge Bearer** | `CdtTrfTxInf/ChrgBr` | — | MISSING | No fee allocation model |
| **Regulatory Reporting** | `CdtTrfTxInf/RgltryRptg` | — | MISSING | No regulatory/compliance metadata |
| **Category Purpose** | `PmtInf/PmtTpInf/CtgyPurp` | — | MISSING | No high-level purpose classification |

**Coverage: ~3 of 23 key fields present (13%), 2 of those are partial matches.**

### 3.2 Fund Transfer Response vs. pain.002 (Payment Status Report)

| ISO 20022 Field (pain.002) | Current Field | Status | Notes |
|---|---|---|---|
| **Message Identification** | — | MISSING | Response has no message-level ID |
| **Original Message ID** | — | MISSING | No reference back to original request |
| **Original Message Type** | — | MISSING | No message type classification |
| **Group Status** | `message` | PARTIAL | Free-text string; not a standardized status code |
| **Status Reason Code** | — | MISSING | Errors return generic `ErrorResponse` with custom codes, not ISO status reasons |
| **Transaction Status** | `transactionId` | PARTIAL | UUID present but no structured status per ISO codes (ACCP, RJCT, etc.) |
| **Original End-to-End ID** | — | MISSING | Cannot be present since request lacks it |
| **Acceptance Date Time** | — | MISSING | No timestamp on response |
| **Original Transaction Reference** | — | MISSING | No echo-back of original payment details |
| **Status Reason Information** | — | MISSING | No structured rejection/error reason |

**Coverage: ~2 of 10 key fields present (20%), both partial matches.**

### 3.3 Utility Payment vs. pacs.008 (FI to FI Customer Credit Transfer)

| ISO 20022 Field (pacs.008) | Current Field | Status | Notes |
|---|---|---|---|
| **Message Identification** | — | MISSING | No message-level ID |
| **Creation Date Time** | — | MISSING | No timestamp |
| **Number of Transactions** | — | MISSING | Single-transaction model |
| **Settlement Method** | — | MISSING | No indication of clearing/settlement method |
| **Interbank Settlement Amount** | `amount` | PARTIAL | Amount present, no currency |
| **Interbank Settlement Date** | — | MISSING | No value date |
| **Charge Bearer** | — | MISSING | No fee model |
| **Debtor Name** | — | MISSING | Only account string |
| **Debtor Account** | `account` | PARTIAL | Account string, not structured ID |
| **Debtor Agent** | — | MISSING | No bank identification |
| **Creditor Name** | — | MISSING | Utility provider resolved by internal `providerId` |
| **Creditor Account** | — | MISSING | Utility account number not exposed in request/response |
| **Creditor Agent** | — | MISSING | No payment provider bank details |
| **Remittance Information** | `referenceNumber` | PARTIAL | Bill reference present but not in ISO structured format |
| **Payment Type Information** | — | MISSING | No service level, local instrument, or category purpose |
| **Instruction ID** | — | MISSING | No instruction-level identifier |
| **End-to-End ID** | — | MISSING | Critical for reconciliation |
| **Transaction ID** | `transactionId` (response) | PARTIAL | Server-generated, not in request |
| **Purpose Code** | — | MISSING | Could map `providerId` but currently uses internal DB ID |

**Coverage: ~4 of 19 key fields present (21%), all partial matches.**

---

## 4. Payment Validation Rules Assessment

### 4.1 Input Validation

| Validation Rule | Fund Transfer | Utility Payment | Assessment |
|---|---|---|---|
| **Jakarta Bean Validation annotations** | None | None | **CRITICAL** — No `@NotNull`, `@NotBlank`, `@Min`, `@Positive`, `@Size` on any DTO field |
| **`@Valid` on controller** | Not present | Not present | **CRITICAL** — Even if annotations existed, validation would not trigger |
| **Null amount check** | Not present | Not present | `BigDecimal` could be null, causing NPE in `compareTo()` |
| **Zero/Negative amount check** | Not present | Not present | A transfer of $0 or -$100 would be accepted |
| **Null account check** | Not present | Not present | `fromAccount` / `toAccount` could be null |
| **Same-account transfer** | Not checked | N/A | A user could transfer from account A to account A |
| **Account format validation** | Not present | Not present | Any string accepted as account number |
| **Account existence validation** | Delegated to core-banking | Delegated to core-banking | Done at persistence layer via `findByNumber`, throws `EntityNotFoundException` |
| **Account status validation** | Not present | Not present | Transfers allowed even on DORMANT or BLOCKED accounts |

### 4.2 Amount Limits

| Check | Status | Details |
|---|---|---|
| **Minimum transaction amount** | NOT ENFORCED | No lower bound; $0.00 or $0.001 transfers possible |
| **Maximum transaction amount** | NOT ENFORCED | No upper bound; unlimited transfer amounts |
| **Daily cumulative limit** | NOT ENFORCED | No per-user or per-account daily cap |
| **Per-transaction limit** | NOT ENFORCED | No single-transaction ceiling |
| **Velocity checks** | NOT ENFORCED | No rate limiting on payment frequency |

### 4.3 Currency Handling

| Aspect | Status | Details |
|---|---|---|
| **Currency field** | NOT PRESENT | No `currency` or `Ccy` field on any payment DTO |
| **Multi-currency support** | NOT SUPPORTED | System is implicitly single-currency |
| **Currency validation** | N/A | Cannot validate what doesn't exist |
| **Exchange rate handling** | NOT PRESENT | No FX rate or conversion logic |
| **Currency mismatch detection** | NOT PRESENT | Cannot detect cross-currency transfers |

### 4.4 Duplicate Detection & Idempotency

| Mechanism | Status | Details |
|---|---|---|
| **Idempotency key in request** | NOT PRESENT | No client-supplied idempotency token |
| **Duplicate request detection** | NOT PRESENT | Same request submitted twice = two transactions |
| **Request hash/fingerprint** | NOT PRESENT | No deduplication mechanism |
| **`@Version` optimistic locking** | PRESENT (partial) | `AuditAware` base class has `@Version` on the entity, but this only prevents concurrent *updates* to the same entity — it does NOT prevent duplicate *creation* |
| **Transaction reference uniqueness** | NOT ENFORCED | `transactionReference` / `transactionId` is a UUID generated server-side, no uniqueness constraint in DB schema definition |

### 4.5 Transaction Integrity & Error Handling

| Concern | Fund Transfer Service | Utility Payment Service |
|---|---|---|
| **`@Transactional`** | Not present on service method | Not present on service method |
| **Core banking `@Transactional`** | Present on `TransactionService` class | Present on `TransactionService` class |
| **Failure rollback (local)** | Entity saved as PENDING, then set to SUCCESS without checking Feign response status | Entity saved as PROCESSING, then set to SUCCESS without checking response status |
| **Feign call failure** | If Feign throws, entity remains PENDING forever — no retry or compensation | If Feign throws, entity remains PROCESSING forever — no retry or compensation |
| **Partial debit (core)** | `internalFundTransfer` debits sender and credits receiver in sequence — if credit fails after debit, sender loses funds within same `@Transactional` (should rollback, but `@OneToOne` on `TransactionEntity.account` is a design concern) | `utilPayment` deducts from account balance then saves transaction — if save fails, balance is already modified in-memory within same transaction (should rollback) |
| **Available balance calculation bug** | `availableBalance = actualBalance - amount` applied after `actualBalance` was already subtracted, resulting in **double-deduction** of available balance | Same double-deduction bug: `setAvailableBalance(getActualBalance().subtract(amount))` after `actualBalance` was already reduced |

---

## 5. Gap Severity & Business Risk Matrix

| # | Gap | Severity | Business Risk | Impact Area |
|---|---|---|---|---|
| G-01 | No currency field on any payment payload | **CRITICAL** | Cannot expand to multi-currency; regulatory non-compliance for cross-border | Compliance, Growth |
| G-02 | Zero input validation on payment DTOs | **CRITICAL** | Malformed or malicious payloads processed; financial loss from zero/negative amounts | Security, Financial |
| G-03 | No duplicate detection / idempotency | **CRITICAL** | Network retries or user double-clicks create duplicate debits | Financial, UX |
| G-04 | Available balance double-deduction bug | **CRITICAL** | Available balance calculated incorrectly — deducted twice per transaction | Financial, Data Integrity |
| G-05 | No amount limits (min/max/daily) | **HIGH** | Unlimited transfers enable fraud, money laundering, or accidental large debits | Compliance, Financial |
| G-06 | No account status validation before transfer | **HIGH** | Transfers permitted on BLOCKED/DORMANT accounts | Compliance, Security |
| G-07 | Missing end-to-end ID | **HIGH** | Cannot reconcile payments across systems; no customer-facing reference for disputes | Operations, Compliance |
| G-08 | No transaction timestamps in payloads | **HIGH** | Cannot determine when payment was initiated; audit trail gaps | Compliance, Audit |
| G-09 | No `@Transactional` on fund-transfer/utility-payment service layers | **HIGH** | Local entity status updates (PENDING → SUCCESS) are non-atomic with Feign calls | Data Integrity |
| G-10 | Orphaned PENDING/PROCESSING records on Feign failure | **HIGH** | No compensation, retry, or dead-letter mechanism for failed downstream calls | Operations, Financial |
| G-11 | No debtor/creditor party identification | **MEDIUM** | Cannot comply with KYC/AML reporting requirements at message level | Compliance |
| G-12 | No remittance information field | **MEDIUM** | Payers cannot attach payment descriptions; reconciliation difficulty for recipients | Operations, UX |
| G-13 | No BIC/bank identification | **MEDIUM** | Cannot support interbank or multi-institution transfers | Growth, Interoperability |
| G-14 | No payment purpose or category codes | **MEDIUM** | Cannot classify payments for analytics, reporting, or regulatory filing | Compliance, Analytics |
| G-15 | No payment scheduling (requested execution date) | **LOW** | All payments are immediate-only; no future-dated payment capability | Feature Completeness |
| G-16 | No batch payment support | **LOW** | Each payment requires individual API call; no bulk processing | Feature Completeness |
| G-17 | No charge bearer specification | **LOW** | No model for fee allocation between debtor/creditor | Feature Completeness |
| G-18 | `authID` field dropped at service boundary | **LOW** | Authorization context lost when fund-transfer calls core-banking; audit trail break | Audit |
| G-19 | Empty `UtilityPaymentResponse` class in fund-transfer-service | **LOW** | Dead code / incomplete integration; indicates abandoned cross-service concerns | Code Quality |

---

## 6. Recommendations

### Immediate (Sprint 1-2)

1. **Fix the available balance double-deduction bug** (G-04) in `TransactionService.internalFundTransfer()` and `utilPayment()` — the line `setAvailableBalance(getActualBalance().subtract(amount))` should be `setAvailableBalance(getActualBalance())` since `actualBalance` was already subtracted.

2. **Add Jakarta Bean Validation** annotations to all payment DTOs (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`) and add `@Valid` to controller method parameters.

3. **Implement idempotency** — add a client-supplied `idempotencyKey` field to payment requests; check for existing transactions with the same key before processing.

### Short-Term (Sprint 3-5)

4. **Add currency code** (`String currency` / ISO 4217) to all payment DTOs and entities; validate currency matches account currency.

5. **Implement amount limits** — configurable min/max per transaction, daily cumulative limits, and velocity checks.

6. **Add account status checks** — reject payments from/to accounts not in ACTIVE status.

7. **Add `@Transactional`** to fund-transfer and utility-payment service methods; implement compensation/retry logic for Feign failures.

8. **Introduce end-to-end ID** — allow clients to supply a unique reference that flows through all services for reconciliation.

### Medium-Term (Sprint 6-10)

9. **Adopt ISO 20022 message structures** — introduce pain.001/pain.002 compatible DTOs with proper GroupHeader, PaymentInformation, and CreditTransferTransactionInformation structures.

10. **Add party identification** — debtor/creditor names, addresses, and identification numbers to payment messages for KYC/AML compliance.

11. **Add remittance information** — structured and unstructured remittance data fields.

12. **Implement payment scheduling** — requested execution date support for future-dated payments.
