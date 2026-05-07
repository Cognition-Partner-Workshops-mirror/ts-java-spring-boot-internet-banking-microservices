# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

An internet banking platform built with **Java 21** and **Spring Boot 3.2.4**, organised as six independently deployable microservices communicating via synchronous REST (OpenFeign) and discovered through Netflix Eureka. All external traffic enters through a Spring Cloud API Gateway that enforces OAuth 2.0 / Keycloak authentication.

### 1.2 Service Inventory

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | **core-banking-service** | 8092 | Dummy banking core: accounts, users, transactions, fund-transfer & utility-payment processing |
| 2 | **internet-banking-user-service** | 8083 | User registration, approval workflow, Keycloak identity management |
| 3 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers; delegates to core-banking-service via Feign |
| 4 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payments; delegates to core-banking-service via Feign |
| 5 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; JWT validation, route definitions, `X-Auth-Id` header injection |
| 6 | **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| 7 | **internet-banking-config-server** | 8090 | Spring Cloud Config server; Git-backed centralised configuration |

### 1.3 Communication Patterns

```
                        ┌──────────────────────────────────────────────────────────┐
 Client ──► API Gateway ─┤  /user/**           ──► internet-banking-user-service   │
            (8082)       │  /fund-transfer/**   ──► fund-transfer-service           │
            JWT auth     │  /payment/**         ──► utility-payment-service         │
                        │  /banking-core/**    ──► core-banking-service            │
                        └──────────────────────────────────────────────────────────┘
                                      │
                                      │ Eureka Discovery
                                      ▼
                            Service Registry (8081)

  fund-transfer-service ──Feign──► core-banking-service
  utility-payment-service ──Feign──► core-banking-service
  user-service ──Feign──► core-banking-service
```

- **Synchronous REST via OpenFeign**: All inter-service calls use Feign clients resolved through Eureka.
- **No asynchronous messaging is implemented**: RabbitMQ is referenced in the README as a future integration for a Notification service, but no AMQP dependencies or producers/consumers exist in the codebase.
- **Header propagation**: The API Gateway injects an `X-Auth-Id` header (extracted from the JWT principal) into every downstream request. Downstream services read this via `AppAuthUserFilter`.

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|-----------|----------------|---------|
| **MySQL 8.4** | `mysql:8.4.0` (custom Dockerfile) | Persistent store for all four business services (4 databases) |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | OAuth 2.0 / OpenID Connect identity provider |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing database |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing collector |
| **Spring Cloud Config** | Self-built | Centralised configuration via Git repository |
| **Eureka** | Self-built | Service registration and discovery |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (Database: `banking_core_service`)

#### Tables

**`banking_core_user`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

**`banking_core_account`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

**`banking_core_utility_account`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON, SINGTEL |

**`banking_core_transaction`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID correlation identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### Entity Relationships
```
banking_core_user  1 ──── N  banking_core_account
banking_core_account  1 ──── 1  banking_core_transaction (via CascadeType.ALL)
```

#### Schema Management
- **Flyway** manages schema migrations (3 migration files in `db/migration/`).
- Seed data includes 4 users, 14 bank accounts, and 6 utility providers.

### 2.2 internet-banking-user-service (Database: `banking_core_user_service`)

