# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 System Summary

A microservices-based internet banking platform built with **Java 21**, **Spring Boot 3.2.4**, and **Spring Cloud 2023.0.0**. The system handles user registration, fund transfers between accounts, and utility bill payments.

### 1.2 Services

| Service | Port | Description | Key Dependencies |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway — central request router with OAuth2/JWT security via Keycloak | Spring Cloud Gateway, Spring Security OAuth2, WebFlux |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery | Eureka Server, Actuator |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server — centralized configuration from a Git repository | Spring Cloud Config Server |
| `core-banking-service` | 8092 | Core banking engine — manages users, accounts, transactions, and processes fund transfers/utility payments at the ledger level | Spring Data JPA, Flyway, MySQL, Eureka Client |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates fund transfers by coordinating with core-banking-service via Feign | OpenFeign, Spring Data JPA, MySQL, Eureka Client |
| `internet-banking-user-service` | 8083 | User lifecycle management — registration, approval, and Keycloak integration | OpenFeign, Keycloak Admin Client, Spring Data JPA, MySQL |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility bill payments by coordinating with core-banking-service via Feign | OpenFeign, Spring Data JPA, MySQL, Eureka Client |

### 1.3 Communication Patterns

- **Synchronous REST via OpenFeign**: All inter-service communication uses Spring Cloud OpenFeign clients routed through Eureka service discovery.
  - `fund-transfer-service` → `core-banking-service` (fund transfer processing)
  - `utility-payment-service` → `core-banking-service` (utility payment processing)
  - `user-service` → `core-banking-service` (user lookup by identification)
- **No asynchronous messaging**: RabbitMQ is mentioned in the README documentation but is **not implemented** in the codebase. No message queues, event buses, or async patterns exist.
- **Gateway routing**: The API Gateway routes external requests to downstream services based on path prefixes, adding an `X-Auth-Id` header with the authenticated user's principal name.

### 1.4 Infrastructure Components

| Component | Image/Version | Purpose | Static IP |
|---|---|---|---|
| MySQL | 8.4 (custom build from `docker-compose/mysql/`) | Primary datastore for all 4 service databases | 172.25.0.9 |
| Keycloak | 23.0.7 | Identity and access management (OAuth2/OIDC) | 172.25.0.11 |
| PostgreSQL | 15 | Keycloak's backing database | 172.25.0.10 |
| Zipkin | 3 | Distributed tracing collector and UI | 172.25.0.12 |

### 1.5 Network Topology

All containers run on a custom Docker bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments. Services use `wait-for-it.sh` scripts (50-second timeout) to enforce startup ordering:

```
MySQL → Config Server → Service Registry → [Gateway, Core-Banking, User, Fund-Transfer, Utility-Payment]
```

---

## 2. Data Model Documentation

### 2.1 Database Schemas

The application uses a **single MySQL 8.4 instance** hosting **4 separate schemas**:

| Schema | Owning Service | Migration Strategy |
|---|---|---|
| `banking_core_service` | core-banking-service | Flyway (versioned SQL migrations) |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service | JPA auto-DDL (Hibernate) |
| `banking_core_user_service` | internet-banking-user-service | JPA auto-DDL (Hibernate) |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service | JPA auto-DDL (Hibernate) |

Database user: `javatodev_development` with broad privileges (`CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*`).

### 2.2 Entity Relationship Diagram (Core Banking Service)

```
┌──────────────────┐       ┌──────────────────────┐       ┌──────────────────────────┐
│ banking_core_user│       │ banking_core_account  │       │ banking_core_transaction │
├──────────────────┤       ├──────────────────────-┤       ├──────────────────────────┤
│ id (PK, BIGINT)  │──1:N─→│ id (PK, BIGINT)      │──1:1─→│ id (PK, BIGINT)          │
│ first_name       │       │ number (VARCHAR)      │       │ amount (DECIMAL 19,2)    │
│ last_name        │       │ type (ENUM)           │       │ transaction_type (ENUM)  │
│ email            │       │ status (ENUM)         │       │ reference_number         │
│ identification_  │       │ available_balance     │       │ transaction_id           │
│   number         │       │ actual_balance        │       │ account_id (FK)          │
└──────────────────┘       │ user_id (FK)          │       └──────────────────────────┘
                           └──────────────────────-┘

┌──────────────────────────────┐
│ banking_core_utility_account │
├──────────────────────────────┤
│ id (PK, BIGINT)              │
│ number (VARCHAR)             │
│ provider_name (VARCHAR)      │
└──────────────────────────────┘
```

