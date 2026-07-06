# Mermaid Diagrams — Oracle AI-Powered Payment Agent

All architecture diagrams are provided as Mermaid source blocks. They render natively in GitHub, GitLab, Azure DevOps, and most modern IDEs. Use double-quoted labels; no color styling is applied to ensure maximum portability.

---

## 1. End-to-End Payment Agent Architecture

```mermaid
graph TB
    subgraph "User Channels"
        U1["Finance Analyst"]
        U2["AP Manager"]
        U3["Treasury Manager"]
        U4["Supplier"]
        U5["Auditor"]
    end

    subgraph "Interaction Layer"
        ODA["Oracle Digital Assistant"]
        TEAMS["Microsoft Teams / Slack"]
        PORTAL["Oracle Supplier Portal"]
        EMAIL["Email / Notification Service"]
    end

    subgraph "API Gateway & Security"
        APIGW["OCI API Gateway"]
        IAM["OCI IAM / OAuth2 / SSO"]
    end

    subgraph "AI Agent Orchestration Layer"
        ORCH["Multi-Agent Orchestrator"]
        PA1["Payment Inquiry Agent"]
        PA2["Payment Status Tracking Agent"]
        PA3["Payment Exception Agent"]
        PA4["Payment Approval Agent"]
        PA5["Payment Reconciliation Agent"]
        PA6["Duplicate Payment Detection Agent"]
        PA7["Supplier Communication Agent"]
        PA8["Risk and Fraud Detection Agent"]
        PA9["Compliance and Audit Agent"]
        PA10["Insights and Recommendation Agent"]
    end

    subgraph "AI / GenAI Services"
        GENAI["OCI Generative AI Service"]
        AIAGENTS["Oracle AI Agents"]
        DOCAI["Oracle Document Understanding"]
        LANGSVC["Oracle Language Service"]
        VISION["Oracle Vision AI"]
    end

    subgraph "RAG & Knowledge Layer"
        VECDB["Oracle Autonomous DB — AI Vector Search"]
        EMBED["Embedding Pipeline"]
        KBASE["Enterprise Knowledge Base"]
    end

    subgraph "Oracle Fusion Cloud ERP"
        AP["Accounts Payable"]
        AR["Accounts Receivable"]
        GL["General Ledger"]
        CM["Cash Management"]
        SLA["Subledger Accounting"]
        PAY["Payments"]
        SM["Supplier Management"]
        PROC["Procurement"]
        EXP["Expenses"]
        FR["Financial Reporting"]
    end

    subgraph "Integration Layer"
        OIC["Oracle Integration Cloud"]
        EVENTS["OCI Events / Streaming"]
        FUNCS["OCI Functions"]
        GG["Oracle GoldenGate"]
    end

    subgraph "External Systems"
        BANK["Bank Payment Gateway"]
        BANKSTMT["Bank Statement Files"]
        SNOW["ServiceNow / Ticketing"]
        DL["Enterprise Data Lake"]
    end

    subgraph "Observability & Security"
        MON["OCI Monitoring / APM"]
        LOG["OCI Logging"]
        CG["Oracle Cloud Guard"]
        DS["Oracle Data Safe"]
        VAULT["OCI Vault"]
    end

    U1 --> ODA
    U2 --> ODA
    U3 --> ODA
    U4 --> PORTAL
    U5 --> ODA
    ODA --> APIGW
    TEAMS --> APIGW
    PORTAL --> APIGW
    EMAIL --> APIGW
    APIGW --> IAM
    IAM --> ORCH
    ORCH --> PA1
    ORCH --> PA2
    ORCH --> PA3
    ORCH --> PA4
    ORCH --> PA5
    ORCH --> PA6
    ORCH --> PA7
    ORCH --> PA8
    ORCH --> PA9
    ORCH --> PA10
    PA1 --> GENAI
    PA3 --> GENAI
    PA8 --> GENAI
    PA10 --> GENAI
    PA1 --> VECDB
    PA3 --> VECDB
    PA5 --> VECDB
    PA8 --> VECDB
    GENAI --> EMBED
    EMBED --> VECDB
    KBASE --> EMBED
    PA1 --> OIC
    PA2 --> OIC
    PA3 --> OIC
    PA4 --> OIC
    PA5 --> OIC
    PA6 --> OIC
    PA7 --> OIC
    PA8 --> OIC
    PA9 --> OIC
    PA10 --> OIC
    OIC --> AP
    OIC --> AR
    OIC --> GL
    OIC --> CM
    OIC --> SLA
    OIC --> PAY
    OIC --> SM
    OIC --> PROC
    OIC --> EXP
    OIC --> FR
    OIC --> BANK
    OIC --> BANKSTMT
    OIC --> SNOW
    OIC --> DL
    EVENTS --> FUNCS
    GG --> DL
    ORCH --> MON
    ORCH --> LOG
    CG --> ORCH
    DS --> VECDB
    VAULT --> ORCH
```

