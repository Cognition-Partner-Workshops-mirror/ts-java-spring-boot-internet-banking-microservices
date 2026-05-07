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

This is a Java 21 / Spring Boot 3.2.4 internet banking application built on a microservices architecture with Spring Cloud 2023.0.0. It consists of **7 deployable units** (6 application microservices + 1 infrastructure config server) communicating through synchronous REST (OpenFeign) calls, with service discovery via Netflix Eureka.

### Services

| Service | Port | Purpose | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration, approval workflow, Keycloak identity management | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | API gateway, OAuth2/JWT security enforcement, request routing | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |

### Communication Patterns

```
                    ┌─────────────────────┐
                    │   Keycloak (8080)    │
                    │   Auth / Identity    │
                    └────────┬────────────┘
                             │ OAuth2 / JWT
                             ▼
┌──────────┐    ┌───────────────────────────┐    ┌──────────────────┐
│  Client   │───▶│  API Gateway (8082)       │───▶│ Service Registry │
│ (Browser/ │    │  - OAuth2 Resource Server │    │  Eureka (8081)   │
│  Postman) │    │  - Route definitions      │    └──────────────────┘
└──────────┘    │  - X-Auth-Id propagation  │              ▲
                └─────┬────┬────┬───────────┘              │ Register
                      │    │    │                           │
          ┌───────────┘    │    └──────────────┐           │
          ▼                ▼                   ▼           │
   ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
   │ User Service │ │ Fund Transfer│ │ Utility Payment  │──┘
   │   (8083)     │ │   (8084)     │ │   (8085)         │
   └──────┬───────┘ └──────┬───────┘ └──────┬───────────┘
          │                │                 │
          │  OpenFeign     │  OpenFeign      │  OpenFeign
          ▼                ▼                 ▼
   ┌─────────────────────────────────────────────────┐
   │          Core Banking Service (8092)             │
   │   - Account management                          │
   │   - Transaction processing                      │
   │   - User data (core)                            │
   └──────────────────┬──────────────────────────────┘
                      │
                      ▼
               ┌──────────────┐
               │  MySQL (3306) │
               └──────────────┘
```

**Communication flow:**
1. All client requests enter through the **API Gateway** (OAuth2 JWT-secured)
2. The gateway extracts the authenticated principal and propagates it as an `X-Auth-Id` HTTP header
3. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` for audit trail
4. **User Service**, **Fund Transfer Service**, and **Utility Payment Service** call **Core Banking Service** via OpenFeign (service name-based discovery through Eureka)
5. **User Service** also calls **Keycloak** directly via the Keycloak Admin Client for identity management

### Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| **MySQL** | Custom (docker-compose/mysql) | Shared database server hosting 4 databases |
| **Keycloak** | quay.io/keycloak/keycloak:23.0.7 | Identity and access management, OAuth2/OIDC provider |
| **PostgreSQL** | postgres:15 | Keycloak's backing database |
| **Zipkin** | openzipkin/zipkin:3 | Distributed tracing collection and visualization |
| **Spring Cloud Config** | Custom | Centralized configuration from Git repository |
| **Eureka** | Custom | Service discovery and registration |

### Configuration Management

All services (except Config Server and Service Registry) use **Spring Cloud Config** with bootstrap.yml pointing to the config server. Configuration is sourced from a remote Git repository:
- **Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Path:** `configuration/`

Profile-based bootstrapping:
- `bootstrap.yml` — local development (localhost:8090)
- `bootstrap-dev.yml` — dev environment (192.168.1.5:8090)
- `bootstrap-docker.yml` — Docker Compose (internet-banking-config-server:8090)

---

## 2. Data Model Documentation

### core-banking-service

#### `banking_core_user`
| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Unique user identifier |
| first_name | VARCHAR(255) | | User's first name |
| last_name | VARCHAR(255) | | User's last name |
| email | VARCHAR(255) | | User's email address |
| identification_number | VARCHAR(255) | | National ID / identification number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Unique account identifier |
| number | VARCHAR(255) | | Account number (e.g., "100015003000") |
| type | VARCHAR(255) | | Enum: SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT |
| status | VARCHAR(255) | | Enum: PENDING, ACTIVE, DORMANT, BLOCKED |
| available_balance | DECIMAL(19,2) | | Available balance for transactions |
| actual_balance | DECIMAL(19,2) | | Actual/ledger balance |
| user_id | BIGINT | FK → banking_core_user.id | Owner of the account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Unique utility account identifier |
| number | VARCHAR(255) | | Utility account number |
| provider_name | VARCHAR(255) | | Utility provider (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | Unique transaction identifier |
| amount | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| transaction_type | VARCHAR(30) | NOT NULL | Enum: FUND_TRANSFER, UTILITY_PAYMENT |
| reference_number | VARCHAR(50) | NOT NULL | Reference (destination account or provider ref) |
| transaction_id | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| account_id | BIGINT | FK → banking_core_account.id | Account involved in transaction |

#### Relationships
```
banking_core_user 1──────M banking_core_account 1──────1 banking_core_transaction
```

### internet-banking-user-service

#### `user` (JPA-managed, auto-created via Hibernate)
| Column | Type | Description |
|---|---|---|
| id | BIGINT (PK) | Auto-generated ID |
| auth_id | VARCHAR | Keycloak user UUID |
| identification | VARCHAR | NIC / identification number |
| status | VARCHAR | Enum: PENDING, APPROVED, DISABLED, BLACKLIST |
| created_date | TIMESTAMP | Audit: creation timestamp |
| created_by | VARCHAR | Audit: creator identity |
| modified_date | TIMESTAMP | Audit: last modification timestamp |
| modified_by | VARCHAR | Audit: last modifier identity |
| version | BIGINT | Optimistic locking version |

### internet-banking-fund-transfer-service

#### `fund_transfer` (JPA-managed, auto-created via Hibernate)
| Column | Type | Description |
|---|---|---|
| id | BIGINT (PK) | Auto-generated ID |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| transaction_reference | VARCHAR | Core banking transaction ID |
| status | VARCHAR | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| created_date / created_by / modified_date / modified_by / version | — | Audit fields |

### internet-banking-utility-payment-service

#### `utility_payment` (JPA-managed, auto-created via Hibernate)
| Column | Type | Description |
|---|---|---|
| id | BIGINT (PK) | Auto-generated ID |
| provider_id | BIGINT | Utility provider identifier |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Customer reference number |
| account | VARCHAR | Source bank account number |
| transaction_id | VARCHAR | Core banking transaction ID |
| status | VARCHAR | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| created_date / created_by / modified_date / modified_by / version | — | Audit fields |

---

## 3. API Surface Map

### Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| GET | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Get bank account by account number |
| GET | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Get utility account by provider name |
| POST | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Process fund transfer at core level |
| POST | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment at core level |
| GET | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Get user by identification number |
| GET | `/api/v1/user` | Pageable params | `List<User>` | Get paginated list of users |

### Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| POST | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` (id, email, identification, authId, status) | Register new user (creates in Keycloak + local DB) |
| PATCH | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (APPROVED enables Keycloak account) |
| GET | `/api/v1/bank-users` | Pageable params | `List<User>` | Get paginated list of registered users |
| GET | `/api/v1/bank-users/{id}` | — | `User` | Get user by database ID |

### Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| POST | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate fund transfer |
| GET | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | Get paginated list of fund transfers |

### Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| POST | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| GET | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | Get paginated list of utility payments |

### API Gateway Route Prefixes

All requests through the gateway (port 8082) are routed with path prefixes (configured via Spring Cloud Config):
- `/user/**` → internet-banking-user-service
- `/fund-transfer/**` → internet-banking-fund-transfer-service
- `/banking-core/**` → core-banking-service
- `/utility-payment/**` → internet-banking-utility-payment-service

### Security Rules (Gateway)

| Path Pattern | Access |
|---|---|
| `/user/api/v1/bank-users/register` | Public (permitAll) |
| `/actuator/**`, `/*/actuator/**` | Public (permitAll) |
| All other paths | Authenticated (JWT required) |

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client sends POST to `/user/api/v1/bank-users/register` with `{ email, identification, password }`
2. **User Service** checks if email already exists in Keycloak — throws `UserAlreadyRegisteredException` if so
3. **User Service** calls **Core Banking Service** via Feign to validate the identification number exists
4. Validates the email matches the core banking record — throws `InvalidEmailException` if mismatch
5. Creates a Keycloak user (disabled, email unverified) with provided credentials
6. Retrieves the Keycloak-generated auth ID
7. Saves user entity locally with status `PENDING`
8. Returns the created user

### User Approval Flow

1. Admin sends PATCH to `/user/api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`
2. **User Service** looks up the user in local DB
3. If new status is `APPROVED`, reads the Keycloak user and sets `enabled=true`, `emailVerified=true`
4. Updates local DB status

### Fund Transfer Flow

1. Client sends POST to `/fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`
2. **Fund Transfer Service** creates a local `FundTransferEntity` with status `PENDING`
3. Calls **Core Banking Service** via Feign (`/api/v1/transaction/fund-transfer`)
4. **Core Banking Service**:
   - Reads both source and destination accounts
   - Validates source account has sufficient funds (`actualBalance >= amount`)
   - Throws `InsufficientFundsException` if insufficient
   - Debits source account (subtracts from actualBalance and availableBalance)
   - Credits destination account (adds to actualBalance and availableBalance)
   - Creates two transaction records (debit + credit) with the same transactionId
   - Returns `{ message, transactionId }`
5. **Fund Transfer Service** updates local entity with transaction reference and status `SUCCESS`

### Utility Payment Flow

1. Client sends POST to `/utility-payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`
2. **Utility Payment Service** creates a local `UtilityPaymentEntity` with status `PROCESSING`
3. Calls **Core Banking Service** via Feign (`/api/v1/transaction/util-payment`)
4. **Core Banking Service**:
   - Reads the source bank account
   - Validates sufficient funds
   - Reads the utility account by provider ID
   - Debits the source account
   - Creates a transaction record (type: UTILITY_PAYMENT)
   - Returns `{ message, transactionId }`
