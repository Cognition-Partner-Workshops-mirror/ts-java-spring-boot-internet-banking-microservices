# Internet Banking Microservices - Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 High-Level Architecture

The application is a Spring Boot 3.2.4 / Java 21 microservices-based internet banking platform built on Spring Cloud 2023.0.0. It follows a standard microservices pattern with centralized configuration, service discovery, and an API gateway.

### 1.2 Services

| Service | Port | Description | Database |
|---|---|---|---|
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway entry point. Routes client traffic, enforces OAuth2/JWT security via Keycloak. Reactive (WebFlux). | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for dynamic service discovery. | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server backed by a remote Git repository for centralized property management. | None |
| **core-banking-service** | 8092 | System of record for accounts, users, and transactions. Manages bank accounts, utility accounts, and processes fund transfers / utility payments at the ledger level. | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User profile management. Handles registration, update, and retrieval. Integrates with Keycloak for identity and with core-banking-service for user validation. | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers. Persists transfer records locally and delegates ledger operations to core-banking-service. | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments. Persists payment records locally and delegates ledger operations to core-banking-service. | MySQL (`banking_core_utility_payment_service`) |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| Synchronous REST (service-to-service) | Spring Cloud OpenFeign | user-service -> core-banking-service, fund-transfer-service -> core-banking-service, utility-payment-service -> core-banking-service |
| Service Discovery | Netflix Eureka | All business services register with Eureka; Feign clients resolve service names via Eureka |
| API Gateway Routing | Spring Cloud Gateway | Routes external traffic to downstream services using path-based routing |
| Centralized Config | Spring Cloud Config (Git-backed) | All services fetch configuration from the config server at startup via bootstrap context |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin | Trace propagation across all services; traces exported to Zipkin |
| Message Queue (planned) | RabbitMQ | Mentioned in README for notification service; not yet implemented in code |

### 1.4 Infrastructure Components

| Component | Technology | Docker IP | Purpose |
|---|---|---|---|
| Zipkin | openzipkin/zipkin:3 | 172.25.0.12 | Distributed trace collection and visualization |
| Keycloak | quay.io/keycloak/keycloak:23.0.7 | 172.25.0.11 | Identity and access management (OAuth2/OIDC provider) |
| Keycloak DB | PostgreSQL 15 | 172.25.0.10 | Keycloak persistence (port closed externally) |
| MySQL | Custom MySQL image | 172.25.0.9 | Application data for all business services |
| Config Server | Custom Spring Boot image | 172.25.0.8 | Centralized configuration |
| Service Registry | Custom Spring Boot image | 172.25.0.7 | Service discovery |

### 1.5 Network Architecture

All containers run on a custom Docker bridge network (`javatodev_ib_network`) with subnet `172.25.0.0/16` and fixed IP addresses. Services use `wait-for-it.sh` scripts to ensure infrastructure dependencies (service registry, config server, MySQL) are healthy before application startup.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | - | User's first name |
| `last_name` | VARCHAR(255) | - | User's last name |
| `email` | VARCHAR(255) | - | User's email address |
| `identification_number` | VARCHAR(255) | - | National ID / NIC number (unique business identifier) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | - | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | ENUM string | Account type (`SAVINGS_ACCOUNT`) |
| `status` | VARCHAR(255) | ENUM string | Account status (`ACTIVE`) |
| `available_balance` | DECIMAL(19,2) | - | Available balance for transactions |
| `actual_balance` | DECIMAL(19,2) | - | Actual (ledger) balance |
| `user_id` | BIGINT(20) | FK -> `banking_core_user.id` | Account owner |

**Relationship:** Many accounts belong to one user (`@ManyToOne`).

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | - | Utility provider account number |
| `provider_name` | VARCHAR(255) | - | Provider name (e.g., `VODAFONE`, `VERIZON`) |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | - | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (target account number or utility reference) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping related transaction entries |
| `account_id` | BIGINT(20) | FK -> `banking_core_account.id` | Associated account |