---

## 2. Payment Inquiry Flow

```mermaid
sequenceDiagram
    participant User as "Finance Analyst"
    participant ODA as "Oracle Digital Assistant"
    participant APIGW as "OCI API Gateway"
    participant ORCH as "Agent Orchestrator"
    participant PIAgent as "Payment Inquiry Agent"
    participant GenAI as "OCI Generative AI"
    participant RAG as "RAG — Vector Search"
    participant OIC as "Oracle Integration Cloud"
    participant Fusion as "Oracle Fusion AP/Payments"

    User->>ODA: "What is the status of payment 50012?"
    ODA->>APIGW: POST /api/v1/agent/inquiry
    APIGW->>APIGW: Validate OAuth2 token
    APIGW->>ORCH: Route to Payment Inquiry
    ORCH->>PIAgent: Dispatch inquiry request
    PIAgent->>OIC: GET /fscmRestApi/resources/payments/{id}
    OIC->>Fusion: Fetch payment record
    Fusion-->>OIC: Payment details JSON
    OIC-->>PIAgent: Payment data
    PIAgent->>RAG: Retrieve relevant policies & context
    RAG-->>PIAgent: Grounded context documents
    PIAgent->>GenAI: Generate NL summary with grounding
    GenAI-->>PIAgent: Natural language response
    PIAgent-->>ORCH: Structured response + citations
    ORCH-->>APIGW: Response envelope
    APIGW-->>ODA: Display response
    ODA-->>User: "Payment 50012 — Settled on 2026-07-01..."
```

---

## 3. Payment Exception Handling Flow

```mermaid
sequenceDiagram
    participant System as "Oracle Fusion Payments"
    participant Events as "OCI Events"
    participant ORCH as "Agent Orchestrator"
    participant ExAgent as "Payment Exception Agent"
    participant GenAI as "OCI Generative AI"
    participant RAG as "RAG — Vector Search"
    participant OIC as "Oracle Integration Cloud"
    participant SNOW as "ServiceNow"
    participant Approver as "AP Manager"
    participant ODA as "Oracle Digital Assistant"

    System->>Events: Payment failed event
    Events->>ORCH: Trigger exception workflow
    ORCH->>ExAgent: Dispatch exception
    ExAgent->>OIC: Fetch payment + invoice details
    OIC-->>ExAgent: Payment and invoice data
    ExAgent->>RAG: Retrieve bank return codes & SOPs
    RAG-->>ExAgent: Exception handling procedures
    ExAgent->>GenAI: Analyze root cause + recommend action
    GenAI-->>ExAgent: Root cause + recommendation
    ExAgent->>OIC: Log exception record in Fusion
    ExAgent->>SNOW: Create incident ticket
    SNOW-->>ExAgent: Ticket INC-78901
    ExAgent->>ODA: Notify AP Manager
    ODA->>Approver: "Payment EXC-4521 failed — bank reject code AC04..."
    Approver->>ODA: "Re-submit with corrected bank account"
    ODA->>ExAgent: Manual override instruction
    ExAgent->>OIC: Re-submit payment with correction
    OIC->>System: Updated payment instruction
```

