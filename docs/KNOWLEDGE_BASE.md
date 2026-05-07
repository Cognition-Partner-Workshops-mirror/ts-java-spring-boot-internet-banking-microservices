# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking platform composed of **6 microservices** communicating via REST (OpenFeign) and discovered through Netflix Eureka. All services pull centralized configuration from a Spring Cloud Config Server backed by a Git repository.

### Service Inventory

| Service | Port | Database | Purpose |
|---|---|---|---|
| `core-banking-service` | 8092 | MySQL (`banking_core_service`) | System of record: accounts, users, ledger transactions |
| `internet-banking-user-service` | 8083 | MySQL (`banking_core_user_service`) | Internet banking user registration & Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | MySQL (`banking_core_fund_transfer_service`) | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | MySQL (`banking_core_utility_payment_service`) | Third-party utility bill payments |
| `internet-banking-api-gateway` | 8082 | None | Spring Cloud Gateway + OAuth2 security |
| `internet-banking-service-registry` | 8081 | None | Netflix Eureka discovery server |
| `internet-banking-config-server` | 8090 | None | Spring Cloud Config Server (Git-backed) |

### Communication Patterns

```
                    ┌─────────────────────┐
                    │   Keycloak (IAM)    │
                    │   :8080             │
                    └────────┬────────────┘
                             │ OAuth2 JWT
                    ┌────────▼────────────┐
   Client ────────► │   API Gateway       │
                    │   :8082             │
                    └────┬───┬───┬────────┘
                         │   │   │
            ┌────────────┘   │   └────────────┐
            ▼                ▼                 ▼
   ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
   │ User Service │  │ Fund Transfer│  │ Utility Pay  │
   │ :8083       │  │ :8084        │  │ :8085        │
   └──────┬──────┘  └──────┬───────┘  └──────┬───────┘
          │                │                  │
          │     OpenFeign  │      OpenFeign   │
          └────────┬───────┘──────────────────┘
                   ▼
          ┌──────────────┐
          │ Core Banking │
          │ :8092        │
          └──────┬───────┘
                 │
          ┌──────▼───────┐     ┌──────────┐
          │   MySQL      │     │  Zipkin   │
          │   :3306      │     │  :9411    │
          └──────────────┘     └──────────┘
```

- **Synchronous REST (OpenFeign):** User Service, Fund Transfer Service, and Utility Payment Service all call Core Banking Service via Feign clients discovered through Eureka.
- **Service Discovery:** All services register with Eureka (`internet-banking-service-registry`).
- **Centralized Config:** All services fetch configuration from Config Server at startup via Spring Cloud Bootstrap context.
- **API Gateway:** Routes client traffic to downstream services, enforces OAuth2 JWT authentication, and injects `X-Auth-Id` header.
- **RabbitMQ:** Referenced in README as a planned integration for notification messages, but **not currently implemented** in the codebase.

### Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| MySQL | Custom build from `docker-compose/mysql/` | Primary database for all business services |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | Identity and Access Management (OAuth2/OIDC) |
| PostgreSQL 15 | `postgres:15` | Keycloak backend database |
| Zipkin | `openzipkin/zipkin:3` | Distributed tracing |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID (e.g., NIC) |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping related entries |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction` (mapped via `@OneToOne` in JPA but FK allows many)

### Internet Banking User Service

#### `user` (managed via JPA, no Flyway)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Maps to core banking `identification_number` |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | Audit fields | Via `AuditAware` superclass |

### Internet Banking Fund Transfer Service

#### `fund_transfer` (managed via JPA, no Flyway)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `transaction_reference` | VARCHAR | UUID from core banking |
| Audit fields | | Via `AuditAware` superclass |

### Internet Banking Utility Payment Service

#### `utility_payment` (managed via JPA, no Flyway)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| Audit fields | | Via `AuditAware` superclass |

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Lookup utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer (debit/credit) |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |

**Request/Response Shapes:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }
UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }
```

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register new internet banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List all users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by ID |

**Request/Response Shapes:**

```
User { id: Long, email: String, identification: String, password: String, authId: String, status: Status }
UserUpdateRequest { status: Status }  // Status: PENDING | APPROVED
```

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List all transfers |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List all payments |

### API Gateway Route Prefixes (`:8082`)

| Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules (`TransactionService.fundTransfer`)
1. Look up source and destination bank accounts from Core Banking
2. **Balance validation:** Source account `actualBalance` must be >= 0 AND >= transfer amount; otherwise throws `InsufficientFundsException`
3. Debit source account: subtract amount from both `actualBalance` and `availableBalance`
4. Record debit transaction with negative amount
5. Credit destination account: add amount to both `actualBalance` and `availableBalance`
6. Record credit transaction with positive amount
7. Return UUID `transactionId` linking both transaction entries

