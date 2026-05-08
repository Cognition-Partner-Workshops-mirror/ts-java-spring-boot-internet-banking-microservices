# Internet Banking Microservices — Application Knowledge Base

> **Generated:** 2026-05-08 | **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0

---

## 1. Architecture Overview

### 1.1 Services

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Central request router, OAuth2/JWT security filter via Keycloak | Spring Cloud Gateway, Spring Security OAuth2, WebFlux |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery | Spring Cloud Netflix Eureka Server |
| `internet-banking-config-server` | 8090 | Centralized configuration management (reads from GitHub repo) | Spring Cloud Config Server |
| `core-banking-service` | 8092 | Authoritative ledger — users, accounts, transactions, balance operations | Spring Data JPA, Flyway, MySQL |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates peer-to-peer fund transfers via Feign to core-banking | Spring Cloud OpenFeign, Spring Data JPA, MySQL |
| `internet-banking-user-service` | 8083 | User lifecycle management (registration, approval) with Keycloak integration | Keycloak Admin Client, OpenFeign, Spring Data JPA, MySQL |
| `internet-banking-utility-payment-service` | 8085 | Processes third-party biller/utility payments via Feign to core-banking | Spring Cloud OpenFeign, Spring Data JPA, MySQL |

### 1.2 Communication Patterns

- **Synchronous REST via OpenFeign:** All inter-service communication uses Spring Cloud OpenFeign with service names resolved through Eureka. No async messaging is implemented despite RabbitMQ being mentioned in the README.
- **Gateway → Downstream:** The API Gateway routes requests based on path prefixes and injects an `X-Auth-Id` header (extracted from the JWT principal) into proxied requests.
- **Downstream services trust `X-Auth-Id`:** Fund-transfer, user, and utility-payment services read the `X-Auth-Id` header via `AppAuthUserFilter` but perform no independent JWT validation.

### 1.3 Infrastructure Components

| Component | Image/Version | Port | Purpose |
|---|---|---|---|
| MySQL | 8.4 (custom Dockerfile) | 3306 | Primary data store — 4 schemas on one instance |
| Keycloak | 23.0.7 | 8080 | Identity and Access Management (OAuth2/OIDC) |
| PostgreSQL | 15 | 5432 (closed) | Keycloak's backing store |
| Zipkin | 3 | 9411 | Distributed tracing collector |

### 1.4 Network Topology

All containers run on a static Docker bridge network `javatodev_ib_network` (172.25.0.0/16). Each container has a fixed IPv4 address:

```
172.25.0.2  — core-banking-service
172.25.0.3  — internet-banking-utility-payment-service
172.25.0.4  — internet-banking-fund-transfer-service
172.25.0.5  — internet-banking-user-service
172.25.0.6  — internet-banking-api-gateway
172.25.0.7  — internet-banking-service-registry
172.25.0.8  — internet-banking-config-server
172.25.0.9  — mysql_javatodev_app
172.25.0.10 — keycloak_postgre_db
172.25.0.11 — keycloak_web
172.25.0.12 — openzipkin_server
```

---

## 2. Data Model Documentation

### 2.1 Schema: `banking_core_service` (owned by core-banking-service)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | — | First name |
| `last_name` | VARCHAR(255) | — | Last name |
| `email` | VARCHAR(255) | — | Email address |
| `identification_number` | VARCHAR(255) | — | National ID (NIC) — unique business key |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | — | Account number (12-digit string) |
| `type` | VARCHAR(255) | ENUM(`SAVINGS_ACCOUNT`) | Account type |
| `status` | VARCHAR(255) | ENUM(`ACTIVE`) | Account status |
| `actual_balance` | DECIMAL(19,2) | — | Ledger balance |
| `available_balance` | DECIMAL(19,2) | — | Available balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owning user |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | — | Transaction amount (negative = debit) |
| `transaction_type` | VARCHAR(30) | NOT NULL, ENUM(`FUND_TRANSFER`, `UTILITY_PAYMENT`) | Type discriminator |
| `reference_number` | VARCHAR(50) | NOT NULL | Target account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID correlating debit/credit legs |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal utility account ID |
| `number` | VARCHAR(255) | — | Utility provider account number |
| `provider_name` | VARCHAR(255) | — | Provider name (e.g., VODAFONE, AIRTEL) |