---

## 4. Payment Approval Flow

```mermaid
sequenceDiagram
    participant System as "Oracle Fusion Payments"
    participant Events as "OCI Events"
    participant ORCH as "Agent Orchestrator"
    participant ApprAgent as "Payment Approval Agent"
    participant GenAI as "OCI Generative AI"
    participant RAG as "RAG — Vector Search"
    participant RiskAgent as "Risk and Fraud Detection Agent"
    participant ODA as "Oracle Digital Assistant"
    participant Approver as "AP Manager"
    participant OIC as "Oracle Integration Cloud"

    System->>Events: Payment pending approval
    Events->>ORCH: Route to Approval Agent
    ORCH->>ApprAgent: Dispatch approval task
    ApprAgent->>OIC: Fetch payment + invoice + PO details
    OIC-->>ApprAgent: Full payment context
    ApprAgent->>RAG: Retrieve approval matrix & policies
    RAG-->>ApprAgent: Approval rules + delegation matrix
    ApprAgent->>RiskAgent: Request risk assessment
    RiskAgent-->>ApprAgent: Risk score 0.23 — Low
    ApprAgent->>GenAI: Generate approval recommendation
    GenAI-->>ApprAgent: "Recommend: Approve — within policy limits"
    ApprAgent->>ODA: Present to approver with context
    ODA->>Approver: Approval request + AI recommendation
    Approver->>ODA: "Approved"
    ODA->>ApprAgent: Approval decision
    ApprAgent->>OIC: Update approval status in Fusion
    OIC->>System: Payment approved — proceed to settlement
    ApprAgent->>ORCH: Log audit trail
```

---

## 5. Duplicate Payment Detection Flow

```mermaid
sequenceDiagram
    participant System as "Oracle Fusion AP"
    participant Events as "OCI Events"
    participant ORCH as "Agent Orchestrator"
    participant DupAgent as "Duplicate Payment Detection Agent"
    participant GenAI as "OCI Generative AI"
    participant VecDB as "Oracle Autonomous DB — Vector Search"
    participant OIC as "Oracle Integration Cloud"
    participant ODA as "Oracle Digital Assistant"
    participant Analyst as "Finance Analyst"

    System->>Events: New payment instruction created
    Events->>ORCH: Route to Duplicate Detection
    ORCH->>DupAgent: Dispatch detection request
    DupAgent->>OIC: Fetch payment + invoice details
    OIC-->>DupAgent: Payment record
    DupAgent->>VecDB: Semantic similarity search against history
    VecDB-->>DupAgent: Top-5 similar payments (score > 0.85)
    DupAgent->>GenAI: Analyze matches — confirm or dismiss
    GenAI-->>DupAgent: "Match confirmed — INV-2024-8832 paid on 2026-06-15"
    DupAgent->>OIC: Place payment on hold in Fusion
    DupAgent->>ODA: Alert analyst with evidence
    ODA->>Analyst: "Potential duplicate: Payment 60234 matches..."
    Analyst->>ODA: "Confirm — cancel duplicate"
    ODA->>DupAgent: Cancel instruction
    DupAgent->>OIC: Cancel payment + log reason
    DupAgent->>ORCH: Log audit event
```

---

## 6. Payment Reconciliation Flow

