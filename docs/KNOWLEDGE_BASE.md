# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is an **Internet Banking** platform built with **Java 21** and **Spring Boot 3.2.4**, following a microservices architecture orchestrated via **Spring Cloud 2023.0.0**. The system comprises 6 independently deployable services that collectively provide banking operations including user management, fund transfers, and utility payments.

### Services

| Service | Port | Description |
|---|---|---|
| **core-banking-service** | 8092 | Central banking core: manages accounts, users, transactions. Acts as the system-of-record for balances and account data. |
| **internet-banking-user-service** | 8083 | Handles internet banking user registration, approval workflows, and profile management. Integrates with Keycloak for identity. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests between bank accounts, delegates actual ledger operations to core-banking-service. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (electricity, telecom, etc.), delegates ledger operations to core-banking-service. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client traffic. Enforces OAuth2/JWT authentication via Keycloak. Routes requests to downstream services. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server. Serves externalized configuration from a Git repository. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server. Provides service discovery so services locate each other by logical name. |

### Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| **Synchronous REST** | Spring Cloud OpenFeign | Fund-transfer-service -> core-banking-service; Utility-payment-service -> core-banking-service; User-service -> core-banking-service |
| **Service Discovery** | Netflix Eureka | All business services register with Eureka; Feign clients resolve service names via Eureka |
| **API Gateway** | Spring Cloud Gateway | All external traffic enters through the gateway, which routes by path prefix to downstream services |
| **Centralized Config** | Spring Cloud Config (Git-backed) | All services (except config-server and service-registry) pull configuration from a remote Git repo at startup |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | Trace context propagated across Feign calls; spans exported to Zipkin |
| **Auth Context Propagation** | Custom HTTP Header (`X-Auth-Id`) | Gateway extracts the JWT principal name and forwards it as `X-Auth-Id`; downstream services read it via a servlet filter (`AppAuthUserFilter`) |

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| **Database** | MySQL 8.x | Persistent storage for all business services (4 separate schemas) |
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC provider; manages user credentials, realms, clients |
| **Distributed Tracing** | Zipkin 3 | Collects and visualizes distributed traces |
| **Message Broker** | RabbitMQ (mentioned in README) | Intended for notification service - **not yet implemented** |

### Request Flow

```
Client -> API Gateway (JWT validation) -> Eureka lookup -> Downstream Service -> (Feign) -> core-banking-service -> MySQL
```

1. Client sends request with a Bearer JWT to the API Gateway (port 8082).
2. Gateway validates the JWT against Keycloak's JWK endpoint.
3. Gateway extracts the principal name and adds `X-Auth-Id` header.
4. Gateway routes the request to the appropriate service via Eureka.
5. Business services (user/fund-transfer/utility-payment) may call core-banking-service via Feign.
6. core-banking-service performs database operations and returns results.

---

## 2. Data Model Documentation

### core-banking-service (MySQL schema: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK -> banking_core_user.id) | Account owner |

**Relationship:** Many accounts to one user (`@ManyToOne`).

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Payee account number or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK -> banking_core_account.id) | The account this entry belongs to |

**Relationship:** `@OneToOne` with `CascadeType.ALL` to `BankAccountEntity` (note: this mapping is semantically incorrect - should be `@ManyToOne`).

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

### internet-banking-user-service (MySQL schema: `banking_core_user_service`)

#### `user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (links to core-banking user) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | (inherited from AuditAware) | Audit columns |

### internet-banking-fund-transfer-service (MySQL schema: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID returned by core-banking-service |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | (inherited from AuditAware) | Audit columns |

### internet-banking-utility-payment-service (MySQL schema: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Payment reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Transaction ID returned by core-banking-service |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | (inherited from AuditAware) | Audit columns |

### Database Migration Strategy

- **core-banking-service** uses **Flyway** with versioned SQL migrations under `src/main/resources/db/migration/`.
- **Other services** rely on **JPA/Hibernate `ddl-auto`** (configured via Spring Cloud Config) - no Flyway migrations present.
- Test environments use **H2 in-memory** databases with Flyway disabled.

---

## 3. API Surface Map

### core-banking-service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | - | `BankAccount` (number, type, status, balances) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer at ledger level | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment at ledger level | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### internet-banking-user-service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User` (email, identification, password) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve registration) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List all registered users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### internet-banking-fund-transfer-service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List all fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### internet-banking-utility-payment-service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List all utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### API Gateway Routes (port 8082)

All external requests pass through the gateway. Routes are defined via Spring Cloud Config and map path prefixes to service names:

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Authentication:** All endpoints require a valid JWT except:
- `/user/api/v1/bank-users/register` (public)
- `/actuator/**` on all services (public)

### OpenAPI / Swagger

All business services include `springdoc-openapi-starter-webflux-ui` dependency and use `@Tag` / `@Operation` annotations. Swagger UI is available at `/swagger-ui.html` on each service.

---

## 4. Key Business Logic Inventory

### Fund Transfer Flow

1. **Client** sends `POST /api/v1/transfer` to fund-transfer-service.
2. **FundTransferService** saves a `FundTransferEntity` with status `PENDING`.
3. **FundTransferService** calls core-banking-service via Feign (`POST /api/v1/transaction/fund-transfer`).
4. **core-banking TransactionService**:
   a. Reads both from/to bank accounts.
   b. **Validates** the sender has sufficient funds (`actualBalance >= amount`).
   c. Debits the sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   d. Saves a debit `TransactionEntity` (negative amount).
   e. Credits the receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
   f. Saves a credit `TransactionEntity` (positive amount).
   g. Returns a `FundTransferResponse` with a UUID `transactionId`.
