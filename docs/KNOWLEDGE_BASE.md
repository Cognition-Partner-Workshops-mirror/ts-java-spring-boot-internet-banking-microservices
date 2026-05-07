# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is an **Internet Banking** application built with **Java 21** and **Spring Boot 3.2.4** using a microservices architecture. The system models a simplified banking platform supporting user registration, fund transfers between accounts, and utility bill payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | `spring-cloud-starter-netflix-eureka-server` |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; serves externalized configuration from a Git repo | `spring-cloud-config-server` |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point, OAuth2/JWT security, request routing | `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security` |
| **core-banking-service** | 8092 | Core banking engine; manages users, accounts, and transaction processing (debit/credit) | `spring-boot-starter-data-jpa`, Flyway, MySQL |
| **internet-banking-user-service** | 8083 | Internet banking user registration and management; integrates with Keycloak for identity | `keycloak-admin-client`, OpenFeign, MySQL |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers; delegates to core-banking-service via Feign | OpenFeign, MySQL |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments; delegates to core-banking-service via Feign | OpenFeign, MySQL |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and is not implemented.

### 1.3 Communication Patterns

```
┌─────────────┐       ┌──────────────────────┐       ┌────────────────────────────┐
│   Client     │──────▶│  API Gateway (:8082)  │──────▶│  user-service (:8083)      │
│  (Browser /  │       │  (OAuth2 + JWT)       │       │  fund-transfer-svc (:8084) │
│   Postman)   │       │                      │       │  utility-payment-svc(:8085)│
└─────────────┘       └──────────────────────┘       │  core-banking-svc (:8092)  │
                                                      └────────────────────────────┘
```

- **Client → API Gateway**: HTTP/REST with Bearer JWT token (issued by Keycloak).
- **API Gateway → Downstream Services**: Routes requests by path prefix; injects `X-Auth-Id` header with the authenticated principal name.
- **Service → Service (Feign)**: Synchronous REST calls via Spring Cloud OpenFeign, resolved through Eureka service discovery:
  - `internet-banking-user-service` → `core-banking-service` (read user by identification)
  - `internet-banking-fund-transfer-service` → `core-banking-service` (fund transfer processing)
  - `internet-banking-utility-payment-service` → `core-banking-service` (utility payment processing)
- **Service Discovery**: All services register with the Eureka server; Feign clients resolve service names through Eureka.
- **Configuration**: All services (except the registry and config server itself) bootstrap from the Spring Cloud Config Server, which fetches configuration from a remote Git repository (`internet-banking-microservices-configurations`).

### 1.4 Infrastructure Components