```mermaid
sequenceDiagram
    participant Bank as "Bank Statement (MT940/CAMT)"
    participant OIC as "Oracle Integration Cloud"
    participant ORCH as "Agent Orchestrator"
    participant RecAgent as "Payment Reconciliation Agent"
    participant GenAI as "OCI Generative AI"
    participant VecDB as "Oracle Autonomous DB"
    participant Fusion as "Oracle Cash Management"
    participant ODA as "Oracle Digital Assistant"
    participant Treasury as "Treasury Manager"

    Bank->>OIC: Ingest bank statement file
    OIC->>ORCH: Trigger reconciliation workflow
    ORCH->>RecAgent: Dispatch reconciliation batch
    RecAgent->>OIC: Fetch GL entries + payment records
    OIC-->>RecAgent: ERP transaction data
    RecAgent->>VecDB: Match bank entries to ERP records
    VecDB-->>RecAgent: Matched: 1247 / Unmatched: 23
    RecAgent->>GenAI: Analyze unmatched items — suggest resolution
    GenAI-->>RecAgent: Resolution recommendations
    RecAgent->>OIC: Auto-reconcile matched items in Fusion
    OIC->>Fusion: Post reconciliation entries
    RecAgent->>ODA: Notify treasury of exceptions
    ODA->>Treasury: "23 unmatched items require review..."
    Treasury->>ODA: "Apply recommended resolution for items 1-18"
    ODA->>RecAgent: Partial approval
    RecAgent->>OIC: Apply resolutions + flag remaining 5
```

---

## 7. Human-in-the-Loop Workflow

```mermaid
graph TB
    subgraph "AI Processing"
        REQ["Incoming Request"]
        AGENT["AI Agent Processing"]
        RISK["Risk Assessment"]
        REC["AI Recommendation"]
    end

    subgraph "Decision Gate"
        GATE{"Risk Level?"}
        AUTO["Auto-Execute"]
        REVIEW["Human Review Required"]
    end

    subgraph "Human Review"
        NOTIFY["Notify Approver via ODA"]
        PRESENT["Present Context + Recommendation"]
        DECISION{"Approver Decision"}
        APPROVE["Approved"]
        REJECT["Rejected"]
        MODIFY["Modified"]
    end

    subgraph "Execution"
        EXEC["Execute Action in Fusion"]
        AUDIT["Log Audit Trail"]
        FEEDBACK["Capture Feedback for Model Tuning"]
    end

    REQ --> AGENT
    AGENT --> RISK
    RISK --> REC
    REC --> GATE
    GATE -->|"Low Risk"| AUTO
    GATE -->|"Medium / High Risk"| REVIEW
    AUTO --> EXEC
    REVIEW --> NOTIFY
    NOTIFY --> PRESENT
    PRESENT --> DECISION
    DECISION -->|"Approve"| APPROVE
    DECISION -->|"Reject"| REJECT
    DECISION -->|"Modify"| MODIFY
    APPROVE --> EXEC
    REJECT --> AUDIT
    MODIFY --> AGENT
    EXEC --> AUDIT
    AUDIT --> FEEDBACK
```

---

## 8. RAG Architecture

```mermaid
graph TB
    subgraph "Knowledge Sources"
        KS1["Payment Policies"]
        KS2["Approval Matrix"]
        KS3["Supplier Master Data"]
        KS4["Invoice Data"]
        KS5["Payment History"]
        KS6["Bank Return Codes"]
        KS7["Reconciliation Rules"]
        KS8["Exception SOPs"]
        KS9["Audit Policies"]
        KS10["Compliance Documents"]
        KS11["Fusion Transactional Data"]
    end

    subgraph "Ingestion Pipeline"
        INGEST["Document Ingestion"]
        CHUNK["Chunking & Preprocessing"]
        EMBED["OCI Generative AI — Embedding Model"]
    end

    subgraph "Vector Storage"
        VECSTORE["Oracle Autonomous DB — AI Vector Search"]
        META["Metadata Store — Role / Category Tags"]
    end

    subgraph "Retrieval Pipeline"
        QUERY["User Query"]
        QEMBED["Query Embedding"]
        SEARCH["Semantic Similarity Search"]
        FILTER["Role-Based Context Filtering"]
        RERANK["Re-ranking & Relevance Scoring"]
    end

    subgraph "Generation"
        CONTEXT["Grounded Context Assembly"]
        LLM["OCI Generative AI — LLM"]
        RESPONSE["Cited Response with Sources"]
        GUARD["AI Guardrails Validation"]
    end

    KS1 --> INGEST
    KS2 --> INGEST
    KS3 --> INGEST
    KS4 --> INGEST
    KS5 --> INGEST
    KS6 --> INGEST
    KS7 --> INGEST
    KS8 --> INGEST
    KS9 --> INGEST
    KS10 --> INGEST
    KS11 --> INGEST
    INGEST --> CHUNK
    CHUNK --> EMBED
    EMBED --> VECSTORE
    EMBED --> META
    QUERY --> QEMBED
    QEMBED --> SEARCH
    SEARCH --> VECSTORE
    VECSTORE --> FILTER
    FILTER --> META
    FILTER --> RERANK
    RERANK --> CONTEXT
    CONTEXT --> LLM
    LLM --> RESPONSE
    RESPONSE --> GUARD
```

