# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### 1.1 System Summary

The Internet Banking application is a Java 21 / Spring Boot 3.2.4 microservices system built on Spring Cloud 2023.0.0. It implements an internet banking concept with six independently deployable services communicating via synchronous REST (OpenFeign) and asynchronous messaging (RabbitMQ — planned).

### 1.2 Services

| Service | Port | Responsibility |
|---|---|---|
| **core-banking-service** | 8092 | Acts as the core banking ledger. Manages bank accounts, users, utility accounts, and processes fund transfers and utility payments at the database level. |
| **internet-banking-user-service** | 8083 | Handles internet banking user registration, approval workflows, and profile management. Integrates with Keycloak for identity and with core-banking-service for user verification. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests from internet banking clients. Persists transfer records locally and delegates actual balance changes to core-banking-service via Feign. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments. Persists payment records locally and delegates balance changes to core-banking-service via Feign. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway acting as the single entry point. Routes requests to downstream services, enforces OAuth2/JWT authentication via Keycloak, and injects `X-Auth-Id` header. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery. All business services register here and resolve each other by service name. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server that externalizes configuration from a [remote Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). |

### 1.3 Communication Patterns

```
┌─────────┐       ┌──────────────┐       ┌──────────────────────┐
│  Client  │──────▶│  API Gateway │──────▶│  Service Registry    │
│ (Browser/│  JWT  │  (8082)      │       │  (Eureka – 8081)     │
│  Postman)│       └──────┬───────┘       └──────────────────────┘
                          │                        ▲
                          │  routes to             │ registers
                          ▼                        │
              ┌───────────────────────┐            │
              │  user-service (8083)  │────────────┤
              │  fund-transfer (8084) │────────────┤
              │  utility-payment(8085)│────────────┤
              │  core-banking (8092)  │────────────┘
              └───────────┬───────────┘
                          │
                          │  OpenFeign (sync REST)
                          ▼
              ┌───────────────────────┐
              │  core-banking-service │
              │  (source of truth)    │
              └───────────────────────┘
```

- **Synchronous (OpenFeign):** user-service, fund-transfer-service, and utility-payment-service call core-banking-service via Feign clients resolved through Eureka.
- **Asynchronous (RabbitMQ):** Planned for notification service (not yet implemented). Fund transfer and utility payment services are designed to push messages to a RabbitMQ queue.
- **Configuration:** All services fetch configuration at startup from the Config Server, which reads from a remote Git repository.
- **Service Discovery:** All services register with the Eureka service registry and resolve inter-service calls by logical service name.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Discovery | Netflix Eureka | Service registration and DNS-like resolution |
| API Gateway | Spring Cloud Gateway | Routing, authentication, header injection |
| Config Server | Spring Cloud Config | Externalized, Git-backed configuration |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC, user management, JWT issuance |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across service boundaries |
| Message Broker | RabbitMQ (planned) | Async notifications (not yet implemented) |
| Containerization | Docker + Docker Compose | Local development and deployment |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / identification number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `actual_balance` | DECIMAL(19,2) | | Actual/ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owning user |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counterparty account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID linking related debit/credit entries |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Account involved |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal utility account ID |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, auto-DDL)

| Field | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Internal ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Links to core banking user's identification number |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic lock version |

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, auto-DDL)

| Field | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Internal ID |
| `transaction_reference` | VARCHAR | UUID from core-banking-service |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields (inherited from `AuditAware`) |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, auto-DDL)

| Field | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Internal ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking-service |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields (inherited from `AuditAware`) |

### 2.5 Entity Relationships

```
banking_core_user  1──────*  banking_core_account  1──────1  banking_core_transaction
                                                               (per entry; same transaction_id
                                                                links debit + credit records)

banking_core_utility_account  (standalone lookup table)

user_service.user  ──(auth_id)──▶  Keycloak user
user_service.user  ──(identification)──▶  core_banking.banking_core_user.identification_number

fund_transfer.fund_transfer  ──(from_account/to_account)──▶  core_banking.banking_core_account.number
utility_payment.utility_payment  ──(account)──▶  core_banking.banking_core_account.number
```

---

## 3. API Surface Map

