# Internet Banking Microservices - Application Knowledge Base

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)

---

## 1. Architecture Overview

### 1.1 System Summary

A containerized microservices-based internet banking platform built with **Java 21** and **Spring Boot 3.2.4** (Spring Cloud 2023.0.0). The system manages user registration, account-to-account fund transfers, and third-party utility bill payments. It comprises **six microservices**, each independently deployable via Docker, and relies on centralized configuration, service discovery, an API gateway, and an external identity provider (Keycloak).

### 1.2 Service Inventory

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | **internet-banking-config-server** | 8090 | Centralized Spring Cloud Config server; fetches configuration from a remote Git repository. |
| 2 | **internet-banking-service-registry** | 8081 | Netflix Eureka discovery server for dynamic service location. |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point for all client traffic, performs OAuth2/JWT validation and request routing. |
| 4 | **core-banking-service** | 8092 | System of record: manages users, bank accounts, utility accounts, and processes the actual ledger-level fund transfers and utility payments. |
| 5 | **internet-banking-user-service** | 8083 | Handles user registration and management; integrates with Keycloak for identity and with core-banking-service for user validation. |
| 6 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer workflows; persists transfer records and delegates to core-banking-service for execution. |
| 7 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment workflows; persists payment records and delegates to core-banking-service for execution. |

> **Note:** A Notification Service is referenced in the README as consuming RabbitMQ messages but is **not yet implemented** (marked as "PENDING Development").

### 1.3 Communication Patterns

#### Synchronous (REST / OpenFeign)

All inter-service communication uses **Spring Cloud OpenFeign** declarative REST clients, resolved through Eureka service discovery. No hardcoded URLs are used between application services at runtime; the Feign client name (e.g., `core-banking-service`) is resolved via Eureka.

| Caller | Callee | Feign Client | Endpoints Called |
|--------|--------|--------------|-----------------|
| internet-banking-user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| internet-banking-fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| internet-banking-utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

#### Asynchronous (RabbitMQ)

RabbitMQ is declared in the technology stack and is intended for fund-transfer and utility-payment services to push notification messages. However, **no RabbitMQ producer or consumer code is currently implemented** in the codebase. This is tied to the pending Notification Service.

#### Gateway Routing

The API Gateway routes requests to downstream services using path-based prefixes (configured via Spring Cloud Config):

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

The gateway strips the prefix before forwarding (e.g., `/user/api/v1/bank-users` -> `/api/v1/bank-users`).

#### Identity Propagation

1. The API Gateway validates the JWT token via OAuth2 Resource Server (Keycloak JWK endpoint).
2. A `GlobalFilter` (`GatewayConfiguration`) extracts the authenticated principal name and injects it into the `X-Auth-Id` HTTP header on all proxied requests.
3. Downstream services read this header via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder` for audit logging.

### 1.4 Infrastructure Components

| Component | Image / Technology | Docker IP | Port | Purpose |
|-----------|--------------------|-----------|------|---------|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | 8080 | OAuth2/OIDC identity provider; manages realms, clients, users, and roles. Initialized with realm import on first boot. |
| **Keycloak PostgreSQL** | `postgres:15` | 172.25.0.10 | 5432 (closed) | Persistent store for Keycloak data. |
| **MySQL** | Custom build from `docker-compose/mysql/` | 172.25.0.9 | 3306 | Shared relational database for core-banking-service, user-service, fund-transfer-service, and utility-payment-service. |
| **Zipkin** | `openzipkin/zipkin:3` | 172.25.0.12 | 9411 | Distributed tracing server; collects spans from all services via Micrometer Brave bridge. |
| **RabbitMQ** | Referenced but not in docker-compose | - | - | Intended for async notification messaging (not yet deployed). |

### 1.5 Networking & Startup Orchestration

- All containers run on a custom Docker bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with fixed IPs for predictable DNS.
- Application services use **`wait-for-it.sh`** scripts in their entrypoints to ensure infrastructure dependencies (service registry, config server, MySQL) are healthy before the JVM starts.
- The `docker` Spring profile activates `bootstrap-docker.yml`, which points the Config Client to `http://internet-banking-config-server:8090` instead of `localhost`.

### 1.6 Configuration Management

