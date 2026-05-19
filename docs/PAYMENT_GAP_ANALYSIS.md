# Payment Payload Gap Analysis

> **Scope**: Fund Transfer Service, Utility Payment Service, and Core Banking Service transaction processing  
> **Standard**: ISO 20022 payment messages — pain.001 (Customer Credit Transfer Initiation), pain.002 (Payment Status Report), pacs.008 (FI to FI Customer Credit Transfer)  
> **Date**: May 2025

---

## 1. Current Payment Payload Structures

### 1.1 Fund Transfer — Request

| Layer | Class | Fields |
|-------|-------|--------|
| **Fund Transfer Service** | `FundTransferRequest` | `fromAccount` (String), `toAccount` (String), `amount` (BigDecimal), `authID` (String) |
| **Core Banking Service** | `FundTransferRequest` | `fromAccount` (String), `toAccount` (String), `amount` (BigDecimal) |

**Observations:**
- The `authID` field exists only at the orchestration layer and is **not forwarded** to core banking — it is silently dropped during the Feign call.
- No currency field — all amounts are implicitly single-currency.
- No transfer purpose, description, or beneficiary metadata.

### 1.2 Fund Transfer — Response

| Layer | Class | Fields |
|-------|-------|--------|
| **Fund Transfer Service** | `FundTransferResponse` | `message` (String), `transactionId` (String) |
| **Core Banking Service** | `FundTransferResponse` | `message` (String), `transactionId` (String) |

**Observations:**
- Response contains only a free-text message and a UUID transaction ID.
- No structured status code, no timestamp, no balance-after, no fees.

### 1.3 Fund Transfer — Persisted Entity

| Table | Column | Type | Notes |
|-------|--------|------|-------|
| `fund_transfer` | `id` | BIGINT (auto) | Surrogate PK |
| | `transaction_reference` | VARCHAR | UUID from core banking |
| | `from_account` | VARCHAR | Debtor account number |
| | `to_account` | VARCHAR | Creditor account number |
| | `amount` | DECIMAL(19,2) | Transfer amount |
| | `status` | ENUM | `PENDING`, `SUCCESS` (no `FAILED`) |
| | `created_date` | INSTANT | Via AuditAware |
| | `modified_date` | INSTANT | Via AuditAware |
| | `version` | BIGINT | Optimistic lock |

### 1.4 Utility Payment — Request

| Layer | Class | Fields |
|-------|-------|--------|
| **Utility Payment Service** | `UtilityPaymentRequest` | `providerId` (Long), `amount` (BigDecimal), `referenceNumber` (String), `account` (String) |
| **Core Banking Service** | `UtilityPaymentRequest` | `providerId` (Long), `amount` (BigDecimal), `referenceNumber` (String), `account` (String) |

**Observations:**
- `providerId` is an opaque Long — no provider name, no routing details.
- `referenceNumber` is a free-text string with no format validation.
- `account` is the payer's account — named generically, easy to confuse with provider account.

### 1.5 Utility Payment — Response

| Layer | Class | Fields |
|-------|-------|--------|
| **Utility Payment Service** | `UtilityPaymentResponse` | `message` (String), `transactionId` (String) |
| **Core Banking Service** | `UtilityPaymentResponse` | `message` (String), `transactionId` (String) |
| **Fund Transfer Service** | `UtilityPaymentResponse` | *(empty class — 0 fields)* |

**Observations:**
- The fund-transfer service contains a vestigial empty `UtilityPaymentResponse` class — dead code indicating a copy-paste artifact.
- Same minimal response structure as fund transfer: message + UUID only.

### 1.6 Core Banking — Transaction Entity

