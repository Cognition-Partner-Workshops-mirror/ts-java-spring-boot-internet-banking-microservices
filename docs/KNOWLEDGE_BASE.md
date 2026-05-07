# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built using a microservices architecture with Spring Cloud 2023.0.0. The system consists of 6 microservices that collectively provide banking operations including user management, fund transfers, and utility payments.

### Services

| Service | Port | Responsibility |
|---------|------|---------------|
| **core-banking-service** | 8092 | Core banking ledger — manages accounts, users, and processes transactions (fund transfers, utility payments) |
| **internet-banking-user-service** | 8083 | User registration and management — integrates with Keycloak for identity and with core-banking for user validation |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer workflows — persists transfer records and delegates to core-banking for execution |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment workflows — persists payment records and delegates to core-banking for execution |
| **internet-banking-api-gateway** | 8082 | Edge gateway — routes external traffic, enforces OAuth2/JWT authentication, injects `X-Auth-Id` header |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server — service registration and discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server — serves externalized configuration from a Git repository |

### Communication Patterns

```
                    ┌─────────────────────┐
                    │   External Client    │
                    └──────────┬──────────┘
                               │ HTTPS (JWT Bearer)
                    ┌──────────▼──────────┐
                    │    API Gateway       │
                    │   (Spring Cloud)     │
                    └──┬──────┬──────┬────┘
                       │      │      │
          ┌────────────┘      │      └────────────┐
          ▼                   ▼                    ▼
┌─────────────────┐ ┌─────────────────┐ ┌──────────────────────┐
│  User Service   │ │ Fund Transfer   │ │ Utility Payment      │
│                 │ │   Service       │ │   Service            │
└────────┬────────┘ └────────┬────────┘ └──────────┬───────────┘
         │                   │                     │
         │   OpenFeign       │   OpenFeign         │  OpenFeign
         │   (sync REST)     │   (sync REST)       │  (sync REST)
         ▼                   ▼                     ▼
┌────────────────────────────────────────────────────────────────┐
│                    Core Banking Service                         │
│              (Account, Transaction, User APIs)                  │
└────────────────────────────────────────────────────────────────┘
```

**Pattern Details:**

1. **Synchronous REST (OpenFeign)** — All inter-service communication uses Spring Cloud OpenFeign with Eureka service discovery. Services reference each other by Eureka application name (e.g., `core-banking-service`).

2. **API Gateway Routing** — Spring Cloud Gateway (reactive WebFlux) routes all external requests to downstream services. Routes are defined via Spring Cloud Config.

3. **Service Discovery** — Netflix Eureka provides registration and discovery. All services register at startup and resolve peers via Eureka.

4. **Centralized Configuration** — Spring Cloud Config Server fetches configuration from [this Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git) and serves it to services at bootstrap.

5. **Authentication Propagation** — The API Gateway extracts the OAuth2 principal and forwards it as an `X-Auth-Id` HTTP header. Downstream services read this header via a servlet filter (`AppAuthUserFilter`) to establish the request context.

