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

### High-Level Summary

The Internet Banking system is a Java 21 / Spring Boot 3.2.4 microservices application implementing a banking platform with fund transfers and utility payments. It follows a standard Spring Cloud architecture with centralized configuration, service discovery, and an API gateway.

### Services

| Service | Port | Description |
|---|---|---|
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point for all client requests. Handles OAuth2/JWT authentication via Keycloak, routes requests to downstream services, and injects `X-Auth-Id` header for user identity propagation. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — serves externalized configuration from a Git repository (`internet-banking-microservices-configurations` on GitHub). All business services bootstrap from this server. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server — service discovery registry. All services register here and use it to locate each other by logical name. |
| **core-banking-service** | 8092 | The "bank core" — manages users, bank accounts, utility accounts, and processes the actual financial transactions (fund transfers, utility payments). Owns the canonical banking data and Flyway-managed schema. |
| **internet-banking-user-service** | 8083 | User management facade — handles user registration, approval workflows, and profile retrieval. Integrates with Keycloak for identity management and delegates to `core-banking-service` via OpenFeign for account verification. |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestrator — accepts transfer requests, persists a local audit record, delegates the actual transfer to `core-banking-service` via OpenFeign, and updates the local record with the result. |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestrator — accepts utility payment requests, persists a local audit record, delegates to `core-banking-service` via OpenFeign, and updates the local record with the result. |

### Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| Synchronous REST (service-to-service) | Spring Cloud OpenFeign | `user-service` → `core-banking-service`, `fund-transfer-service` → `core-banking-service`, `utility-payment-service` → `core-banking-service` |
| Service Discovery | Netflix Eureka | All services register with and discover each other through the Eureka registry. Feign clients resolve service names (e.g., `core-banking-service`) via Eureka. |
| API Gateway Routing | Spring Cloud Gateway | Client → `api-gateway` → downstream services. Routes are defined in externalized config. |
| Centralized Configuration | Spring Cloud Config (Git-backed) | All services (except config-server and service-registry) pull configuration from the config server at startup via bootstrap.yml. |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin | Trace context is propagated across services; spans are reported to a Zipkin collector. |
| Asynchronous Messaging | RabbitMQ (planned) | README mentions RabbitMQ for notification service, but no RabbitMQ dependencies or message producers/consumers exist in the current codebase. The Notification Service is listed as "PENDING Development." |
| Authentication | OAuth2 / JWT (Keycloak) | The API gateway validates JWT tokens issued by Keycloak and passes the authenticated user's ID downstream via the `X-Auth-Id` HTTP header. |

### Infrastructure Components

| Component | Technology | Container Name | Port |
|---|---|---|---|
| Relational Database | MySQL 8.4.0 | `mysql_javatodev_app` | 3306 |
| Identity Provider | Keycloak 23.0.7 | `keycloak_web` | 8080 |
| Keycloak Database | PostgreSQL 15 | `keycloak_postgre_db` | 5432 (closed) |
| Distributed Tracing | Zipkin 3 | `openzipkin_server` | 9411 |
| Message Broker | RabbitMQ (planned) | N/A | N/A |

### Network Topology (Docker Compose)

All containers are placed on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IP assignments. Services use `wait-for-it.sh` to wait for dependencies (service-registry, config-server, MySQL) before starting.

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

The core banking service owns the canonical banking data, managed via Flyway migrations.

#### Entity: `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | bigint(20) | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | varchar(255) | | User's first name |
| `last_name` | varchar(255) | | User's last name |
| `email` | varchar(255) | | User's email address |
| `identification_number` | varchar(255) | | National ID / identification number (unique business key) |

#### Entity: `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | bigint(20) | PK, AUTO_INCREMENT | Internal account ID |
| `number` | varchar(255) | | Account number (lookup key) |
| `type` | varchar(255) | | Account type: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | varchar(255) | | Account status: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | decimal(19,2) | | Actual balance |
| `available_balance` | decimal(19,2) | | Available balance |
| `user_id` | bigint(20) | FK → `banking_core_user(id)` | Owning user |