**Relationship:** Each transaction links to one account (`@OneToOne` in JPA, though semantically many transactions per account).

#### Entity Relationship Diagram (Textual)

```
banking_core_user 1----* banking_core_account 1----* banking_core_transaction
                                                     
banking_core_utility_account (standalone)
```

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | VARCHAR | - | Keycloak user UUID (maps internal user to Keycloak identity) |
| `identification` | VARCHAR | - | NIC / identification number |
| `status` | VARCHAR | ENUM string | `PENDING` or `APPROVED` |
| `created_by` | VARCHAR | Audit | Creator (from `AuditAware`) |
| `created_on` | TIMESTAMP | Audit | Creation timestamp |
| `updated_by` | VARCHAR | Audit | Last updater |
| `updated_on` | TIMESTAMP | Audit | Last update timestamp |

This entity extends `AuditAware` (JPA auditing via `@EntityListeners`).

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `from_account` | VARCHAR | - | Source account number |
| `to_account` | VARCHAR | - | Destination account number |
| `amount` | DECIMAL | - | Transfer amount |
| `transaction_reference` | VARCHAR | - | Transaction ID returned from core-banking-service |
| `status` | VARCHAR | ENUM string | `PENDING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit | From `AuditAware` |
| `created_on` | TIMESTAMP | Audit | From `AuditAware` |
| `updated_by` | VARCHAR | Audit | From `AuditAware` |
| `updated_on` | TIMESTAMP | Audit | From `AuditAware` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | BIGINT | - | Utility provider ID (references core-banking utility_account) |
| `amount` | DECIMAL | - | Payment amount |
| `reference_number` | VARCHAR | - | Customer reference number |
| `account` | VARCHAR | - | Source bank account number |
| `transaction_id` | VARCHAR | - | Transaction ID from core-banking-service |
| `status` | VARCHAR | ENUM string | `PROCESSING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit | From `AuditAware` |
| `created_on` | TIMESTAMP | Audit | From `AuditAware` |
| `updated_by` | VARCHAR | Audit | From `AuditAware` |
| `updated_on` | TIMESTAMP | Audit | From `AuditAware` |

---

## 3. API Surface Map

All external traffic is routed through the **API Gateway** (port 8082). Internal service ports are also listed.