- **Spring Cloud Config Server** fetches externalized configuration from a remote Git repo: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`).
- Each service has a `bootstrap.yml` (local dev, points to `localhost:8090`) and `bootstrap-docker.yml` (Docker profile, points to the config server container).
- Keycloak properties (`server-url`, `realm`, `clientId`, `client-secret`) are injected via the Config Server into the user-service.

### 1.7 Observability

| Concern | Implementation |
|---------|----------------|
| **Distributed Tracing** | Micrometer Tracing with Brave bridge + Zipkin Reporter. All services export spans to `http://zipkin:9411`. |
| **Service Health** | Spring Boot Actuator endpoints (`/actuator/**`) exposed on every service. |
| **Git Info** | `gradle-git-properties` plugin bakes git commit metadata into `/actuator/info`. |
| **Audit Logging** | `AuditAware` base class (JPA `@MappedSuperclass`) with `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy`, and `@Version` fields on user, fund-transfer, and utility-payment entities. `AuditorAwareConfig` populates auditor from `ApiRequestContextHolder`. |

### 1.8 Database Strategy

- **core-banking-service**: Uses **Flyway** for versioned schema migrations (`src/main/resources/db/migration/`). MySQL database `banking_core_service`.
- **internet-banking-user-service, fund-transfer-service, utility-payment-service**: Use **JPA auto-DDL** (Hibernate) against MySQL. Each service has its own logical schema/database.
- **Tests**: All services use **H2 in-memory database** for unit testing.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (System of Record)

Database: `banking_core_service` (MySQL). Schema managed by Flyway.

#### Entity: `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Internal user identifier |
| `first_name` | `VARCHAR(255)` | Nullable | User's first name |
| `last_name` | `VARCHAR(255)` | Nullable | User's last name |
| `email` | `VARCHAR(255)` | Nullable | User's email address |
| `identification_number` | `VARCHAR(255)` | Nullable | National ID / NIC number (primary lookup key) |

**JPA Entity**: `UserEntity` -> `@OneToMany` relationship to `BankAccountEntity` (mapped by `user`, `LAZY` fetch, `CASCADE ALL`).

#### Entity: `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Internal account identifier |
| `number` | `VARCHAR(255)` | Nullable | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` | Nullable | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | Nullable | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | `DECIMAL(19,2)` | Nullable | Ledger balance |
| `available_balance` | `DECIMAL(19,2)` | Nullable | Available balance for transactions |
| `user_id` | `BIGINT(20)` | FK -> `banking_core_user(id)` | Owning user |

**JPA Entity**: `BankAccountEntity` -> `@ManyToOne` to `UserEntity`.

#### Entity: `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Internal utility account identifier |
| `number` | `VARCHAR(255)` | Nullable | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | Nullable | Provider name (e.g., `VODAFONE`, `VERIZON`, `AIRTEL`) |

**JPA Entity**: `UtilityAccountEntity` - standalone, no foreign keys.