### 3.1 Core Banking Service (`/api/v1/...` on port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Look up bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Look up utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Look up user by identification number |
| `GET` | `/api/v1/user` | Pageable query params | `List<User>` | Paginated list of users |
| `POST` | `/api/v1/transaction/fund-transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Execute a fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Execute a utility payment |

### 3.2 Internet Banking User Service (`/api/v1/bank-users/...` on port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` (email, identification, password) | `User` | Register a new internet banking user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{status}` | `User` | Update user status (e.g., approve user → enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | Pageable query params | `List<User>` | Paginated list of registered users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Look up user by internal ID |

### 3.3 Internet Banking Fund Transfer Service (`/api/v1/transfer/...` on port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Initiate a fund transfer (delegates to core-banking-service) |
| `GET` | `/api/v1/transfer` | Pageable query params | `List<FundTransfer>` | Paginated list of fund transfer records |

### 3.4 Internet Banking Utility Payment Service (`/api/v1/utility-payment/...` on port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Process a utility payment (delegates to core-banking-service) |
| `GET` | `/api/v1/utility-payment` | Pageable query params | `List<UtilityPayment>` | Paginated list of utility payment records |

### 3.5 API Gateway Routes (port 8082)

The gateway routes requests to downstream services using path prefixes (configured externally via Config Server):

| Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Security rules:**
- `/user/api/v1/bank-users/register` — **public** (no auth required)
- `/actuator/**` and service-specific actuator paths — **public**
- All other endpoints — **authenticated** (JWT required, validated against Keycloak JWK URI)

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Git build info |
| Service Registry | `http://localhost:8081/` | Eureka dashboard |
| Config Server | `http://localhost:8090/{service}/{profile}` | Configuration retrieval |
| Core Banking | `/swagger-ui.html` (via springdoc) | OpenAPI documentation |
| Fund Transfer | `/swagger-ui.html` (via springdoc) | OpenAPI documentation |
| Utility Payment | `/swagger-ui.html` (via springdoc) | OpenAPI documentation |
| User Service | `/swagger-ui.html` (via springdoc) | OpenAPI documentation |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Call core-banking-service to verify the user exists by identification number.
3. Validate that the email in the request matches the email in core banking. If not, throw `InvalidEmailException`.
4. Create a Keycloak user representation (disabled, email unverified) with the provided password.
5. Call Keycloak Admin API to create the user. If HTTP 201 is returned:
   - Read back the created user from Keycloak to get the `authId`.
   - Save to local database with status `PENDING`.
6. If user not found in core banking, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Look up user entity by ID.
2. If the new status is `APPROVED`:
   - Read user from Keycloak by `authId`.
   - Set `enabled = true` and `emailVerified = true` in Keycloak.
   - Update the Keycloak user.
3. Update local user entity status.

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Create a `FundTransferEntity` with status `PENDING` and save to local DB.
2. Call core-banking-service `/api/v1/transaction/fund-transfer` via Feign.
3. Core banking service (`TransactionService.fundTransfer()`):
   a. Look up source and destination bank accounts.
   b. Validate source account has sufficient balance.
   c. Debit source account (subtract from both `actualBalance` and `availableBalance`).
   d. Create debit transaction record.
   e. Credit destination account (add to both `actualBalance` and `availableBalance`).
   f. Create credit transaction record.
   g. Both transaction records share the same `transactionId` UUID.
4. Update local entity with `transactionReference` and status `SUCCESS`.

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Create a `UtilityPaymentEntity` with status `PROCESSING` and save to local DB.
2. Call core-banking-service `/api/v1/transaction/util-payment` via Feign.
3. Core banking service (`TransactionService.utilPayment()`):
   a. Look up source bank account.
   b. Validate sufficient balance.
   c. Look up utility provider by `providerId`.
   d. Debit source account.
   e. Create transaction record of type `UTILITY_PAYMENT`.
4. Update local entity with `transactionId` and status `SUCCESS`.

### 4.5 Balance Validation Rules

**Location:** `core-banking-service` → `TransactionService.validateBalance()`

- Throws `InsufficientFundsException` if:
  - `actualBalance < 0`, OR
  - `actualBalance < requestedAmount`

### 4.6 Authentication / Authorization Header Propagation

1. Client sends JWT bearer token to API Gateway.
2. Gateway validates JWT against Keycloak JWK endpoint.
3. `GatewayConfiguration` global filter extracts the principal name and injects it as `X-Auth-Id` header into the proxied request.
4. Downstream services have `AppAuthUserFilter` that reads `X-Auth-Id` and stores it in a thread-local `ApiRequestContextHolder`.
5. `AuditorAwareConfig` uses this context to populate `createdBy` / `modifiedBy` audit fields.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **Realm** | Imported via `realm-export.json` at container startup |
| **Admin credentials** | `admin` / `password` (Docker Compose env) |
| **Integration** | `keycloak-admin-client:24.0.4` in user-service |
| **Auth flow** | Client credentials grant (`client_credentials`) for admin operations |
| **Properties** | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Config Server) |
| **Gateway** | OAuth2 Resource Server validating JWTs via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |

### 5.2 RabbitMQ (Message Broker)

| Aspect | Detail |
|---|---|
| **Status** | **Planned but not implemented** |
| **Intended use** | Notification service to consume fund transfer and payment events |
| **Current state** | No RabbitMQ dependency in any `build.gradle`; no message producer/consumer code exists |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Port** | 9411 |
| **Libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave` in all business services |
| **Configuration** | Trace export configured via Config Server (`management.zipkin.tracing.endpoint`) |

### 5.4 Database Connections

| Service | Database Name | Driver | Schema Management |
|---|---|---|---|
| core-banking-service | `banking_core_service` | MySQL 8.4.0 (`com.mysql:mysql-connector-j:8.4.0`) | Flyway migrations |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4.0 | JPA auto-DDL |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4.0 | JPA auto-DDL |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4.0 | JPA auto-DDL |

**Test databases:** All services use H2 in-memory for tests (`com.h2database:h2:2.2.224`).

**MySQL initialization:** `privileges.sql` creates a `javatodev_development` user with broad privileges and pre-creates all four databases.

### 5.5 Spring Cloud Config Server

| Aspect | Detail |
|---|---|
| **Git URI** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search path** | `configuration` |
| **Profiles** | `default` (local dev), `docker` (container deployment) |
| **Bootstrap** | Services use `bootstrap.yml` / `bootstrap-docker.yml` to locate config server |

### 5.6 Inter-Service Feign Clients

| Source Service | Target Service | Feign Client | Endpoints Called |
|---|---|---|---|
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (per-service `build.gradle`, no multi-project root build file)
- **Java version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot:** 3.2.4 via `org.springframework.boot` Gradle plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Common plugins:** `com.gorylenko.gradle-git-properties` for embedding Git metadata

### 6.2 Docker Build

Each service has its own `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Build sequence: `./gradlew build` in each service directory → `docker build` → `docker-compose up`.

### 6.3 Docker Compose Topology

**Primary file:** `docker-compose/docker-compose.yml`

Brings up the full stack with a custom bridge network (`172.25.0.0/16`) and static IPs:

| Container | Image | IP | Dependencies |
|---|---|---|---|
| `openzipkin_server` | `openzipkin/zipkin:3` | 172.25.0.12 | — |
| `keycloak_web` | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | keycloakdb |
| `keycloak_postgre_db` | `postgres:15` | 172.25.0.10 | — |
| `mysql_javatodev_app` | Custom (MySQL 8.4.0) | 172.25.0.9 | — |
| `internet-banking-config-server` | `javatodev/internet-banking-config-server` | 172.25.0.8 | — |
| `internet-banking-service-registry` | `javatodev/internet-banking-service-registry` | 172.25.0.7 | — |
| `internet-banking-api-gateway` | `javatodev/internet-banking-api-gateway` | 172.25.0.6 | registry, config-server |
| `internet-banking-user-service` | `javatodev/internet-banking-user-service` | 172.25.0.5 | registry, config-server, mysql |
| `internet-banking-fund-transfer-service` | `javatodev/internet-banking-fund-transfer-service` | 172.25.0.4 | registry, config-server, mysql |
| `internet-banking-utility-payment-service` | `javatodev/internet-banking-utility-payment-service` | 172.25.0.3 | registry, config-server, mysql |
| `core-banking-service` | `javatodev/core-banking-service` | 172.25.0.2 | registry, config-server, mysql |

**Support-only file:** `docker-compose-support-apps.yml` brings up only infrastructure (Zipkin, Keycloak, MySQL, Config Server, Service Registry) for local development against IDE-launched services.

### 6.4 Startup Orchestration

Services use `wait-for-it.sh` scripts in their Docker entrypoints to wait for:
1. Service Registry (port 8081)
2. Config Server (port 8090)
3. MySQL (port 3306) — for services with DB access

### 6.5 Test Infrastructure

- **Framework:** JUnit 5 (`useJUnitPlatform()`)
- **Test DB:** H2 in-memory
- **Existing tests:** Only `core-banking-service` has meaningful unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only empty context-load test stubs.
- **No integration tests, no contract tests.**

### 6.6 Monitoring & Observability

- **Actuator:** All services include `spring-boot-starter-actuator`
- **Tracing:** Micrometer Brave bridge + Zipkin reporter
- **Metrics:** Actuator endpoints exposed (Prometheus mentioned in README but no explicit Prometheus dependency or configuration found)
- **OpenAPI:** `springdoc-openapi-starter-webflux-ui:2.1.0` included in business services for Swagger UI