**`user`** (managed by JPA/Hibernate `ddl-auto`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC from core-banking |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_at` | TIMESTAMP | Audit field (via `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit field (via `AuditAware`) |
| `created_by` | VARCHAR | Audit field |

### 2.3 internet-banking-fund-transfer-service (Database: `banking_core_fund_transfer_service`)

**`fund_transfer`** (managed by JPA/Hibernate `ddl-auto`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core-banking response |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_at` / `updated_at` / `created_by` | | Audit fields |

### 2.4 internet-banking-utility-payment-service (Database: `banking_core_utility_payment_service`)

**`utility_payment`** (managed by JPA/Hibernate `ddl-auto`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking response |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` / `updated_at` / `created_by` | | Audit fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests flow through the gateway at port **8082**. Route prefixes strip the service prefix before forwarding:

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` or `/core/**` | core-banking-service |

### 3.2 core-banking-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (number, type, status, actualBalance, availableBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer at core level | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment at core level | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | `?page=&size=&sort=` | `List<User>` |

### 3.3 internet-banking-user-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new banking user (public) | `{ password, email, identification }` | `User` (id, authId, identification, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | `?page=&size=` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 internet-banking-fund-transfer-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | `?page=&size=` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | `?page=&size=` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Health check (each service) |
| `GET` | `/actuator/**` | Spring Boot Actuator endpoints |
| `GET` | `http://localhost:8081/` | Eureka dashboard |
| `GET` | `http://localhost:9411/` | Zipkin tracing UI |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (user-service)
1. Check if email already exists in Keycloak (duplicate check).
2. Fetch user from core-banking-service by NIC identification.
3. Validate that the email matches the core banking record.
4. Create the user in Keycloak (disabled, email unverified).
5. Save user locally with status `PENDING`.
6. Admin manually approves via `PATCH /update/{id}` with `status=APPROVED`, which enables the Keycloak account and marks email as verified.

### 4.2 Fund Transfer Flow (fund-transfer-service → core-banking-service)
1. Persist transfer record with status `PENDING`.
2. Delegate to core-banking-service via Feign (`POST /api/v1/transaction/fund-transfer`).
3. Core banking validates source account balance (`actualBalance >= amount`).
4. Debit source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
5. Credit destination account.
6. Record two `TransactionEntity` rows (debit and credit) linked by the same `transactionId`.
7. Fund-transfer-service updates local record to `SUCCESS` with the transaction reference.

### 4.3 Utility Payment Flow (utility-payment-service → core-banking-service)
1. Persist payment record with status `PROCESSING`.
2. Delegate to core-banking-service via Feign (`POST /api/v1/transaction/util-payment`).
3. Core banking validates source account balance.
4. Debit source account.
5. Record a `TransactionEntity` row for the debit.
6. Utility-payment-service updates local record to `SUCCESS`.

### 4.4 Balance Validation Rules
- `actualBalance` must be >= 0.
- `actualBalance` must be >= transfer/payment amount.
- Violation throws `InsufficientFundsException` (HTTP 400).

### 4.5 Authentication & Authorization
- API Gateway validates JWT tokens issued by Keycloak.
- Registration endpoint (`/user/api/v1/bank-users/register`) and all actuator endpoints are publicly accessible.
- All other endpoints require a valid Bearer token.
- The authenticated user's principal name is injected as `X-Auth-Id` header to downstream services.

---

## 5. Integration Points

### 5.1 Keycloak (OAuth 2.0 / OIDC)
- **Version**: 23.0.7
- **Realm**: Imported from `docker-compose/keycloak/realm-export.json`
- **Client credentials grant**: user-service uses `keycloak-admin-client:24.0.4` to manage users programmatically.
- **JWT validation**: API Gateway uses `spring-boot-starter-oauth2-resource-server` with JWK set URI.
- **Configuration properties**: `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (sourced from config server).
- **Singleton pattern**: `KeycloakProperties` maintains a static singleton of the `Keycloak` admin client (not thread-safe initialization).

### 5.2 RabbitMQ
- **Status**: Referenced in README documentation only. No AMQP dependencies, producers, or consumers are present in any service. The Notification service is listed as "PENDING Development".

### 5.3 Zipkin (Distributed Tracing)
- **Version**: Zipkin 3 (`openzipkin/zipkin:3`)
- **Integration**: All services (except config-server and service-registry) include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration**: Tracing configuration is expected to be provided via the Spring Cloud Config server.

### 5.4 Database Connections
- **MySQL 8.4**: Single MySQL instance hosts 4 databases:
  - `banking_core_service` (core-banking-service, Flyway-managed)
  - `banking_core_user_service` (user-service, JPA ddl-auto)
  - `banking_core_fund_transfer_service` (fund-transfer-service, JPA ddl-auto)
  - `banking_core_utility_payment_service` (utility-payment-service, JPA ddl-auto)
- **PostgreSQL 15**: Dedicated to Keycloak (database: `keycloak`).
- **H2**: Used in test profiles for core-banking-service.

### 5.5 Spring Cloud Config Server
- **Git repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search path**: `configuration`
- All services use `bootstrap.yml` / `bootstrap-docker.yml` to fetch configuration from the config server at startup.

### 5.6 Eureka Service Registry
- All business services register as Eureka clients.
- Feign clients use Eureka-resolved service names (e.g., `core-banking-service`) for inter-service calls.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System
- **Gradle 8.6** with individual `build.gradle` per service (no multi-project build).
- Each service is a standalone Spring Boot application with its own `settings.gradle`.
- `com.gorylenko.gradle-git-properties` plugin used in 5 of 7 services for Git info endpoints.

### 6.2 Key Dependencies

| Dependency | Version | Used By |
|-----------|---------|---------|
| Spring Boot | 3.2.4 | All services |
| Spring Cloud | 2023.0.0 | All services |
| Lombok | Managed by Spring Boot BOM | 4 business services |
| Flyway | 10.12.0 | core-banking-service only |
| MySQL Connector | 8.4.0 | 4 business services |
| springdoc-openapi | 2.1.0 (webflux-ui) | 4 business services |
| Keycloak Admin Client | 24.0.4 | user-service only |
| feign-okhttp | 13.2.1 | user-service only |

### 6.3 Docker Compose Deployment
- **`docker-compose.yml`**: Full stack (infrastructure + all 7 services).
- **`docker-compose-support-apps.yml`**: Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, config-server, service-registry).
- Custom MySQL Dockerfile creates the 4 application databases and a development user via `privileges.sql`.
- Services use `wait-for-it.sh` to handle startup ordering (wait for service-registry, config-server, and MySQL).
- Docker profile activated via `-Dspring.profiles.active=docker`.
- Static IP assignment on a custom bridge network (`172.25.0.0/16`).

### 6.4 CI/CD
- No CI/CD pipeline files exist in the repository (`.github/` contains only `FUNDING.yml`).
- No Dockerfile definitions for the application services (relies on pre-built images published to Docker Hub under `javatodev/*`).
- No Kubernetes manifests despite Kubernetes being listed in the technology stack.
