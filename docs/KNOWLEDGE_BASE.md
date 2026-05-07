# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built on a microservices architecture using Spring Cloud 2023.0.0. The system models a simplified internet banking platform with user registration, fund transfers, and utility bill payments.

### 1.2 Microservices Inventory

| Service | Port | Description |
|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server; externalizes configuration from a Git repo |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point, routes requests, enforces OAuth2/JWT security |
| **internet-banking-user-service** | 8083 | User registration, approval, and profile management; integrates with Keycloak |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between bank accounts via the core banking service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via the core banking service |
| **core-banking-service** | 8092 | Backend-of-record for accounts, users, transactions, and balance management |

> **Notification Service** is listed in the README as planned but has **not been implemented**.

### 1.3 Communication Patterns

```
                        +-----------------------+
   Client  ──────────►  | API Gateway (8082)    |  ◄── OAuth2 / JWT (Keycloak)
                        +-----------+-----------+
                                    |
                  ┌─────────────────┼─────────────────┐
                  ▼                 ▼                  ▼
         User Service       Fund Transfer       Utility Payment
           (8083)           Service (8084)       Service (8085)
              |                   |                    |
              |   OpenFeign       |   OpenFeign        |   OpenFeign
              ▼                   ▼                    ▼
         Core Banking Service (8092)
              |
              ▼
           MySQL DB
```

