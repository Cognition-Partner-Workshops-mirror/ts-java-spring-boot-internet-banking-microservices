# Architecture Documentation

## System Overview

This is a Java Spring Boot microservices-based internet banking application. It implements a distributed architecture with six services communicating via REST (OpenFeign), secured by Keycloak OAuth2, registered through Netflix Eureka, and traced with Zipkin.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          API Gateway (:8082)                             │
│                   (OAuth2 Resource Server + Routing)                     │
└────────┬──────────────┬───────────────────┬────────────────┬────────────┘
         │              │                   │                │
         ▼              ▼                   ▼                ▼
┌─────────────┐ ┌──────────────┐ ┌───────────────────┐ ┌──────────────┐
│ User Service│ │Fund Transfer │ │ Utility Payment   │ │Core Banking  │
│   (:8083)   │ │Service(:8084)│ │ Service (:8085)   │ │Service(:8092)│
└──────┬──────┘ └──────┬───────┘ └─────────┬─────────┘ └──────┬───────┘
       │               │                   │                   │
       │               └───────────────────┴───────────────────┘
       │                         │ (Feign Clients)
       ▼                         ▼
┌─────────────┐         ┌──────────────┐
│  Keycloak   │         │    MySQL     │
│  (:8080)    │         │   (:3306)    │
└─────────────┘         └──────────────┘
```

**Infrastructure Services:**
- **Config Server** (:8090) — Centralized configuration from Git repository
- **Service Registry** (:8081) — Netflix Eureka for service discovery

---

## 1. Entities, Relationships, and Key Fields Per Service

### Core Banking Service

| Entity | Table | Key Fields | Relationships |
|--------|-------|------------|---------------|
| `UserEntity` | `banking_core_user` | `id` (PK, auto), `firstName`, `lastName`, `email`, `identificationNumber` | One-to-Many → `BankAccountEntity` |
| `BankAccountEntity` | `banking_core_account` | `id` (PK, auto), `number`, `type` (SAVINGS_ACCOUNT), `status` (ACTIVE), `availableBalance`, `actualBalance` | Many-to-One → `UserEntity` (FK: `user_id`) |
| `TransactionEntity` | `banking_core_transaction` | `id` (PK, auto), `amount`, `transactionType` (FUND_TRANSFER/UTILITY_PAYMENT), `referenceNumber`, `transactionId` | One-to-One → `BankAccountEntity` (FK: `account_id`) |
| `UtilityAccountEntity` | `banking_core_utility_account` | `id` (PK, auto), `number`, `providerName` | None |

### Internet Banking User Service

| Entity | Table | Key Fields | Relationships |
|--------|-------|------------|---------------|
| `UserEntity` | `user` | `id` (PK, auto), `authId` (Keycloak reference), `identification`, `status` (PENDING/APPROVED) | Extends `AuditAware` (createdAt, updatedAt) |

### Internet Banking Fund Transfer Service

| Entity | Table | Key Fields | Relationships |
|--------|-------|------------|---------------|
| `FundTransferEntity` | `fund_transfer` | `id` (PK, auto), `transactionReference`, `fromAccount`, `toAccount`, `amount`, `status` (PENDING/SUCCESS) | Extends `AuditAware` |

### Internet Banking Utility Payment Service

| Entity | Table | Key Fields | Relationships |
|--------|-------|------------|---------------|
| `UtilityPaymentEntity` | `utility_payment` | `id` (PK, auto), `providerId`, `amount`, `referenceNumber`, `account`, `transactionId`, `status` (PROCESSING/SUCCESS) | Extends `AuditAware` |

### Entity Relationship Diagram

```
banking_core_user (1) ──────< banking_core_account (N)
                                       │
                                       │ (1)
                                       ▼
                              banking_core_transaction (1)

banking_core_utility_account (standalone)

user (internet-banking-user-service, references Keycloak authId)

fund_transfer (internet-banking-fund-transfer-service, references account numbers as strings)

