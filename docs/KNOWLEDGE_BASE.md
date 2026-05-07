# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built using a microservices architecture with **Spring Cloud 2023.0.0**. The system comprises 6 independently deployable services that communicate via synchronous REST (OpenFeign) and are orchestrated through service discovery (Eureka), centralized configuration (Spring Cloud Config), and an API gateway with OAuth2 security (Keycloak).

### Services

| Service | Port | Description |
|---|---|---|
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — serves externalized configuration from a Git repository |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server — service discovery and registration |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point with OAuth2/JWT security, route proxying |
| **internet-banking-user-service** | 8083 | Manages internet banking user registration, approval, and profile operations via Keycloak |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between bank accounts via the core banking service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via the core banking service |
| **core-banking-service** | 8092 | Core banking engine — manages accounts, users, transactions, and balance operations |

> **Note:** A Notification Service is referenced in the architecture diagrams but is **not yet implemented** (marked as "PENDING Development" in the README).

### Communication Patterns

```
                         ┌──────────────────────┐
                         │   API Gateway (:8082) │
                         │  OAuth2 + JWT Auth    │
                         └────────┬─────────────┘
                                  │  Routes via Eureka
                    ┌─────────────┼─────────────┐
                    │             │              │
              ┌─────▼─────┐ ┌────▼─────┐ ┌──────▼──────┐
              │   User     │ │  Fund    │ │  Utility    │
              │  Service   │ │ Transfer │ │  Payment    │
              │  (:8083)   │ │ (:8084)  │ │  (:8085)    │
              └─────┬──────┘ └────┬─────┘ └──────┬──────┘
                    │             │               │
                    │    OpenFeign (sync REST)     │
                    │             │               │
              ┌─────▼─────────────▼───────────────▼─────┐
              │       Core Banking Service (:8092)       │
              │   Accounts · Users · Transactions        │
              └─────────────────────────────────────────┘
```

- **Synchronous REST via OpenFeign**: User Service, Fund Transfer Service, and Utility Payment Service all call Core Banking Service through Feign clients registered via Eureka.
- **Service Discovery**: All services register with Eureka; inter-service calls use logical service names resolved by Eureka.
- **Centralized Configuration**: All services (except Config Server itself) pull configuration from the Config Server, which reads from a remote Git repository.
- **API Gateway Routing**: The gateway proxies requests to downstream services using path-based routing (e.g., `/user/**` → User Service, `/fund-transfer/**` → Fund Transfer Service).
- **Auth Propagation**: The API Gateway extracts the authenticated principal from the JWT and forwards it as an `X-Auth-Id` header to downstream services via a `GlobalFilter`.

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Identity Provider | Keycloak 23.0.7 (PostgreSQL-backed) | OAuth2/OIDC authentication, user management |
| Database | MySQL (custom Docker image with seed data) | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Tracing (Brave) | Request tracing across services |
| Service Discovery | Netflix Eureka | Service registration and lookup |
| Config Management | Spring Cloud Config Server (Git-backed) | Externalized, centralized configuration |
| Message Broker | RabbitMQ (referenced but not yet integrated) | Planned for notification service |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / passport number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | Current actual balance |
| `available_balance` | DECIMAL(19,2) | | Available balance for withdrawal |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE, AIRTEL) |

### Internet Banking User Service

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | VARCHAR(255) | | Keycloak user ID (UUID) |
| `identification` | VARCHAR(255) | | Maps to core banking `identification_number` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit | Auto-populated creation timestamp |
| `created_by` | VARCHAR(255) | Audit | User who created the record |
| `modified_date` | TIMESTAMP | Audit | Last modification timestamp |
| `modified_by` | VARCHAR(255) | Audit | User who last modified |
| `version` | BIGINT | | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `transaction_reference` | VARCHAR(255) | | UUID from core banking response |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit | Auto-populated |
| `created_by` | VARCHAR(255) | Audit | From `X-Auth-Id` header |
| `modified_date` | TIMESTAMP | Audit | Auto-populated |
| `modified_by` | VARCHAR(255) | Audit | From `X-Auth-Id` header |
| `version` | BIGINT | | Optimistic locking version |