---

## 9. Multi-Agent Orchestration

```mermaid
graph TB
    subgraph "Request Intake"
        INPUT["User Request / System Event"]
        CLASSIFY["Intent Classification — OCI GenAI"]
        ROUTE["Agent Router"]
    end

    subgraph "Agent Pool"
        A1["Payment Inquiry Agent"]
        A2["Payment Status Tracking Agent"]
        A3["Payment Exception Agent"]
        A4["Payment Approval Agent"]
        A5["Payment Reconciliation Agent"]
        A6["Duplicate Payment Detection Agent"]
        A7["Supplier Communication Agent"]
        A8["Risk and Fraud Detection Agent"]
        A9["Compliance and Audit Agent"]
        A10["Insights and Recommendation Agent"]
    end

    subgraph "Shared Services"
        RAG["RAG — Knowledge Retrieval"]
        GENAI["OCI Generative AI"]
        OIC["Oracle Integration Cloud"]
        VECDB["Vector Database"]
    end

    subgraph "Orchestration Control"
        STATE["State Manager"]
        MEMORY["Conversation Memory"]
        CHAIN["Agent Chaining Logic"]
        GUARD["Guardrails Engine"]
    end

    subgraph "Output"
        RESP["Response Formatter"]
        AUDIT["Audit Logger"]
        FEEDBACK["Feedback Collector"]
    end

    INPUT --> CLASSIFY
    CLASSIFY --> ROUTE
    ROUTE --> A1
    ROUTE --> A2
    ROUTE --> A3
    ROUTE --> A4
    ROUTE --> A5
    ROUTE --> A6
    ROUTE --> A7
    ROUTE --> A8
    ROUTE --> A9
    ROUTE --> A10
    A1 --> RAG
    A3 --> RAG
    A5 --> RAG
    A8 --> RAG
    A1 --> GENAI
    A3 --> GENAI
    A7 --> GENAI
    A8 --> GENAI
    A10 --> GENAI
    A1 --> OIC
    A2 --> OIC
    A3 --> OIC
    A4 --> OIC
    A5 --> OIC
    A6 --> OIC
    A7 --> OIC
    A6 --> VECDB
    A8 --> VECDB
    STATE --> MEMORY
    STATE --> CHAIN
    CHAIN --> GUARD
    A1 --> STATE
    A2 --> STATE
    A3 --> STATE
    A4 --> STATE
    A5 --> STATE
    GUARD --> RESP
    RESP --> AUDIT
    AUDIT --> FEEDBACK
```

---

## 10. Security and Audit Flow