### 2.3 Entities Per Service

#### Core Banking Service (`banking_core_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UserEntity` | `banking_core_user` | `id`, `firstName`, `lastName`, `email`, `identificationNumber` | One-to-Many → `BankAccountEntity` |
| `BankAccountEntity` | `banking_core_account` | `id`, `number`, `type` (SAVINGS_ACCOUNT), `status` (ACTIVE), `availableBalance`, `actualBalance` | Many-to-One → `UserEntity` |
| `TransactionEntity` | `banking_core_transaction` | `id`, `amount`, `transactionType` (FUND_TRANSFER/UTILITY_PAYMENT), `referenceNumber`, `transactionId` | One-to-One → `BankAccountEntity` (cascade ALL) |
| `UtilityAccountEntity` | `banking_core_utility_account` | `id`, `number`, `providerName` | None |

#### Fund Transfer Service (`banking_core_fund_transfer_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `FundTransferEntity` | `fund_transfer` | `id`, `transactionReference`, `fromAccount`, `toAccount`, `amount`, `status` (PENDING/SUCCESS) | Extends `AuditAware` (createdDate, createdBy, modifiedDate, modifiedBy, version) |

#### User Service (`banking_core_user_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UserEntity` | `user` | `id`, `authId` (Keycloak UUID), `identification` (NIC), `status` (PENDING/APPROVED) | Extends `AuditAware` |

#### Utility Payment Service (`banking_core_utility_payment_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UtilityPaymentEntity` | `utility_payment` | `id`, `providerId`, `amount`, `referenceNumber`, `account`, `transactionId`, `status` (PROCESSING/SUCCESS) | Extends `AuditAware` |

### 2.4 Enumerations

| Enum | Values | Used In |
|---|---|---|
| `AccountType` | `SAVINGS_ACCOUNT` | `BankAccountEntity.type` |
| `AccountStatus` | `ACTIVE` | `BankAccountEntity.status` |
| `TransactionType` | `FUND_TRANSFER`, `UTILITY_PAYMENT` | `TransactionEntity.transactionType` |
| `TransactionStatus` | `PENDING`, `SUCCESS`, `PROCESSING` | `FundTransferEntity.status`, `UtilityPaymentEntity.status` |
| `Status` | `PENDING`, `APPROVED` | User-service `UserEntity.status` |

### 2.5 Seed Data

The Flyway migration `V1.0.20210427174721__temp_data.sql` inserts:
- **4 users**: Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon
- **14 bank accounts**: Various SAVINGS_ACCOUNT entries with balances ranging from 12,000 to 889,000.33
- **6 utility providers**: VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO

---

## 3. API Surface Map

### 3.1 Gateway Routing Prefixes

| Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | — | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer at ledger level | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment at ledger level | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number (NIC) | — | `User { id, firstName, lastName, email, identificationNumber }` |
| `GET` | `/api/v1/user` | List users (paginated via Spring `Pageable`) | — | `List<User>` |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer (orchestrates via core-banking) | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

### 3.4 User Service (port 8083)