### Internet Banking Utility Payment Service

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Utility bill reference |
| `account` | VARCHAR(255) | | Payer's bank account number |
| `transaction_id` | VARCHAR(255) | | UUID from core banking response |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit | Auto-populated |
| `created_by` | VARCHAR(255) | Audit | From `X-Auth-Id` header |
| `modified_date` | TIMESTAMP | Audit | Auto-populated |
| `modified_by` | VARCHAR(255) | Audit | From `X-Auth-Id` header |
| `version` | BIGINT | | Optimistic locking version |

### Entity Relationships

```
banking_core_user 1──N banking_core_account 1──1 banking_core_transaction
                                                         │
                                            (referenced by fund_transfer
                                             and utility_payment services
                                             via account number strings)
```

- Core Banking entities use **JPA foreign key relationships** (`@ManyToOne`, `@OneToOne`).
- Cross-service references use **account number strings** (no foreign keys across service boundaries).

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Look up bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Look up utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Execute a fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Execute a utility bill payment |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Look up core banking user by ID number |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated list of core banking users |

**Request/Response Shapes:**

```
FundTransferRequest:  { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse: { message: String, transactionId: String }

UtilityPaymentRequest:  { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse: { message: String, transactionId: String }

BankAccount: { id: Long, number: String, type: String, status: String, availableBalance: BigDecimal, actualBalance: BigDecimal }
UtilityAccount: { id: Long, number: String, providerName: String }
User: { id: Long, firstName: String, lastName: String, email: String, identificationNumber: String, bankAccounts: List<BankAccount> }
```

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register a new internet banking user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user status (e.g., approve user — enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated list of registered users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by ID |

**Request/Response Shapes:**

```
User (request):       { email: String, identification: String, password: String }
User (response):      { id: Long, email: String, identification: String, authId: String, status: String }
UserUpdateRequest:    { status: String }  // PENDING, APPROVED, DISABLED, BLACKLIST
```

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | Paginated list of fund transfers |

**Request/Response Shapes:**

```
FundTransferRequest:  { fromAccount: String, toAccount: String, amount: BigDecimal, authID: String }
FundTransferResponse: { message: String, transactionId: String }
FundTransfer:         { id: Long, transactionReference: String, status: String, fromAccount: String, toAccount: String, amount: BigDecimal, version: Long }
```

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | Paginated list of utility payments |

**Request/Response Shapes:**

```
UtilityPaymentRequest:  { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse: { message: String, transactionId: String }
UtilityPayment:         { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String, status: String, version: Long }
```

### API Gateway (`:8082`) — Route Prefixes

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` (all services)

All other endpoints require a valid JWT Bearer token issued by Keycloak.

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (public, no auth required).
2. User Service checks if the email is already registered in Keycloak. If so → `UserAlreadyRegisteredException`.
3. User Service calls Core Banking Service (`GET /api/v1/user/{identification}`) to verify the user exists in the core banking system.
4. If the core user is found, validates that the email matches. If mismatch → `InvalidEmailException`.
5. Creates the user in Keycloak with `enabled=false`, `emailVerified=false`.
6. Persists the user locally with `status=PENDING`.
7. An admin later calls `PATCH /user/api/v1/bank-users/update/{id}` with `status=APPROVED` to enable the Keycloak account.

### Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer` (authenticated).
2. Fund Transfer Service creates a local `FundTransferEntity` with `status=PENDING`.
3. Calls Core Banking Service (`POST /api/v1/transaction/fund-transfer`) via Feign.
4. Core Banking Service:
   - Validates source account has sufficient balance.
   - Debits source account (both `actualBalance` and `availableBalance`).
   - Credits destination account.
   - Creates two `TransactionEntity` records (debit + credit).
   - Returns `transactionId`.
5. Fund Transfer Service updates local entity with `transactionReference` and `status=SUCCESS`.

### Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment` (authenticated).
2. Utility Payment Service creates a local `UtilityPaymentEntity` with `status=PROCESSING`.
3. Calls Core Banking Service (`POST /api/v1/transaction/util-payment`) via Feign.
4. Core Banking Service:
   - Validates source account has sufficient balance.
   - Debits the source account.
   - Creates a `TransactionEntity` record.
   - Returns `transactionId`.
5. Utility Payment Service updates local entity with `transactionId` and `status=SUCCESS`.

### Balance Validation Rules

- Both `actualBalance` must be ≥ 0 **and** ≥ the transfer/payment amount.
- Violation throws `InsufficientFundsException`.
- No minimum balance enforcement or daily transfer limits are implemented.

### Audit Trail

- Fund Transfer, Utility Payment, and User Service entities extend `AuditAware`, providing `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, and `version` (optimistic locking).
- The `createdBy`/`modifiedBy` is populated from the `X-Auth-Id` header via `AppAuthUserFilter` → `ApiRequestContextHolder` → `AuditorAwareConfig`.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Database:** PostgreSQL 15
- **Realm:** `javatodev-internet-banking` (auto-imported via `realm-export.json`)
- **Client:** `internet-banking-api-client` (client credentials grant)
- **Integration:**
  - API Gateway validates JWT tokens using Keycloak's JWK Set URI.
  - User Service uses the Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`) to create/update/read users.
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` in the User Service's config.

### Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies. Trace context is automatically propagated across Feign calls.

### MySQL (Database)

- **Custom Docker Image:** Built from `docker-compose/mysql/Dockerfile` with `privileges.sql` for initial DB/user setup.
- **Databases Created:**
  - `banking_core_service` — Core Banking Service
  - `banking_core_fund_transfer_service` — Fund Transfer Service
  - `banking_core_user_service` — User Service
  - `banking_core_utility_payment_service` — Utility Payment Service
- **Credentials:** Root password and app user (`javatodev_development` / `oPItyPticIAt`) configured in Docker Compose.
- **Schema Management:** Core Banking Service uses **Flyway** (`flyway-core:10.12.0` + `flyway-mysql:10.12.0`) with versioned SQL migrations. Other services rely on JPA/Hibernate DDL auto.

### RabbitMQ (Message Broker)

- **Status:** Referenced in the README and architecture diagrams but **not yet integrated** in the codebase.
- **Planned Use:** Notification service would consume messages from Fund Transfer and Utility Payment services.

### Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration`
- All services use `spring-cloud-starter-config` and `spring-cloud-starter-bootstrap` to fetch configuration at startup.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Gradle** with Spring Boot plugin (`3.2.4`) and Spring Dependency Management plugin (`1.1.4`).
- Each service is an independent Gradle project (no multi-project build — each has its own `settings.gradle` and `build.gradle`).
- **Java 21** (`sourceCompatibility = '21'`).
- Git properties plugin (`com.gorylenko.gradle-git-properties:2.4.2`) generates build metadata (used by most services except Service Registry).