5. **FundTransferService** updates the entity to status `SUCCESS` with the transaction reference.

**Key Business Rules:**
- Balance validation: `actualBalance` must be >= 0 AND >= transfer amount.
- Both debit and credit happen in a single `@Transactional` method.
- Transaction ID is a random UUID (not a database sequence).

**Known Bug:** The `availableBalance` calculation is incorrect. After debiting, the code sets `availableBalance = actualBalance - amount`, which double-subtracts the amount. Same issue exists on the credit side.

### Utility Payment Flow

1. **Client** sends `POST /api/v1/utility-payment` to utility-payment-service.
2. **UtilityPaymentService** saves a `UtilityPaymentEntity` with status `PROCESSING`.
3. **UtilityPaymentService** calls core-banking-service via Feign (`POST /api/v1/transaction/util-payment`).
4. **core-banking TransactionService**:
   a. Reads the payer's bank account.
   b. **Validates** sufficient funds.
   c. Reads the utility provider account by ID.
   d. Debits the payer's account.
   e. Saves a `TransactionEntity` for the debit.
   f. Returns a `UtilityPaymentResponse` with a UUID `transactionId`.
5. **UtilityPaymentService** updates the entity to status `SUCCESS`.

**Known Bug:** Same `availableBalance` double-subtraction bug as fund transfer.

### User Registration Flow

1. **Client** sends `POST /api/v1/bank-users/register` (public endpoint).
2. **UserService** checks if the email is already registered in Keycloak.
3. **UserService** calls core-banking-service via Feign to verify the user exists by identification number.
4. **UserService** validates the email matches the core banking record.
5. **UserService** creates a Keycloak user (disabled, email unverified) with the provided password.
6. **UserService** saves a local `UserEntity` with status `PENDING` and the Keycloak `authId`.
7. **Admin** later calls `PATCH /api/v1/bank-users/update/{id}` with status `APPROVED`.
8. **UserService** enables the Keycloak user and marks email as verified.

### User Approval Flow

- When a user's status is updated to `APPROVED`, the Keycloak user is enabled and their email is marked as verified.
- This allows the user to authenticate and obtain JWTs.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Configuration:** Server URL, realm, client ID, client secret via `app.config.keycloak.*` properties (served by Config Server)
- **Usage:**
  - User registration (create Keycloak user)
  - User approval (enable Keycloak user)
  - User lookup by email
  - JWT validation at API Gateway (OAuth2 Resource Server with JWK Set URI)
- **Singleton Pattern:** `KeycloakProperties` maintains a static singleton `Keycloak` instance (not thread-safe, not refreshable).

### RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not implemented** in the current codebase.
- **Intended Use:** Notification service (consuming fund transfer and payment events).
- No RabbitMQ dependencies in any `build.gradle` files.

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Dependencies:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **All 6 services** include tracing dependencies.
- Trace context is automatically propagated through Feign calls.

### MySQL Database Connections

- **4 separate databases** provisioned via `privileges.sql`:
  - `banking_core_service` (core-banking-service)
  - `banking_core_fund_transfer_service` (fund-transfer-service)
  - `banking_core_user_service` (user-service)
  - `banking_core_utility_payment_service` (utility-payment-service)
- **Connection details:** Served by Spring Cloud Config Server from a Git repository.
- **MySQL user:** `javatodev_development` with broad privileges.

### Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration`
- All services use `spring-cloud-starter-config` and `spring-cloud-starter-bootstrap` to fetch configuration at startup.

### Netflix Eureka Service Registry

- **Server:** Standalone Eureka server on port 8081.
- **Clients:** All business services and the API Gateway register with Eureka.
- **Feign Clients** resolve service names through Eureka (e.g., `@FeignClient(name = "core-banking-service")`).

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (each service has its own `build.gradle` - no multi-project build)
- **Java Version:** 21 (Eclipse Temurin JDK)
- **Spring Boot:** 3.2.4 via Spring Boot Gradle Plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Git Properties Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (on most services)

### Docker

Each service has a `Dockerfile` following the same pattern:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build Process (per service):**
```bash
./gradlew clean build
docker build -t javatodev/<service-name> .
```

### Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs for each container.

**Service Startup Ordering:** `wait-for-it.sh` script ensures services wait for:
1. Service Registry (port 8081)
2. Config Server (port 8090)
3. MySQL (port 3306)

**Volumes:**
- `postgres_data` - Keycloak PostgreSQL data
- `mysqldata` - Application MySQL data

### Keycloak Setup

- A `realm-export.json` is mounted into the Keycloak container and imported on startup (`--import-realm`).
- Custom `Dockerfile` in `docker-compose/keycloak/` for the Keycloak image.

### MySQL Setup

- Custom `Dockerfile` in `docker-compose/mysql/` that creates databases and the application user via `privileges.sql`.

### Test Configuration

- **Test Framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test Database:** H2 in-memory (dependency in `testImplementation`)
- Tests exist only in **core-banking-service** (3 service test classes with ~20 test methods).
- Other services have only empty `ApplicationTests` classes.