| Table | Column | Type | Notes |
|-------|--------|------|-------|
| `banking_core_transaction` | `id` | BIGINT (auto) | Surrogate PK |
| | `amount` | DECIMAL(19,2) | Signed amount (negative = debit) |
| | `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| | `reference_number` | VARCHAR(50) | Counterparty account or bill ref |
| | `transaction_id` | VARCHAR(50) | UUID correlation ID |
| | `account_id` | BIGINT FK | Link to `banking_core_account` |

---

## 2. ISO 20022 Comparison

### 2.1 pain.001 — Customer Credit Transfer Initiation

This message initiates a credit transfer from the debtor's side. The current `FundTransferRequest` maps loosely to a single `CreditTransferTransactionInformation` within pain.001.

| ISO 20022 Element | ISO Field Path | Current Equivalent | Status |
|---|---|---|---|
| **MessageIdentification** | `GrpHdr/MsgId` | *(none)* | **MISSING** — No client-generated message ID |
| **CreationDateTime** | `GrpHdr/CreDtTm` | *(none)* | **MISSING** — No client-side timestamp |
| **NumberOfTransactions** | `GrpHdr/NbOfTxs` | *(always 1)* | **IMPLICIT** — API only handles single transfers |
| **ControlSum** | `GrpHdr/CtrlSum` | *(none)* | **MISSING** — No batch control sum |
| **InitiatingParty** | `GrpHdr/InitgPty/Nm` | `authID` | **RENAMED** — `authID` maps to initiating party but only carries a Keycloak UUID, not a structured name/address |
| **PaymentInformationId** | `PmtInf/PmtInfId` | *(none)* | **MISSING** — No payment information grouping |
| **PaymentMethod** | `PmtInf/PmtMtd` | *(none)* | **MISSING** — Always assumed `TRF` (transfer) |
| **RequestedExecutionDate** | `PmtInf/ReqdExctnDt` | *(none)* | **MISSING** — All transfers execute immediately |
| **DebtorName** | `PmtInf/Dbtr/Nm` | *(none)* | **MISSING** — Only account number sent |
| **DebtorAccount/IBAN** | `PmtInf/DbtrAcct/Id/IBAN` | `fromAccount` | **RENAMED** — Uses proprietary account number, not IBAN |
| **DebtorAgent/BIC** | `PmtInf/DbtrAgt/FinInstnId/BIC` | *(none)* | **MISSING** — Single-institution assumption |
| **CreditorName** | `CdtTrfTxInf/Cdtr/Nm` | *(none)* | **MISSING** — No beneficiary name |
| **CreditorAccount/IBAN** | `CdtTrfTxInf/CdtrAcct/Id/IBAN` | `toAccount` | **RENAMED** — Proprietary account number |
| **CreditorAgent/BIC** | `CdtTrfTxInf/CdtrAgt/FinInstnId/BIC` | *(none)* | **MISSING** — Single-institution assumption |
| **Amount/Currency** | `CdtTrfTxInf/Amt/InstdAmt Ccy=` | `amount` (BigDecimal) | **PARTIAL** — Amount present, **currency missing** |
| **EndToEndIdentification** | `CdtTrfTxInf/PmtId/EndToEndId` | *(none)* | **MISSING** — No end-to-end correlation ID from client |
| **InstructionIdentification** | `CdtTrfTxInf/PmtId/InstrId` | *(none)* | **MISSING** — No instruction-level ID |
| **Purpose** | `CdtTrfTxInf/Purp/Cd` | *(none)* | **MISSING** — No transfer purpose code |
| **RemittanceInformation** | `CdtTrfTxInf/RmtInf/Ustrd` | *(none)* | **MISSING** — No payment description/memo |
| **ChargeBearer** | `CdtTrfTxInf/ChrgBr` | *(none)* | **MISSING** — No fee allocation model |

**Summary**: 3 fields present (2 renamed), 16 standard fields completely missing.

### 2.2 pain.002 — Payment Status Report

This message reports the status of a payment initiation. The current `FundTransferResponse` / `UtilityPaymentResponse` loosely corresponds to this.

| ISO 20022 Element | ISO Field Path | Current Equivalent | Status |
|---|---|---|---|
| **MessageIdentification** | `GrpHdr/MsgId` | *(none)* | **MISSING** |
| **CreationDateTime** | `GrpHdr/CreDtTm` | *(none)* | **MISSING** |
| **OriginalMessageId** | `OrgnlGrpInfAndSts/OrgnlMsgId` | *(none)* | **MISSING** — No reference back to request |
| **GroupStatus** | `OrgnlGrpInfAndSts/GrpSts` | `message` (free text) | **PARTIAL** — Unstructured text vs. ISO code (ACCP, RJCT, etc.) |
| **TransactionStatus** | `TxInfAndSts/TxSts` | `status` (entity only) | **PARTIAL** — Uses `PENDING`/`SUCCESS` vs ISO codes |
| **StatusReasonCode** | `TxInfAndSts/StsRsnInf/Rsn/Cd` | *(none)* | **MISSING** — No reason code on failure |
| **OriginalEndToEndId** | `TxInfAndSts/OrgnlEndToEndId` | `transactionId` | **RENAMED** — UUID, not client-supplied |
| **AcceptanceDateTime** | `TxInfAndSts/AccptncDtTm` | *(none)* | **MISSING** — No processing timestamp in response |
| **AccountServicerReference** | `TxInfAndSts/AcctSvcrRef` | `transactionId` | **RENAMED** |

**Summary**: 2 fields present (both renamed), 7 standard fields missing.

### 2.3 pacs.008 — FI to FI Customer Credit Transfer

This message handles the actual clearing between financial institutions. The current `TransactionService.internalFundTransfer()` operation loosely corresponds to this.

| ISO 20022 Element | ISO Field Path | Current Equivalent | Status |
|---|---|---|---|
| **MessageIdentification** | `GrpHdr/MsgId` | `transactionId` (UUID) | **RENAMED** |
| **CreationDateTime** | `GrpHdr/CreDtTm` | `createdDate` (AuditAware) | **RENAMED** — Audit timestamp, not explicit |
| **SettlementMethod** | `GrpHdr/SttlmInf/SttlmMtd` | *(hardcoded CLRG)* | **MISSING** — Assumed internal book transfer |
| **InstructingAgent** | `GrpHdr/InstgAgt` | *(none)* | **MISSING** — Single-institution |
| **InstructedAgent** | `GrpHdr/InstdAgt` | *(none)* | **MISSING** — Single-institution |
| **InterbankSettlementAmount** | `CdtTrfTxInf/IntrBkSttlmAmt` | `amount` | **RENAMED** |
| **InterbankSettlementDate** | `CdtTrfTxInf/IntrBkSttlmDt` | *(none)* | **MISSING** — Instant settlement only |
| **ChargeBearer** | `CdtTrfTxInf/ChrgBr` | *(none)* | **MISSING** |
| **DebtorAccount** | `CdtTrfTxInf/DbtrAcct/Id` | `fromAccount` | **RENAMED** |
| **CreditorAccount** | `CdtTrfTxInf/CdtrAcct/Id` | `toAccount` / `referenceNumber` | **RENAMED** |

**Summary**: 4 fields present (all renamed), 6 standard fields missing.

---

## 3. Payment Validation Rules Assessment

### 3.1 Amount Limits

| Check | Implemented? | Details |
|-------|-------------|---------|
| Minimum amount > 0 | **NO** | No validation — `BigDecimal` accepts 0 and negative values. A zero or negative transfer will be processed. |
| Maximum amount limit | **NO** | No per-transaction or daily limit. An account with $100K balance could transfer it all in one request. |
| Decimal precision check | **NO** | No enforcement of 2 decimal places — amounts like `100.999` are accepted. |
| Balance sufficiency | **PARTIAL** | `validateBalance()` checks `actualBalance >= amount` but has a **double-deduction bug**: `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already reduced, causing double subtraction. |