utility_payment (internet-banking-utility-payment-service, references account number + providerId)
```

---

## 2. API Surface Map

### API Gateway Routes (Port 8082)

All downstream service endpoints are exposed through the gateway with path prefixes:

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (Port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (number, type, status, availableBalance, actualBalance) | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber) | Get user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) | Process utility payment |

### Internet Banking User Service (Port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` (email, password, identification) | `User` | Register new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` (status) | `User` | Update user (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List all users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by ID |

### Internet Banking Fund Transfer Service (Port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List all fund transfers (paginated) |

### Internet Banking Utility Payment Service (Port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List all payments (paginated) |

### Authentication Endpoint (via Keycloak)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | Keycloak token endpoint | OAuth2 credentials | JWT Token | Authenticate user |

---

## 3. Key Business Logic Inventory

### Fund Transfer Rules (TransactionService)

1. **Balance Validation**: Verifies sender's `actualBalance` >= transfer `amount` and `actualBalance` > 0
2. **Atomic Transfer**: Debits sender account, credits receiver account, records two `TransactionEntity` entries (debit + credit) with matching `transactionId`
3. **Transaction ID**: Generated via `UUID.randomUUID()` for each transfer
4. **Status Tracking**: Fund transfer entity starts as `PENDING`, transitions to `SUCCESS` upon core banking confirmation
5. **Orchestration**: Fund Transfer Service saves local record → calls Core Banking via Feign → updates local record with transaction reference

### Payment Processing (UtilityPaymentService)

1. **Balance Validation**: Same as fund transfer — checks `actualBalance` >= payment `amount`
2. **Provider Resolution**: Looks up utility provider by ID from `banking_core_utility_account` table
3. **Debit Only**: Only debits the sender's account (no credit counterpart — assumes third-party provider handles their side)
4. **Status Tracking**: Utility payment entity starts as `PROCESSING`, transitions to `SUCCESS` after core banking confirmation
5. **Reference Number**: Caller supplies a reference number for the payment

### User Management (UserService)

1. **Registration Flow**:
   - Check Keycloak for existing email (reject duplicates)
   - Validate user exists in Core Banking by identification number
   - Verify email matches Core Banking record
   - Create Keycloak user (disabled, email unverified)
   - Save local user record with `PENDING` status
2. **Approval Flow**:
   - Admin updates user status to `APPROVED`
   - Enables the Keycloak user and marks email as verified
3. **User Identity**: Links local user record to Keycloak via `authId`

---

## 4. Integration Points

### Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Purpose**: Authentication (OAuth2/JWT), user management
- **Integration**: API Gateway validates JWTs via `jwk-set-uri`; User Service uses `keycloak-admin-client` (v24.0.4) for programmatic user CRUD
- **Backing Store**: PostgreSQL 15
- **Realm**: Imported via volume mount at startup (`docker-compose/keycloak/`)

### RabbitMQ (Message Queue)

- **Purpose**: Notification delivery (mentioned in architecture but **not yet implemented**)
- **Planned Use**: Fund Transfer and Utility Payment services push notification messages; a Notification service consumes them

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- **Scope**: Traces propagated via Spring Cloud Sleuth/Micrometer across Feign calls

### Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| Core Banking Service | MySQL 3306 | `banking_core_service` |
| Fund Transfer Service | MySQL 3306 | `banking_core_fund_transfer_service` |
| User Service | MySQL 3306 | `banking_core_user_service` |
| Utility Payment Service | MySQL 3306 | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL 5432 | `keycloak` |

- **ORM**: Spring Data JPA with Hibernate
- **Migrations**: Flyway (Core Banking Service only — V1.0.x series)
- **Credentials**: `javatodev_development` / `oPItyPticIAt` (created in `privileges.sql`)

### Inter-Service Communication (OpenFeign)

| Caller | Target | Feign Client | Endpoints Called |
|--------|--------|-------------|-----------------|
| Fund Transfer Service | Core Banking Service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{num}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking Service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{num}`, `POST /api/v1/transaction/util-payment` |
| User Service | Core Banking Service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |

### Spring Cloud Config Server

- **Port**: 8090
- **Git Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- **Purpose**: Externalized configuration for all services (database URLs, Eureka settings, Keycloak URIs, Zipkin endpoint, etc.)

### Netflix Eureka Service Registry

- **Port**: 8081
- **Purpose**: Service discovery — all application services register themselves; Feign clients use service names (e.g., `core-banking-service`) resolved via Eureka

---

## 5. Build and Deployment Pipeline

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`)
- **Java Version**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugins**: `spring-boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`

### Docker Compose Deployment

**File**: `docker-compose/docker-compose.yml`

**Startup Order** (managed via `wait-for-it.sh` entrypoint scripts):
1. Infrastructure: MySQL, PostgreSQL (Keycloak DB), Zipkin, Keycloak
2. Config Server (port 8090)
3. Service Registry (port 8081)
4. Application services wait for both Config Server and Service Registry before starting

**Network**: Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments.

**Support Compose** (`docker-compose-support-apps.yml`): Lighter variant with only infrastructure + config/registry services (no application services).

### Container Images

All services use pre-built images from Docker Hub under the `javatodev/` namespace:
- `javatodev/internet-banking-config-server`
- `javatodev/internet-banking-service-registry`
- `javatodev/internet-banking-api-gateway`
- `javatodev/internet-banking-user-service`
- `javatodev/internet-banking-fund-transfer-service`
- `javatodev/internet-banking-utility-payment-service`
- `javatodev/core-banking-service`

### Profiles

- **Default**: Local development (localhost connections)
- **Docker** (`-Dspring.profiles.active=docker`): Used in Docker Compose with container hostnames

### Monitoring

- **Spring Boot Actuator**: Enabled on all services (health, info, metrics)
- **Micrometer**: Metrics bridge for tracing
- **Prometheus**: Listed in technology stack (dependency not explicitly present in build files)