6. **Asynchronous Messaging (Planned, Not Implemented)** — The README references RabbitMQ for notifications, but no messaging code or dependencies exist in the current codebase.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Persistent storage for all business services (4 schemas) |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user identity management |
| Keycloak Database | PostgreSQL 15 | Backing store for Keycloak |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Tracing Bridge | Micrometer Tracing (Brave) | In-app instrumentation that exports spans to Zipkin |
| Service Registry | Netflix Eureka | Service discovery |
| Config Store | Git repository | Externalized YAML configuration |
| Container Runtime | Docker / Docker Compose | Local development and deployment |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user` (UserEntity)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| firstName | String | User's first name |
| lastName | String | User's last name |
| email | String | User's email address |
| identificationNumber | String | National identification number |
| accounts | List\<BankAccountEntity\> | One-to-many relationship to bank accounts |

#### `banking_core_account` (BankAccountEntity)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| number | String | Account number (unique identifier for lookups) |
| type | AccountType enum | SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT |
| status | AccountStatus enum | PENDING, ACTIVE, DORMANT, BLOCKED |
| availableBalance | BigDecimal | Balance available for transactions |
| actualBalance | BigDecimal | Actual ledger balance |
| user_id | FK to UserEntity | Many-to-one relationship to user |

#### `banking_core_transaction` (TransactionEntity)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| amount | BigDecimal | Transaction amount (negative for debits) |
| transactionType | TransactionType enum | FUND_TRANSFER, UTILITY_PAYMENT |
| referenceNumber | String | Reference (target account number or utility ref) |
| transactionId | String | UUID grouping related debit/credit entries |
| account_id | FK to BankAccountEntity | The account involved (OneToOne with CASCADE ALL) |

#### `banking_core_utility_account` (UtilityAccountEntity)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| number | String | Utility provider account number |
| providerName | String | Name of the utility provider |

### Internet Banking User Service

#### `user` (UserEntity extends AuditAware)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| authId | String | Keycloak user ID reference |
| identification | String | National identification number |
| status | Status enum | PENDING, APPROVED, DISABLED, BLACKLIST |
| createdBy | String (inherited) | Audit field |
| createdDate | LocalDateTime (inherited) | Audit field |
| lastModifiedBy | String (inherited) | Audit field |
| lastModifiedDate | LocalDateTime (inherited) | Audit field |

### Internet Banking Fund Transfer Service

#### `fund_transfer` (FundTransferEntity extends AuditAware)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| transactionReference | String | Transaction ID returned from core-banking |
| fromAccount | String | Source account number |
| toAccount | String | Destination account number |
| amount | BigDecimal | Transfer amount |
| status | TransactionStatus enum | PENDING, PROCESSING, SUCCESS, FAILED |
| createdBy / createdDate / lastModifiedBy / lastModifiedDate | Audit fields | Inherited from AuditAware |

### Internet Banking Utility Payment Service

#### `utility_payment` (UtilityPaymentEntity extends AuditAware)

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK, auto-increment) | Primary key |
| providerId | Long | Utility provider ID |
| amount | BigDecimal | Payment amount |
| referenceNumber | String | Customer reference number |
| account | String | Source bank account number |
| transactionId | String | Transaction ID returned from core-banking |
| status | TransactionStatus enum | PENDING, PROCESSING, SUCCESS, FAILED |
| createdBy / createdDate / lastModifiedBy / lastModifiedDate | Audit fields | Inherited from AuditAware |

### Database Schema Layout

```
MySQL Instance (mysql_javatodev_app:3306)
├── banking_core_service          → core-banking-service tables
├── banking_core_fund_transfer_service  → fund-transfer-service tables
├── banking_core_user_service     → user-service tables
└── banking_core_utility_payment_service → utility-payment-service tables

PostgreSQL Instance (keycloak_postgre_db:5432)
└── keycloak                      → Keycloak realm/user data
```

---

## 3. API Surface Map

### Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | BankAccount DTO |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | UtilityAccount DTO |
| GET | `/api/v1/user/{identification}` | Get user by identification number | — | User DTO |
| GET | `/api/v1/user` | List users (paginated) | — | List\<User\> |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest{fromAccount, toAccount, amount}` | `FundTransferResponse{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest{providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse{message, transactionId}` |

### Internet Banking User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `User{email, identification, password}` | User DTO |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest{status}` | User DTO |
| GET | `/api/v1/bank-users` | List users (paginated) | — | List\<User\> |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | — | User DTO |

### Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest{fromAccount, toAccount, amount, authID}` | `FundTransferResponse{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | — | List\<FundTransfer\> |

### Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest{providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | — | List\<UtilityPayment\> |

### API Gateway Route Prefixes

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### Actuator Endpoints (all services)

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health check |
| `/actuator/info` | Application info (git properties) |
| `/actuator/metrics` | Micrometer metrics |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation** — Before any transfer, the source account's `actualBalance` must be >= transfer amount and > 0. Throws `InsufficientFundsException` otherwise.
2. **Double-Entry Recording** — Each fund transfer creates two transaction records: a debit (negative amount) on the source account and a credit (positive amount) on the destination account, linked by a shared UUID `transactionId`.
3. **Orchestration Pattern** — The fund-transfer-service saves a PENDING record, calls core-banking synchronously, then updates the record to SUCCESS with the transaction reference.
4. **No Rollback on Failure** — If the core-banking call fails after the PENDING record is created, there is no compensation or retry logic.

### Utility Payment Processing