### 2.2 Schema: `banking_core_fund_transfer_service`

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `transaction_reference` | VARCHAR(255) | — | UUID from core-banking response |
| `from_account` | VARCHAR(255) | — | Source account number |
| `to_account` | VARCHAR(255) | — | Destination account number |
| `amount` | DECIMAL(19,2) | — | Transfer amount |
| `status` | VARCHAR(255) | ENUM(`PENDING`, `SUCCESS`) | Transfer status |
| `created_date` | TIMESTAMP | Audit | Creation timestamp |
| `created_by` | VARCHAR(255) | Audit | Creator (from X-Auth-Id) |
| `modified_date` | TIMESTAMP | Audit | Last modification timestamp |
| `modified_by` | VARCHAR(255) | Audit | Last modifier |
| `version` | BIGINT | Optimistic lock | JPA `@Version` field |

### 2.3 Schema: `banking_core_user_service`

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `auth_id` | VARCHAR(255) | — | Keycloak user UUID |
| `identification` | VARCHAR(255) | — | NIC linking to core-banking user |
| `status` | VARCHAR(255) | ENUM(`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | Approval status |
| `created_date` / `modified_date` | TIMESTAMP | Audit | Timestamps |
| `created_by` / `modified_by` | VARCHAR(255) | Audit | Actor identifiers |
| `version` | BIGINT | Optimistic lock | JPA `@Version` field |

### 2.4 Schema: `banking_core_utility_payment_service`

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `provider_id` | BIGINT | — | Utility provider ID |
| `amount` | DECIMAL(19,2) | — | Payment amount |
| `reference_number` | VARCHAR(255) | — | Bill reference |
| `account` | VARCHAR(255) | — | Paying bank account number |
| `transaction_id` | VARCHAR(255) | — | UUID from core-banking response |
| `status` | VARCHAR(255) | ENUM(`PROCESSING`, `SUCCESS`) | Payment status |
| `created_date` / `modified_date` | TIMESTAMP | Audit | Timestamps |
| `created_by` / `modified_by` | VARCHAR(255) | Audit | Actor identifiers |
| `version` | BIGINT | Optimistic lock | JPA `@Version` field |

### 2.5 Entity Relationship Summary

```
banking_core_user  1──*  banking_core_account  1──1  banking_core_transaction
                                                        (per transaction leg)

banking_core_utility_account  (standalone — referenced by providerId/providerName)

fund_transfer  (standalone — references account numbers as strings)
user           (standalone — references core user by NIC, Keycloak by authId)
utility_payment (standalone — references account number as string)
```

---

## 3. API Surface Map

### 3.1 Gateway Routes

| Path Prefix | Target Service | Example |
|---|---|---|
| `/user/**` | internet-banking-user-service | `/user/api/v1/bank-users/register` |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | `/fund-transfer/api/v1/transfer` |
| `/payment/**` | internet-banking-utility-payment-service | `/payment/api/v1/utility-payment` |
| `/core/**` | core-banking-service | `/core/api/v1/account/bank-account/{num}` |

Public endpoint: `POST /user/api/v1/bank-users/register`
All other endpoints require a valid Keycloak JWT.

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Retrieve bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Retrieve utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Execute internal fund transfer (debit + credit) |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Execute utility payment (debit only) |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Retrieve user by NIC |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated list of users |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate a fund transfer (saves PENDING, calls core-banking, updates SUCCESS) |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | Paginated list of fund transfers |

### 3.4 User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` | Register new user (creates Keycloak account + local record) — PUBLIC |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (e.g., APPROVED) |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated list of users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by internal ID |

### 3.5 Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process a utility payment (saves PROCESSING, calls core-banking, updates SUCCESS) |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | Paginated list of utility payments |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

```
Client → Gateway → fund-transfer-service → core-banking-service
```

1. `FundTransferService.fundTransfer()` receives request (fromAccount, toAccount, amount, authID).
2. Saves a `FundTransferEntity` with status `PENDING`.
3. Makes a synchronous Feign call to `core-banking-service /api/v1/transaction/fund-transfer`.
4. Core-banking's `TransactionService.fundTransfer()`:
   - Reads both bank accounts via `AccountService`.
   - Validates the sender has sufficient balance (`actualBalance >= amount`).
   - Calls `internalFundTransfer()` which debits source and credits destination.
   - Creates two `TransactionEntity` records (debit leg and credit leg) with the same `transactionId`.
5. Fund-transfer-service updates entity to `SUCCESS` with the returned `transactionId`.

**Known Bug:** `internalFundTransfer()` sets `availableBalance = actualBalance - amount` AFTER `actualBalance` was already debited, causing a double-deduction on `availableBalance`. Same bug exists in `utilPayment()`.

### 4.2 Utility Payment Flow

```
Client → Gateway → utility-payment-service → core-banking-service
```

1. `UtilityPaymentService.utilPayment()` receives request (providerId, amount, referenceNumber, account).
2. Saves a `UtilityPaymentEntity` with status `PROCESSING`.
3. Makes a synchronous Feign call to `core-banking-service /api/v1/transaction/util-payment`.
4. Core-banking's `TransactionService.utilPayment()`:
   - Reads the bank account and validates balance.
   - Reads the utility account by `providerId`.
   - Debits `actualBalance` and `availableBalance` from the source account.
   - Creates one `TransactionEntity` record.
5. Utility-payment-service updates entity to `SUCCESS`.

### 4.3 User Registration Flow

```
Client → Gateway → user-service → Keycloak + core-banking-service
```

1. `UserService.createUser()` receives (email, identification/NIC, password).
2. Checks Keycloak for existing user with the same email (rejects if found).
3. Calls core-banking-service via Feign to look up the user by NIC.
4. Validates that the email matches the core-banking user's email.
5. Creates a Keycloak user (disabled, email unverified, with provided password).
6. Saves a local `UserEntity` with status `PENDING` and the Keycloak `authId`.
7. Admin approves via `PATCH /update/{id}` with `{ status: "APPROVED" }`, which enables the Keycloak user and marks email as verified.

### 4.4 Balance Validation Rules

- Transfer/payment is rejected if `actualBalance < 0` OR `actualBalance < requestedAmount`.
- Throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001`.

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

- **Version:** 23.0.7 (via Docker)
- **Used by:** API Gateway (JWT validation), User Service (admin client for user CRUD)
- **Realm:** Imported from `docker-compose/keycloak/` volume mount
- **Gateway config:** OAuth2 Resource Server with JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)
- **User Service config:** Keycloak Admin Client using `client_credentials` grant (properties: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret`)
- **Credential store:** PostgreSQL 15 (dedicated container)

### 5.2 Zipkin (Distributed Tracing)

- **Version:** 3 (via Docker)
- **Integration:** All 6 services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Collection endpoint:** `http://172.25.0.12:9411`
- **Feign tracing:** `feign-micrometer` dependency enables trace propagation across Feign calls.

### 5.3 MySQL (Data Store)

- **Version:** 8.4 (custom Dockerfile with privilege initialization)
- **Connection:** Single MySQL instance with 4 schemas: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **User:** `javatodev_development` with broad `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` privileges
- **Root password:** Hardcoded in `docker-compose.yml`
- **Application password:** Hardcoded in `privileges.sql`

### 5.4 Spring Cloud Config Server

- **Source:** GitHub repo `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`)
- **Profiles:** `dev` (local development) and `docker` (containerized deployment)
- **Bootstrap:** Each service has `bootstrap.yml` pointing to `http://localhost:8090` and profile-specific overrides (`bootstrap-dev.yml`, `bootstrap-docker.yml`).

### 5.5 Netflix Eureka (Service Discovery)

- **Server:** `internet-banking-service-registry` on port 8081
- **Clients:** All other 5 services register as Eureka clients
- **Feign resolution:** `@FeignClient(name = "core-banking-service")` resolved via Eureka

### 5.6 RabbitMQ (Not Implemented)

- Mentioned in README as part of the architecture but **no RabbitMQ dependency, configuration, or code** exists in the codebase. The planned Notification service is listed as "PENDING Development."

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (each service has its own independent `build.gradle` — no multi-module root project)
- **Java version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Common plugins:** `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`
- **Build command:** `./gradlew clean build` (per service)

### 6.2 Docker

- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has a `Dockerfile` that copies the fat JAR as `app.jar`, installs bash (for `wait-for-it.sh`), and sets the entrypoint.
- **Startup ordering:** `wait-for-it.sh` script with 50-second timeouts ensures services wait for config-server, service-registry, and MySQL before starting.
- **Active profile:** `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:
- **`docker-compose-support-apps.yml`:** Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry)
- **`docker-compose.yml`:** Full stack including all 6 application services plus infrastructure

### 6.4 CI/CD

- **No CI/CD pipeline** is configured in the repository. GitHub Actions workflows were removed (commit `fa1c445`).
- **No Kubernetes manifests** despite Kubernetes being listed in the README technology stack.

### 6.5 Test Infrastructure

- **Test database:** H2 in-memory (`com.h2database:h2:2.2.224`) configured as a test dependency in 4 services
- **Test framework:** JUnit 5 via `spring-boot-starter-test`
- **Existing tests:** Only `core-banking-service` has meaningful unit tests (3 test classes, ~20 test methods). All other services have only empty `ApplicationTests` classes.

### 6.6 API Documentation

- **Swagger/OpenAPI:** `springdoc-openapi-starter-webflux-ui:2.1.0` is included in 4 services (core-banking, fund-transfer, user, utility-payment). However, the `webflux-ui` variant is incorrect for Spring MVC services — `webmvc-ui` should be used instead.
- **Annotations:** Controllers have `@Tag` and `@Operation` annotations for Swagger documentation.
