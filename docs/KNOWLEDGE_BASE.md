# Internet Banking Microservices — Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

A containerized **Internet Banking** platform built with **Java 21**, **Spring Boot 3.2.4**, and **Spring Cloud 2023.0.0**. The system is decomposed into six microservices communicating synchronously via REST (OpenFeign) with Eureka-based service discovery.

### 1.2 Service Inventory

| Service | Port | Role | Key Dependencies |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Edge router, OAuth2/JWT enforcement via Keycloak | Spring Cloud Gateway, OAuth2 Resource Server, WebFlux |
| `internet-banking-service-registry` | 8081 | Service discovery (Netflix Eureka Server) | Eureka Server, Actuator |
| `internet-banking-config-server` | 8090 | Externalized configuration from GitHub repo | Spring Cloud Config Server |
| `core-banking-service` | 8092 | System of record — users, accounts, transactions, utility accounts | Spring Data JPA, Flyway, MySQL, Eureka Client |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates account-to-account money transfers | OpenFeign, Spring Data JPA, MySQL, Eureka Client |
| `internet-banking-user-service` | 8083 | User registration & profile management via Keycloak | OpenFeign, Keycloak Admin Client 24.0.4, Spring Data JPA, MySQL |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates bill/utility payments | OpenFeign, Spring Data JPA, MySQL, Eureka Client |

### 1.3 Communication Patterns

- **Synchronous REST** — All inter-service calls use Spring Cloud OpenFeign clients resolved through Eureka.
- **No async messaging** — RabbitMQ is mentioned in the README for notifications but is not implemented in code.
- **Gateway → Downstream** — The API Gateway adds an `X-Auth-Id` header (JWT subject) to every proxied request. Downstream services read this header via `AppAuthUserFilter` servlet filter.

### 1.4 High-Level Request Flow

```
Client → API Gateway (JWT validation) → Eureka (resolve target) → Target Service → (Feign) → Core Banking Service → MySQL
```

---

## 2. Data Models

### 2.1 Database Topology

All four MySQL schemas reside on a single **MySQL 8.4** instance. The database user `javatodev_development` has broad privileges across all schemas.

| Schema | Owning Service | Migration Strategy |
|---|---|---|
| `banking_core_service` | core-banking-service | Flyway (3 migration files) |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service | JPA auto-DDL (Hibernate) |
| `banking_core_user_service` | internet-banking-user-service | JPA auto-DDL (Hibernate) |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service | JPA auto-DDL (Hibernate) |

### 2.2 Entity Relationship Diagrams

#### core-banking-service (Schema: `banking_core_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UserEntity` | `banking_core_user` | `id` (PK, auto), `firstName`, `lastName`, `email`, `identificationNumber` | One-to-Many → `BankAccountEntity` |
| `BankAccountEntity` | `banking_core_account` | `id` (PK, auto), `number`, `type` (enum), `status` (enum), `availableBalance`, `actualBalance` | Many-to-One → `UserEntity` (`user_id` FK) |
| `TransactionEntity` | `banking_core_transaction` | `id` (PK, auto), `amount`, `transactionType` (enum), `referenceNumber`, `transactionId` | One-to-One → `BankAccountEntity` (`account_id` FK) |
| `UtilityAccountEntity` | `banking_core_utility_account` | `id` (PK, auto), `number`, `providerName` | None |

**Enums:** `AccountType` (SAVINGS_ACCOUNT, etc.), `AccountStatus` (ACTIVE, etc.), `TransactionType` (FUND_TRANSFER, UTILITY_PAYMENT)

#### internet-banking-fund-transfer-service (Schema: `banking_core_fund_transfer_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `FundTransferEntity` | `fund_transfer` | `id` (PK, auto), `transactionReference`, `fromAccount`, `toAccount`, `amount`, `status` (enum) | Extends `AuditAware` (createdDate, createdBy, modifiedDate, modifiedBy, version) |

**Enums:** `TransactionStatus` (PENDING, SUCCESS)

#### internet-banking-user-service (Schema: `banking_core_user_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UserEntity` | `user` | `id` (PK, auto), `authId` (Keycloak UUID), `identification` (NIC), `status` (enum) | Extends `AuditAware` |

**Enums:** `Status` (PENDING, APPROVED)

#### internet-banking-utility-payment-service (Schema: `banking_core_utility_payment_service`)

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| `UtilityPaymentEntity` | `utility_payment` | `id` (PK, auto), `providerId`, `amount`, `referenceNumber`, `account`, `transactionId`, `status` (enum) | Extends `AuditAware` |