| Method | Endpoint | Description | Auth | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | **Public** (no JWT required) | `{ email, identification, password }` | `User { id, authId, identification, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (admin approval) | JWT | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | JWT | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | JWT | — | `User` |

### 3.5 Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment (orchestrates via core-banking) | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

All services expose Spring Boot Actuator endpoints (enabled via `spring-boot-starter-actuator`). These are permitted without authentication at the gateway level:
- `/actuator/**`, `/user/actuator/**`, `/fund-transfer/actuator/**`, `/banking-core/actuator/**`, `/utility-payment/actuator/**`

### 3.7 OpenAPI/Swagger

All business services include `springdoc-openapi-starter-webflux-ui:2.1.0` dependency for Swagger UI. **Note**: This is the WebFlux variant, which is incorrect for the MVC-based services (core-banking, fund-transfer, user, utility-payment). Only the API Gateway (which uses WebFlux) should use this dependency.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

```
Client → Gateway → fund-transfer-service → core-banking-service
```

1. **fund-transfer-service** receives `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. Creates a `FundTransferEntity` with status `PENDING` and saves to its local database.
3. Makes a synchronous Feign call to `core-banking-service` `POST /api/v1/transaction/fund-transfer`.
4. **core-banking-service** `TransactionService.fundTransfer()`:
   - Looks up both from/to `BankAccount` DTOs via `AccountService`.
   - Validates the sender has sufficient `actualBalance` (must be ≥ amount and ≥ 0).
   - Calls `internalFundTransfer()`:
     - Debits sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (**BUG**: double deduction).
     - Saves debit transaction record.
     - Credits receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount` (**BUG**: double addition).
     - Saves credit transaction record.
   - Returns `{ message, transactionId }`.
5. **fund-transfer-service** updates the entity with `transactionReference` and status `SUCCESS`.

**Known Bug — Double Balance Calculation**: In `TransactionService.internalFundTransfer()`, the `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been debited. This causes a double subtraction on the sender and double addition on the receiver. The same bug exists in `utilPayment()`.

### 4.2 Utility Payment Flow

```
Client → Gateway → utility-payment-service → core-banking-service
```

1. **utility-payment-service** receives `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. Creates a `UtilityPaymentEntity` with status `PROCESSING` and saves to its local database.
3. Makes a synchronous Feign call to `core-banking-service` `POST /api/v1/transaction/util-payment`.
4. **core-banking-service** `TransactionService.utilPayment()`:
   - Looks up the bank account and validates balance.
   - Looks up the utility account by provider ID.
   - Debits the bank account (same double-deduction bug as fund transfer).
   - Saves transaction record with type `UTILITY_PAYMENT`.
   - Returns `{ message, transactionId }`.
5. **utility-payment-service** updates the entity with `transactionId` and status `SUCCESS`.

### 4.3 User Registration Flow

```
Client → Gateway → user-service → [core-banking-service, Keycloak]
```

1. **user-service** receives `POST /api/v1/bank-users/register` (public endpoint) with `{ email, identification, password }`.
2. Checks Keycloak for existing user with same email; throws `UserAlreadyRegisteredException` if found.
3. Calls `core-banking-service` `GET /api/v1/user/{identification}` via Feign to verify the user exists in the banking core.
4. Validates the email matches the core banking user's email; throws `InvalidEmailException` if mismatched.
5. Creates a Keycloak `UserRepresentation` with `emailVerified=false`, `enabled=false`, and the provided password.
6. If Keycloak returns HTTP 201:
   - Reads back the created Keycloak user to get the `authId`.
   - Saves a local `UserEntity` with status `PENDING`.
7. If user not found in core-banking, throws `InvalidBankingUserException`.

### 4.4 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. Reads the Keycloak user by `authId`.
3. Sets `enabled=true` and `emailVerified=true` on the Keycloak user.
4. Updates local entity status to `APPROVED`.

### 4.5 Business Rules Summary

| Rule | Implementation | Service |
|---|---|---|
| Sufficient balance check | `actualBalance >= 0 && actualBalance >= amount` | core-banking-service |
| Email uniqueness | Keycloak email search before registration | user-service |
| Email-NIC matching | Core banking user email must match registration email | user-service |
| User must exist in core | Feign call to core-banking validates identification | user-service |
| New users disabled | Keycloak user created with `enabled=false` | user-service |
| Admin approval required | PATCH endpoint toggles `enabled` in Keycloak | user-service |

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **URL** | `http://keycloak_web:8080` (Docker) / configured via `app.config.keycloak.server-url` |
| **Integration Type** | Keycloak Admin Client SDK (v24.0.4) in user-service; JWT validation at Gateway |
| **Authentication Flow** | OAuth2 Resource Server with JWT — Gateway validates tokens using JWK Set URI |
| **Realm Configuration** | Imported via volume mount from `docker-compose/keycloak/` |
| **Client Type** | Service account (`client_credentials` grant) for programmatic user management |
| **Gateway Security** | `SecurityWebFilterChain` permits `/user/api/v1/bank-users/register` and all `/actuator/**`; everything else requires authentication |
| **User Propagation** | Gateway extracts `Principal.getName()` and forwards as `X-Auth-Id` header to downstream services |

### 5.2 RabbitMQ

**Status**: **Not implemented**. Mentioned in README documentation as part of the notification service architecture, but no RabbitMQ dependency, configuration, or message producer/consumer exists in the codebase. The notification service is listed as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | 3 |
| **URL** | `http://172.25.0.12:9411` |
| **Integration** | Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) |
| **Coverage** | All 4 business services + API Gateway include tracing dependencies |
| **Feign Tracing** | `feign-micrometer` dependency included for propagating trace context across Feign calls |

### 5.4 Database Connections

| Service | Schema | Driver | Migration |
|---|---|---|---|
| core-banking-service | `banking_core_service` | MySQL Connector/J 8.4.0 | Flyway 10.12.0 |
| fund-transfer-service | `banking_core_fund_transfer_service` | MySQL Connector/J 8.4.0 | Hibernate auto-DDL |
| user-service | `banking_core_user_service` | MySQL Connector/J 8.4.0 | Hibernate auto-DDL |
| utility-payment-service | `banking_core_utility_payment_service` | MySQL Connector/J 8.4.0 | Hibernate auto-DDL |

All services connect to the same MySQL 8.4 instance (`mysql_core_db` / 172.25.0.9:3306) with user `javatodev_development`.

### 5.5 Spring Cloud Config Server

| Aspect | Detail |
|---|---|
| **Git Repository** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration/` |
| **Client Binding** | All business services use `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap` with `bootstrap.yml` pointing to `http://localhost:8090` (dev) or `http://internet-banking-config-server:8090` (docker) |
| **Profiles** | `dev` (local development), `docker` (containerized) |

### 5.6 Service Discovery (Eureka)

| Aspect | Detail |
|---|---|
| **Server** | `internet-banking-service-registry` on port 8081 |
| **Clients** | All 5 other services register as Eureka clients |
| **Feign Integration** | Feign clients reference services by Eureka name (e.g., `@FeignClient(value = "core-banking-service")`) |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool**: Gradle (each service has its own independent `build.gradle` — no multi-module root project)
- **Gradle Wrapper**: Each service includes `gradlew` / `gradlew.bat`
- **Java**: 21 (`sourceCompatibility = '21'`)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Common plugins**: `java`, `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`

### 6.2 Build Command

Per-service build (from service root directory):
```bash
./gradlew clean build
```

### 6.3 Docker Images

Each service has its own `Dockerfile` using:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Image naming convention: `javatodev/<service-name>` (e.g., `javatodev/core-banking-service`).

### 6.4 Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |
| `docker-compose.yml` | Full stack: All 6 services + all infrastructure |

Both use Compose file version `3.6` and define the `javatodev_ib_network` bridge network with subnet `172.25.0.0/16`.

### 6.5 Startup Ordering

Services that depend on MySQL, Config Server, and Service Registry use `wait-for-it.sh` in their entrypoints:

```
wait-for-it.sh internet-banking-service-registry:8081 --timeout=50
wait-for-it.sh internet-banking-config-server:8090 --timeout=50
wait-for-it.sh mysql_core_db:3306 --timeout=50
```

### 6.6 CI/CD Pipeline

**No CI/CD pipeline** is configured in the repository. The `.github/` directory exists but contains no workflow files. Builds and deployments are manual.

### 6.7 Testing Infrastructure

- **Test database**: H2 in-memory (included as test dependency in all services)
- **Test framework**: JUnit 5 via `spring-boot-starter-test`
- **Test configuration**: `src/test/resources/application.yml` with H2 datasource, Flyway disabled
- **Current coverage**: Only `core-banking-service` has meaningful tests (3 service-layer test classes with 19 test methods). All other services have only empty `ApplicationTests` classes.

### 6.8 API Testing

A Postman collection is available in `postman_collection/` and via the [Postman Collection link](https://www.postman.com/javatodev-api/workspace/javatodev-api-collections/folder/24962357-0fecb63e-fa48-4a0d-91ba-6b7fdc5ddebd). Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`.