### 3.2 Currency Handling

| Check | Implemented? | Details |
|-------|-------------|---------|
| Currency field present | **NO** | No currency code anywhere in the payload or database. |
| Multi-currency support | **NO** | Entire system assumes a single, unnamed currency. |
| Cross-currency conversion | **NO** | No FX rate lookup, no conversion logic. |
| Currency mismatch check | **NO** | Cannot exist without currency field. |

### 3.3 Duplicate Detection

| Check | Implemented? | Details |
|-------|-------------|---------|
| Idempotency key | **NO** | No client-supplied idempotency key on any POST endpoint. Resubmitting the same request creates a new transaction. |
| Duplicate transaction check | **NO** | No check for identical `fromAccount` + `toAccount` + `amount` within a time window. |
| Unique constraint on transaction_id | **NO** | `transaction_id` in `banking_core_transaction` has no UNIQUE constraint in DDL. |
| Request deduplication at API Gateway | **NO** | Gateway performs no deduplication or throttling. |

### 3.4 Idempotency

| Check | Implemented? | Details |
|-------|-------------|---------|
| Idempotency header support | **NO** | No `Idempotency-Key` header handling. |
| Idempotent status transitions | **NO** | Status transitions (PENDING→SUCCESS) are not guarded by optimistic locking at the service level despite `@Version` on AuditAware. |
| Retry-safe design | **NO** | If the Feign call to core-banking succeeds but the response is lost (network partition), re-calling creates a duplicate. The fund-transfer entity stays PENDING forever. |

### 3.5 Account Validation

| Check | Implemented? | Details |
|-------|-------------|---------|
| Account existence | **YES** | `AccountService.readBankAccount()` throws `EntityNotFoundException` if not found. |
| Account status check | **NO** | Accounts with status `DORMANT` or `BLOCKED` can still send/receive transfers. |
| Self-transfer prevention | **NO** | `fromAccount == toAccount` is not checked — user can transfer to themselves. |
| Account ownership check | **NO** | Any authenticated user can debit any account — no ownership verification against `user_id`. |

### 3.6 Additional Payment Validation Gaps

