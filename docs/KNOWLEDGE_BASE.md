# Application Knowledge Base

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### System Summary

This is an internet banking platform built on **Java 21** and **Spring Boot 3.2.4** using a microservices architecture. The system is composed of **6 microservices** that collaborate to provide user management, fund transfers, utility payments, and core banking operations.

### Services

| Service | Port | Description | Database |
|---------|------|-------------|----------|
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point for all client traffic. Enforces OAuth2/JWT authentication and routes requests to downstream services. | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server — provides dynamic service discovery so services locate each other by logical name rather than hardcoded URLs. | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — serves externalized configuration from a remote Git repository (`internet-banking-microservices-configurations`). | None |
| **internet-banking-user-service** | 8083 | Manages internet banking user registration, approval workflows, and profile retrieval. Integrates with Keycloak for identity management. | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates account-to-account fund transfers. Persists transfer records locally and delegates actual ledger operations to Core Banking via Feign. | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (e.g., telecom providers). Persists payment records locally and delegates ledger operations to Core Banking via Feign. | MySQL (`banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | System of record for bank accounts, users, transactions, and utility accounts. Executes actual balance mutations and transaction logging. | MySQL (`banking_core_service`) |

### Communication Patterns

```
                    ┌─────────────────────┐
                    │     Client (UI)     │
                    └──────────┬──────────┘
                               │ HTTP (JWT Bearer)
                    ┌──────────▼──────────┐
                    │   API Gateway       │
                    │   (port 8082)       │
                    │   OAuth2 Resource   │
                    │   Server + Routing  │
                    └──┬───┬──────┬───┬───┘
                       │   │      │   │
          ┌────────────┘   │      │   └────────────┐
          ▼                ▼      ▼                ▼
   ┌──────────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐
   │ User Service │ │Fund Xfer │ │ Util Payment │ │ Core Banking │
   │ (port 8083)  │ │(port 8084│ │ (port 8085)  │ │ (port 8092)  │
   └──────┬───────┘ └────┬─────┘ └──────┬───────┘ └──────────────┘
          │               │              │                ▲
          │  OpenFeign    │  OpenFeign   │  OpenFeign     │
          └───────────────┴──────────────┴────────────────┘
                    (All → Core Banking Service)
```

- **Client → Gateway**: HTTP/REST with JWT Bearer token (obtained from Keycloak)
- **Gateway → Downstream Services**: Proxied HTTP with `X-Auth-Id` header injected by `GatewayConfiguration`
- **Service → Service (Sync)**: Spring Cloud OpenFeign declarative REST clients, resolved via Eureka service names
- **Service Discovery**: All services register with Eureka; Feign clients resolve `core-banking-service` by name
- **Configuration**: All services fetch configuration at startup from the Config Server via Spring Cloud Config bootstrap

### Infrastructure Components

| Component | Image/Technology | Purpose | Docker IP |
|-----------|-----------------|---------|-----------|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity and Access Management (IAM), OAuth2/OIDC provider | 172.25.0.11 |
| **PostgreSQL** | `postgres:15` | Keycloak's backing database | 172.25.0.10 |
| **MySQL** | Custom build (with seed scripts) | Application database for all 4 data-bearing services | 172.25.0.9 |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collector and UI | 172.25.0.12 |
| **RabbitMQ** | Referenced in README | Async messaging for notifications (Notification Service is PENDING) | Not in docker-compose |

### Network Topology

All containers run on a custom Docker bridge network (`javatodev_ib_network`) with subnet `172.25.0.0/16` and static IP assignments. Services use `wait-for-it.sh` scripts to ensure the Config Server, Service Registry, and MySQL are healthy before starting.

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal user identifier |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number (unique business identifier) |

**Relationships**: One-to-Many → `banking_core_account`

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal account identifier |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) ENUM | Account type: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) ENUM | Account status: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available (spendable) balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Owning user |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal transaction identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account number or payment reference) |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

### Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `auth_id` | VARCHAR(255) | Keycloak user UUID (maps local user to Keycloak identity) |
| `identification` | VARCHAR(255) | National ID / NIC |
| `status` | VARCHAR(255) ENUM | `PENDING` or `APPROVED` |
| `created_at` | TIMESTAMP | Audit: creation time (inherited from `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit: last update time (inherited from `AuditAware`) |

### Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `transaction_reference` | VARCHAR(255) | Transaction ID returned from Core Banking |
| `status` | VARCHAR(255) ENUM | `PENDING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit: creation time |
| `updated_at` | TIMESTAMP | Audit: last update time |

### Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `provider_id` | BIGINT | Utility provider ID (references Core Banking utility account) |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Customer/bill reference number |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | Transaction ID from Core Banking |
| `status` | VARCHAR(255) ENUM | `PROCESSING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit: creation time |
| `updated_at` | TIMESTAMP | Audit: last update time |

### Entity Relationship Diagram (Logical)

```
┌───────────────────────────────────────────────────────────────┐
│                    CORE BANKING SERVICE                       │
│                                                               │
│  banking_core_user ──1:N──▶ banking_core_account              │
│                                      │                        │
│                                     1:N                       │
│                                      ▼                        │
│                            banking_core_transaction            │
│                                                               │
│  banking_core_utility_account (standalone lookup table)        │
└───────────────────────────────────────────────────────────────┘

┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│  USER SERVICE        │  │ FUND TRANSFER SVC    │  │ UTIL PAYMENT SVC     │
│                      │  │                      │  │                      │
│  user (local copy    │  │ fund_transfer        │  │ utility_payment      │
│   w/ Keycloak ref)   │  │ (local audit trail)  │  │ (local audit trail)  │
└──────────────────────┘  └──────────────────────┘  └──────────────────────┘
```

---

## 3. API Surface Map

### Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | — | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer at the ledger level | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment at the ledger level | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (id, firstName, lastName, email, identificationNumber) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable query params | `List<User>` |

### Internet Banking User Service (port 8083)

Via Gateway prefix: `/user/api/v1/bank-users/...`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User` (email, password, identification) | `User` (id, authId, identification, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve registration) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### Internet Banking Fund Transfer Service (port 8084)

Via Gateway prefix: `/fund-transfer/api/v1/transfer/...`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount, authID) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable query params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (port 8085)

Via Gateway prefix: `/utility-payment/api/v1/utility-payment/...`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable query params | `List<UtilityPayment>` |

### API Gateway Routes (port 8082)

The gateway routes requests based on path prefixes (configured via Spring Cloud Config):

| Path Prefix | Target Service |
|-------------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

### Common Actuator Endpoints (all services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/info` | Application info (git properties) |
| `GET` | `/actuator/health` | Health check |

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with email, password, and identification (NIC)
2. **User Service** checks if the email is already registered in Keycloak → throws `UserAlreadyRegisteredException` if duplicate
3. **User Service** calls **Core Banking** via Feign (`GET /api/v1/user/{identification}`) to verify the identification exists in the bank's records
4. If the user exists in Core Banking, validates that the email matches the bank record → throws `InvalidEmailException` on mismatch
5. Creates a Keycloak user representation (disabled, email unverified) with the provided password
6. Persists a local `UserEntity` with `status=PENDING` and the Keycloak `authId`
7. An admin later calls `PATCH /api/v1/bank-users/update/{id}` with `status=APPROVED` to enable the Keycloak account

### Fund Transfer Rules

1. Client calls `POST /fund-transfer/api/v1/transfer` with source account, destination account, and amount
2. **Fund Transfer Service** persists a `FundTransferEntity` with `status=PENDING`
3. Delegates to **Core Banking** via Feign (`POST /api/v1/transaction/fund-transfer`)
4. **Core Banking** `TransactionService.fundTransfer()`:
   - Reads both source and destination `BankAccount` objects
   - Validates the source account has sufficient `actualBalance` (≥ requested amount, and > 0)
   - Calls `internalFundTransfer()`:
     - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
     - Creates a debit `TransactionEntity` (negative amount)
     - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
     - Creates a credit `TransactionEntity` (positive amount)
     - Both transactions share the same UUID `transactionId`
5. **Fund Transfer Service** updates its local record with `status=SUCCESS` and the `transactionReference`

### Utility Payment Processing

1. Client calls `POST /utility-payment/api/v1/utility-payment` with provider ID, amount, reference number, and source account
2. **Utility Payment Service** persists a `UtilityPaymentEntity` with `status=PROCESSING`
3. Delegates to **Core Banking** via Feign (`POST /api/v1/transaction/util-payment`)
4. **Core Banking** `TransactionService.utilPayment()`:
   - Reads the source `BankAccount` and validates balance
   - Reads the `UtilityAccount` by provider ID
   - Debits the source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Creates a `TransactionEntity` of type `UTILITY_PAYMENT`
5. **Utility Payment Service** updates its record with `status=SUCCESS` and the `transactionId`

### User Approval Workflow

1. After registration, users are in `PENDING` state with Keycloak accounts disabled
2. An admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`
3. **User Service** enables the Keycloak account (`setEnabled(true)`, `setEmailVerified(true)`)
4. Updates the local `UserEntity` status to `APPROVED`

### Balance Validation

- Applied before every fund transfer and utility payment
- Checks: `actualBalance >= 0 AND actualBalance >= requestedAmount`
- Throws `InsufficientFundsException` with error code `INSUFFICIENT_FUNDS` on failure

### Authentication Flow

1. Client obtains a JWT access token from Keycloak (realm configured via imported JSON)
2. All requests pass through the **API Gateway** which validates the JWT via the `jwk-set-uri`
3. The Gateway's `GatewayConfiguration` global filter extracts the `Principal` name and injects it as an `X-Auth-Id` header
4. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in `ApiRequestContextHolder` (ThreadLocal)
5. The `/user/api/v1/bank-users/register` endpoint is publicly accessible (no JWT required)
6. All actuator endpoints are publicly accessible

---

## 5. Integration Points

### Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Connection**: User Service → Keycloak Admin Client (`keycloak-admin-client:24.0.4`)
- **Configuration**: `KeycloakProperties` reads from Spring Cloud Config:
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm name
  - `app.config.keycloak.clientId` — Service account client ID
  - `app.config.keycloak.client-secret` — Service account client secret
- **Grant Type**: `client_credentials`
- **Operations**: Create user, update user (enable/verify), read user by email, read user by auth ID
- **Realm Data**: Pre-imported via volume mount (`docker-compose/keycloak/` → `/opt/keycloak/data/import`)
- **Gateway Integration**: JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### RabbitMQ (Async Messaging)

- **Status**: Referenced in README but **not implemented** in the current codebase
- **Intended Use**: Notification service (PENDING development) would consume messages from Fund Transfer and Utility Payment services
- **No RabbitMQ dependency** exists in any `build.gradle` file
- **No RabbitMQ container** is defined in `docker-compose.yml`

### Zipkin (Distributed Tracing)

- **Version**: Zipkin 3 (via Docker image `openzipkin/zipkin:3`)
- **Port**: 9411
- **Client Libraries**: All services include:
  - `io.micrometer:micrometer-tracing-bridge-brave` — Brave tracer bridge for Micrometer
  - `io.zipkin.reporter2:zipkin-reporter-brave` — Zipkin span reporter
  - `io.github.openfeign:feign-micrometer` — Feign tracing integration
- **Configuration**: Zipkin URL is provided via Spring Cloud Config (externalized)

### Database Connections

| Service | Database | Engine | Schema Mgmt |
|---------|----------|--------|-------------|
| Core Banking | `banking_core_service` | MySQL 8 | Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| User Service | `banking_core_user_service` | MySQL 8 | JPA auto (Hibernate) |
| Fund Transfer | `banking_core_fund_transfer_service` | MySQL 8 | JPA auto (Hibernate) |
| Utility Payment | `banking_core_utility_payment_service` | MySQL 8 | JPA auto (Hibernate) |
| Keycloak | `keycloak` (PostgreSQL) | PostgreSQL 15 | Keycloak internal |

- **MySQL Connection**: All services connect to the same MySQL container (`mysql_javatodev_app` at 172.25.0.9:3306) but use separate databases
- **MySQL User**: `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql`)
- **Test Databases**: All services use H2 in-memory database for tests (`com.h2database:h2:2.2.224`)

### Spring Cloud Config (Externalized Configuration)

- **Config Server** fetches from Git: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`, **Search path**: `configuration`
- **Bootstrap**: Each service has `bootstrap.yml` (local) and `bootstrap-docker.yml` (Docker) pointing to the Config Server at port 8090
- **Profiles**: `docker` profile activates Docker-specific config (container hostnames instead of localhost)

### Netflix Eureka (Service Discovery)

- **Server**: `internet-banking-service-registry` at port 8081
- **Clients**: All other services register as Eureka clients
- **Usage**: Feign clients resolve service names (e.g., `core-banking-service`) via Eureka

### OpenFeign (Inter-Service Communication)

| Consumer | Feign Client | Target Service | Endpoints Called |
|----------|-------------|---------------|-----------------|
| User Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | `BankingCoreFeignClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (wrapper included per service, `gradlew`)
- **Java Version**: 21 (Eclipse Temurin 21.0.2)
- **Spring Boot**: 3.2.4 (via `org.springframework.boot` Gradle plugin)
- **Spring Cloud**: 2023.0.0 (via BOM)
- **Lombok**: Compile-only with annotation processor
- **Git Properties**: `com.gorylenko.gradle-git-properties:2.4.2` plugin generates `git.properties` for `/actuator/info`

### Docker Build

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose Orchestration

- **Full stack**: `docker-compose/docker-compose.yml` — all 6 services + MySQL + PostgreSQL + Keycloak + Zipkin
- **Support only**: `docker-compose/docker-compose-support-apps.yml` — infrastructure components only (for local development of services outside Docker)
- **Startup ordering**: `wait-for-it.sh` ensures Config Server, Service Registry, and MySQL are available before each application service starts

### Build & Deploy Steps

1. Build each service: `./gradlew clean build` (in each service directory)
2. Build Docker images: `docker build -t javatodev/<service-name> .` (in each service directory)
3. Start the stack: `docker-compose up -d` (in `docker-compose/` directory)

### Test Infrastructure

- **Framework**: JUnit 5 (via `spring-boot-starter-test`)
- **Test Database**: H2 in-memory (Flyway disabled in test profile)
- **Existing Tests**: Only `core-banking-service` has unit tests:
  - `AccountServiceTest` — 6 tests (read account by number/provider/ID, not-found cases)
  - `TransactionServiceTest` — 10 tests (fund transfer success/failure, utility payment success/failure, balance validation)
  - `UserServiceTest` — exists but content not detailed
- **Other Services**: Only have empty Spring Boot application context tests

### OpenAPI / Swagger

- All data-bearing services include `springdoc-openapi-starter-webflux-ui:2.1.0`
- Controllers are annotated with `@Tag`, `@Operation` from `io.swagger.v3.oas.annotations`
- Swagger UI available at `/swagger-ui.html` on each service (when running)