**Known bug:** `availableBalance` is set to `actualBalance - amount` instead of `availableBalance - amount`, causing a double-subtraction on the source account.

### Utility Payment Processing (`TransactionService.utilPayment`)
1. Look up source bank account
2. Validate balance (same rules as fund transfer)
3. Look up utility provider account by ID
4. Debit source account
5. Record transaction with `UTILITY_PAYMENT` type
6. Return UUID `transactionId`

**Note:** No actual third-party API call is made — commented as a placeholder.

### User Registration (`UserService.createUser`)
1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException`
2. Validate user exists in Core Banking by identification number
3. Validate email matches Core Banking record → throw `InvalidEmailException`
4. Create user in Keycloak (disabled, unverified, with password)
5. Retrieve Keycloak user ID (`authId`)
6. Save user entity locally with `PENDING` status
7. Admin must later call `PATCH /update/{id}` with `APPROVED` status to enable Keycloak account

### User Approval (`UserService.updateUser`)
1. When status changes to `APPROVED`:
   - Enable the Keycloak user
   - Mark email as verified
   - Update local entity status

---

## 5. Integration Points

### Keycloak (IAM)
- **Version:** 23.0.7
- **Realm:** Configured via imported realm JSON at `docker-compose/keycloak/`
- **Integration:** User Service uses `keycloak-admin-client:24.0.4` for user CRUD
- **Auth flow:** API Gateway validates JWTs using Keycloak's JWK Set URI
- **Properties:** `app.config.keycloak.server-url`, `.realm`, `.clientId`, `.client-secret`
- **Singleton pattern:** `KeycloakProperties` maintains a static `Keycloak` instance (not thread-safe initialization)

### Zipkin (Distributed Tracing)
- **Version:** 3 (via Docker image `openzipkin/zipkin:3`)
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Coverage:** Traces propagate across Feign calls via `feign-micrometer`

### RabbitMQ
- **Status:** Referenced in README but **not implemented** in the codebase
- **Planned use:** Notification messages for fund transfers and utility payments

### Database Connections
- **MySQL:** Shared MySQL instance with separate databases per service
  - `banking_core_service` — Core Banking
  - `banking_core_fund_transfer_service` — Fund Transfer
  - `banking_core_user_service` — User Service
  - `banking_core_utility_payment_service` — Utility Payment
- **PostgreSQL 15:** Dedicated to Keycloak backend
- **DB user:** `javatodev_development` / `oPItyPticIAt` (created in `privileges.sql`)

### OpenFeign Clients
| Client | In Service | Calls |
|---|---|---|
| `BankingCoreRestClient` (user-svc) | User Service | `GET /api/v1/user/{identification}` |
| `BankingCoreFeignClient` (fund-transfer-svc) | Fund Transfer Service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| `BankingCoreRestClient` (utility-payment-svc) | Utility Payment Service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System
- **Gradle** with Spring Boot plugin (`org.springframework.boot:3.2.4`)
- **Java 21** (`sourceCompatibility = '21'`)
- **Spring Cloud:** `2023.0.0`
- Each service is an independent Gradle project (no multi-project build)
- `com.gorylenko.gradle-git-properties` plugin generates Git commit info

### Containerization
- Each service has its own `Dockerfile`
- Pre-built images published under `javatodev/` namespace on Docker Hub
- `wait-for-it.sh` scripts ensure dependency ordering at startup

### Docker Compose Orchestration
Two compose files:
1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IPs per container.

**Startup Order (via `wait-for-it.sh`):**
1. Service Registry (`:8081`) + Config Server (`:8090`) + MySQL (`:3306`) start first
2. Business services wait for all three before starting
3. API Gateway waits for Service Registry + Config Server

### Database Migrations
- **Core Banking:** Flyway migrations in `src/main/resources/db/migration/`
  - `V1.0.20210427174638` — Create base tables (user, account, utility_account)
  - `V1.0.20210427174721` — Seed test data
  - `V1.0.20210429210839` — Create transaction table
- **Other services:** JPA auto-DDL (no Flyway migrations)

### Configuration Management
- Spring Cloud Config Server pulls from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, search path: `configuration`
- Each service has `bootstrap.yml` (local) and `bootstrap-docker.yml` (Docker) profiles pointing to Config Server
- Service-specific settings (DB URLs, Keycloak URLs, gateway routes) are managed in the external Git config repo