#### Entity: `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | bigint(20) | PK, AUTO_INCREMENT | Internal ID |
| `number` | varchar(255) | | Utility account number |
| `provider_name` | varchar(255) | | Utility provider name (e.g., `VODAFONE`, `VERIZON`) |

#### Entity: `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | bigint(20) | PK, AUTO_INCREMENT | Internal ID |
| `amount` | decimal(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | varchar(30) | NOT NULL | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | varchar(50) | NOT NULL | Reference (destination account number or utility ref) |
| `transaction_id` | varchar(50) | NOT NULL | UUID transaction identifier |
| `account_id` | bigint(20) | FK → `banking_core_account(id)` | Associated bank account |

#### Relationships

```
banking_core_user (1) ──── (N) banking_core_account
banking_core_account (1) ──── (N) banking_core_transaction
banking_core_utility_account (standalone — referenced by provider name or ID)
```

### 2.2 internet-banking-user-service (MySQL: `banking_core_user_service`)

#### Entity: `user` (JPA-managed, no Flyway)

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Internal ID |
| `authId` | String | Keycloak user ID |
| `identification` | String | National ID (links to core-banking-service user) |
| `status` | Enum (`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | User approval status |
| `createdDate` | Instant | Audit: creation timestamp |
| `createdBy` | String | Audit: creator auth ID |
| `modifiedDate` | Instant | Audit: last modification timestamp |
| `modifiedBy` | String | Audit: last modifier auth ID |
| `version` | long | Optimistic locking version |

### 2.3 internet-banking-fund-transfer-service (MySQL: `banking_core_fund_transfer_service`)

#### Entity: `fund_transfer` (JPA-managed, no Flyway)

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Internal ID |
| `transactionReference` | String | Transaction ID returned from core-banking-service |
| `fromAccount` | String | Source account number |
| `toAccount` | String | Destination account number |
| `amount` | BigDecimal | Transfer amount |
| `status` | Enum (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Transaction status |
| `createdDate` / `createdBy` / `modifiedDate` / `modifiedBy` / `version` | (audit fields) | Same audit pattern as user-service |

### 2.4 internet-banking-utility-payment-service (MySQL: `banking_core_utility_payment_service`)

#### Entity: `utility_payment` (JPA-managed, no Flyway)

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Internal ID |
| `providerId` | Long | Utility provider ID |
| `amount` | BigDecimal | Payment amount |
| `referenceNumber` | String | Customer reference number |
| `account` | String | Source account number |
| `transactionId` | String | Transaction ID returned from core-banking-service |
| `status` | Enum (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Payment status |
| `createdDate` / `createdBy` / `modifiedDate` / `modifiedBy` / `version` | (audit fields) | Same audit pattern |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All client requests go through the API gateway. Routes are defined in externalized config (Git repo) and prefix-strip downstream. Public endpoints and actuator paths are exempt from authentication.

| Gateway Path Prefix | Downstream Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

### 3.2 core-banking-service (port 8092)

#### AccountController — `/api/v1/account`

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### TransactionController — `/api/v1/transaction`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

#### UserController — `/api/v1/user`

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 internet-banking-user-service (port 8083)

#### UserController — `/api/v1/bank-users`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/register` | Register a new banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |
| `PATCH` | `/{id}` | Update user status | `{ status }` | `User` |

### 3.4 internet-banking-fund-transfer-service (port 8084)

#### FundTransferController — `/api/v1/transfer`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service (port 8085)

#### UtilityPaymentController — `/api/v1/utility-payment`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Actuator Endpoints (all services)

All services include `spring-boot-starter-actuator`. Default actuator endpoints (`/actuator/health`, `/actuator/info`, etc.) are available. Specific endpoint exposure depends on externalized config.

### 3.7 Swagger/OpenAPI

Core-banking-service, user-service, fund-transfer-service, and utility-payment-service include `springdoc-openapi-starter-webflux-ui:2.1.0` as a dependency for API documentation. Controllers are annotated with `@Tag` and `@Operation`.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (`internet-banking-user-service`)

**Location:** `UserService.createUser()`

1. Check if the email is already registered in Keycloak. If yes → throw `UserAlreadyRegisteredException`.
2. Call `core-banking-service` (via Feign) to verify the user exists by identification number.
3. Validate that the email matches the core banking user's email. If mismatch → throw `InvalidEmailException`.
4. Create user in Keycloak with: email, first/last name, password, `emailVerified=false`, `enabled=false`.
5. If Keycloak returns HTTP 201:
   - Retrieve the Keycloak user ID.
   - Persist a local `UserEntity` with status `PENDING`.
6. If the user is not found in core banking → throw `InvalidBankingUserException`.

### 4.2 User Approval Flow (`internet-banking-user-service`)

**Location:** `UserService.updateUser()`

1. Look up user by ID. If not found → throw `EntityNotFoundException`.
2. If the new status is `APPROVED`:
   - Read the user from Keycloak by `authId`.
   - Set `enabled=true` and `emailVerified=true` in Keycloak.
3. Update the local entity's status and persist.

### 4.3 Fund Transfer Flow

**Orchestration (`internet-banking-fund-transfer-service`):**

**Location:** `FundTransferService.fundTransfer()`

1. Create a `FundTransferEntity` with status `PENDING` and persist it.
2. Call `core-banking-service` `/api/v1/transaction/fund-transfer` via Feign.
3. On success: update entity with `transactionReference` and status `SUCCESS`.
4. Return success response.

**Execution (`core-banking-service`):**

**Location:** `TransactionService.fundTransfer()` and `internalFundTransfer()`

1. Read source and destination bank accounts.
2. Validate source account has sufficient funds (`actualBalance >= amount`). If not → throw `InsufficientFundsException`.
3. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
4. Record debit transaction entry.
5. Credit destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
6. Record credit transaction entry.
7. Return transaction ID (UUID).

**Note:** The entire operation runs within a single `@Transactional` scope.

### 4.4 Utility Payment Flow

**Orchestration (`internet-banking-utility-payment-service`):**

**Location:** `UtilityPaymentService.utilPayment()`

1. Create a `UtilityPaymentEntity` with status `PROCESSING` and persist it.
2. Call `core-banking-service` `/api/v1/transaction/util-payment` via Feign.
3. On success: update entity with `transactionId` and status `SUCCESS`.
4. Return success response.

**Execution (`core-banking-service`):**

**Location:** `TransactionService.utilPayment()`

1. Read source bank account.
2. Validate sufficient funds.
3. Read the utility account by provider ID.
4. Debit source account.
5. Record transaction entry with type `UTILITY_PAYMENT`.
6. Return transaction ID.

### 4.5 Balance Validation Rules

**Location:** `TransactionService.validateBalance()`

- Balance must be >= 0 AND >= requested amount.
- Throws `InsufficientFundsException` with error code `BANKING-CORE-SERVICE-1001`.

### 4.6 Identity Propagation

- API Gateway extracts the authenticated principal name from the JWT and injects it as `X-Auth-Id` header.
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in a `ThreadLocal` (`ApiRequestContextHolder`).
- JPA audit (`AuditorAwareConfig`) uses this context to set `createdBy`/`modifiedBy` fields.

---

## 5. Integration Points

### 5.1 Keycloak (Identity and Access Management)

| Aspect | Details |
|---|---|
| **Version** | 23.0.7 |
| **Realm** | `javatodev-internet-banking` (imported via `realm-export.json`) |
| **Client** | `internet-banking-api-client` |
| **Integration Library** | `keycloak-admin-client:24.0.4` (user-service) |
| **Gateway Integration** | OAuth2 Resource Server with JWT validation via `jwk-set-uri` |
| **User-Service Operations** | Create user, update user, search by email, read by auth ID |
| **Configuration** | `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) |

### 5.2 RabbitMQ (Message Broker)

| Aspect | Details |
|---|---|
| **Status** | **Planned but not implemented** |
| **Intended Use** | Fund transfer and utility payment services would push notification messages; a Notification Service would consume them |
| **Current State** | No RabbitMQ dependencies in any `build.gradle`. No message producers or consumers. No RabbitMQ container in Docker Compose. |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Details |
|---|---|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Port** | 9411 |
| **Client Libraries** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` (in all business services + API gateway) |
| **Feign Integration** | `feign-micrometer` for propagating trace context across Feign calls |
| **Configuration** | Trace reporting endpoint configured via externalized config |

### 5.4 Database Connections

| Service | Database | Schema | Migration Strategy |
|---|---|---|---|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` | Flyway (3 migration files) |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` | JPA `ddl-auto` (no Flyway) |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` | JPA `ddl-auto` (no Flyway) |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` | JPA `ddl-auto` (no Flyway) |

**Connection Details:** Credentials configured via externalized config (config server). The MySQL Docker container creates a dedicated user `javatodev_development` with broad privileges.

### 5.5 Spring Cloud Config (Git-backed)

| Aspect | Details |
|---|---|
| **Config Repo** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration` |
| **Bootstrap** | Services use `bootstrap.yml` / `bootstrap-docker.yml` / `bootstrap-dev.yml` for profile-specific config server URIs |
| **Profiles** | `default` (localhost:8090), `dev` (192.168.1.5:8090), `docker` (internet-banking-config-server:8090) |

### 5.6 Netflix Eureka (Service Discovery)

| Aspect | Details |
|---|---|
| **Server Port** | 8081 |
| **Configuration** | Self-registration disabled (`register-with-eureka: false`, `fetch-registry: false`) |
| **Clients** | All business services and the API gateway register as Eureka clients |
| **Feign Resolution** | Feign clients use logical service names (e.g., `core-banking-service`) resolved via Eureka |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

| Aspect | Details |
|---|---|
| **Build Tool** | Gradle (wrapper included per service, Gradle 8.6) |
| **Java Version** | 21 (Eclipse Temurin 21.0.2) |
| **Spring Boot** | 3.2.4 |
| **Spring Cloud** | 2023.0.0 |
| **Structure** | Each service has its own independent Gradle build (no multi-module root `build.gradle`) |
| **Plugins** | `java`, `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties` (most services) |

### 6.2 Docker

Each service has its own `Dockerfile` following a consistent pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses JRE-only Alpine images for small footprint.
- `wait-for-it.sh` is bundled for dependency readiness checks.
- Docker profile activates `bootstrap-docker.yml` for config server URI resolution.

### 6.3 Docker Compose

Two compose files are provided:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack — all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only — MySQL, Keycloak, PostgreSQL, Zipkin, config-server, service-registry |

**Startup Order (via `wait-for-it.sh` entrypoints):**
1. Infrastructure: MySQL, PostgreSQL, Keycloak, Zipkin
2. Config Server (port 8090)
3. Service Registry (port 8081)
4. Business services wait for both config-server and service-registry, plus MySQL

### 6.4 Build Workflow

```bash
# Per service (no root build file):
cd <service-directory>
./gradlew clean build        # Compile, test, package JAR
docker build -t javatodev/<service-name> .  # Build Docker image

# Run all:
cd docker-compose
docker-compose up -d
```

### 6.5 Test Infrastructure

| Aspect | Details |
|---|---|
| **Test Framework** | JUnit 5 (via `spring-boot-starter-test`) |
| **Test Database** | H2 in-memory (all services) |
| **Flyway in Tests** | Disabled (`flyway.enabled: false`) |
| **Eureka in Tests** | Disabled (`eureka.client.enabled: false`) for business services |
| **Existing Tests** | `contextLoads()` placeholder tests in most services; `core-banking-service` has unit tests for `TransactionService` and `UserService` |

### 6.6 CI/CD

No CI/CD pipeline configuration files (e.g., GitHub Actions, Jenkins, GitLab CI) are present in the repository. Deployment is manual via Docker Compose.
