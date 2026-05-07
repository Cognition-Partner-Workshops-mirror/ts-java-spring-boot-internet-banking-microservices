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

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. It models a simplified banking system with user registration, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| # | Service | Port | Purpose | Database |
|---|---------|------|---------|----------|
| 1 | `core-banking-service` | 8092 | Central banking ledger — accounts, users, transactions | MySQL (`banking_core_service`) |
| 2 | `internet-banking-user-service` | 8083 | User registration/management with Keycloak integration | MySQL (`banking_core_user_service`) |
| 3 | `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration between accounts | MySQL (`banking_core_fund_transfer_service`) |
| 4 | `internet-banking-utility-payment-service` | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| 5 | `internet-banking-api-gateway` | 8082 | Single entry point, routing, OAuth2 enforcement | None |
| 6 | `internet-banking-service-registry` | 8081 | Eureka service discovery server | None |

**Infrastructure services** (not application microservices):

| Service | Port | Purpose |
|---------|------|---------|
| `internet-banking-config-server` | 8090 | Spring Cloud Config — centralized configuration (Git-backed) |
| Keycloak | 8080 | Identity provider (OAuth2 / OpenID Connect) |
| MySQL 8.4 | 3306 | Shared RDBMS instance (4 logical databases) |
| PostgreSQL 15 | 5432 (internal) | Keycloak's backing store |
| Zipkin | 9411 | Distributed tracing collector |

### 1.3 Communication Patterns

```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │ HTTPS (OAuth2 Bearer Token)
       ▼
