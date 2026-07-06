# Sample API Specifications — Oracle AI-Powered Payment Agent

This document provides sample API specifications for the 10 primary external-facing APIs exposed by the Payment Agent platform. Each specification includes the API name, purpose, endpoint, HTTP method, request payload, response payload, error codes, and security requirements.

---

## Table of Contents

1. [Get Payment Status](#1-get-payment-status)
2. [Get Invoice Details](#2-get-invoice-details)
3. [Get Supplier Payment History](#3-get-supplier-payment-history)
4. [Check Duplicate Payment](#4-check-duplicate-payment)
5. [Submit Payment Exception](#5-submit-payment-exception)
6. [Approve or Reject Payment](#6-approve-or-reject-payment)
7. [Generate Remittance Summary](#7-generate-remittance-summary)
8. [Create Audit Trail](#8-create-audit-trail)
9. [Trigger Reconciliation](#9-trigger-reconciliation)
10. [Create ServiceNow Ticket for Unresolved Exception](#10-create-servicenow-ticket-for-unresolved-exception)

---

## 1. Get Payment Status

| Attribute | Detail |
|---|---|
| **API Name** | Get Payment Status |
| **Purpose** | Retrieve the current status and details of a payment by payment ID or invoice number |
| **Endpoint** | `GET /api/v1/payments/{payment_id}/status` |
| **HTTP Method** | GET |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_VIEWER`, `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER`, `PAYMENT_AGENT_TREASURY`, `PAYMENT_AGENT_SUPPLIER` (own payments only) |

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `payment_id` | String | Yes (path) | Fusion payment ID |
| `include_timeline` | Boolean | No | Include lifecycle timeline (default: false) |
| `include_invoice` | Boolean | No | Include linked invoice details (default: false) |

### Request

```
GET /api/v1/payments/PAY-60234/status?include_timeline=true&include_invoice=true
Authorization: Bearer {oauth2_token}
Accept: application/json
```

### Response (200 OK)

```json
{
    "payment_id": "PAY-60234",
    "payment_number": "60234",
    "status": "SETTLED",
    "status_description": "Payment has been settled by the bank",
    "amount": 45000.00,
    "currency": "USD",
    "payment_date": "2026-06-28",
    "settlement_date": "2026-07-01",
    "payment_method": "EFT",
    "bank_reference": "BNK-REF-789012",
    "supplier": {
        "supplier_id": "SUP-1001",
        "supplier_name": "ACME Corporation",
        "supplier_site": "ACME-HQ"
    },
    "invoice": {
        "invoice_id": "INV-2026-4521",
        "invoice_number": "INV-2026-4521",
        "invoice_amount": 45000.00,
        "invoice_date": "2026-06-15",
        "po_number": "PO-2026-8832"
    },
    "timeline": [
        {"stage": "CREATED", "timestamp": "2026-06-25T10:00:00Z", "actor": "system"},
        {"stage": "VALIDATED", "timestamp": "2026-06-25T10:05:00Z", "actor": "system"},
        {"stage": "APPROVED", "timestamp": "2026-06-26T14:30:00Z", "actor": "john.doe@example.com"},
        {"stage": "FORMATTED", "timestamp": "2026-06-27T08:00:00Z", "actor": "system"},
        {"stage": "TRANSMITTED", "timestamp": "2026-06-28T06:00:00Z", "actor": "system"},
        {"stage": "SETTLED", "timestamp": "2026-07-01T09:00:00Z", "actor": "bank"}
    ],
    "metadata": {
        "business_unit": "NA_OPERATIONS",
        "legal_entity": "ACME_US_INC",
        "source": "Oracle Fusion Payments"
    }
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_PAYMENT_ID` | Payment ID format is invalid |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have access to this payment |
| 404 | `PAYMENT_NOT_FOUND` | Payment with the specified ID does not exist |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded (100 req/min per user) |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 502 | `FUSION_API_UNAVAILABLE` | Oracle Fusion API is temporarily unavailable |

---

## 2. Get Invoice Details

| Attribute | Detail |
|---|---|
| **API Name** | Get Invoice Details |
| **Purpose** | Retrieve detailed information about an invoice including line items, payment status, and matching details |
| **Endpoint** | `GET /api/v1/invoices/{invoice_id}` |
| **HTTP Method** | GET |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_VIEWER`, `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER` |

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `invoice_id` | String | Yes (path) | Fusion invoice ID or invoice number |
| `include_lines` | Boolean | No | Include invoice line items (default: false) |
| `include_holds` | Boolean | No | Include any active holds (default: false) |
| `include_payments` | Boolean | No | Include linked payment details (default: false) |

### Request

```
GET /api/v1/invoices/INV-2026-4521?include_lines=true&include_holds=true&include_payments=true
Authorization: Bearer {oauth2_token}
Accept: application/json
```

### Response (200 OK)

```json
{
    "invoice_id": "300000012345678",
    "invoice_number": "INV-2026-4521",
    "invoice_type": "STANDARD",
    "supplier": {
        "supplier_id": "SUP-1001",
        "supplier_name": "ACME Corporation",
        "supplier_site": "ACME-HQ"
    },
    "invoice_amount": 45000.00,
    "currency": "USD",
    "invoice_date": "2026-06-15",
    "due_date": "2026-07-15",
    "payment_terms": "Net 30",
    "approval_status": "APPROVED",
    "payment_status": "FULLY_PAID",
    "po_number": "PO-2026-8832",
    "description": "IT consulting services — June 2026",
    "lines": [
        {
            "line_number": 1,
            "description": "Senior Consultant — 120 hours",
            "quantity": 120,
            "unit_price": 250.00,
            "amount": 30000.00,
            "po_line_number": 1
        },
        {
            "line_number": 2,
            "description": "Junior Consultant — 100 hours",
            "quantity": 100,
            "unit_price": 150.00,
            "amount": 15000.00,
            "po_line_number": 2
        }
    ],
    "holds": [],
    "payments": [
        {
            "payment_id": "PAY-60234",
            "payment_amount": 45000.00,
            "payment_date": "2026-06-28",
            "status": "SETTLED"
        }
    ],
    "metadata": {
        "business_unit": "NA_OPERATIONS",
        "legal_entity": "ACME_US_INC",
        "source": "Oracle Fusion AP"
    }
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_INVOICE_ID` | Invoice ID format is invalid |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have access to this invoice |
| 404 | `INVOICE_NOT_FOUND` | Invoice with the specified ID does not exist |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 502 | `FUSION_API_UNAVAILABLE` | Oracle Fusion API is temporarily unavailable |

---

## 3. Get Supplier Payment History

| Attribute | Detail |
|---|---|
| **API Name** | Get Supplier Payment History |
| **Purpose** | Retrieve payment history for a specific supplier within a date range |
| **Endpoint** | `GET /api/v1/suppliers/{supplier_id}/payments` |
| **HTTP Method** | GET |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER`, `PAYMENT_AGENT_SUPPLIER` (own history only) |

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `supplier_id` | String | Yes (path) | Fusion supplier ID |
| `start_date` | Date (ISO 8601) | No | Start of date range (default: 90 days ago) |
| `end_date` | Date (ISO 8601) | No | End of date range (default: today) |
| `status` | String | No | Filter by status: ALL, SETTLED, PENDING, FAILED (default: ALL) |
| `page` | Integer | No | Page number (default: 1) |
| `page_size` | Integer | No | Items per page (default: 25, max: 100) |

### Request

```
GET /api/v1/suppliers/SUP-1001/payments?start_date=2026-04-01&end_date=2026-07-06&status=ALL&page=1&page_size=25
Authorization: Bearer {oauth2_token}
Accept: application/json
```

### Response (200 OK)

```json
{
    "supplier_id": "SUP-1001",
    "supplier_name": "ACME Corporation",
    "date_range": {
        "start": "2026-04-01",
        "end": "2026-07-06"
    },
    "summary": {
        "total_payments": 12,
        "total_amount": 342000.00,
        "currency": "USD",
        "settled": 10,
        "pending": 1,
        "failed": 1
    },
    "payments": [
        {
            "payment_id": "PAY-60234",
            "invoice_number": "INV-2026-4521",
            "amount": 45000.00,
            "currency": "USD",
            "payment_date": "2026-06-28",
            "status": "SETTLED",
            "payment_method": "EFT",
            "bank_reference": "BNK-REF-789012"
        },
        {
            "payment_id": "PAY-60189",
            "invoice_number": "INV-2026-4488",
            "amount": 32000.00,
            "currency": "USD",
            "payment_date": "2026-06-20",
            "status": "FAILED",
            "payment_method": "EFT",
            "failure_reason": "Bank reject — AC04 (Closed Account)"
        }
    ],
    "pagination": {
        "page": 1,
        "page_size": 25,
        "total_pages": 1,
        "total_records": 12
    }
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_DATE_RANGE` | Start date is after end date or range exceeds 12 months |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have access to this supplier's data |
| 404 | `SUPPLIER_NOT_FOUND` | Supplier with the specified ID does not exist |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |

---

## 4. Check Duplicate Payment

| Attribute | Detail |
|---|---|
| **API Name** | Check Duplicate Payment |
| **Purpose** | Check if a payment instruction may be a duplicate of an existing payment |
| **Endpoint** | `POST /api/v1/payments/duplicate-check` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER` |

### Request

```
POST /api/v1/payments/duplicate-check
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "invoice_number": "INV-2026-8832",
    "supplier_id": "SUP-1001",
    "amount": 45000.00,
    "currency": "USD",
    "payment_date": "2026-07-05",
    "check_window_days": 90,
    "include_semantic_match": true
}
```

### Response (200 OK — Duplicate Found)

```json
{
    "is_duplicate": true,
    "confidence_score": 0.96,
    "detection_method": "EXACT_MATCH + SEMANTIC",
    "matches": [
        {
            "existing_payment_id": "PAY-60234",
            "invoice_number": "INV-2026-8832",
            "amount": 45000.00,
            "payment_date": "2026-06-15",
            "status": "SETTLED",
            "match_score": 0.98,
            "match_type": "EXACT",
            "match_factors": [
                "Same invoice number",
                "Same supplier",
                "Same amount",
                "Paid within 90 days"
            ]
        }
    ],
    "recommendation": "HOLD — This payment appears to be a duplicate of PAY-60234 which was settled on 2026-06-15. Recommend placing on hold pending analyst review.",
    "auto_hold_applied": true,
    "hold_reference": "HOLD-DUP-2026-0705-001"
}
```

### Response (200 OK — No Duplicate)

```json
{
    "is_duplicate": false,
    "confidence_score": 0.12,
    "detection_method": "EXACT_MATCH + SEMANTIC",
    "matches": [],
    "recommendation": "No duplicate detected. Payment may proceed.",
    "auto_hold_applied": false
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing required fields (invoice_number, supplier_id, amount) |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have permission to perform duplicate checks |
| 422 | `VALIDATION_ERROR` | Invalid field values (negative amount, invalid currency) |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 503 | `VECTOR_SEARCH_UNAVAILABLE` | AI Vector Search service is temporarily unavailable |

---

## 5. Submit Payment Exception

| Attribute | Detail |
|---|---|
| **API Name** | Submit Payment Exception |
| **Purpose** | Create a new payment exception record for investigation and resolution |
| **Endpoint** | `POST /api/v1/exceptions` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER` |

### Request

```
POST /api/v1/exceptions
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "payment_id": "PAY-60189",
    "invoice_id": "INV-2026-4488",
    "supplier_id": "SUP-1001",
    "exception_type": "BANK_REJECT",
    "exception_code": "AC04",
    "description": "Payment rejected by bank — beneficiary account closed",
    "requested_action": "INVESTIGATE",
    "priority_override": null,
    "attachments": []
}
```

### Response (201 Created)

```json
{
    "exception_id": "EXC-2026-4521",
    "payment_id": "PAY-60189",
    "invoice_id": "INV-2026-4488",
    "supplier_id": "SUP-1001",
    "exception_type": "BANK_REJECT",
    "exception_code": "AC04",
    "severity": "HIGH",
    "priority": "P2",
    "status": "OPEN",
    "assigned_to": "jane.smith@example.com",
    "sla_deadline": "2026-07-06T20:00:00Z",
    "ai_analysis": {
        "root_cause": "Supplier ACME Corporation's bank account ending in ****5678 at First National Bank has been closed. Last successful payment to this account was on 2026-05-15.",
        "recommendation": "Contact supplier to obtain updated bank account details. Update supplier bank account in Oracle Fusion Supplier Management before re-submitting payment.",
        "confidence_score": 0.91,
        "supporting_evidence": [
            "Bank return code AC04: Closed Account Number",
            "Last successful payment: PAY-59812 on 2026-05-15",
            "Supplier has 2 other active bank accounts in system"
        ]
    },
    "servicenow_ticket": {
        "ticket_id": "INC-78901",
        "status": "NEW",
        "url": "https://instance.service-now.com/nav_to.do?uri=incident.do?sys_id=abc123"
    },
    "created_at": "2026-07-06T12:00:00Z"
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing required fields or invalid exception type |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have permission to create exceptions |
| 404 | `PAYMENT_NOT_FOUND` | Referenced payment does not exist |
| 409 | `DUPLICATE_EXCEPTION` | An open exception already exists for this payment |
| 422 | `VALIDATION_ERROR` | Invalid field values |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |

---

## 6. Approve or Reject Payment

| Attribute | Detail |
|---|---|
| **API Name** | Approve or Reject Payment |
| **Purpose** | Submit an approval or rejection decision for a pending payment approval task |
| **Endpoint** | `PUT /api/v1/approvals/{task_id}/decision` |
| **HTTP Method** | PUT |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_APPROVER` (must be the assigned approver) |

### Request — Approve

```
PUT /api/v1/approvals/TASK-2026-0705-001/decision
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "decision": "APPROVED",
    "comments": "Verified against PO and contract terms. Amount within approved budget.",
    "conditions": null
}
```

### Request — Reject

```json
{
    "decision": "REJECTED",
    "comments": "Invoice amount exceeds PO amount by 15%. Requires revised invoice from supplier.",
    "conditions": null
}
```

### Response (200 OK)

```json
{
    "task_id": "TASK-2026-0705-001",
    "payment_id": "PAY-60301",
    "decision": "APPROVED",
    "decided_by": "john.doe@example.com",
    "decided_at": "2026-07-06T14:30:00Z",
    "next_action": {
        "description": "Payment approved at Level 1. Proceeding to payment processing.",
        "next_approval_required": false,
        "estimated_settlement": "2026-07-08"
    },
    "audit_reference": "AUD-2026-0706-14300-001",
    "ai_recommendation_was": "APPROVE",
    "human_aligned_with_ai": true
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_DECISION` | Decision must be APPROVED or REJECTED |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `NOT_AUTHORIZED_APPROVER` | User is not the assigned approver for this task |
| 404 | `TASK_NOT_FOUND` | Approval task does not exist |
| 409 | `TASK_ALREADY_DECIDED` | Task has already been approved or rejected |
| 409 | `TASK_EXPIRED` | Task has expired and been escalated |
| 422 | `COMMENTS_REQUIRED` | Comments are required for rejection decisions |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |

---

## 7. Generate Remittance Summary

| Attribute | Detail |
|---|---|
| **API Name** | Generate Remittance Summary |
| **Purpose** | Generate a natural language remittance summary for a specific payment, explaining all applied invoices, deductions, and credits |
| **Endpoint** | `POST /api/v1/remittance/summary` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER`, `PAYMENT_AGENT_SUPPLIER` (own payments only) |

### Request

```
POST /api/v1/remittance/summary
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "payment_id": "PAY-60234",
    "format": "DETAILED",
    "language": "en",
    "include_nl_explanation": true
}
```

### Response (200 OK)

```json
{
    "payment_id": "PAY-60234",
    "supplier_name": "ACME Corporation",
    "payment_amount": 43500.00,
    "currency": "USD",
    "payment_date": "2026-06-28",
    "remittance_lines": [
        {
            "type": "INVOICE",
            "reference": "INV-2026-4521",
            "gross_amount": 45000.00,
            "description": "IT consulting services — June 2026"
        },
        {
            "type": "CREDIT_NOTE",
            "reference": "CN-2026-0112",
            "gross_amount": -1500.00,
            "description": "Credit for unused consulting hours"
        }
    ],
    "net_amount": 43500.00,
    "nl_explanation": "This payment of USD 43,500.00 to ACME Corporation covers invoice INV-2026-4521 for IT consulting services in June 2026 (USD 45,000.00), less a credit note CN-2026-0112 of USD 1,500.00 for unused consulting hours. The net payment was settled via EFT on 2026-06-28 with bank reference BNK-REF-789012.",
    "citations": [
        {"source": "Oracle Fusion AP — Invoice INV-2026-4521", "accessed_at": "2026-07-06T10:30:00Z"},
        {"source": "Oracle Fusion AP — Credit Note CN-2026-0112", "accessed_at": "2026-07-06T10:30:00Z"}
    ]
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing payment_id or invalid format |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have access to this payment |
| 404 | `PAYMENT_NOT_FOUND` | Payment does not exist |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 502 | `GENAI_UNAVAILABLE` | OCI Generative AI service is temporarily unavailable |

---

## 8. Create Audit Trail

| Attribute | Detail |
|---|---|
| **API Name** | Create Audit Trail |
| **Purpose** | Record an audit event for an AI agent action or human decision (used internally by agents; also available for external audit integrations) |
| **Endpoint** | `POST /api/v1/audit/events` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token (service account) |
| **Required Roles** | `PAYMENT_AGENT_ADMIN` (service accounts only) |

### Request

```
POST /api/v1/audit/events
Authorization: Bearer {service_oauth2_token}
Content-Type: application/json

{
    "request_id": "req-uuid-456",
    "session_id": "sess-abc-123",
    "user_id": "john.doe@example.com",
    "user_role": "PAYMENT_AGENT_APPROVER",
    "agent_id": "PA-APR-004",
    "action_type": "APPROVAL",
    "request_summary": "Approve payment PAY-60301 for USD 45,000 to ACME Corporation",
    "response_summary": "Payment approved by john.doe@example.com. AI recommendation was APPROVE (confidence: 0.92).",
    "data_accessed": ["INV-2026-4521", "PAY-60301", "SUP-1001", "PO-2026-8832"],
    "decision": "APPROVED",
    "decision_rationale": "Within policy limits; risk score LOW (0.15); no duplicate detected; PO and contract validated.",
    "confidence_score": 0.92,
    "risk_score": 0.15,
    "human_override": false,
    "source_citations": [
        {"source": "Oracle Fusion AP", "reference": "INV-2026-4521"},
        {"source": "Approval Matrix", "reference": "Rule AM-003"}
    ],
    "compliance_tags": ["SOX", "PCI"]
}
```

### Response (201 Created)

```json
{
    "audit_id": "AUD-2026-0706-14300-001",
    "status": "RECORDED",
    "created_at": "2026-07-06T14:30:01Z",
    "retention_expiry": "2033-07-06T14:30:01Z"
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_AUDIT_EVENT` | Missing required fields or invalid action_type |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `SERVICE_ACCOUNT_REQUIRED` | Only service accounts can create audit events |
| 422 | `VALIDATION_ERROR` | Invalid field values or data types |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 503 | `AUDIT_STORE_UNAVAILABLE` | Audit database is temporarily unavailable |

---

## 9. Trigger Reconciliation

| Attribute | Detail |
|---|---|
| **API Name** | Trigger Reconciliation |
| **Purpose** | Initiate a reconciliation process for a bank statement file or manual reconciliation request |
| **Endpoint** | `POST /api/v1/reconciliation/trigger` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_TREASURY` |

### Request

```
POST /api/v1/reconciliation/trigger
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "bank_account_id": "BA-001",
    "statement_date": "2026-07-05",
    "statement_source": "FILE",
    "file_reference": "MT940-20260705-BA001.txt",
    "matching_rules": "STANDARD",
    "auto_reconcile_threshold": 0.95,
    "notify_on_completion": true
}
```

### Response (202 Accepted)

```json
{
    "reconciliation_id": "RECON-2026-0705-001",
    "batch_id": "BATCH-2026-0705-BA001",
    "status": "PROCESSING",
    "bank_account_id": "BA-001",
    "statement_date": "2026-07-05",
    "estimated_items": 1270,
    "estimated_completion": "2026-07-06T10:45:00Z",
    "progress_url": "/api/v1/reconciliation/RECON-2026-0705-001/status",
    "created_at": "2026-07-06T10:30:00Z"
}
```

### Response — Progress Check (200 OK)

```json
{
    "reconciliation_id": "RECON-2026-0705-001",
    "status": "COMPLETED",
    "summary": {
        "total_items": 1270,
        "auto_matched": 1200,
        "ai_recommended": 47,
        "unmatched": 23,
        "match_rate": 94.49,
        "auto_reconciled": 1200,
        "pending_review": 70
    },
    "completed_at": "2026-07-06T10:42:00Z",
    "report_url": "/api/v1/reconciliation/RECON-2026-0705-001/report"
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing required fields or invalid bank account |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have treasury permissions |
| 404 | `BANK_ACCOUNT_NOT_FOUND` | Bank account ID does not exist |
| 404 | `STATEMENT_FILE_NOT_FOUND` | Referenced bank statement file not found |
| 409 | `RECONCILIATION_IN_PROGRESS` | A reconciliation is already running for this account/date |
| 422 | `INVALID_STATEMENT_FORMAT` | Bank statement file format is not supported |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |

---

## 10. Create ServiceNow Ticket for Unresolved Exception

| Attribute | Detail |
|---|---|
| **API Name** | Create ServiceNow Ticket |
| **Purpose** | Create a ServiceNow incident for an unresolved payment exception that requires ITSM tracking |
| **Endpoint** | `POST /api/v1/exceptions/{exception_id}/ticket` |
| **HTTP Method** | POST |
| **Authentication** | OAuth2 Bearer Token |
| **Required Roles** | `PAYMENT_AGENT_ANALYST`, `PAYMENT_AGENT_APPROVER` |

### Request

```
POST /api/v1/exceptions/EXC-2026-4521/ticket
Authorization: Bearer {oauth2_token}
Content-Type: application/json

{
    "urgency": "HIGH",
    "impact": "MEDIUM",
    "category": "PAYMENT_EXCEPTION",
    "subcategory": "BANK_REJECT",
    "short_description": "Payment PAY-60189 rejected — bank account closed (AC04)",
    "description": "Payment PAY-60189 to ACME Corporation for USD 32,000 was rejected by the bank with return code AC04 (Closed Account). AI analysis indicates the supplier's bank account ending in ****5678 has been closed. Supplier has 2 other active accounts on file. Recommended action: contact supplier for updated banking details.",
    "assigned_group": "AP_OPERATIONS",
    "additional_context": {
        "payment_id": "PAY-60189",
        "supplier_name": "ACME Corporation",
        "amount": 32000.00,
        "exception_id": "EXC-2026-4521",
        "ai_confidence_score": 0.91
    }
}
```

### Response (201 Created)

```json
{
    "exception_id": "EXC-2026-4521",
    "servicenow_ticket": {
        "ticket_id": "INC-78901",
        "number": "INC0078901",
        "status": "NEW",
        "priority": "P2",
        "assigned_group": "AP_OPERATIONS",
        "url": "https://instance.service-now.com/nav_to.do?uri=incident.do?sys_id=abc123",
        "created_at": "2026-07-06T12:05:00Z"
    },
    "linked_in_exception": true,
    "audit_reference": "AUD-2026-0706-12050-001"
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing required fields |
| 401 | `AUTH_TOKEN_EXPIRED` | OAuth2 token has expired |
| 403 | `INSUFFICIENT_PERMISSIONS` | User does not have permission to create tickets |
| 404 | `EXCEPTION_NOT_FOUND` | Exception with the specified ID does not exist |
| 409 | `TICKET_ALREADY_EXISTS` | A ServiceNow ticket already exists for this exception |
| 422 | `INVALID_URGENCY_IMPACT` | Invalid urgency or impact values |
| 429 | `RATE_LIMIT_EXCEEDED` | API rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected internal error |
| 502 | `SERVICENOW_UNAVAILABLE` | ServiceNow API is temporarily unavailable |

---

### Common Security Requirements (All APIs)

| Requirement | Implementation |
|---|---|
| Authentication | OAuth2 Bearer token (OCI IAM) — required for all endpoints |
| Authorization | RBAC role verification — role checked against endpoint permission matrix |
| Rate Limiting | 100 requests/minute per user (configurable at API Gateway) |
| Input Validation | JSON schema validation at API Gateway; injection prevention |
| TLS | TLS 1.3 mandatory for all API communications |
| Audit Logging | Every API call logged with user identity, action, and timestamp |
| Data Masking | Sensitive fields (bank accounts, SSN) masked in all responses |
| CORS | Allowlisted origins only |
| Request Size | Maximum 1 MB request body |
| Timeout | 30 seconds gateway timeout; agent-specific timeouts internal |

---

*This document is referenced from [01-HLD.md](../01-HLD.md) and [02-LLD.md](../02-LLD.md).*