#### Entity: `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Internal transaction identifier |
| `amount` | `DECIMAL(19,2)` | Nullable | Transaction amount (negative for debits, positive for credits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | For fund transfers: destination account number. For utility payments: customer reference number |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID linking debit/credit legs of a transfer |
| `account_id` | `BIGINT(20)` | FK -> `banking_core_account(id)` | Associated bank account |

**JPA Entity**: `TransactionEntity` -> `@OneToOne(CASCADE ALL)` to `BankAccountEntity`.

#### Entity Relationship Diagram (Core Banking)

```
banking_core_user (1) ----< (N) banking_core_account (1) ----< (N) banking_core_transaction
                                                                        |
                                                                  transaction_type: FUND_TRANSFER | UTILITY_PAYMENT

banking_core_utility_account (standalone - referenced by provider ID in utility payments)
```

### 2.2 Internet Banking User Service

Database: MySQL (JPA auto-DDL). Table: `user`.

#### Entity: `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `auth_id` | `VARCHAR` | Keycloak user UUID (links internal user to Keycloak identity) |
| `identification` | `VARCHAR` | National ID / NIC number (matches `identification_number` in core banking) |
| `status` | `VARCHAR` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | `TIMESTAMP` | Audit: creation timestamp |
| `created_by` | `VARCHAR` | Audit: creator identity |
| `modified_date` | `TIMESTAMP` | Audit: last modification timestamp |
| `modified_by` | `VARCHAR` | Audit: last modifier identity |
| `version` | `BIGINT` | Optimistic locking version |

**JPA Entity**: `UserEntity extends AuditAware`. The `AuditAware` superclass provides `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, and `version` columns via `@MappedSuperclass`.

### 2.3 Internet Banking Fund Transfer Service

Database: MySQL (JPA auto-DDL). Table: `fund_transfer`.

#### Entity: `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL(19,2)` | Transfer amount |
| `status` | `VARCHAR` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | `VARCHAR` | UUID returned from core-banking-service after successful execution |
| `created_date` | `TIMESTAMP` | Audit field |
| `created_by` | `VARCHAR` | Audit field |
| `modified_date` | `TIMESTAMP` | Audit field |
| `modified_by` | `VARCHAR` | Audit field |
| `version` | `BIGINT` | Optimistic locking version |

**JPA Entity**: `FundTransferEntity extends AuditAware`.

### 2.4 Internet Banking Utility Payment Service

Database: MySQL (JPA auto-DDL). Table: `utility_payment`.

#### Entity: `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `provider_id` | `BIGINT` | Utility provider identifier (references `banking_core_utility_account.id`) |
| `amount` | `DECIMAL(19,2)` | Payment amount |
| `reference_number` | `VARCHAR` | Customer reference / bill number |
| `account` | `VARCHAR` | Source bank account number |
| `transaction_id` | `VARCHAR` | UUID returned from core-banking-service after successful execution |
| `status` | `VARCHAR` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | `TIMESTAMP` | Audit field |
| `created_by` | `VARCHAR` | Audit field |
| `modified_date` | `TIMESTAMP` | Audit field |
| `modified_by` | `VARCHAR` | Audit field |
| `version` | `BIGINT` | Optimistic locking version |

**JPA Entity**: `UtilityPaymentEntity extends AuditAware`.

### 2.5 Seed Data (Flyway)

The core-banking-service includes test seed data in migration `V1.0.20210427174721__temp_data.sql`:

- **4 users**: Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon
- **14 savings accounts** distributed across the 4 users with balances ranging from 12,000 to 889,000.33
- **6 utility providers**: VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO

---

## 3. API Surface Map

### 3.1 API Gateway (Port 8082) - External Entry Points

All external requests are routed through the API Gateway. The gateway performs OAuth2 JWT validation (via Keycloak) and forwards requests to downstream services.

#### Public Endpoints (No Authentication Required)

| Method | Gateway URL | Target Service |
|--------|------------|----------------|
| `POST` | `/user/api/v1/bank-users/register` | internet-banking-user-service |
| `GET` | `/actuator/**` | API Gateway |
| `GET` | `/user/actuator/**` | internet-banking-user-service |
| `GET` | `/fund-transfer/actuator/**` | internet-banking-fund-transfer-service |
| `GET` | `/banking-core/actuator/**` | core-banking-service |
| `GET` | `/utility-payment/actuator/**` | internet-banking-utility-payment-service |

#### Protected Endpoints (JWT Required)

All other endpoints require a valid Bearer token obtained from Keycloak.

### 3.2 Core Banking Service (Port 8092)

Base path: `/api/v1`

#### Account Controller (`/api/v1/account`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/bank-account/{account_number}` | Get bank account by account number | Path: `account_number` (String) | `BankAccount` { id, number, type, status, availableBalance, actualBalance, user } |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount` { id, number, providerName } |

#### User Controller (`/api/v1/user`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User` { id, firstName, lastName, email, identificationNumber, bankAccounts[] } |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/fund-transfer` | Process fund transfer at ledger level | `FundTransferRequest` { fromAccount, toAccount, amount } | `FundTransferResponse` { message, transactionId } |
| `POST` | `/util-payment` | Process utility payment at ledger level | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } |

### 3.3 Internet Banking User Service (Port 8083)

Base path: `/api/v1/bank-users`