### 3.1 Core Banking Service (Port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` (number, type, status, availableBalance, actualBalance) | Get bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` (id, number, providerName) | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | - | `User` (identificationNumber, firstName, lastName, email, accounts[]) | Get user by identification number |
| `GET` | `/api/v1/user` | Query: `page`, `size`, `sort` | `List<User>` | Get paginated list of users |
| `POST` | `/api/v1/transaction/fund-transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Process fund transfer at ledger level |
| `POST` | `/api/v1/transaction/util-payment` | `{account, providerId, amount, referenceNumber}` | `{message, transactionId}` | Process utility payment at ledger level |

### 3.2 Internet Banking User Service (Port 8083)

Gateway prefix: `/user`

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `{identification, email, password}` | `User` (id, authId, identification, status) | Register new user (creates Keycloak account + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{status}` | `User` | Update user (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | Query: `page`, `size`, `sort` | `List<User>` | Get paginated list of registered users |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Get user by internal ID |

### 3.3 Internet Banking Fund Transfer Service (Port 8084)

Gateway prefix: `/fund-transfer`

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Initiate fund transfer (persists locally, delegates to core) |
| `GET` | `/api/v1/transfer` | Query: `page`, `size`, `sort` | `List<FundTransfer>` | Get paginated list of fund transfers |

### 3.4 Internet Banking Utility Payment Service (Port 8085)

Gateway prefix: `/utility-payment`

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Process utility payment (persists locally, delegates to core) |
| `GET` | `/api/v1/utility-payment` | Query: `page`, `size`, `sort` | `List<UtilityPayment>` | Get paginated list of utility payments |

### 3.5 API Gateway (Port 8082)

The gateway routes requests to downstream services based on path prefixes. It also:
- Enforces OAuth2/JWT authentication for all endpoints except `/user/api/v1/bank-users/register` and `/actuator/**`
- Injects `X-Auth-Id` header with the authenticated principal's name into downstream requests

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `http://localhost:8081` | Eureka dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Configuration endpoint |
| Zipkin | `http://localhost:9411` | Trace visualization UI |
| Keycloak | `http://localhost:8080` | IAM admin console |
| All services | `/actuator/**` | Spring Boot Actuator health/info endpoints |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** `internet-banking-user-service` -> `core-banking-service`

1. Check if email is already registered in Keycloak (reject duplicates with `ERROR_EMAIL_REGISTERED`)
2. Validate user exists in core-banking-service by identification number (via Feign)
3. Verify the provided email matches the core banking record (reject mismatches with `ERROR_INVALID_EMAIL`)
4. Create user in Keycloak (disabled, email not verified, with provided password)
5. Retrieve Keycloak user to get `authId`
6. Save local user record with status `PENDING`
7. If user not found in core banking, throw `ERROR_USER_NOT_FOUND_UNDER_NIC`

### 4.2 User Approval Flow

**Service:** `internet-banking-user-service`

1. Admin updates user status to `APPROVED`
2. If approved: enable Keycloak account, set emailVerified = true
3. Persist new status to local database

### 4.3 Fund Transfer Flow

**Service:** `internet-banking-fund-transfer-service` -> `core-banking-service`

1. Persist fund transfer record locally with status `PENDING`
2. Delegate to core-banking-service via Feign (`/api/v1/transaction/fund-transfer`)
3. **Core banking logic:**
   a. Look up both source and destination accounts
   b. Validate source account has sufficient balance (`actualBalance >= amount`)
   c. Debit source: subtract amount from `actualBalance` and `availableBalance`
   d. Record debit transaction entry (negative amount)
   e. Credit destination: add amount to `actualBalance` and `availableBalance`
   f. Record credit transaction entry (positive amount)
   g. Return transaction ID
4. Update local record with transaction reference and status `SUCCESS`

**Business Rules:**
- Balance must be >= 0 AND >= transfer amount
- Both accounts must exist
- Double-entry bookkeeping: two transaction records per transfer (debit + credit)
- UUID-based transaction IDs

### 4.4 Utility Payment Flow

**Service:** `internet-banking-utility-payment-service` -> `core-banking-service`

1. Persist utility payment record locally with status `PROCESSING`
2. Delegate to core-banking-service via Feign (`/api/v1/transaction/util-payment`)
3. **Core banking logic:**
   a. Look up source bank account
   b. Validate sufficient balance
   c. Look up utility provider account by ID
   d. Debit source account balance
   e. Record transaction entry (negative amount, type `UTILITY_PAYMENT`)
   f. Return transaction ID
4. Update local record with transaction ID and status `SUCCESS`

**Business Rules:**
- Same balance validation as fund transfers
- No credit-side entry for utility payments (funds leave the banking system)
- Reference number is customer-provided

### 4.5 Authentication & Authorization

- **Keycloak** manages user identities, realms, and clients
- API Gateway validates JWT tokens using Keycloak's JWK Set URI
- `X-Auth-Id` header is injected by the gateway for downstream services
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for audit purposes
- User registration endpoint is publicly accessible (no auth required)
- All actuator endpoints are publicly accessible

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Detail |
|---|---|
| Version | 23.0.7 |
| Connection | Admin Client SDK (`keycloak-admin-client:24.0.4`) via `client_credentials` grant |
| Configuration | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from config server) |
| Usage | User CRUD (create, read, update), email search, enable/disable users |
| Realm | Imported via `realm-export.json` at Keycloak startup |
| Consumer | `internet-banking-user-service` only |

