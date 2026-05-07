# Application Knowledge Base

## 1. Architecture Overview

### System Architecture

This is a **Java 21 / Spring Boot 3.2.4** microservices-based internet banking application using **Spring Cloud 2023.0.0**. The system follows a service-oriented architecture with centralized configuration, service discovery, and API gateway patterns.

### Services

| Service | Port | Role |
|---------|------|------|
| `internet-banking-config-server` | 8090 | Centralized configuration (Git-backed) |
| `internet-banking-service-registry` | 8081 | Netflix Eureka service discovery |
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway + OAuth2/JWT security |
| `internet-banking-user-service` | 8083 | User registration, approval, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment orchestration |
| `core-banking-service` | 8092 | Core banking ledger (accounts, transactions, users) |

### Communication Patterns

- **Synchronous (HTTP/REST via OpenFeign):**
  - `internet-banking-user-service` -> `core-banking-service` (user validation)
  - `internet-banking-fund-transfer-service` -> `core-banking-service` (fund transfer execution)
  - `internet-banking-utility-payment-service` -> `core-banking-service` (utility payment execution)
- **Service Discovery:** All services register with Eureka; Feign clients resolve service names via Eureka
- **Gateway Routing:** API Gateway routes external requests to internal services with path-based routing
- **Auth Propagation:** Gateway extracts JWT principal and forwards `X-Auth-Id` header to downstream services

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka | Service discovery and registration |
| Config Server | Spring Cloud Config | Centralized config from Git repo |
| API Gateway | Spring Cloud Gateway | Routing, security, header propagation |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication |
| Database | MySQL 8.4.0 | Persistent storage (4 schemas) |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Message Broker | RabbitMQ | Notification messages (planned, not implemented) |

---

## 2. Data Model Documentation

### Core Banking Service (Database: `banking_core_service`)

#### `banking_core_user`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / identification |

#### `banking_core_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `CURRENT_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE`, `INACTIVE` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK -> banking_core_user.id) | Account owner |

#### `banking_core_utility_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### `banking_core_transaction`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or reference) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK -> banking_core_account.id) | Associated account |

### Internet Banking User Service (Database: `banking_core_user_service`)

#### `user`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID reference |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `REJECTED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Fund Transfer Service (Database: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `created_by` / `modified_by` | VARCHAR | Audit fields |
| `version` | BIGINT | Optimistic locking |

### Utility Payment Service (Database: `banking_core_utility_payment_service`)

#### `utility_payment`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `version` | BIGINT | Optimistic locking |

### Relationships

```
banking_core_user 1──N banking_core_account 1──N banking_core_transaction
                                                   |
banking_core_utility_account ──────────────────────┘ (referenced via provider_id)
```

---

## 3. API Surface Map

### Core Banking Service (`/api/v1`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: account_number | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: account_name | `UtilityAccount` (id, number, providerName) |
| GET | `/api/v1/user/{identification}` | Get user by ID number | Path: identification | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| GET | `/api/v1/user` | List users (paginated) | Query: page, size, sort | `List<User>` |
| POST | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| POST | `/api/v1/transaction/util-payment` | Execute utility payment | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |

### Internet Banking User Service (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `User` (email, password, identification) | `User` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` (status) | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | Query: page, size | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `User` |

### Internet Banking Fund Transfer Service (`/api/v1/transfer`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount, authID) | `FundTransferResponse` (message, transactionId) |
| GET | `/api/v1/transfer` | List transfers (paginated) | Query: page, size | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| GET | `/api/v1/utility-payment` | List payments (paginated) | Query: page, size | `List<UtilityPayment>` |

### API Gateway Route Prefixes

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### Public Endpoints (No Auth Required)

- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

---

## 4. Key Business Logic Inventory

### User Registration Flow
1. Client sends registration request (email, password, identification) to User Service
2. User Service checks Keycloak for existing email -> throws `UserAlreadyRegisteredException` if found
3. User Service calls Core Banking Service to validate identification number exists
4. Validates email matches the core banking record -> throws `InvalidEmailException` on mismatch
5. Creates Keycloak user (disabled, email unverified) with provided credentials
6. Saves local user record with status `PENDING` and Keycloak `authId`
7. Admin later calls `PATCH /update/{id}` with status `APPROVED` to enable Keycloak account

### Fund Transfer Flow
1. Client sends transfer request (fromAccount, toAccount, amount) to Fund Transfer Service
2. Service creates local `FundTransferEntity` with status `PENDING`
3. Calls Core Banking Service via Feign (`/api/v1/transaction/fund-transfer`)
4. Core Banking validates sender balance (throws `InsufficientFundsException` if insufficient)
5. Core Banking debits sender, credits receiver, creates transaction records
6. Fund Transfer Service updates local record to `SUCCESS` with transaction reference
7. Returns success response to client

