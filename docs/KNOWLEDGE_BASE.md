# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** that communicate via REST (OpenFeign) and are orchestrated through Docker Compose. The system implements a layered microservices architecture with an API Gateway as the single entry point, a service registry for discovery, and a config server for centralized configuration.

### 1.2 Microservices Inventory

| Service | Port | Purpose |
|---|---|---|
| `internet-banking-api-gateway` | 8082 | Single entry point; routes requests, enforces OAuth2/JWT security |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server (Git-backed) |
| `internet-banking-user-service` | 8083 | User registration, approval, profile management; integrates with Keycloak |
| `internet-banking-fund-transfer-service` | 8084 | Initiates and tracks fund transfers between bank accounts |
| `internet-banking-utility-payment-service` | 8085 | Processes utility bill payments (telecom, electricity, etc.) |
| `core-banking-service` | 8092 | Core banking engine: accounts, users, transactions, balance management |

### 1.3 Communication Patterns

```
Client
  │
  ▼
┌──────────────────────────────┐
│   API Gateway (:8082)        │  ◄── OAuth2 JWT validation via Keycloak
│   Spring Cloud Gateway       │
└──────┬───────┬───────┬───────┘
       │       │       │
       ▼       ▼       ▼
   ┌───────┐ ┌──────┐ ┌──────────┐
   │ User  │ │ Fund │ │ Utility  │
   │Service│ │Xfer  │ │ Payment  │
   │(:8083)│ │(:8084)│ │(:8085)   │
   └───┬───┘ └──┬───┘ └────┬─────┘
       │        │           │
       ▼        ▼           ▼
   ┌──────────────────────────────┐
   │   Core Banking Service       │
   │          (:8092)             │
   └──────────────────────────────┘
```