### 5.2 RabbitMQ (Message Queue)

| Aspect | Detail |
|---|---|
| Status | **NOT IMPLEMENTED** - mentioned in README for future notification service |
| Planned Usage | Fund transfer and utility payment services would push notification messages |
| Current State | No RabbitMQ dependency in any `build.gradle`; no message producer/consumer code |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| Version | 3 (Docker image) |
| Connection | HTTP reporter via Micrometer Tracing + Brave bridge |
| Dependencies | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| Coverage | All 5 business services (gateway, user, fund-transfer, utility-payment, core-banking) |
| Port | 9411 |

### 5.4 Database Connections

| Service | Database | Technology | Schema Migration |
|---|---|---|---|
| core-banking-service | MySQL (`banking_core_service`) | Spring Data JPA + MySQL Connector/J 8.4.0 | Flyway (3 migration scripts) |
| user-service | MySQL (`banking_core_user_service`) | Spring Data JPA + MySQL Connector/J 8.4.0 | JPA auto-DDL (no Flyway) |
| fund-transfer-service | MySQL (`banking_core_fund_transfer_service`) | Spring Data JPA + MySQL Connector/J 8.4.0 | JPA auto-DDL (no Flyway) |
| utility-payment-service | MySQL (`banking_core_utility_payment_service`) | Spring Data JPA + MySQL Connector/J 8.4.0 | JPA auto-DDL (no Flyway) |
| keycloak | PostgreSQL (`keycloak`) | Keycloak internal | Keycloak managed |

All MySQL databases share a single MySQL container with a shared user (`javatodev_development`).

### 5.5 Inter-Service Communication (OpenFeign)

| Source Service | Target Service | Feign Client | Endpoints Called |
|---|---|---|---|
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

### 5.6 Spring Cloud Config (Remote Configuration)

| Aspect | Detail |
|---|---|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch | `main` |
| Search Path | `configuration` |
| Bootstrap | Each service uses `bootstrap.yml` (local) or `bootstrap-docker.yml` (Docker profile) to locate the config server |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service, no multi-project build)
- **Java Version:** 21 (Eclipse Temurin 21.0.2 JRE Alpine in Docker)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `gradle-git-properties` for embedding Git info into builds

Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`.

### 6.2 Docker Build

Each service has its own `Dockerfile` following this pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Build steps:
1. Run `./gradlew build` in each service directory to produce the fat JAR
2. Run `docker build` to create the Docker image
3. Images are tagged as `javatodev/{service-name}`

### 6.3 Docker Compose Deployment

Two compose files:
- **`docker-compose.yml`**: Full stack (all infrastructure + all services)
- **`docker-compose-support-apps.yml`**: Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup Order (enforced via `wait-for-it.sh`):**
1. MySQL, PostgreSQL, Zipkin, Keycloak start first (no dependencies)
2. Config Server starts
3. Service Registry starts
4. All business services wait for: Service Registry (8081), Config Server (8090), MySQL (3306)

### 6.4 Configuration Profiles

| Profile | Config Source | Purpose |
|---|---|---|
| (default) | `bootstrap.yml` -> `localhost:8090` | Local development |
| `dev` | `bootstrap-dev.yml` | Development environment |
| `docker` | `bootstrap-docker.yml` -> `internet-banking-config-server:8090` | Docker Compose deployment |

### 6.5 Test Infrastructure

- **Test Framework:** JUnit 5 (JUnit Platform)
- **Test Database:** H2 in-memory (for services with database dependencies)
- **Mocking:** Mockito
- **Test Coverage:** Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only empty Spring Boot context load tests.

### 6.6 API Documentation

- **Technology:** SpringDoc OpenAPI (`springdoc-openapi-starter-webflux-ui:2.1.0`)
- **Coverage:** Swagger `@Tag` and `@Operation` annotations present on all controllers in core-banking, user, fund-transfer, and utility-payment services
- **Postman Collection:** Available via external link for manual API testing