5. **Utility Payment Service** updates local entity with transaction ID and status `SUCCESS`

### Balance Validation Rules

- Source account `actualBalance` must be >= 0 AND >= transfer/payment amount
- If either condition fails: `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001`

### Audit Trail

- Three services (user, fund-transfer, utility-payment) implement JPA auditing via `AuditAware` base class
- Captures: `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`
- Auditor identity sourced from `X-Auth-Id` header (propagated by API Gateway from JWT principal)
- Uses `ThreadLocal`-based `ApiRequestContextHolder` pattern
- Includes optimistic locking via `@Version` field

---

## 5. Integration Points

### Keycloak (Identity Provider)

| Aspect | Details |
|---|---|
| **Version** | 23.0.7 |
| **Connection** | Keycloak Admin Client 24.0.4 (internet-banking-user-service) |
| **Realm** | `javatodev-internet-banking` (imported via realm-export.json) |
| **Client** | `internet-banking-api-client` (client_credentials grant) |
| **JWT Validation** | API Gateway validates JWT via `jwk-set-uri` (configured in Spring Cloud Config) |
| **Operations** | Create user, update user (enable/disable), search by email, read by ID |
| **Data Flow** | User Service → Keycloak Admin REST API |

### RabbitMQ (Message Broker)

| Aspect | Details |
|---|---|
| **Status** | Listed in technology stack and README but **NOT IMPLEMENTED** in current codebase |
| **Intended Use** | Fund Transfer and Utility Payment services would push notification messages |
| **Notification Service** | Referenced in README as "PENDING Development" |

### Zipkin (Distributed Tracing)

| Aspect | Details |
|---|---|
| **Version** | openzipkin/zipkin:3 |
| **Port** | 9411 |
| **Integration** | Via Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`) |
| **Coverage** | All services except Config Server and Service Registry include tracing dependencies |
| **Feign Integration** | `feign-micrometer` dependency for trace propagation across Feign calls |

### Database Connections

| Service | Database | Driver | Migration |
|---|---|---|---|
| core-banking-service | `banking_core_service` (MySQL) | mysql-connector-j 8.4.0 | Flyway 10.12.0 (3 migration scripts) |
| internet-banking-user-service | `banking_core_user_service` (MySQL) | mysql-connector-j 8.4.0 | Hibernate auto-DDL |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` (MySQL) | mysql-connector-j 8.4.0 | Hibernate auto-DDL |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` (MySQL) | mysql-connector-j 8.4.0 | Hibernate auto-DDL |

**Test databases:** All services use H2 in-memory databases with Flyway disabled for testing.

### OpenFeign Inter-Service Communication

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| internet-banking-user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| internet-banking-fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| internet-banking-utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project build)
- **Java:** 21 (source compatibility)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Gradle Wrapper:** 8.7 (distributionUrl in gradle-wrapper.properties)

### Gradle Plugins

| Plugin | Version | Services |
|---|---|---|
| `org.springframework.boot` | 3.2.4 | All |
| `io.spring.dependency-management` | 1.1.4 | All |
| `com.gorylenko.gradle-git-properties` | 2.4.2 | All except service-registry |

### Docker

Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`:
1. Copies the built JAR (`build/libs/<service>-0.0.1-SNAPSHOT.jar`) as `app.jar`
2. Includes `wait-for-it.sh` for startup ordering
3. Runs with `-Dspring.profiles.active=docker` profile

### Docker Compose

**`docker-compose.yml`** — Full stack deployment:
- All 7 services + MySQL + Keycloak + PostgreSQL + Zipkin
- Custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`)
- Fixed IP addresses per container
- `wait-for-it.sh` for dependency ordering (registry → config → mysql → service)
- Named volumes for MySQL and PostgreSQL data persistence

**`docker-compose-support-apps.yml`** — Infrastructure only:
- Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry
- For local development of business services outside Docker

### Startup Order

```
1. MySQL + PostgreSQL + Zipkin (no dependencies)
2. Keycloak (depends on PostgreSQL)
3. Config Server (no service dependencies)
4. Service Registry (no service dependencies)
5. API Gateway (waits for: registry, config server)
6. User Service (waits for: registry, config server, MySQL)
7. Fund Transfer Service (waits for: registry, config server, MySQL)
8. Utility Payment Service (waits for: registry, config server, MySQL)
9. Core Banking Service (waits for: registry, config server, MySQL)
```

### Test Data

Flyway migrations in core-banking-service seed:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with varying balances
- 6 utility accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak realm export includes pre-configured:
- Realm: `javatodev-internet-banking`
- Client: `internet-banking-api-client`
- Test user: `ib_admin@javatodev.com / 5V7huE3G86uB`

### Postman Collection

Located in `postman_collection/`:
- `JAVA_TO_DEV_MICROSERVICES.postman_collection.json` — API test collection
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json` — Environment variables
- Environment: `LOCAL_DOCKER_SETUP` for Docker Compose testing