```mermaid
sequenceDiagram
    participant User as "User"
    participant ODA as "Oracle Digital Assistant"
    participant APIGW as "OCI API Gateway"
    participant IAM as "OCI IAM"
    participant ORCH as "Agent Orchestrator"
    participant GUARD as "Guardrails Engine"
    participant VAULT as "OCI Vault"
    participant MASK as "Data Masking Service"
    participant AGENT as "AI Agent"
    participant AUDIT as "Audit Logger"
    participant CG as "Oracle Cloud Guard"
    participant DS as "Oracle Data Safe"

    User->>ODA: Submit request
    ODA->>APIGW: Forward with credentials
    APIGW->>IAM: Validate token + RBAC check
    IAM-->>APIGW: Authorized — Role: AP_ANALYST
    APIGW->>ORCH: Authenticated request with claims
    ORCH->>GUARD: Pre-execution guardrail check
    GUARD-->>ORCH: Passed — no policy violation
    ORCH->>VAULT: Retrieve secrets for integration
    VAULT-->>ORCH: Decrypted credentials
    ORCH->>AGENT: Dispatch to agent
    AGENT->>MASK: Mask sensitive fields (bank acct, SSN)
    MASK-->>AGENT: Masked data
    AGENT-->>ORCH: Agent response
    ORCH->>GUARD: Post-execution guardrail check
    GUARD-->>ORCH: Validated — no PII leakage
    ORCH->>AUDIT: Log request + response + decision
    AUDIT->>CG: Security event notification
    AUDIT->>DS: Data access audit record
    ORCH-->>APIGW: Secured response
    APIGW-->>ODA: Display to user
```

---

## 11. Deployment Architecture

```mermaid
graph TB
    subgraph "OCI Region — Primary"
        subgraph "Public Subnet"
            APIGW["OCI API Gateway"]
            LB["OCI Load Balancer"]
        end

        subgraph "Private Subnet — Application Tier"
            OKE["OCI Kubernetes Engine (OKE)"]
            ORCH_POD["Orchestrator Pod"]
            AGENT_PODS["Agent Pods (10 agents)"]
            ODA_INT["ODA Integration Pod"]
            FUNCS["OCI Functions"]
        end

        subgraph "Private Subnet — AI Tier"
            GENAI["OCI Generative AI Endpoint"]
            EMBED_SVC["Embedding Service"]
            DOCAI["Document Understanding"]
        end

        subgraph "Private Subnet — Data Tier"
            ADB["Oracle Autonomous Database"]
            VECSTORE["AI Vector Search"]
            OBJSTORE["OCI Object Storage"]
        end

        subgraph "Integration Tier"
            OIC["Oracle Integration Cloud"]
            STREAM["OCI Streaming (Kafka)"]
            EVENTS["OCI Events"]
            GG["Oracle GoldenGate"]
        end

        subgraph "Security & Observability"
            IAM["OCI IAM"]
            VAULT["OCI Vault"]
            CG["Cloud Guard"]
            DS["Data Safe"]
            MON["OCI Monitoring"]
            APM["OCI APM"]
            LOG["OCI Logging"]
        end
    end

    subgraph "Oracle Fusion Cloud ERP (SaaS)"
        FUSION["Fusion ERP Modules"]
        BIPR["BI Publisher Reports"]
        FBDI["FBDI Import/Export"]
    end

    subgraph "External Systems"
        BANK["Bank Payment Gateway"]
        SNOW["ServiceNow"]
        TEAMS["MS Teams / Slack"]
    end

    APIGW --> LB
    LB --> OKE
    OKE --> ORCH_POD
    ORCH_POD --> AGENT_PODS
    ORCH_POD --> ODA_INT
    AGENT_PODS --> GENAI
    AGENT_PODS --> EMBED_SVC
    AGENT_PODS --> DOCAI
    AGENT_PODS --> ADB
    ADB --> VECSTORE
    AGENT_PODS --> OIC
    OIC --> FUSION
    OIC --> BIPR
    OIC --> FBDI
    OIC --> BANK
    OIC --> SNOW
    OIC --> TEAMS
    STREAM --> AGENT_PODS
    EVENTS --> FUNCS
    FUNCS --> AGENT_PODS
    GG --> ADB
    IAM --> APIGW
    VAULT --> ORCH_POD
    CG --> OKE
    DS --> ADB
    MON --> OKE
    APM --> AGENT_PODS
    LOG --> OKE
    OBJSTORE --> EMBED_SVC
```

---

*All diagrams above are referenced from the HLD (`01-HLD.md`) and LLD (`02-LLD.md`) documents. Embed key diagrams inline in those documents using the same Mermaid source blocks.*