**Enums:** `TransactionStatus` (PROCESSING, SUCCESS)

### 2.3 Seed Data

Flyway migration `V1.0.20210427174721__temp_data.sql` seeds:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility provider accounts (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)

---

## 3. API Surface Map

### 3.1 Gateway Routes

All client traffic enters through the API Gateway (port 8082). Routes are defined in externalized configuration:

| Path Prefix | Target Service | Auth Required |
|---|---|---|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/payment/**` | internet-banking-utility-payment-service | Yes |
| `/core/**` | core-banking-service | Yes |
| `/actuator/**` | Various | No (permit all) |

### 3.2 Core Banking Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` DTO |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` DTO |
| `POST` | `/api/v1/transaction/fund-transfer` | Process internal fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment debit | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User` DTO |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |

### 3.3 Fund Transfer Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

### 3.4 User Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (PUBLIC) | `{ email, identification, password }` | `User` DTO |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{ status }` | `User` DTO |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` DTO |

### 3.5 Utility Payment Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

---

## 4. Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives `POST /api/v1/transfer`
2. Creates `FundTransferEntity` with status `PENDING`, saves to local DB
3. Calls **Core Banking Service** via Feign: `POST /api/v1/transaction/fund-transfer`
4. Core Banking validates source account balance (`actualBalance >= amount`)
5. Core Banking debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount` ⚠️ double deduction bug)
6. Core Banking credits target account (`actualBalance += amount`, `availableBalance = actualBalance + amount` ⚠️ double addition bug)
7. Core Banking saves two `TransactionEntity` records (debit + credit) and returns `transactionId`
8. Fund Transfer Service updates local entity to `SUCCESS` with `transactionReference`

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives `POST /api/v1/utility-payment`
2. Creates `UtilityPaymentEntity` with status `PROCESSING`, saves to local DB
3. Calls **Core Banking Service** via Feign: `POST /api/v1/transaction/util-payment`
4. Core Banking validates account balance and utility provider existence
5. Core Banking debits source account (⚠️ same double-deduction bug)
6. Core Banking saves `TransactionEntity` record and returns `transactionId`
7. Utility Payment Service updates local entity to `SUCCESS` with `transactionId`

### 4.3 User Registration Flow

1. **User Service** receives `POST /api/v1/bank-users/register`
2. Checks Keycloak — if email already registered, throws `UserAlreadyRegisteredException`
3. Calls **Core Banking Service** via Feign: `GET /api/v1/user/{identification}` to verify user exists
4. Validates email matches core banking record
5. Creates Keycloak user (disabled, unverified) with provided password
6. If Keycloak returns 201, saves `UserEntity` with status `PENDING` and Keycloak `authId`
7. Admin approves via `PATCH /api/v1/bank-users/update/{id}` with `status: APPROVED`
8. Approval enables Keycloak user and sets `emailVerified = true`

### 4.4 Known Business Logic Bugs

| Bug | Location | Impact |
|---|---|---|
| **Double balance deduction** | `TransactionService.internalFundTransfer()` lines 90-91 | `availableBalance` computed from already-debited `actualBalance`, causing 2× deduction on sender |
| **Double balance addition** | `TransactionService.internalFundTransfer()` lines 99-100 | Same pattern on receiver side — `availableBalance` gets 2× the credit |
| **Double deduction in utility payment** | `TransactionService.utilPayment()` lines 63-64 | Identical bug for utility payment debits |

---

## 5. Integration Points

### 5.1 Identity Provider — Keycloak

- **Version:** 23.0.7 (via Docker)
- **Realm:** `javatodev-internet-banking` (imported from JSON on startup)
- **Integration:** User Service uses `keycloak-admin-client:24.0.4` with `client_credentials` grant
- **Backing store:** PostgreSQL 15 (dedicated container)
- **Gateway auth:** OAuth2 Resource Server validates JWTs against Keycloak's JWKS endpoint

### 5.2 Distributed Tracing — Zipkin

- **Version:** Zipkin 3 (Docker image: `openzipkin/zipkin:3`)
- **Integration:** All services (except config-server and registry) include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Port:** 9411

### 5.3 Service Discovery — Eureka

- **Server:** `internet-banking-service-registry` (port 8081)
- **Clients:** All other five services register with Eureka
- **Feign resolution:** `@FeignClient(name = "core-banking-service")` resolved via Eureka

### 5.4 Configuration — Spring Cloud Config

- **Server:** `internet-banking-config-server` (port 8090)
- **Source:** GitHub repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`)
- **Clients:** All services use `bootstrap.yml` pointing to `http://localhost:8090` (overridden in Docker profile)

### 5.5 Database — MySQL 8.4

- Single MySQL instance hosting 4 schemas
- Docker container: `mysql_javatodev_app` at 172.25.0.9:3306
- Schema initialization: `privileges.sql` creates user and databases; Flyway handles core-banking tables

---

## 6. Build & Deployment Summary

### 6.1 Build System

- **Gradle 8.6** with per-service wrapper (`gradlew`) — no root multi-module build
- **No shared library** — common code (BaseMapper, AuditAware, GlobalExceptionHandler, AppAuthUserFilter) is duplicated across services
- Each service: `./gradlew clean build` → produces fat JAR in `build/libs/`

### 6.2 Dependencies Summary (per service)

| Dependency | core-banking | fund-transfer | user-service | utility-payment | gateway | config-server | registry |
|---|---|---|---|---|---|---|---|
| Spring Web (MVC) | ✓ | ✓ | ✓ | ✓ | — | — | ✓ |
| Spring WebFlux | — | — | — | — | ✓ | — | — |
| Spring Data JPA | ✓ | ✓ | ✓ | ✓ | — | — | — |
| MySQL Connector | ✓ | ✓ | ✓ | ✓ | — | — | — |
| Flyway | ✓ | — | — | — | — | — | — |
| OpenFeign | — | ✓ | ✓ | ✓ | — | — | — |
| Eureka Client | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| Eureka Server | — | — | — | — | — | — | ✓ |
| OAuth2/Security | — | — | — | — | ✓ | — | — |
| Keycloak Admin | — | — | ✓ | — | — | — | — |
| Zipkin/Micrometer | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| Actuator | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| springdoc-openapi | ✓ | ✓ | ✓ | ✓ | — | — | — |
| Lombok | ✓ | ✓ | ✓ | ✓ | — | — | — |
| H2 (test) | ✓ | ✓ | ✓ | ✓ | — | — | — |

### 6.3 Docker

- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- Each service has its own `Dockerfile`; all use `wait-for-it.sh` for startup ordering
- **Docker Compose** (`docker-compose/docker-compose.yml`): orchestrates all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin
- **Network:** `javatodev_ib_network` (172.25.0.0/16) with static IPs per container
- **Startup ordering:** `wait-for-it.sh` with 50-second timeout chains: registry → config-server → MySQL → service start

### 6.4 Container Map

| Container | Image | Static IP | Port |
|---|---|---|---|
| `openzipkin_server` | `openzipkin/zipkin:3` | 172.25.0.12 | 9411 |
| `keycloak_web` | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | 8080 |
| `keycloak_postgre_db` | `postgres:15` | 172.25.0.10 | 5432 (internal) |
| `mysql_javatodev_app` | Custom (MySQL 8.4) | 172.25.0.9 | 3306 |
| `internet-banking-config-server` | `javatodev/internet-banking-config-server` | 172.25.0.8 | 8090 |
| `internet-banking-service-registry` | `javatodev/internet-banking-service-registry` | 172.25.0.7 | 8081 |
| `internet-banking-api-gateway` | `javatodev/internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| `internet-banking-user-service` | `javatodev/internet-banking-user-service` | 172.25.0.5 | 8083 |
| `internet-banking-fund-transfer-service` | `javatodev/internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| `internet-banking-utility-payment-service` | `javatodev/internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| `core-banking-service` | `javatodev/core-banking-service` | 172.25.0.2 | 8092 |

### 6.5 CI/CD

- **No CI/CD pipeline** is configured in the repository (no GitHub Actions, Jenkins, etc.)
- `.github/FUNDING.yml` exists for sponsorship only
- Postman collection provided for manual API testing (`postman_collection/`)

### 6.6 Configuration Profiles

| Profile | Purpose | Config Source |
|---|---|---|
| `default` / `dev` | Local development | `bootstrap.yml` → Config Server at localhost:8090 |
| `docker` | Docker Compose deployment | Config Server with Docker-specific overrides |
| `test` | Unit tests | Local `src/test/resources/application.yml` with H2 in-memory DB |