### Utility Payment Flow
1. Client sends payment request (providerId, amount, referenceNumber, account) to Utility Payment Service
2. Service creates local `UtilityPaymentEntity` with status `PROCESSING`
3. Calls Core Banking Service via Feign (`/api/v1/transaction/util-payment`)
4. Core Banking validates balance, debits account, records transaction
5. Utility Payment Service updates local record to `SUCCESS` with transaction ID
6. Returns success response

### Balance Validation Rules
- Account `actualBalance` must be >= 0
- Account `actualBalance` must be >= requested transfer/payment amount
- Violation throws `InsufficientFundsException`

### Audit Trail
- Fund Transfer, Utility Payment, and User entities extend `AuditAware`
- Tracks: `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`
- Optimistic locking via `@Version` field
- `createdBy`/`modifiedBy` populated from `X-Auth-Id` header via `AppAuthUserFilter`

---

## 5. Integration Points

### Keycloak (Identity Provider)
- **Version:** 23.0.7
- **Connection:** Admin Client API (`keycloak-admin-client:24.0.4`)
- **Realm:** Configured via `app.config.keycloak.realm`
- **Auth Type:** Client credentials grant
- **Operations:** Create user, update user (enable/disable), search by email, read by ID
- **JWT Validation:** API Gateway validates JWTs against Keycloak's JWK endpoint

### RabbitMQ (Message Broker)
- **Status:** Referenced in architecture docs but NOT implemented in code
- **Planned Use:** Push notification messages from Fund Transfer and Utility Payment services
- **No dependencies** on RabbitMQ libraries exist in any `build.gradle`

### Zipkin (Distributed Tracing)
- **Version:** 3
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- **Coverage:** HTTP requests and Feign calls are traced

### Database Connections
- **Engine:** MySQL 8.4.0
- **Driver:** `com.mysql:mysql-connector-j:8.4.0`
- **Schemas:** 4 separate databases (`banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`)
- **Migrations:** Flyway 10.12.0 (core-banking-service only)
- **ORM:** Spring Data JPA / Hibernate
- **User:** `javatodev_development` / `oPItyPticIAt`

### Spring Cloud Config
- **Config Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Bootstrap:** All services use `bootstrap.yml` pointing to config server at `localhost:8090` (or Docker hostname)

### Service Discovery (Eureka)
- **Registry Port:** 8081
- All business services register as Eureka clients
- Feign clients use Eureka service names for resolution

---

## 6. Build and Deployment Pipeline Summary

### Build System
- **Build Tool:** Gradle (per-service, no multi-project build)
- **Java Version:** 21
- **Spring Boot:** 3.2.4 (via Spring Boot Gradle plugin)
- **Spring Cloud:** 2023.0.0
- **Artifact:** Fat JAR per service

### Docker
- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has its own `Dockerfile`
- **Wait Script:** `wait-for-it.sh` for startup ordering (waits for config server, service registry, MySQL)
- **Profile:** Docker profile activated via `-Dspring.profiles.active=docker`

### Docker Compose
- **Main file:** `docker-compose/docker-compose.yml` (full stack)
- **Support file:** `docker-compose/docker-compose-support-apps.yml` (infra only)
- **Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs
- **Volumes:** `postgres_data` (Keycloak DB), `mysqldata` (application DB)

### Deployment Topology (Docker Compose)
```
┌─────────────────────────────────────────────────────────────────────┐
│                    Docker Network (172.25.0.0/16)                     │
│                                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────────┐ │
│  │   Zipkin     │  │   Keycloak   │  │   MySQL (4 schemas)          │ │
│  │  :9411       │  │   :8080      │  │   :3306                      │ │
│  └─────────────┘  └──────────────┘  └─────────────────────────────┘ │
│                                                                       │
│  ┌──────────────────┐  ┌─────────────────────┐                      │
│  │  Config Server    │  │  Service Registry    │                      │
│  │  :8090            │  │  :8081 (Eureka)      │                      │
│  └──────────────────┘  └─────────────────────┘                      │
│                                                                       │
│  ┌─────────────────┐                                                 │
│  │  API Gateway     │ ← External traffic entry point                 │
│  │  :8082           │                                                 │
│  └─────────────────┘                                                 │
│         │                                                             │
│         ├──→ User Service (:8083)                                    │
│         ├──→ Fund Transfer Service (:8084)                           │
│         ├──→ Utility Payment Service (:8085)                         │
│         └──→ Core Banking Service (:8092)                            │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### CI/CD
- **GitHub Actions:** Not configured (workflows removed)
- **No automated build/test pipeline** present in the repository
- **Image publishing:** Images referenced as `javatodev/<service-name>` (DockerHub)

### Startup Order
1. MySQL, Keycloak DB, Zipkin (infrastructure)
2. Keycloak (depends on PostgreSQL)
3. Config Server (standalone)
4. Service Registry (standalone)
5. API Gateway (waits for registry + config server)
6. Business services (wait for registry + config server + MySQL)
