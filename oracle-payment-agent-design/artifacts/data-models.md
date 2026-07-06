# Sample Data Models — Oracle AI-Powered Payment Agent

This document provides field-level data model definitions and JSON schemas for the 10 core entities used by the Payment Agent platform.

---

## Table of Contents

1. [Payment](#1-payment)
2. [Invoice](#2-invoice)
3. [Supplier](#3-supplier)
4. [Payment Exception](#4-payment-exception)
5. [Approval Task](#5-approval-task)
6. [Reconciliation Record](#6-reconciliation-record)
7. [Audit Event](#7-audit-event)
8. [AI Recommendation](#8-ai-recommendation)
9. [User Interaction](#9-user-interaction)
10. [Risk Score](#10-risk-score)

---

## 1. Payment

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `payment_id` | String (50) | Yes | Unique payment identifier | Oracle Fusion Payments |
| `payment_number` | String (30) | Yes | Human-readable payment number | Oracle Fusion Payments |
| `payment_batch_id` | String (50) | No | Payment batch/process request ID | Oracle Fusion Payments |
| `invoice_id` | String (50) | Yes | Linked invoice identifier | Oracle Fusion AP |
| `supplier_id` | String (50) | Yes | Supplier identifier | Oracle Fusion Supplier Mgmt |
| `supplier_name` | String (200) | Yes | Supplier display name | Oracle Fusion Supplier Mgmt |
| `amount` | Decimal (15,2) | Yes | Payment amount | Oracle Fusion Payments |
| `currency` | String (3) | Yes | ISO 4217 currency code | Oracle Fusion Payments |
| `payment_date` | Date | Yes | Scheduled payment date | Oracle Fusion Payments |
| `settlement_date` | Date | No | Actual bank settlement date | Oracle Cash Management |
| `status` | Enum | Yes | Payment lifecycle status | Oracle Fusion Payments |
| `payment_method` | Enum | Yes | Payment method (EFT, CHECK, WIRE, ACH) | Oracle Fusion Payments |
| `bank_account_id` | String (50) | Yes | Disbursement bank account ID | Oracle Fusion Payments |
| `bank_reference` | String (100) | No | Bank transaction reference | Bank statement |
| `approval_status` | Enum | Yes | Approval workflow status | Oracle Fusion BPM |
| `business_unit` | String (50) | Yes | Business unit code | Oracle Fusion |
| `legal_entity` | String (50) | Yes | Legal entity code | Oracle Fusion |
| `created_at` | Timestamp (TZ) | Yes | Record creation timestamp | System |
| `updated_at` | Timestamp (TZ) | No | Last update timestamp | System |

**Status Enum Values**: `CREATED`, `VALIDATED`, `APPROVED`, `FORMATTED`, `TRANSMITTED`, `SETTLED`, `FAILED`, `VOIDED`, `CANCELLED`

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "Payment",
    "description": "Represents a payment record from Oracle Fusion Payments",
    "type": "object",
    "required": ["payment_id", "payment_number", "invoice_id", "supplier_id", "supplier_name", "amount", "currency", "payment_date", "status", "payment_method", "bank_account_id", "approval_status", "business_unit", "legal_entity"],
    "properties": {
        "payment_id": {
            "type": "string",
            "maxLength": 50,
            "description": "Unique payment identifier from Oracle Fusion"
        },
        "payment_number": {
            "type": "string",
            "maxLength": 30,
            "description": "Human-readable payment number"
        },
        "payment_batch_id": {
            "type": ["string", "null"],
            "maxLength": 50,
            "description": "Payment batch or process request ID"
        },
        "invoice_id": {
            "type": "string",
            "maxLength": 50,
            "description": "Linked invoice identifier"
        },
        "supplier_id": {
            "type": "string",
            "maxLength": 50,
            "description": "Supplier identifier"
        },
        "supplier_name": {
            "type": "string",
            "maxLength": 200,
            "description": "Supplier display name"
        },
        "amount": {
            "type": "number",
            "minimum": 0,
            "description": "Payment amount"
        },
        "currency": {
            "type": "string",
            "pattern": "^[A-Z]{3}$",
            "description": "ISO 4217 currency code"
        },
        "payment_date": {
            "type": "string",
            "format": "date",
            "description": "Scheduled payment date"
        },
        "settlement_date": {
            "type": ["string", "null"],
            "format": "date",
            "description": "Actual bank settlement date"
        },
        "status": {
            "type": "string",
            "enum": ["CREATED", "VALIDATED", "APPROVED", "FORMATTED", "TRANSMITTED", "SETTLED", "FAILED", "VOIDED", "CANCELLED"],
            "description": "Payment lifecycle status"
        },
        "payment_method": {
            "type": "string",
            "enum": ["EFT", "CHECK", "WIRE", "ACH", "RTGS", "SWIFT"],
            "description": "Payment method"
        },
        "bank_account_id": {
            "type": "string",
            "maxLength": 50,
            "description": "Disbursement bank account ID"
        },
        "bank_reference": {
            "type": ["string", "null"],
            "maxLength": 100,
            "description": "Bank transaction reference"
        },
        "approval_status": {
            "type": "string",
            "enum": ["PENDING", "APPROVED", "REJECTED", "NOT_REQUIRED"],
            "description": "Approval workflow status"
        },
        "business_unit": {
            "type": "string",
            "maxLength": 50,
            "description": "Business unit code"
        },
        "legal_entity": {
            "type": "string",
            "maxLength": 50,
            "description": "Legal entity code"
        },
        "created_at": {
            "type": "string",
            "format": "date-time",
            "description": "Record creation timestamp (ISO 8601)"
        },
        "updated_at": {
            "type": ["string", "null"],
            "format": "date-time",
            "description": "Last update timestamp (ISO 8601)"
        }
    }
}
```

---

## 2. Invoice

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `invoice_id` | String (50) | Yes | Unique invoice identifier | Oracle Fusion AP |
| `invoice_number` | String (50) | Yes | Human-readable invoice number | Oracle Fusion AP |
| `invoice_type` | Enum | Yes | Invoice type (STANDARD, CREDIT, DEBIT, PREPAYMENT) | Oracle Fusion AP |
| `supplier_id` | String (50) | Yes | Supplier identifier | Oracle Fusion AP |
| `supplier_name` | String (200) | Yes | Supplier display name | Oracle Fusion Supplier Mgmt |
| `supplier_site` | String (100) | No | Supplier site code | Oracle Fusion Supplier Mgmt |
| `invoice_amount` | Decimal (15,2) | Yes | Total invoice amount | Oracle Fusion AP |
| `currency` | String (3) | Yes | ISO 4217 currency code | Oracle Fusion AP |
| `invoice_date` | Date | Yes | Invoice date | Oracle Fusion AP |
| `due_date` | Date | Yes | Payment due date | Oracle Fusion AP |
| `payment_terms` | String (50) | Yes | Payment terms description | Oracle Fusion AP |
| `po_number` | String (50) | No | Purchase order reference | Oracle Fusion Procurement |
| `description` | String (500) | No | Invoice description | Oracle Fusion AP |
| `approval_status` | Enum | Yes | Approval workflow status | Oracle Fusion AP |
| `payment_status` | Enum | Yes | Payment status | Oracle Fusion AP |
| `hold_status` | Enum | Yes | Whether invoice is on hold | Oracle Fusion AP |
| `matching_status` | Enum | No | PO matching status (2-way, 3-way) | Oracle Fusion AP |
| `lines` | Array | No | Invoice line items | Oracle Fusion AP |
| `business_unit` | String (50) | Yes | Business unit code | Oracle Fusion |
| `legal_entity` | String (50) | Yes | Legal entity code | Oracle Fusion |
| `created_at` | Timestamp (TZ) | Yes | Record creation timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "Invoice",
    "description": "Represents an invoice record from Oracle Fusion Accounts Payable",
    "type": "object",
    "required": ["invoice_id", "invoice_number", "invoice_type", "supplier_id", "supplier_name", "invoice_amount", "currency", "invoice_date", "due_date", "payment_terms", "approval_status", "payment_status", "hold_status", "business_unit", "legal_entity"],
    "properties": {
        "invoice_id": {"type": "string", "maxLength": 50},
        "invoice_number": {"type": "string", "maxLength": 50},
        "invoice_type": {"type": "string", "enum": ["STANDARD", "CREDIT", "DEBIT", "PREPAYMENT", "MIXED"]},
        "supplier_id": {"type": "string", "maxLength": 50},
        "supplier_name": {"type": "string", "maxLength": 200},
        "supplier_site": {"type": ["string", "null"], "maxLength": 100},
        "invoice_amount": {"type": "number"},
        "currency": {"type": "string", "pattern": "^[A-Z]{3}$"},
        "invoice_date": {"type": "string", "format": "date"},
        "due_date": {"type": "string", "format": "date"},
        "payment_terms": {"type": "string", "maxLength": 50},
        "po_number": {"type": ["string", "null"], "maxLength": 50},
        "description": {"type": ["string", "null"], "maxLength": 500},
        "approval_status": {"type": "string", "enum": ["INITIATED", "IN_PROCESS", "APPROVED", "REJECTED", "WFAPPROVED"]},
        "payment_status": {"type": "string", "enum": ["NOT_PAID", "PARTIALLY_PAID", "FULLY_PAID"]},
        "hold_status": {"type": "string", "enum": ["NONE", "HELD", "RELEASED"]},
        "matching_status": {"type": ["string", "null"], "enum": ["NOT_MATCHED", "MATCHED", "PARTIAL_MATCH", null]},
        "lines": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "line_number": {"type": "integer"},
                    "description": {"type": "string"},
                    "quantity": {"type": "number"},
                    "unit_price": {"type": "number"},
                    "amount": {"type": "number"},
                    "po_line_number": {"type": ["integer", "null"]},
                    "tax_amount": {"type": "number", "default": 0}
                }
            }
        },
        "business_unit": {"type": "string", "maxLength": 50},
        "legal_entity": {"type": "string", "maxLength": 50},
        "created_at": {"type": "string", "format": "date-time"}
    }
}
```

---

## 3. Supplier

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `supplier_id` | String (50) | Yes | Unique supplier identifier | Oracle Fusion Supplier Mgmt |
| `supplier_name` | String (200) | Yes | Legal name of the supplier | Oracle Fusion Supplier Mgmt |
| `supplier_number` | String (30) | Yes | Supplier number | Oracle Fusion Supplier Mgmt |
| `supplier_type` | Enum | Yes | Supplier classification | Oracle Fusion Supplier Mgmt |
| `tax_id` | String (20) | No | Tax identification number (masked) | Oracle Fusion Supplier Mgmt |
| `status` | Enum | Yes | Supplier status | Oracle Fusion Supplier Mgmt |
| `payment_terms` | String (50) | No | Default payment terms | Oracle Fusion Supplier Mgmt |
| `payment_method` | Enum | No | Preferred payment method | Oracle Fusion Supplier Mgmt |
| `currency` | String (3) | No | Default payment currency | Oracle Fusion Supplier Mgmt |
| `sites` | Array | No | Supplier sites | Oracle Fusion Supplier Mgmt |
| `bank_accounts` | Array | No | Supplier bank accounts (masked) | Oracle Fusion Supplier Mgmt |
| `contact_name` | String (200) | No | Primary contact name | Oracle Fusion Supplier Mgmt |
| `contact_email` | String (200) | No | Primary contact email | Oracle Fusion Supplier Mgmt |
| `risk_category` | Enum | No | Assigned risk category | Payment Agent (internal) |
| `total_ytd_payments` | Decimal (15,2) | No | Year-to-date payment total | Computed |
| `created_at` | Timestamp (TZ) | Yes | Record creation timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "Supplier",
    "description": "Represents a supplier record from Oracle Fusion Supplier Management (bank account details are masked)",
    "type": "object",
    "required": ["supplier_id", "supplier_name", "supplier_number", "supplier_type", "status"],
    "properties": {
        "supplier_id": {"type": "string", "maxLength": 50},
        "supplier_name": {"type": "string", "maxLength": 200},
        "supplier_number": {"type": "string", "maxLength": 30},
        "supplier_type": {"type": "string", "enum": ["VENDOR", "CONTRACTOR", "EMPLOYEE", "GOVERNMENT", "INTERCOMPANY"]},
        "tax_id": {
            "type": ["string", "null"],
            "maxLength": 20,
            "description": "Masked — only last 4 characters visible (e.g., ***-**-6789)"
        },
        "status": {"type": "string", "enum": ["ACTIVE", "INACTIVE", "SUSPENDED"]},
        "payment_terms": {"type": ["string", "null"], "maxLength": 50},
        "payment_method": {"type": ["string", "null"], "enum": ["EFT", "CHECK", "WIRE", "ACH", null]},
        "currency": {"type": ["string", "null"], "pattern": "^[A-Z]{3}$"},
        "sites": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "site_code": {"type": "string"},
                    "site_name": {"type": "string"},
                    "address": {"type": "string"},
                    "country": {"type": "string"},
                    "primary": {"type": "boolean"}
                }
            }
        },
        "bank_accounts": {
            "type": "array",
            "description": "Bank account numbers are always masked — only last 4 digits visible",
            "items": {
                "type": "object",
                "properties": {
                    "bank_account_id": {"type": "string"},
                    "bank_name": {"type": "string"},
                    "branch_name": {"type": "string"},
                    "masked_account_number": {"type": "string", "description": "e.g., ****5678"},
                    "currency": {"type": "string"},
                    "primary": {"type": "boolean"},
                    "status": {"type": "string", "enum": ["ACTIVE", "INACTIVE"]}
                }
            }
        },
        "contact_name": {"type": ["string", "null"], "maxLength": 200},
        "contact_email": {"type": ["string", "null"], "maxLength": 200, "format": "email"},
        "risk_category": {"type": ["string", "null"], "enum": ["LOW", "MEDIUM", "HIGH", null]},
        "total_ytd_payments": {"type": ["number", "null"]},
        "created_at": {"type": "string", "format": "date-time"}
    }
}
```

---

## 4. Payment Exception

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `exception_id` | String (36) | Yes | Unique exception identifier (UUID) | Payment Agent |
| `payment_id` | String (50) | Yes | Referenced payment | Oracle Fusion Payments |
| `invoice_id` | String (50) | No | Referenced invoice | Oracle Fusion AP |
| `supplier_id` | String (50) | No | Referenced supplier | Oracle Fusion Supplier Mgmt |
| `exception_type` | Enum | Yes | Exception classification | Payment Agent |
| `exception_code` | String (20) | No | Bank return code or system error code | Bank / System |
| `severity` | Enum | Yes | CRITICAL, HIGH, MEDIUM, LOW | Payment Agent (AI classified) |
| `priority` | Enum | Yes | P1, P2, P3, P4 | Payment Agent (AI classified) |
| `status` | Enum | Yes | Exception lifecycle status | Payment Agent |
| `root_cause` | Text | No | AI-generated root cause analysis | Payment Agent (GenAI) |
| `resolution_recommendation` | Text | No | AI-generated resolution recommendation | Payment Agent (GenAI) |
| `resolution_action` | Text | No | Actual resolution action taken | Human input |
| `assigned_to` | String (100) | No | User assigned to resolve | Payment Agent |
| `servicenow_ticket_id` | String (50) | No | Linked ServiceNow incident | ServiceNow |
| `ai_confidence_score` | Decimal (3,2) | No | AI confidence in root cause (0.00–1.00) | Payment Agent (GenAI) |
| `sla_deadline` | Timestamp (TZ) | No | SLA resolution deadline | Payment Agent |
| `escalation_count` | Integer | No | Number of escalations | Payment Agent |
| `created_at` | Timestamp (TZ) | Yes | Created timestamp | System |
| `resolved_at` | Timestamp (TZ) | No | Resolution timestamp | System |

**Exception Type Enum**: `BANK_REJECT`, `VALIDATION_FAILURE`, `INSUFFICIENT_FUNDS`, `APPROVAL_TIMEOUT`, `MATCHING_EXCEPTION`, `RECONCILIATION_EXCEPTION`, `DUPLICATE_DETECTED`, `FRAUD_ALERT`, `SYSTEM_ERROR`, `COMPLIANCE_VIOLATION`

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "PaymentException",
    "description": "Represents a payment exception detected and managed by the Payment Agent",
    "type": "object",
    "required": ["exception_id", "payment_id", "exception_type", "severity", "priority", "status"],
    "properties": {
        "exception_id": {"type": "string", "format": "uuid"},
        "payment_id": {"type": "string", "maxLength": 50},
        "invoice_id": {"type": ["string", "null"], "maxLength": 50},
        "supplier_id": {"type": ["string", "null"], "maxLength": 50},
        "exception_type": {
            "type": "string",
            "enum": ["BANK_REJECT", "VALIDATION_FAILURE", "INSUFFICIENT_FUNDS", "APPROVAL_TIMEOUT", "MATCHING_EXCEPTION", "RECONCILIATION_EXCEPTION", "DUPLICATE_DETECTED", "FRAUD_ALERT", "SYSTEM_ERROR", "COMPLIANCE_VIOLATION"]
        },
        "exception_code": {"type": ["string", "null"], "maxLength": 20},
        "severity": {"type": "string", "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW"]},
        "priority": {"type": "string", "enum": ["P1", "P2", "P3", "P4"]},
        "status": {"type": "string", "enum": ["OPEN", "IN_PROGRESS", "RESOLVED", "ESCALATED", "CLOSED"]},
        "root_cause": {"type": ["string", "null"]},
        "resolution_recommendation": {"type": ["string", "null"]},
        "resolution_action": {"type": ["string", "null"]},
        "assigned_to": {"type": ["string", "null"], "maxLength": 100},
        "servicenow_ticket_id": {"type": ["string", "null"], "maxLength": 50},
        "ai_confidence_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "sla_deadline": {"type": ["string", "null"], "format": "date-time"},
        "escalation_count": {"type": "integer", "minimum": 0, "default": 0},
        "created_at": {"type": "string", "format": "date-time"},
        "resolved_at": {"type": ["string", "null"], "format": "date-time"}
    }
}
```

---

## 5. Approval Task

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `task_id` | String (36) | Yes | Unique approval task identifier (UUID) | Payment Agent |
| `payment_id` | String (50) | Yes | Payment requiring approval | Oracle Fusion Payments |
| `payment_batch_id` | String (50) | No | Payment batch ID | Oracle Fusion Payments |
| `amount` | Decimal (15,2) | Yes | Payment amount | Oracle Fusion Payments |
| `currency` | String (3) | Yes | Currency code | Oracle Fusion Payments |
| `supplier_id` | String (50) | No | Supplier identifier | Oracle Fusion Supplier Mgmt |
| `approval_level` | Integer | Yes | Approval level (1, 2, 3) | Payment Agent |
| `approver_user_id` | String (100) | Yes | Assigned approver | Payment Agent |
| `approver_role` | String (50) | No | Approver's RBAC role | OCI IAM |
| `status` | Enum | Yes | Task lifecycle status | Payment Agent |
| `ai_recommendation` | Enum | No | AI recommendation | Payment Agent (GenAI) |
| `ai_recommendation_rationale` | Text | No | AI explanation | Payment Agent (GenAI) |
| `ai_risk_score` | Decimal (3,2) | No | Risk score from Risk Agent | Risk Agent |
| `policy_compliance` | Enum | No | Whether payment complies with policies | Payment Agent |
| `decision` | Enum | No | Final human decision | Human input |
| `decision_comments` | Text | No | Approver's comments | Human input |
| `decided_at` | Timestamp (TZ) | No | Decision timestamp | System |
| `created_at` | Timestamp (TZ) | Yes | Task creation timestamp | System |
| `sla_deadline` | Timestamp (TZ) | No | Approval SLA deadline | Payment Agent |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "ApprovalTask",
    "description": "Represents a payment approval task within the human-in-the-loop workflow",
    "type": "object",
    "required": ["task_id", "payment_id", "amount", "currency", "approval_level", "approver_user_id", "status"],
    "properties": {
        "task_id": {"type": "string", "format": "uuid"},
        "payment_id": {"type": "string", "maxLength": 50},
        "payment_batch_id": {"type": ["string", "null"], "maxLength": 50},
        "amount": {"type": "number", "minimum": 0},
        "currency": {"type": "string", "pattern": "^[A-Z]{3}$"},
        "supplier_id": {"type": ["string", "null"], "maxLength": 50},
        "approval_level": {"type": "integer", "minimum": 1, "maximum": 5},
        "approver_user_id": {"type": "string", "maxLength": 100},
        "approver_role": {"type": ["string", "null"], "maxLength": 50},
        "status": {"type": "string", "enum": ["PENDING", "APPROVED", "REJECTED", "ESCALATED", "EXPIRED"]},
        "ai_recommendation": {"type": ["string", "null"], "enum": ["APPROVE", "REJECT", "HOLD", null]},
        "ai_recommendation_rationale": {"type": ["string", "null"]},
        "ai_risk_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "policy_compliance": {"type": ["string", "null"], "enum": ["COMPLIANT", "NON_COMPLIANT", null]},
        "decision": {"type": ["string", "null"], "enum": ["APPROVED", "REJECTED", null]},
        "decision_comments": {"type": ["string", "null"]},
        "decided_at": {"type": ["string", "null"], "format": "date-time"},
        "created_at": {"type": "string", "format": "date-time"},
        "sla_deadline": {"type": ["string", "null"], "format": "date-time"}
    }
}
```

---

## 6. Reconciliation Record

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `recon_id` | String (36) | Yes | Unique reconciliation record ID (UUID) | Payment Agent |
| `batch_id` | String (50) | Yes | Reconciliation batch ID | Payment Agent |
| `bank_statement_ref` | String (100) | No | Bank statement line reference | Bank statement |
| `bank_amount` | Decimal (15,2) | No | Amount from bank statement | Bank statement |
| `bank_date` | Date | No | Transaction date from bank | Bank statement |
| `bank_description` | String (500) | No | Description from bank statement | Bank statement |
| `erp_payment_id` | String (50) | No | Matched payment from ERP | Oracle Fusion |
| `erp_amount` | Decimal (15,2) | No | Payment amount in ERP | Oracle Fusion |
| `erp_date` | Date | No | Payment date in ERP | Oracle Fusion |
| `match_status` | Enum | Yes | Matching result | Payment Agent |
| `match_confidence` | Decimal (3,2) | No | Matching confidence score (0.00–1.00) | Payment Agent |
| `match_method` | Enum | No | How the match was made | Payment Agent |
| `resolution` | String (50) | No | Resolution category for unmatched | Payment Agent |
| `resolution_recommendation` | Text | No | AI recommendation for unmatched items | Payment Agent (GenAI) |
| `approved_by` | String (100) | No | Approver for manual matches | Human input |
| `created_at` | Timestamp (TZ) | Yes | Record creation timestamp | System |
| `resolved_at` | Timestamp (TZ) | No | Resolution timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "ReconciliationRecord",
    "description": "Represents a bank-to-ERP reconciliation matching record",
    "type": "object",
    "required": ["recon_id", "batch_id", "match_status"],
    "properties": {
        "recon_id": {"type": "string", "format": "uuid"},
        "batch_id": {"type": "string", "maxLength": 50},
        "bank_statement_ref": {"type": ["string", "null"], "maxLength": 100},
        "bank_amount": {"type": ["number", "null"]},
        "bank_date": {"type": ["string", "null"], "format": "date"},
        "bank_description": {"type": ["string", "null"], "maxLength": 500},
        "erp_payment_id": {"type": ["string", "null"], "maxLength": 50},
        "erp_amount": {"type": ["number", "null"]},
        "erp_date": {"type": ["string", "null"], "format": "date"},
        "match_status": {"type": "string", "enum": ["MATCHED", "UNMATCHED", "PARTIAL", "EXCEPTION"]},
        "match_confidence": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "match_method": {"type": ["string", "null"], "enum": ["EXACT", "FUZZY", "AI_RECOMMENDED", "MANUAL", null]},
        "resolution": {"type": ["string", "null"], "maxLength": 50},
        "resolution_recommendation": {"type": ["string", "null"]},
        "approved_by": {"type": ["string", "null"], "maxLength": 100},
        "created_at": {"type": "string", "format": "date-time"},
        "resolved_at": {"type": ["string", "null"], "format": "date-time"}
    }
}
```

---

## 7. Audit Event

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `audit_id` | String (36) | Yes | Unique audit event ID (UUID) | Payment Agent |
| `request_id` | String (36) | Yes | Originating request ID | Payment Agent |
| `session_id` | String (36) | No | Conversation session ID | Payment Agent |
| `user_id` | String (100) | Yes | Authenticated user | OCI IAM |
| `user_role` | String (50) | Yes | User's RBAC role at time of action | OCI IAM |
| `agent_id` | String (20) | Yes | Agent that processed the request | Payment Agent |
| `action_type` | Enum | Yes | Category of action | Payment Agent |
| `request_summary` | Text | No | PII-masked summary of user request | Payment Agent |
| `response_summary` | Text | No | PII-masked summary of agent response | Payment Agent |
| `data_accessed` | JSON Array | No | List of entities/records accessed | Payment Agent |
| `decision` | String (50) | No | AI recommendation or human decision | Payment Agent |
| `decision_rationale` | Text | No | Explanation of reasoning | Payment Agent (GenAI) |
| `confidence_score` | Decimal (3,2) | No | Agent confidence (0.00–1.00) | Payment Agent |
| `risk_score` | Decimal (3,2) | No | Risk score if applicable | Risk Agent |
| `human_override` | Boolean | No | Whether human overrode AI recommendation | Payment Agent |
| `source_citations` | JSON Array | No | References to source documents/data | RAG Service |
| `ip_address_hash` | String (64) | No | SHA-256 hash of client IP | Payment Agent |
| `compliance_tags` | String (200) | No | Applicable compliance frameworks | Payment Agent |
| `created_at` | Timestamp (TZ) | Yes | Event timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "AuditEvent",
    "description": "Immutable audit trail record for AI agent actions and human decisions",
    "type": "object",
    "required": ["audit_id", "request_id", "user_id", "user_role", "agent_id", "action_type", "created_at"],
    "properties": {
        "audit_id": {"type": "string", "format": "uuid"},
        "request_id": {"type": "string", "format": "uuid"},
        "session_id": {"type": ["string", "null"], "format": "uuid"},
        "user_id": {"type": "string", "maxLength": 100},
        "user_role": {"type": "string", "maxLength": 50},
        "agent_id": {"type": "string", "maxLength": 20},
        "action_type": {"type": "string", "enum": ["INQUIRY", "RECOMMENDATION", "APPROVAL", "EXCEPTION", "ALERT", "CONFIG_CHANGE"]},
        "request_summary": {"type": ["string", "null"]},
        "response_summary": {"type": ["string", "null"]},
        "data_accessed": {"type": ["array", "null"], "items": {"type": "string"}},
        "decision": {"type": ["string", "null"], "maxLength": 50},
        "decision_rationale": {"type": ["string", "null"]},
        "confidence_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "risk_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "human_override": {"type": "boolean", "default": false},
        "source_citations": {
            "type": ["array", "null"],
            "items": {
                "type": "object",
                "properties": {
                    "source": {"type": "string"},
                    "reference": {"type": "string"},
                    "accessed_at": {"type": "string", "format": "date-time"}
                }
            }
        },
        "ip_address_hash": {"type": ["string", "null"], "maxLength": 64},
        "compliance_tags": {"type": ["string", "null"], "maxLength": 200},
        "created_at": {"type": "string", "format": "date-time"}
    }
}
```

---

## 8. AI Recommendation

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `recommendation_id` | String (36) | Yes | Unique recommendation ID (UUID) | Payment Agent |
| `request_id` | String (36) | Yes | Originating request ID | Payment Agent |
| `agent_id` | String (20) | Yes | Agent that generated the recommendation | Payment Agent |
| `recommendation_type` | Enum | Yes | Type of recommendation | Payment Agent |
| `recommendation_text` | Text | Yes | Recommendation in natural language | Payment Agent (GenAI) |
| `supporting_evidence` | JSON | No | Structured evidence supporting the recommendation | Payment Agent |
| `confidence_score` | Decimal (3,2) | Yes | AI confidence (0.00–1.00) | Payment Agent (GenAI) |
| `risk_score` | Decimal (3,2) | No | Associated risk score | Risk Agent |
| `human_decision` | Enum | No | Human's decision on the recommendation | Human input |
| `human_decision_reason` | Text | No | Human's reason for decision | Human input |
| `feedback_score` | Integer | No | User feedback rating (1–5) | Human input |
| `created_at` | Timestamp (TZ) | Yes | Recommendation timestamp | System |
| `decided_at` | Timestamp (TZ) | No | Human decision timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "AIRecommendation",
    "description": "Represents an AI-generated recommendation from any Payment Agent",
    "type": "object",
    "required": ["recommendation_id", "request_id", "agent_id", "recommendation_type", "recommendation_text", "confidence_score"],
    "properties": {
        "recommendation_id": {"type": "string", "format": "uuid"},
        "request_id": {"type": "string", "format": "uuid"},
        "agent_id": {"type": "string", "maxLength": 20},
        "recommendation_type": {
            "type": "string",
            "enum": ["APPROVAL", "RESOLUTION", "DISCOUNT_CAPTURE", "FRAUD_ALERT", "RECONCILIATION_MATCH", "DUPLICATE_HOLD", "ESCALATION", "PROCESS_IMPROVEMENT"]
        },
        "recommendation_text": {"type": "string"},
        "supporting_evidence": {
            "type": ["object", "null"],
            "properties": {
                "data_points": {"type": "array", "items": {"type": "string"}},
                "policy_references": {"type": "array", "items": {"type": "string"}},
                "historical_precedent": {"type": ["string", "null"]},
                "risk_factors": {"type": "array", "items": {"type": "string"}}
            }
        },
        "confidence_score": {"type": "number", "minimum": 0, "maximum": 1},
        "risk_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
        "human_decision": {"type": ["string", "null"], "enum": ["ACCEPTED", "REJECTED", "MODIFIED", "PENDING", null]},
        "human_decision_reason": {"type": ["string", "null"]},
        "feedback_score": {"type": ["integer", "null"], "minimum": 1, "maximum": 5},
        "created_at": {"type": "string", "format": "date-time"},
        "decided_at": {"type": ["string", "null"], "format": "date-time"}
    }
}
```

---

## 9. User Interaction

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `interaction_id` | String (36) | Yes | Unique interaction ID (UUID) | Payment Agent |
| `session_id` | String (36) | Yes | Conversation session ID | Payment Agent |
| `user_id` | String (100) | Yes | Authenticated user | OCI IAM |
| `channel` | Enum | Yes | Interaction channel | Payment Agent |
| `intent` | String (50) | No | Classified intent | ODA / Orchestrator |
| `query_text_masked` | Text | No | PII-masked user query | Payment Agent |
| `response_text_masked` | Text | No | PII-masked agent response | Payment Agent |
| `agent_id` | String (20) | No | Agent that handled the request | Payment Agent |
| `response_time_ms` | Integer | No | End-to-end response time in milliseconds | Payment Agent |
| `token_count_input` | Integer | No | LLM input tokens consumed | OCI GenAI |
| `token_count_output` | Integer | No | LLM output tokens consumed | OCI GenAI |
| `satisfaction_rating` | Integer | No | User satisfaction rating (1–5) | User feedback |
| `created_at` | Timestamp (TZ) | Yes | Interaction timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "UserInteraction",
    "description": "Represents a single user interaction with the Payment Agent (PII-masked)",
    "type": "object",
    "required": ["interaction_id", "session_id", "user_id", "channel"],
    "properties": {
        "interaction_id": {"type": "string", "format": "uuid"},
        "session_id": {"type": "string", "format": "uuid"},
        "user_id": {"type": "string", "maxLength": 100},
        "channel": {"type": "string", "enum": ["ODA", "TEAMS", "SLACK", "PORTAL", "EMAIL", "API"]},
        "intent": {"type": ["string", "null"], "maxLength": 50},
        "query_text_masked": {"type": ["string", "null"]},
        "response_text_masked": {"type": ["string", "null"]},
        "agent_id": {"type": ["string", "null"], "maxLength": 20},
        "response_time_ms": {"type": ["integer", "null"], "minimum": 0},
        "token_count_input": {"type": ["integer", "null"], "minimum": 0},
        "token_count_output": {"type": ["integer", "null"], "minimum": 0},
        "satisfaction_rating": {"type": ["integer", "null"], "minimum": 1, "maximum": 5},
        "created_at": {"type": "string", "format": "date-time"}
    }
}
```

---

## 10. Risk Score

### Field Table

| Field | Type | Required | Description | Source |
|---|---|---|---|---|
| `risk_id` | String (36) | Yes | Unique risk assessment ID (UUID) | Payment Agent |
| `payment_id` | String (50) | Yes | Assessed payment | Oracle Fusion Payments |
| `supplier_id` | String (50) | No | Supplier identifier | Oracle Fusion Supplier Mgmt |
| `overall_score` | Decimal (3,2) | Yes | Overall risk score (0.00–1.00) | Risk Agent (GenAI) |
| `risk_category` | Enum | Yes | Risk classification | Risk Agent |
| `risk_factors` | JSON Array | Yes | Contributing risk factors with weights | Risk Agent (GenAI) |
| `anomaly_details` | JSON | No | Detailed anomaly evidence | Risk Agent (GenAI) |
| `explainability` | Text | No | Human-readable risk explanation | Risk Agent (GenAI) |
| `flagged_for_review` | Boolean | No | Whether flagged for human review | Risk Agent |
| `reviewed_by` | String (100) | No | Reviewer's user ID | Human input |
| `review_decision` | Enum | No | Reviewer's decision | Human input |
| `created_at` | Timestamp (TZ) | Yes | Assessment timestamp | System |
| `reviewed_at` | Timestamp (TZ) | No | Review timestamp | System |

### JSON Schema

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "RiskScore",
    "description": "Represents a risk assessment for a payment, generated by the Risk and Fraud Detection Agent",
    "type": "object",
    "required": ["risk_id", "payment_id", "overall_score", "risk_category", "risk_factors"],
    "properties": {
        "risk_id": {"type": "string", "format": "uuid"},
        "payment_id": {"type": "string", "maxLength": 50},
        "supplier_id": {"type": ["string", "null"], "maxLength": 50},
        "overall_score": {"type": "number", "minimum": 0, "maximum": 1},
        "risk_category": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]},
        "risk_factors": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "factor": {"type": "string", "description": "Risk factor description"},
                    "weight": {"type": "number", "minimum": 0, "maximum": 1, "description": "Contribution to overall score"},
                    "evidence": {"type": "string", "description": "Supporting evidence"}
                },
                "required": ["factor", "weight"]
            }
        },
        "anomaly_details": {
            "type": ["object", "null"],
            "properties": {
                "anomaly_type": {"type": "string"},
                "baseline_value": {"type": "number"},
                "observed_value": {"type": "number"},
                "deviation_percentage": {"type": "number"},
                "historical_pattern": {"type": "string"}
            }
        },
        "explainability": {"type": ["string", "null"]},
        "flagged_for_review": {"type": "boolean", "default": false},
        "reviewed_by": {"type": ["string", "null"], "maxLength": 100},
        "review_decision": {"type": ["string", "null"], "enum": ["CONFIRMED_FRAUD", "FALSE_POSITIVE", "CLEARED", "ESCALATED", null]},
        "created_at": {"type": "string", "format": "date-time"},
        "reviewed_at": {"type": ["string", "null"], "format": "date-time"}
    }
}
```

---

*This document is referenced from [01-HLD.md](../01-HLD.md) and [02-LLD.md](../02-LLD.md).*