1. **Balance Validation** — Same as fund transfers: source account balance must cover the payment amount.
2. **Provider Lookup** — The utility provider is resolved by `providerId` from the `banking_core_utility_account` table.
3. **Single-Entry Debit** — Only the payer's account is debited; no corresponding credit to a utility provider account.
4. **Third-Party Integration Placeholder** — Code comments indicate a future call to external payment providers, but this is not implemented.

### User Management

1. **Registration Flow:**
   - Check if email already exists in Keycloak (reject duplicates)
   - Validate the user's identification number against core-banking-service
   - Verify the email matches the core-banking record
   - Create the user in Keycloak (disabled, email unverified)
   - Persist a local user record with status PENDING

2. **Approval Flow:**
   - Admin updates user status to APPROVED
   - Keycloak user is enabled and email marked as verified
   - User can now authenticate and use the system

3. **Status Lifecycle:** PENDING -> APPROVED -> DISABLED/BLACKLIST

### Authentication & Authorization

1. **OAuth2 Resource Server** — API Gateway validates JWT tokens issued by Keycloak.
2. **Public Endpoints** — Only `/user/api/v1/bank-users/register` and actuator endpoints bypass authentication.
3. **User Context Propagation** — The principal name from the JWT is forwarded as `X-Auth-Id` header to downstream services. Services use `AppAuthUserFilter` to store this in a thread-local `ApiRequestContext`.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User service connects via `keycloak-admin-client:24.0.4`
- **Auth Flow:** Client credentials grant (`client_credentials`)
- **Configuration Properties:**
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm
  - `app.config.keycloak.clientId` — OAuth2 client ID
  - `app.config.keycloak.client-secret` — Client secret
- **Operations:** Create user, update user, search by email, read user by ID
- **Realm Data:** Pre-imported via `/opt/keycloak/data/import` volume mount

### Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Port:** 9411
- **Integration:** Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- **Coverage:** All services include tracing dependencies; Feign calls are instrumented via `feign-micrometer`

### MySQL (Data Store)

- **Version:** 8.4.0
- **Port:** 3306
- **User:** `javatodev_development` / `oPItyPticIAt`
- **Schemas:** 4 databases (one per business service)
- **ORM:** Spring Data JPA with Hibernate
- **Migrations:** Flyway (core-banking-service only — `flyway-core:10.12.0`, `flyway-mysql:10.12.0`)
- **DDL Strategy:** Other services likely use `spring.jpa.hibernate.ddl-auto` (auto-generate)

### RabbitMQ (Messaging — Planned)

- **Status:** Referenced in README but NOT implemented
- **Intended Use:** Notification service consuming messages from fund transfer and payment services
- **Current State:** No RabbitMQ dependency in any build.gradle, no publisher/consumer code

### Netflix Eureka (Service Discovery)

- **Port:** 8081
- **Mode:** Standalone (single instance, `register-with-eureka: false`)
- **Clients:** All business services register via `spring-cloud-starter-netflix-eureka-client`

### Spring Cloud Config Server

- **Port:** 8090
- **Backend:** Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`)
- **Branch:** `main`
- **Path:** `configuration/`
- **Clients:** All services fetch config at bootstrap via `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap`

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Tool:** Gradle (per-service, no multi-project build)
- **Java:** 21 (sourceCompatibility)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:**
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) — generates git.properties for `/actuator/info`

### Docker

- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has its own Dockerfile that copies the fat JAR and a `wait-for-it.sh` script
- **Startup:** Services wait for dependencies (registry, config-server, MySQL) using `wait-for-it.sh` before launching
- **Profile:** Docker containers run with `-Dspring.profiles.active=docker`

### Docker Compose

- **File:** `docker-compose/docker-compose.yml`
- **Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs
- **Services:** All 6 application services + MySQL + Keycloak + PostgreSQL + Zipkin
- **Volumes:** Persistent volumes for MySQL and PostgreSQL data
- **Support File:** `docker-compose-support-apps.yml` — runs only infrastructure (Zipkin, Keycloak, MySQL, Config, Registry) for local development

### Testing

- **Framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test Database:** H2 in-memory (`com.h2database:h2:2.2.224`) for service-level tests
- **Coverage:** Unit tests exist only for core-banking-service (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only the default Spring Boot context load test.

### CI/CD

- **GitHub Actions:** Workflows were removed (commit `fa1c445`) due to PAT scope compatibility issues
- **No active CI pipeline** in the repository