| Check | Implemented? | Details |
|-------|-------------|---------|
| Bean Validation (`@Valid`) | **NO** | No `@Valid` annotation on any `@RequestBody`. No `@NotNull`, `@Positive`, `@Size` constraints on DTOs. |
| Utility provider validation | **PARTIAL** | Provider looked up by `providerId` but no validation of provider status or routing info. |
| Reference number format | **NO** | `referenceNumber` is free-text with no format or length validation. |
| Transaction atomicity | **NO** | Fund transfer debit + credit are in one `@Transactional` block but utility payment debit is not atomic with the external provider call. |

---

## 4. Gap Severity & Business Risk Rating

| # | Gap | Severity | Business Risk | Effort to Remediate |
|---|-----|----------|---------------|---------------------|
| **G-01** | No currency field — all transactions assume single currency | **Critical** | Cannot expand to multi-currency; regulatory non-compliance for cross-border | Medium |
| **G-02** | Double-deduction bug in `internalFundTransfer()` and `utilPayment()` | **Critical** | Direct financial loss — customer balances incorrectly reduced | Small |
| **G-03** | No idempotency — duplicate transactions possible | **Critical** | Financial loss, customer complaints, reconciliation failures | Medium |
| **G-04** | No input validation (`@Valid`) on any payment request | **Critical** | NPEs on null fields, negative transfers, injection risk | Small |
| **G-05** | No account ownership verification | **Critical** | Any authenticated user can debit any account — unauthorized fund movement | Medium |
| **G-06** | Account status not checked (DORMANT/BLOCKED accounts can transact) | **High** | Regulatory violation — frozen accounts can send money | Small |
| **G-07** | No transaction amount limits (min/max) | **High** | Money laundering risk, no AML/CFT compliance | Medium |
| **G-08** | No rollback/compensation on Feign failure | **High** | Transactions stuck in PENDING/PROCESSING forever, funds in limbo | Large |
| **G-09** | Missing 16 of 19 pain.001 fields | **High** | Cannot generate ISO 20022 compliant payment initiation messages | Large |
| **G-10** | Missing 7 of 9 pain.002 fields | **High** | Cannot produce standards-compliant payment status reports | Large |
| **G-11** | All errors return HTTP 400 | **High** | Clients cannot distinguish validation errors from server failures; inhibits retry logic | Small |
| **G-12** | No end-to-end transaction correlation ID from client | **High** | No way for originator to trace payment through the chain | Small |
| **G-13** | No self-transfer prevention | **Medium** | Potential for circular transactions, complicates reconciliation | Small |
| **G-14** | No decimal precision enforcement | **Medium** | Sub-cent amounts could cause rounding discrepancies across ledgers | Small |
| **G-15** | `authID` silently dropped between services | **Medium** | Audit trail breaks — core banking has no record of who initiated the transfer | Small |
| **G-16** | No remittance information / payment memo | **Medium** | Customer cannot describe transfer purpose; limits regulatory reporting | Small |
| **G-17** | Missing pacs.008 settlement fields | **Medium** | No path to interbank/clearing-house integration | Large |
| **G-18** | `UtilityPaymentResponse` in fund-transfer service is empty dead code | **Low** | Technical debt, confusion for developers | Small |
| **G-19** | No requested execution date — all transfers are immediate | **Low** | Cannot support scheduled/future-dated payments | Medium |
| **G-20** | Free-text `referenceNumber` with no format validation | **Medium** | Data quality issues, cannot match against provider billing systems | Small |

---

## 5. Summary

### Fields Coverage Against ISO 20022

| Standard | Total Key Fields | Present (renamed) | Missing | Coverage |
|----------|-----------------|-------------------|---------|----------|
| pain.001 | 19 | 3 (16%) | 16 (84%) | **16%** |
| pain.002 | 9 | 2 (22%) | 7 (78%) | **22%** |
| pacs.008 | 10 | 4 (40%) | 6 (60%) | **40%** |

### Critical Gaps Requiring Immediate Attention

1. **Double-deduction bug** (G-02) — Active defect causing financial loss
2. **No idempotency** (G-03) — Duplicate transactions can be created
3. **No input validation** (G-04) — System vulnerable to null/negative values
4. **No account ownership check** (G-05) — Unauthorized account access
5. **No currency support** (G-01) — Fundamental ISO 20022 non-compliance

### Remediation Priority

- **Phase 1 (Immediate)**: G-02, G-04, G-06, G-11, G-13, G-15, G-18 — bug fixes and validation (Small effort)
- **Phase 2 (Short-term)**: G-01, G-03, G-05, G-07, G-12, G-14, G-16, G-20 — payment model enrichment (Medium effort)
- **Phase 3 (Medium-term)**: G-08, G-09, G-10, G-17, G-19 — ISO 20022 alignment and resilience (Large effort)