### Docker

- Each service has a `Dockerfile` using `eclipse-temurin:21.0.2_13-jre-alpine` as the base image.
- JARs are built locally and copied into Docker images via `ADD build/libs/*.jar app.jar`.
- All service containers include `wait-for-it.sh` for startup dependency ordering.
- Services that depend on Config Server, Service Registry, or MySQL wait for those to be available before starting.

### Docker Compose

Two compose files are provided:

| File | Contents |
|---|---|
| `docker-compose.yml` | Full stack — all 6 microservices + MySQL + Keycloak (with PostgreSQL) + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only — MySQL + Keycloak + Zipkin + Config Server + Service Registry |

- Uses a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IPs.
- Named volumes for MySQL and PostgreSQL data persistence.
- Services use `-Dspring.profiles.active=docker` to activate Docker-specific configuration.

### CI/CD

- No CI/CD pipeline is currently defined. The `.github/` directory contains only a `FUNDING.yml` file.
- No GitHub Actions workflows, Jenkinsfiles, or similar automation exists.

### Testing

- **Test Framework:** JUnit 5 (via `spring-boot-starter-test`).
- **Test Database:** H2 in-memory (`com.h2database:h2:2.2.224`) used in test profiles with Flyway disabled.
- **Existing Tests:**
  - Core Banking Service: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` — unit tests with Mockito.
  - Other services: Only context-load tests (`*ApplicationTests.java`).

### API Documentation

- Core Banking Service, User Service, Fund Transfer Service, and Utility Payment Service include `springdoc-openapi-starter-webflux-ui:2.1.0` for Swagger/OpenAPI documentation.
- Controllers use `@Tag` and `@Operation` annotations for API metadata.
- A Postman collection is provided in `postman_collection/` for manual API testing.