| Component | Image / Technology | Purpose |
|---|---|---|
| **MySQL 8.4** | `mysql:8.4.0` (custom Dockerfile) | Primary data store for all business services |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity & Access Management (OAuth2 / OIDC) |
| **PostgreSQL 15** | `postgres:15` | Backing database for Keycloak |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing |
| **Spring Cloud Config Server** | Custom (Spring Boot app) | Externalized configuration |
| **Netflix Eureka** | Custom (Spring Boot app) | Service discovery |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `email` | VARCHAR(255) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport number |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (e.g. `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Many accounts per user |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g. `VODAFONE`, `VERIZON`, `AIRTEL` |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | |
| `transaction_id` | VARCHAR(50) | UUID generated per transaction |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:1 `banking_core_transaction` (via `@OneToOne` in JPA — note: this is likely an error; should be 1:N)

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, schema auto-created by Hibernate)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Maps to core banking `identification_number` |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR | UUID from core-banking-service |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | — | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | UUID from core-banking-service |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | — | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All downstream services are accessed through the API Gateway at port `8082`. The gateway routes by path prefix (configured externally via Spring Cloud Config):

| Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Security rules (defined in `SecurityConfiguration`):**
- `/user/api/v1/bank-users/register` — **permitAll** (public registration)
- `/actuator/**` (all services) — **permitAll**
- All other endpoints — **authenticated** (JWT required)

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` DTO |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` DTO |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` DTO (with bank accounts) |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

#### Request/Response Shapes

**`FundTransferRequest`**: `{ fromAccount: string, toAccount: string, amount: BigDecimal }`

**`FundTransferResponse`**: `{ message: string, transactionId: string }`

**`UtilityPaymentRequest`**: `{ providerId: long, amount: BigDecimal, referenceNumber: string, account: string }`

**`UtilityPaymentResponse`**: `{ message: string, transactionId: string }`

### 3.3 Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User` DTO | `User` DTO |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest` | `User` DTO |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` DTO |

#### Request/Response Shapes

**`User` (request)**: `{ email: string, identification: string, password: string }`

**`User` (response)**: `{ id: long, email: string, identification: string, authId: string, status: enum, version: long }`

**`UserUpdateRequest`**: `{ status: enum (PENDING|APPROVED|DISABLED|BLACKLIST) }`

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

#### Request/Response Shapes

**`FundTransferRequest`**: `{ fromAccount: string, toAccount: string, amount: BigDecimal, authID: string }`

**`FundTransferResponse`**: `{ message: string, transactionId: string }`

### 3.5 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

#### Request/Response Shapes

**`UtilityPaymentRequest`**: `{ providerId: long, amount: BigDecimal, referenceNumber: string, account: string }`

**`UtilityPaymentResponse`**: `{ message: string, transactionId: string }`

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` with email, identification, and password.
2. **User Service** checks Keycloak for existing users with the same email → throws `UserAlreadyRegisteredException` if found.
3. Calls **Core Banking Service** (`GET /api/v1/user/{identification}`) to verify the user exists in the banking core.
4. Validates the email matches the one on file → throws `InvalidEmailException` if mismatch.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with status `PENDING` and the Keycloak `authId`.
7. An admin later calls `PATCH /update/{id}` with `status: APPROVED` to enable the Keycloak user and mark them active.

### 4.2 Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` with source account, destination account, and amount.
2. **Fund Transfer Service** persists a `FundTransferEntity` with status `PENDING`.
3. Calls **Core Banking Service** (`POST /api/v1/transaction/fund-transfer`).
4. Core Banking Service:
   - Reads both accounts; throws `EntityNotFoundException` if either doesn't exist.
   - Validates the source account has sufficient funds; throws `InsufficientFundsException` if not.
   - Debits the source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits the destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Creates two `TransactionEntity` records (debit and credit) with a shared UUID `transactionId`.
5. Fund Transfer Service updates the local entity to `SUCCESS` with the transaction reference.

### 4.3 Utility Payment Flow

1. Client sends `POST /utility-payment/api/v1/utility-payment` with provider ID, amount, reference number, and account.
2. **Utility Payment Service** persists a `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls **Core Banking Service** (`POST /api/v1/transaction/util-payment`).
4. Core Banking Service:
   - Reads the source bank account; validates sufficient funds.
   - Looks up the utility provider by ID.
   - Debits the source account.
   - Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
5. Utility Payment Service updates the local entity to `SUCCESS` with the transaction ID.

### 4.4 Authentication & Authorization

- **Keycloak** serves as the OAuth2/OIDC identity provider.
- The **API Gateway** acts as an OAuth2 Resource Server, validating JWT tokens against Keycloak's JWK Set URI.
- The gateway extracts the authenticated principal name and forwards it as the `X-Auth-Id` HTTP header.
- Downstream services have an `AppAuthUserFilter` servlet filter that reads `X-Auth-Id` and stores it in a `ThreadLocal` (`ApiRequestContextHolder`) for JPA auditing.
- The user registration endpoint (`/user/api/v1/bank-users/register`) is the only public endpoint.

### 4.5 JPA Auditing

Three services (user, fund-transfer, utility-payment) use Spring Data JPA auditing:
- **`AuditAware`** (`@MappedSuperclass`): Base class with `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, `version`.
- **`AuditorAwareConfig`**: Returns the current `X-Auth-Id` or `"SYSTEM_USER"` as the auditor.
- **`AuditConfig`**: Enables `@EnableJpaAuditing` and registers the `AppAuthUserFilter`.

---

## 5. Integration Points

### 5.1 Keycloak

- **Version**: 23.0.7
- **Realm**: `javatodev-internet-banking` (imported from `docker-compose/keycloak/realm-export.json`)
- **Client**: `internet-banking-api-client` (client credentials grant for admin operations)
- **Integration**: The User Service uses `keycloak-admin-client:24.0.4` to:
  - Create users (`POST` to Keycloak Users API)
  - Search users by email
  - Read user details by auth ID
  - Update user status (enable/disable)
- **Gateway**: Validates JWTs issued by Keycloak using the JWK Set URI.

### 5.2 RabbitMQ

- **Status**: Referenced in README as a messaging backbone for notification service, but **not currently implemented** in code. No RabbitMQ dependency or configuration exists in any service's `build.gradle` or YAML configuration.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3
- **Port**: 9411
- **Integration**: All business services include Micrometer Tracing (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) and `feign-micrometer` for propagating trace context across Feign calls.
- **Configuration**: Tracing settings are externalized in the Spring Cloud Config Git repository.

### 5.4 Database Connections

| Service | Database | Connection |
|---|---|---|
| core-banking-service | `banking_core_service` | MySQL via `mysql-connector-j:8.4.0` |
| internet-banking-user-service | `banking_core_user_service` | MySQL via `mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL via `mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL via `mysql-connector-j:8.4.0` |
| Keycloak | `keycloak` | PostgreSQL 15 |

- MySQL credentials: root password `woVERANKliGharym`, application user `javatodev_development` / `oPItyPticIAt`.
- Databases are auto-created via `privileges.sql` in the MySQL Docker init script.
- Core banking service uses **Flyway** (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) for schema migration.
- Other services rely on Hibernate `ddl-auto` (configured externally).

### 5.5 Spring Cloud Config (External Configuration)

- **Config Server** fetches from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`, **Path**: `configuration/`
- Each service bootstraps from the config server using `bootstrap.yml` / `bootstrap-docker.yml`.
- Profile-specific configs: `dev` (local IP `192.168.1.5`), `docker` (Docker service name), default (`localhost`).

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool**: Gradle (wrapper) with Spring Boot plugin 3.2.4 and Spring Dependency Management 1.1.4.
- **Java version**: 21 (source compatibility).
- **Spring Cloud BOM**: `2023.0.0`.
- Each service is an independent Gradle project (no multi-module root `build.gradle`).
- Notable plugins:
  - `com.gorylenko.gradle-git-properties:2.4.2` — embeds Git commit info in the build.
  - `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` — OpenAPI/Swagger UI (on core-banking, user, fund-transfer, utility-payment services).

### 6.2 Docker

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` to wait for dependent services (Eureka, Config Server, MySQL) before starting.
- Images are tagged as `javatodev/<service-name>`.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`**: Full stack — all 6 microservices + MySQL + Keycloak + PostgreSQL + Zipkin.
2. **`docker-compose-support-apps.yml`**: Infrastructure only — MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry.

All containers are connected via a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments.

### 6.4 CI/CD

- **No CI/CD pipeline is configured.** There are no GitHub Actions workflows, Jenkinsfiles, or any other pipeline definitions in the repository.
- The `.github/` directory contains only `FUNDING.yml`.

### 6.5 Testing

- Test framework: JUnit 5 (via `spring-boot-starter-test`).
- H2 in-memory database for test profiles (configured in `src/test/resources/application.yml`).
- Eureka client disabled in test profiles.
- Flyway disabled in test profiles.
- **Only `core-banking-service` has meaningful unit tests** (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with Mockito-based mocking.
- All other services have only a default `contextLoads()` test (which would fail without infrastructure).

### 6.6 Postman Collection

A Postman collection is provided at `postman_collection/`:
- `JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
- Includes a `LOCAL_DOCKER_SETUP` environment.
- Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`.