- **Synchronous REST via OpenFeign**: User Service, Fund Transfer Service, and Utility Payment Service all call Core Banking Service via Feign clients using service discovery names (e.g., `@FeignClient(name = "core-banking-service")`).
- **Service Discovery**: All services register with Eureka and discover each other by logical service names.
- **Centralized Config**: All services fetch configuration from the Config Server, which pulls from a Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **API Gateway Routing**: The gateway routes requests by path prefix (e.g., `/user/**` → user-service, `/fund-transfer/**` → fund-transfer-service).
- **Auth Header Propagation**: The gateway extracts the authenticated user's principal name and injects it as the `X-Auth-Id` HTTP header, which downstream services read via a servlet filter (`AppAuthUserFilter`).

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8 | Primary data store for all business services (4 schemas) |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15-backed) | OAuth2/OIDC authentication, user management |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Service Discovery | Netflix Eureka | Service registration and lookup |
| Config Management | Spring Cloud Config (Git-backed) | Externalized, centralized configuration |
| Database Migration | Flyway 10.12 | Schema versioning (core-banking-service only) |
| API Documentation | SpringDoc OpenAPI (springdoc 2.1.0) | Swagger UI for API docs |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (Database: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| `first_name` | VARCHAR(255) | User first name |
| `last_name` | VARCHAR(255) | User last name |
| `email` | VARCHAR(255) | User email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal account ID |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal transaction ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Utility account ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### Relationships
```
banking_core_user (1) ──< (N) banking_core_account
banking_core_account (1) ──< (N) banking_core_transaction
banking_core_utility_account (standalone, no FK)
```

### 2.2 User Service (Database: `banking_core_user_service`)

#### `user` (JPA-managed, extends `AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (matches core banking) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service (Database: `banking_core_fund_transfer_service`)

#### `fund_transfer` (extends `AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal transfer ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (Database: `banking_core_utility_payment_service`)

#### `utility_payment` (extends `AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All routes below are accessible through the gateway. The gateway prefixes service-specific paths:
- `/user/**` → `internet-banking-user-service`
- `/fund-transfer/**` → `internet-banking-fund-transfer-service`
- `/utility-payment/**` → `internet-banking-utility-payment-service`
- `/banking-core/**` → `core-banking-service`

**Public endpoints** (no JWT required):
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**`, `GET /*/actuator/**`

**All other endpoints require a valid JWT Bearer token.**

### 3.2 Core Banking Service (Port 8092)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest { account, providerId, amount, referenceNumber }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User { id, firstName, lastName, email, identificationNumber }` |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable query params | `List<User>` |

### 3.3 User Service (Port 8083)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (creates in Keycloak + local DB) | `User { email, identification, password }` | `User { id, email, identification, status, authId }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest { status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 Fund Transfer Service (Port 8084)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable query params | `List<FundTransfer>` |

### 3.5 Utility Payment Service (Port 8085)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable query params | `List<UtilityPayment>` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow
1. Client sends `POST /user/api/v1/bank-users/register` with email, identification, and password.
2. User Service checks if email already exists in Keycloak → throws `UserAlreadyRegisteredException` if found.
3. User Service calls Core Banking Service (`GET /api/v1/user/{identification}`) to verify the user exists in the core banking system.
4. Validates email matches the core banking record → throws `InvalidEmailException` if mismatch.
5. Creates a Keycloak user (disabled, email unverified) with the provided credentials.
6. Saves user record locally with `status = PENDING`.
7. Admin later approves via `PATCH /api/v1/bank-users/update/{id}` with `status = APPROVED`, which enables the Keycloak user and marks email as verified.

### 4.2 Fund Transfer Flow
1. Client sends `POST /api/v1/transfer` to Fund Transfer Service.
2. Service creates a `FundTransferEntity` with `status = PENDING` and persists it.
3. Calls Core Banking Service (`POST /api/v1/transaction/fund-transfer`) via Feign.
4. Core Banking Service:
   - Reads both source and destination `BankAccount` records.
   - **Validates balance**: source account `actualBalance` must be ≥ transfer amount (throws `InsufficientFundsException` otherwise).
   - Debits source account (subtracts from `actualBalance` and `availableBalance`).
   - Credits destination account (adds to `actualBalance` and `availableBalance`).
   - Creates two `TransactionEntity` records (debit + credit) with a shared `transactionId`.
   - Entire operation is `@Transactional`.
5. Fund Transfer Service updates local record with `transactionReference` and `status = SUCCESS`.

### 4.3 Utility Payment Flow
1. Client sends `POST /api/v1/utility-payment` to Utility Payment Service.
2. Service creates a `UtilityPaymentEntity` with `status = PROCESSING` and persists it.
3. Calls Core Banking Service (`POST /api/v1/transaction/util-payment`) via Feign.
4. Core Banking Service:
   - Reads the payer's `BankAccount`.
   - **Validates balance** (same as fund transfer).
   - Looks up the utility provider by `providerId`.
   - Debits payer's account.
   - Creates a `TransactionEntity` of type `UTILITY_PAYMENT`.
5. Utility Payment Service updates local record with `transactionId` and `status = SUCCESS`.

### 4.4 Balance Validation Rules
- `actualBalance` must be ≥ 0 **AND** ≥ requested amount.
- If either condition fails → `InsufficientFundsException` with code `INSUFFICIENT_FUNDS`.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)
- **Version**: 23.0.7
- **Connection**: User Service connects via `keycloak-admin-client` (v24.0.4) using client credentials grant.
- **Configuration**: Server URL, realm, client ID, and client secret are injected via `@Value` from Spring Cloud Config properties (`app.config.keycloak.*`).
- **Operations**: Create user, read user by email, read user by ID, update user (enable/disable, email verification).
- **Singleton pattern**: `KeycloakProperties` maintains a static singleton `Keycloak` instance (not thread-safe).
- **Realm import**: Docker Compose mounts `docker-compose/keycloak/realm-export.json` for initial realm setup.

### 5.2 RabbitMQ (Message Broker)
- **Status**: Listed in the technology stack and README as an integration for notifications.
- **Current state**: **Not implemented** in code. No RabbitMQ dependencies in any `build.gradle`. The Notification Service is marked as "PENDING Development" in the README.

### 5.3 Zipkin (Distributed Tracing)
- **Version**: Zipkin 3
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Docker Compose**: Zipkin runs on port 9411 at `172.25.0.12`.
- **Configuration**: Trace reporting is configured via Spring Cloud Config properties.

### 5.4 Database Connections
- **MySQL 8**: All four business services connect to a shared MySQL instance with separate databases:
  - `banking_core_service` (Core Banking)
  - `banking_core_user_service` (User Service)
  - `banking_core_fund_transfer_service` (Fund Transfer)
  - `banking_core_utility_payment_service` (Utility Payment)
- **Credentials**: MySQL root password `woVERANKliGharym`, app user `javatodev_development` / `oPItyPticIAt`.
- **H2 in tests**: Core Banking Service tests use an in-memory H2 database.

### 5.5 Service Registry (Eureka)
- All business services register as Eureka clients.
- Feign clients use Eureka service names for load-balanced calls (e.g., `@FeignClient(name = "core-banking-service")`).

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System
- **Build tool**: Gradle 8.6 (per wrapper), individual `build.gradle` per service (no multi-module root project).
- **Java**: 21 (source compatibility).
- **Spring Boot**: 3.2.4 with Spring Cloud 2023.0.0.
- **Key plugins**: `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`.

### 6.2 Docker
- Each service has its own `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- Dockerfiles copy the built JAR and a `wait-for-it.sh` script for dependency ordering.
- Images are tagged under the `javatodev/` namespace.

### 6.3 Docker Compose
- **Full stack** (`docker-compose.yml`): All 10+ containers with a custom bridge network (`172.25.0.0/16`), static IPs.
- **Support apps only** (`docker-compose-support-apps.yml`): Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry) for local development.
- **Startup ordering**: Services use `wait-for-it.sh` to wait for Service Registry, Config Server, and MySQL before starting.
- **Spring profile**: All services start with `-Dspring.profiles.active=docker`.

### 6.4 CI/CD
- **GitHub Actions**: No workflow files present (previously removed for PAT scope compatibility per commit history).
- **No CI pipeline** currently configured in the repository.

### 6.5 Database Migrations
- **Flyway** is used only in `core-banking-service` with 3 migration scripts:
  - `V1.0.20210427174638`: Creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables.
  - `V1.0.20210427174721`: Seeds test data (4 users, 14 accounts, 6 utility providers).
  - `V1.0.20210429210839`: Creates `banking_core_transaction` table.
- Other services rely on JPA `hibernate.ddl-auto` for schema management (configured via Spring Cloud Config).