┌──────────────────────────┐
│  API Gateway (:8082)     │──── OAuth2 Resource Server (Keycloak JWT)
│  Spring Cloud Gateway    │──── Global Filter: injects X-Auth-Id header
└──────┬───────────────────┘
       │  Routes by path prefix
       ├──► /user/**          → internet-banking-user-service
       ├──► /fund-transfer/** → internet-banking-fund-transfer-service
       ├──► /utility-payment/**→ internet-banking-utility-payment-service
       └──► /banking-core/**  → core-banking-service
```

**Inter-service communication:**

| From | To | Mechanism | Purpose |
|------|----|-----------|---------|
| `user-service` | `core-banking-service` | OpenFeign (sync HTTP) | Validate user by identification number |
| `fund-transfer-service` | `core-banking-service` | OpenFeign (sync HTTP) | Execute fund transfers via core ledger |
| `utility-payment-service` | `core-banking-service` | OpenFeign (sync HTTP) | Execute utility payments via core ledger |
| `user-service` | Keycloak | Keycloak Admin Client (REST) | Create/update/query users in IdP |
| All services | Config Server | Spring Cloud Config Client | Fetch externalized configuration at startup |
| All services | Service Registry | Eureka Client | Register/discover service instances |
| All services | Zipkin | Micrometer Tracing (Brave) | Report distributed traces |

**Key pattern:** The API Gateway extracts the authenticated user's principal name and forwards it as an `X-Auth-Id` HTTP header. Downstream services read this header via `AppAuthUserFilter` and store it in a `ThreadLocal` (`ApiRequestContextHolder`) for JPA auditing (`createdBy`, `modifiedBy` fields).

### 1.4 Infrastructure Components

- **Service Discovery:** Netflix Eureka (`spring-cloud-starter-netflix-eureka-server/client`)
- **API Gateway:** Spring Cloud Gateway (reactive, WebFlux-based)
- **Centralized Config:** Spring Cloud Config Server (Git-backed repository)
- **Authentication:** Keycloak 23.0.7 via OAuth2/OIDC with JWT validation at the gateway
- **Distributed Tracing:** Micrometer Tracing with Brave bridge, reporting to Zipkin
- **Database Migrations:** Flyway (core-banking-service only)
- **Object Mapping:** Manual mappers using `BeanUtils.copyProperties` with a custom `BaseMapper<E, D>` abstract class
- **Build Tool:** Gradle 8.6 with Spring Boot plugin (each service is an independent Gradle project — no multi-module build)

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

The core banking service owns the central ledger and defines 4 tables via Flyway migrations:

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / passport number |

**JPA Entity:** `UserEntity` → `@OneToMany` → `BankAccountEntity` (lazy, cascade ALL)

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Balance available for transactions |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owning user |

**JPA Entity:** `BankAccountEntity` → `@ManyToOne` → `UserEntity`

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

**JPA Entity:** `TransactionEntity` → `@OneToOne(cascade=ALL)` → `BankAccountEntity`

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Provider ID |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE, AIRTEL) |

### 2.2 User Service (internet-banking-user-service)

#### `user` table (managed by JPA/Hibernate — `ddl-auto` via config)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal user ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID linking to core banking user |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service

#### `fund_transfer` table

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Transfer ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking service |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields |

### 2.4 Utility Payment Service

#### `utility_payment` table

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account |
| `transaction_id` | VARCHAR | UUID from core banking service |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields |

### 2.5 Entity Relationship Diagram (Logical)

```
banking_core_user 1──►N banking_core_account 1──►1 banking_core_transaction
                                                          │
banking_core_utility_account (standalone)                  │
                                                          │
user (user-service) ──── keycloak user (auth_id) ────► core banking user (identification)
                                                          │
fund_transfer ──── references ────────────────────────► banking_core_account (via account numbers)
utility_payment ── references ────────────────────────► banking_core_account + banking_core_utility_account
```

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Retrieve bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Retrieve utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` (with nested `BankAccount[]`) | Retrieve user by identification number |
| `GET` | `/api/v1/user` | `Pageable` query params | `List<User>` | Paginated list of users |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` `{fromAccount, toAccount, amount}` | `FundTransferResponse` `{message, transactionId}` | Execute a fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` `{providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse` `{message, transactionId}` | Execute a utility payment |

### 3.2 User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` `{email, identification, password}` | `User` | Register new user (creates Keycloak + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` `{status}` | `User` | Update user status (enables Keycloak account on APPROVED) |
| `GET` | `/api/v1/bank-users` | `Pageable` query params | `List<User>` | Paginated list of registered users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Retrieve user by internal ID |

### 3.3 Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` `{fromAccount, toAccount, amount, authID}` | `FundTransferResponse` `{message, transactionId}` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | `Pageable` query params | `List<FundTransfer>` | Paginated list of fund transfers |

### 3.4 Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` `{providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse` `{message, transactionId}` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | `Pageable` query params | `List<UtilityPayment>` | Paginated list of utility payments |

### 3.5 API Gateway Routes (`:8082`)

All requests flow through the API Gateway with path-based routing:

| Path Prefix | Target Service | Auth Required |
|-------------|---------------|---------------|
| `/user/**` | `internet-banking-user-service` | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | Yes |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | Yes |
| `/banking-core/**` | `core-banking-service` | Yes |
| `/**/actuator/**` | Respective services | No |

### 3.6 Actuator Endpoints (All Services)

All services expose Spring Boot Actuator endpoints (health, info, metrics) at `/actuator/**`. These are explicitly permitted without authentication at the gateway.

### 3.7 OpenAPI / Swagger

Services annotated with `springdoc-openapi-starter-webflux-ui` (core-banking, user, fund-transfer, utility-payment) expose Swagger UI. Each controller uses `@Tag` and `@Operation` annotations for documentation.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException` if found
2. Call core-banking-service to validate `identification` number → fetch `UserResponse`
3. Validate that the provided email matches the core banking record → throw `InvalidEmailException` if mismatch
4. Create user in Keycloak with `emailVerified=false`, `enabled=false`, and provided password
5. If Keycloak returns 201, read back the created user to get `authId`
6. Save user entity locally with `status=PENDING`
7. If core banking user not found → throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Find user by internal ID
2. If new status is `APPROVED`:
   - Read Keycloak user representation
   - Set `enabled=true`, `emailVerified=true`
   - Update Keycloak
3. Update local entity status

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Save transfer record with `status=PENDING`
2. Call `core-banking-service` `/api/v1/transaction/fund-transfer` via Feign
3. On success: update local record with `transactionReference` and `status=SUCCESS`

**Core banking execution** (`TransactionService.fundTransfer()`):

1. Read source and destination bank accounts
2. **Validate balance:** `actualBalance >= 0` AND `actualBalance >= transferAmount` → throw `InsufficientFundsException` otherwise
3. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
4. Record debit transaction (negative amount)
5. Credit destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
6. Record credit transaction (positive amount)
7. Return transaction UUID

**Note:** The entire operation is `@Transactional` at the core banking service level.

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Save payment record with `status=PROCESSING`
2. Call `core-banking-service` `/api/v1/transaction/util-payment` via Feign
3. On success: update local record with `transactionId` and `status=SUCCESS`

**Core banking execution** (`TransactionService.utilPayment()`):

1. Read source bank account
2. **Validate balance** (same rules as fund transfer)
3. Look up utility provider account by `providerId`
4. Debit source account
5. Record debit transaction with `UTILITY_PAYMENT` type
6. Return transaction UUID

### 4.5 Balance Calculation Logic

**Location:** `TransactionService.validateBalance()`

```java
if (actualBalance < 0 || actualBalance < requestedAmount) {
    throw InsufficientFundsException
}
```

**Known issue:** After debit, `availableBalance` is set to `actualBalance - amount` (double subtraction). This is a bug — `availableBalance` should be set to the new `actualBalance` after subtraction, not `actualBalance` minus the amount again.

### 4.6 Audit Trail

All domain services (user, fund-transfer, utility-payment) extend `AuditAware`, which tracks:
- `createdDate` / `createdBy` — set automatically via JPA Auditing
- `modifiedDate` / `modifiedBy` — updated on every save
- `version` — optimistic locking via `@Version`

The `createdBy`/`modifiedBy` values come from the `X-Auth-Id` header propagated by the API Gateway.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Realm:** `javatodev-internet-banking`
- **Client ID:** `internet-banking-core-client` (for token issuance) / `internet-banking-api-client` (for admin API)
- **Integration method:**
  - **Gateway:** JWT validation via `spring-boot-starter-oauth2-resource-server` using JWK Set URI
  - **User Service:** Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`) with `client_credentials` grant
- **Configuration properties:**
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Realm name
  - `app.config.keycloak.clientId` — Client ID for admin operations
  - `app.config.keycloak.client-secret` — Client secret
- **Realm export:** Pre-loaded via Docker volume mount (`docker-compose/keycloak/realm-export.json`)

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in the README as a planned integration for the Notification Service
- **Current usage:** **Not implemented** — no RabbitMQ dependency in any `build.gradle`, no message producer/consumer code
- **Planned purpose:** Push notification messages from fund-transfer and utility-payment services to a centralized queue

### 5.3 Zipkin (Distributed Tracing)

- **Version:** openzipkin/zipkin:3
- **Port:** 9411
- **Integration:** All application services include:
  - `micrometer-tracing-bridge-brave` — Brave tracer bridge
  - `zipkin-reporter-brave` — Zipkin span reporter
  - `feign-micrometer` — Propagates trace context across Feign calls
- **Configuration:** Managed via Spring Cloud Config Server (Zipkin URL set in centralized config)

### 5.4 Database Connections

| Service | Database | Schema | Driver | Migration |
|---------|----------|--------|--------|-----------|
| `core-banking-service` | MySQL 8.4 | `banking_core_service` | `mysql-connector-j:8.4.0` | Flyway (`V1.0.20210427...`, `V1.0.20210429...`) |
| `user-service` | MySQL 8.4 | `banking_core_user_service` | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via config) |
| `fund-transfer-service` | MySQL 8.4 | `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via config) |
| `utility-payment-service` | MySQL 8.4 | `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via config) |
| Keycloak | PostgreSQL 15 | `keycloak` | Internal | Keycloak-managed |

All application databases are on a **single MySQL instance** initialized with `privileges.sql` that creates a shared user (`javatodev_development`) and all 4 schemas.

### 5.5 Spring Cloud Config Server

- **Port:** 8090
- **Purpose:** Centralized externalized configuration for all services
- **Client integration:** All services use `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap` to fetch config at startup
- **Profile support:** `default`, `dev`, `docker` (selected via `-Dspring.profiles.active=docker` in Docker)
- **Bootstrap config per service:** Points to `http://localhost:8090` (local), `http://192.168.1.5:8090` (dev), or `http://internet-banking-config-server:8090` (docker)

### 5.6 Netflix Eureka (Service Discovery)

- **Port:** 8081
- **Server:** `internet-banking-service-registry` with `@EnableEurekaServer`
- **Clients:** All business services register via `spring-cloud-starter-netflix-eureka-client`
- **Usage:** Feign clients resolve service names (e.g., `core-banking-service`) to actual host:port via Eureka

### 5.7 OpenFeign (Inter-Service HTTP Clients)

| Feign Client | Located In | Target Service | Endpoints Called |
|---|---|---|---|
| `BankingCoreRestClient` (user-service) | `user-service` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| `BankingCoreFeignClient` | `fund-transfer-service` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| `BankingCoreRestClient` (utility-payment-service) | `utility-payment-service` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (each service has its own `gradlew` wrapper — **no multi-module parent project**)
- **Java version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot version:** 3.2.4
- **Spring Cloud version:** 2023.0.0
- **Artifact type:** Fat JAR (`spring-boot-starter` plugin)
- **Git info:** `gradle-git-properties` plugin generates `git.properties` in each service

### 6.2 Docker

Each service has an identical Dockerfile pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build flow:** `./gradlew build` → produces JAR in `build/libs/` → `docker build` consumes JAR

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)

**Network:** Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs per container.

**Startup ordering:** `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting.

### 6.4 CI/CD

- **GitHub Actions:** Previously present but removed (commit message: "Remove GH Actions workflows")
- **Current state:** No CI/CD pipeline in the repository
- **No Kubernetes manifests** present despite K8s being listed in the tech stack

### 6.5 Test Infrastructure

- **Test database:** H2 in-memory (`jdbc:h2:mem:*`) for all services
- **Test config:** Eureka client disabled, Flyway disabled, `ddl-auto=none`
- **Test framework:** JUnit 5 (JUnit Platform via Gradle)
- **Existing tests:**
  - `core-banking-service`: `AccountServiceTest` (6 tests), `TransactionServiceTest` (9 tests), `UserServiceTest` (3 tests), `CoreBankingServiceApplicationTests` (context load)
  - All other services: Only `contextLoads()` placeholder tests