| Method | Path | Description | Request Body / Params | Response |
|--------|------|-------------|----------------------|----------|
| `POST` | `/register` | Register a new banking user | `User` { email, identification, password } | `User` { id, email, identification, authId, status, version } |
| `PATCH` | `/update/{id}` | Update user status | Path: `id` (Long); Body: `UserUpdateRequest` { status } | `User` { id, email, identification, authId, status, version } |
| `GET` | `/` | List all users (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<User>` |
| `GET` | `/{id}` | Get user by internal ID | Path: `id` (Long) | `User` { id, email, identification, authId, status, version } |

### 3.4 Internet Banking Fund Transfer Service (Port 8084)

Base path: `/api/v1/transfer`

| Method | Path | Description | Request Body / Params | Response |
|--------|------|-------------|----------------------|----------|
| `POST` | `/` | Initiate a fund transfer | `FundTransferRequest` { fromAccount, toAccount, amount, authID } | `FundTransferResponse` { message, transactionId } |
| `GET` | `/` | List all fund transfers (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<FundTransfer>` { id, transactionReference, status, fromAccount, toAccount, amount } |

### 3.5 Internet Banking Utility Payment Service (Port 8085)

Base path: `/api/v1/utility-payment`

| Method | Path | Description | Request Body / Params | Response |
|--------|------|-------------|----------------------|----------|
| `POST` | `/` | Process a utility payment | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } |
| `GET` | `/` | List all utility payments (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<UtilityPayment>` { providerId, amount, referenceNumber, account, status } |

### 3.6 Service Registry (Port 8081)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Eureka dashboard (web UI) |
| `GET` | `/eureka/apps` | List registered service instances |

### 3.7 Config Server (Port 8090)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/{application}/{profile}` | Fetch configuration for a service and profile |
| `GET` | `/{application}-{profile}.yml` | Fetch configuration as YAML |

### 3.8 Error Response Format

All services return errors in a consistent format via `@ControllerAdvice`:

```json
{
  "code": "ERROR_CODE_STRING",
  "message": "Human-readable error message"
}
```

HTTP Status: `400 Bad Request` for all handled exceptions.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration & Management

**Service**: `internet-banking-user-service` -> `UserService.createUser()`

**Registration Flow** (multi-step, cross-service):

1. **Keycloak duplicate check**: Query Keycloak by email. If the email already exists, throw `UserAlreadyRegisteredException`.
2. **Core banking validation**: Call `core-banking-service` via Feign (`GET /api/v1/user/{identification}`) to verify the user exists in the banking core by their national ID/NIC.
3. **Email cross-validation**: If core banking user is found, verify that the provided email matches the core banking record. If mismatch, throw `InvalidEmailException`.
4. **Keycloak user creation**: Build a `UserRepresentation` with email, first name, last name, and password credentials. User is created as `enabled=false`, `emailVerified=false`.
5. **Retrieve Keycloak auth ID**: After successful creation (HTTP 201), re-query Keycloak by email to get the generated auth UUID.
6. **Persist locally**: Save the user entity with `authId`, `identification`, and `status=PENDING` to the local MySQL database.
7. If the user is not found in core banking, throw `InvalidBankingUserException`.

**User Approval Flow** (`UserService.updateUser()`):

1. When status is changed to `APPROVED`, the corresponding Keycloak user is updated: `enabled=true`, `emailVerified=true`.
2. The local user entity status is updated accordingly.

**User Statuses**: `PENDING` -> `APPROVED` -> `DISABLED` / `BLACKLIST`

### 4.2 Fund Transfer Rules

**Services involved**: `internet-banking-fund-transfer-service` (orchestrator) -> `core-banking-service` (executor)

#### Orchestration Layer (`FundTransferService.fundTransfer()`)

1. Copy request fields into a new `FundTransferEntity` with status `PENDING`.
2. Persist the entity to the local `fund_transfer` table.
3. Delegate to core-banking-service via Feign (`POST /api/v1/transaction/fund-transfer`).
4. On success, update the entity with the returned `transactionId` and set status to `SUCCESS`.
5. Return the response with the message "Fund Transfer Successfully Completed".

#### Execution Layer (`TransactionService.fundTransfer()` in core-banking-service)

1. **Lookup both accounts**: Retrieve source (`fromAccount`) and destination (`toAccount`) from the database by account number. Throw `EntityNotFoundException` if either is not found.
2. **Balance validation** (`validateBalance()`):
   - Check that `actualBalance >= 0`.
   - Check that `actualBalance >= transfer amount`.
   - If either fails, throw `InsufficientFundsException`.
3. **Debit source account**: Subtract the amount from both `actualBalance` and `availableBalance`. Save.
4. **Record debit transaction**: Create a `TransactionEntity` with `FUND_TRANSFER` type, negative amount, reference = destination account number, and a generated UUID transaction ID.
5. **Credit destination account**: Add the amount to both `actualBalance` and `availableBalance`. Save.
6. **Record credit transaction**: Create a `TransactionEntity` with `FUND_TRANSFER` type, positive amount, same transaction ID (links both legs).
7. Return the transaction ID.

**Important**: The entire operation runs within a `@Transactional` boundary in the core-banking-service, ensuring atomicity.

#### Fund Transfer Request Shape

```json
{
  "fromAccount": "100015003000",
  "toAccount": "100015003001",
  "amount": 5000.00
}
```

### 4.3 Utility Payment Processing

**Services involved**: `internet-banking-utility-payment-service` (orchestrator) -> `core-banking-service` (executor)

#### Orchestration Layer (`UtilityPaymentService.utilPayment()`)

1. Copy request fields into a new `UtilityPaymentEntity` with status `PROCESSING`.
2. Persist the entity to the local `utility_payment` table.
3. Delegate to core-banking-service via Feign (`POST /api/v1/transaction/util-payment`).
4. On success, update the entity with the returned `transactionId` and set status to `SUCCESS`.
5. Return the response with the message "Utility Payment Successfully Processed".

#### Execution Layer (`TransactionService.utilPayment()` in core-banking-service)

1. Generate a UUID transaction ID.
2. **Lookup source account**: Retrieve the payer's bank account by account number.
3. **Balance validation**: Same rules as fund transfer (actual balance must cover the payment amount).
4. **Lookup utility provider**: Retrieve the `UtilityAccountEntity` by provider ID to verify it exists.
5. **Debit source account**: Subtract the amount from `actualBalance` and `availableBalance`.
6. **Record transaction**: Create a `TransactionEntity` with `UTILITY_PAYMENT` type, negative amount, and the customer reference number.
7. Return the transaction ID.

**Note**: No actual third-party API call is made. A comment in the code indicates this is where a payment provider integration would go.

#### Utility Payment Request Shape

```json
{
  "providerId": 1,
  "amount": 1500.00,
  "referenceNumber": "BILL-2024-001",
  "account": "100015003000"
}
```

### 4.4 Authentication & Authorization

**Identity Provider**: Keycloak 23.0.7

- **Realm**: Imported from `docker-compose/keycloak/` on first boot.
- **Client authentication**: `client_credentials` grant used by the user-service admin client.
- **User authentication**: End-user tokens are obtained from Keycloak's token endpoint (outside this application).
- **Gateway enforcement**: All requests except user registration and actuator endpoints require a valid JWT.
- **JWK validation**: The API Gateway validates tokens against Keycloak's JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
- **CSRF**: Disabled at the gateway level.
- **Test credentials**: `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 4.5 Exception Handling Strategy

All services use a consistent `@ControllerAdvice` pattern (`GlobalExceptionHandler`):

| Exception Class | Service(s) | Error Code | Trigger |
|----------------|------------|------------|---------|
| `EntityNotFoundException` | All | (varies) | JPA `findById()` / `findByX()` returns empty |
| `InsufficientFundsException` | core-banking | `INSUFFICIENT_FUNDS` | Account balance less than requested amount |
| `UserAlreadyRegisteredException` | user-service | `ERROR_EMAIL_REGISTERED` | Email already exists in Keycloak |
| `InvalidEmailException` | user-service | `ERROR_INVALID_EMAIL` | Email mismatch between request and core banking |
| `InvalidBankingUserException` | user-service | `ERROR_USER_NOT_FOUND_UNDER_NIC` | Identification not found in core banking |
| `SimpleBankingGlobalException` | All | (varies) | Base exception class for domain errors |

### 4.6 Audit Trail

The `AuditAware` `@MappedSuperclass` is used by the user-service, fund-transfer-service, and utility-payment-service entities. It provides:

- `createdDate` / `modifiedDate`: Auto-populated by Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`).
- `createdBy` / `modifiedBy`: Populated from `AuditorAwareConfig`, which reads the authenticated user ID from `ApiRequestContextHolder` (sourced from the `X-Auth-Id` header injected by the API Gateway).
- `version`: Optimistic locking via `@Version`.

> The core-banking-service entities do **not** extend `AuditAware` and lack audit fields.

### 4.7 Cross-Cutting Concerns

| Concern | Implementation Details |
|---------|----------------------|
| **Service Discovery** | Netflix Eureka. All services register on startup. Feign clients resolve by service name. |
| **Centralized Config** | Spring Cloud Config Server backed by Git. Services use `bootstrap.yml` to fetch configs before application context loads. |
| **Distributed Tracing** | Micrometer Tracing (Brave) exports spans to Zipkin. Feign calls are automatically traced via `feign-micrometer`. |
| **API Documentation** | SpringDoc OpenAPI (`springdoc-openapi-starter-webflux-ui:2.1.0`) with Swagger annotations on all controllers. |
| **Database Migrations** | Flyway (core-banking-service only). Other services rely on Hibernate auto-DDL. |
| **Health Checks** | Spring Boot Actuator on all services. Docker startup uses `wait-for-it.sh` for dependency ordering. |
| **Optimistic Locking** | `@Version` field on all `AuditAware` entities prevents lost updates. |