- **Synchronous REST (OpenFeign):** All inter-service calls use Spring Cloud OpenFeign clients routed through Eureka service discovery. The API Gateway proxies external requests; downstream services call `core-banking-service` directly via Feign.
- **Service Discovery:** Netflix Eureka. All services register with the registry; Feign clients resolve service names through it.
- **Centralized Configuration:** Spring Cloud Config Server pulls configuration from a remote Git repository (`internet-banking-microservices-configurations`).
- **Authentication Propagation:** The API Gateway extracts the JWT principal name and injects it as an `X-Auth-Id` HTTP header. Downstream services read this header via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder` for audit purposes.
- **Asynchronous Messaging (RabbitMQ):** Referenced in the README for notifications but **not yet implemented** in the codebase.

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|---|---|---|
| **MySQL** | Custom build (docker-compose/mysql) | Primary data store for all four business services (separate databases) |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing database |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity and access management (OAuth2 / OpenID Connect) |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing collector and UI |
| **RabbitMQ** | Referenced in README | Message broker (not yet deployed in Docker Compose) |
| **Eureka** | Embedded (service-registry) | Service discovery |
| **Spring Cloud Config** | Embedded (config-server) | Externalized configuration |

### 1.5 Database Topology

Four MySQL databases are provisioned via `docker-compose/mysql/privileges.sql`:

| Database | Owning Service |
|---|---|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

All databases share a single MySQL instance and a single database user (`javatodev_development`).

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (Flyway-managed)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID (e.g., NIC) |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, AIRTEL |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Signed amount (negative = debit) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID correlating debit/credit legs |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | |

### 2.2 Internet Banking User Service (JPA auto-generated)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Links to core banking user's NIC |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit field (via `AuditAware`) |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Internet Banking Fund Transfer Service (JPA auto-generated)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| Audit columns | | Via `AuditAware` superclass |

### 2.4 Internet Banking Utility Payment Service (JPA auto-generated)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| Audit columns | | Via `AuditAware` superclass |

---

## 3. API Surface Map

All business APIs are accessed through the API Gateway at port **8082**. The gateway prefixes route to each service:

| Gateway Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.1 Core Banking Service (`/api/v1`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | - | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### 3.2 Internet Banking User Service (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.3 Internet Banking Fund Transfer Service (`/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.4 Internet Banking Utility Payment Service (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.5 Infrastructure Endpoints

| Endpoint | Service | Description |
|---|---|---|
| `/actuator/**` | All services | Spring Boot Actuator (health, info, metrics) |
| `GET /eureka` | Service Registry (8081) | Eureka dashboard |
| `GET /` | Config Server (8090) | Spring Cloud Config endpoints |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client POSTs to `/user/api/v1/bank-users/register` (permitted without JWT).
2. **User Service** checks Keycloak for existing email registration; rejects duplicates.
3. Calls **Core Banking Service** via Feign to validate the identification number exists in the banking core.
4. Validates the email matches the core banking record.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with status `PENDING`.
7. An admin later approves via `PATCH /update/{id}` with `{status: "APPROVED"}`, which enables the Keycloak user and sets email as verified.

### 4.2 Fund Transfer Flow

1. Client POSTs to `/fund-transfer/api/v1/transfer` with JWT.
2. **Fund Transfer Service** persists a `FundTransferEntity` with status `PENDING`.
3. Calls **Core Banking Service** `/api/v1/transaction/fund-transfer` via Feign.
4. **Core Banking** validates both accounts exist, checks sufficient balance, then:
   - Debits the source account (subtracts from `actualBalance` and `availableBalance`).
   - Credits the destination account (adds to `actualBalance` and `availableBalance`).
   - Records two `TransactionEntity` rows (debit + credit) with the same `transactionId`.
5. Returns `transactionId` to Fund Transfer Service.
6. Fund Transfer Service updates its entity to `SUCCESS` with the transaction reference.

### 4.3 Utility Payment Flow

1. Client POSTs to `/utility-payment/api/v1/utility-payment` with JWT.
2. **Utility Payment Service** persists a `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls **Core Banking Service** `/api/v1/transaction/util-payment` via Feign.
4. **Core Banking** validates the source account, checks sufficient balance, looks up the utility provider, then:
   - Debits the source account.
   - Records a `TransactionEntity` with type `UTILITY_PAYMENT`.
5. Returns `transactionId` to Utility Payment Service.
6. Utility Payment Service updates its entity to `SUCCESS`.

### 4.4 Balance Validation Rule

```java
if (actualBalance < 0 || actualBalance < requestedAmount) {
    throw InsufficientFundsException
}
```

No minimum balance enforcement, no daily limits, no transaction amount caps.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Protocol:** OAuth2 / OpenID Connect
- **Integration:** The User Service uses `keycloak-admin-client:24.0.4` to programmatically create, read, and update users in Keycloak via its Admin REST API.
- **API Gateway** validates JWTs using `spring-boot-starter-oauth2-resource-server` against Keycloak's JWK Set URI.
- **Configuration:** Keycloak realm, client ID, and client secret are externalized via Spring Cloud Config (`app.config.keycloak.*`).
- **Realm data** is imported on startup via a volume mount (`docker-compose/keycloak/`).

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in the README for notification delivery but **not implemented** in the codebase.
- No RabbitMQ dependency in any `build.gradle`, no Docker Compose service definition, no publisher/consumer code.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` for automatic trace propagation via Brave/B3.
- Feign calls include `feign-micrometer` for tracing instrumented Feign requests.
- Zipkin server runs at port 9411 in Docker Compose.

### 5.4 MySQL Database

- **Version:** Custom Docker image built from `docker-compose/mysql/`
- **Connection:** All four business services connect to the same MySQL instance but use separate databases.
- **Driver:** `com.mysql:mysql-connector-j:8.4.0`
- **Migrations:** Core Banking Service uses Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`). Other services rely on JPA `ddl-auto`.
- **Credentials:** Hardcoded in Docker Compose (`woVERANKliGharym` root password, `oPItyPticIAt` app user password).

### 5.5 Spring Cloud Config Server

- Pulls configuration from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, search path: `configuration/`
- All services bootstrap from `http://localhost:8090` (or the Docker network equivalent).

### 5.6 Netflix Eureka (Service Discovery)

- Single-node Eureka server at port 8081.
- All services register as Eureka clients and resolve inter-service calls via service names.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (per-service `build.gradle`, no multi-project root build).
- Each service is an independent Gradle project with its own `gradlew` wrapper.
- Java 21 (`sourceCompatibility = '21'`), Spring Boot 3.2.4.
- Common plugins: `spring-boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`.

### 6.2 Docker

- Each service has its own `Dockerfile` using `eclipse-temurin:21.0.2_13-jre-alpine`.
- Pattern: copy fat JAR as `app.jar`, copy `wait-for-it.sh` for startup ordering, expose service port.
- Services use `-Dspring.profiles.active=docker` to activate Docker-specific configuration (bootstrap-docker.yml).

### 6.3 Docker Compose

Two Compose files in `docker-compose/`:

| File | Services |
|---|---|
| `docker-compose.yml` | Full stack: Zipkin, Keycloak + Postgres, MySQL, Config Server, Service Registry, API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak + Postgres, MySQL, Config Server, Service Registry |

- Custom bridge network `javatodev_ib_network` with static IPs (`172.25.0.0/16`).
- `wait-for-it.sh` scripts enforce startup ordering (registry -> config server -> MySQL -> app).
- Named volumes for MySQL and PostgreSQL data persistence.

### 6.4 CI/CD

- **No CI/CD pipeline** is present in the repository (`.github/` only contains `FUNDING.yml`).
- No GitHub Actions workflows, no Jenkinsfile, no GitLab CI configuration.
- Kubernetes is mentioned in the README tech stack but no K8s manifests exist in the repository.

### 6.5 Testing

- **Test framework:** JUnit 5 (`useJUnitPlatform()`) with Mockito.
- **Test database:** H2 in-memory (via `com.h2database:h2:2.2.224` test dependency).
- **Coverage:** Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services have only empty Spring Boot context-load tests.
