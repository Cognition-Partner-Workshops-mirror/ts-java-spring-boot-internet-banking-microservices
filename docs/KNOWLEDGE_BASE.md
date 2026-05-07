# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Topology

The application is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** microservices platform implementing an internet banking system. It consists of **6 independently deployable services** plus supporting infrastructure.

```
                         ┌──────────────────────┐
                         │   Keycloak (IAM)      │
                         │   Port 8080           │
                         └──────────┬─────────────┘
                                    │ OAuth2 JWT
┌───────────┐   HTTP    ┌──────────▼─────────────┐
│  Client    │─────────►│   API Gateway           │
│            │          │   Port 8082             │
└───────────┘          └──┬────┬────┬────────────┘
                          │    │    │
              ┌───────────┘    │    └───────────┐
              ▼                ▼                ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ User Service  │  │ Fund Transfer│  │ Utility      │
   │ Port 8083     │  │ Service      │  │ Payment Svc  │
   │               │  │ Port 8084    │  │ Port 8085    │
   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
          │                  │                  │
          │    OpenFeign     │    OpenFeign     │
          ▼                  ▼                  ▼
   ┌─────────────────────────────────────────────────┐
   │              Core Banking Service                │
   │              Port 8092                           │
   └──────────────────────┬──────────────────────────┘
                          │
                          ▼
                  ┌───────────────┐
                  │   MySQL       │
                  │   Port 3306   │
                  └───────────────┘
```

**Supporting Infrastructure:**
| Component | Purpose | Port | Docker IP |
|---|---|---|---|
| Service Registry (Eureka) | Service discovery | 8081 | 172.25.0.7 |
| Config Server | Centralized configuration | 8090 | 172.25.0.8 |
| Keycloak | Identity & access management | 8080 | 172.25.0.11 |
| PostgreSQL | Keycloak persistence | 5432 | 172.25.0.10 |
| MySQL | Application databases | 3306 | 172.25.0.9 |
| Zipkin | Distributed tracing | 9411 | 172.25.0.12 |

### 1.2 Services

| Service | Port | Role | Database |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Single entry point, OAuth2 enforcement, request routing, user-identity header injection | None |
| `internet-banking-user-service` | 8083 | User registration/management, Keycloak integration | `banking_core_user_service` (MySQL) |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration, transaction record keeping | `banking_core_fund_transfer_service` (MySQL) |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment orchestration | `banking_core_utility_payment_service` (MySQL) |
| `core-banking-service` | 8092 | System of record: accounts, users, ledger transactions | `banking_core_service` (MySQL) |
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server | None |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server (classpath-based) | None |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| Synchronous REST | Spring Cloud OpenFeign | All inter-service calls (User→Core, FundTransfer→Core, UtilityPayment→Core) |
| Service Discovery | Netflix Eureka | All business services register with and discover peers via Eureka |
| API Gateway Routing | Spring Cloud Gateway | Client→Service routing with path-based predicates |
| Identity Propagation | Custom `X-Auth-Id` header | Gateway extracts JWT principal name, injects as header for downstream services |
| Centralized Config | Spring Cloud Config | All services fetch configuration from Config Server at bootstrap |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin | Trace context propagated across Feign calls |

### 1.4 Authentication & Authorization Flow

1. Client authenticates with **Keycloak** (realm-based) to obtain a JWT access token.
2. Client sends requests to the **API Gateway** with `Authorization: Bearer <token>`.
3. Gateway validates the JWT against Keycloak's JWK set URI.
4. Gateway's `GatewayConfiguration` global filter extracts `Principal.getName()` and injects it as `X-Auth-Id` header.
5. Downstream services use `AppAuthUserFilter` (servlet filter) to read `X-Auth-Id` and store it in a thread-local `ApiRequestContextHolder`.
6. The `/user/api/v1/bank-users/register` endpoint is explicitly permitted without authentication.
7. All `/actuator/**` endpoints are permitted without authentication.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email |
| `identification_number` | VARCHAR(255) | | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g. `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Signed transaction amount |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counter-party account or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping related entries |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Account involved |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g. `VODAFONE`) |

**Entity Relationships (Core Banking):**
```
banking_core_user  1──────*  banking_core_account
banking_core_account  1──────1  banking_core_transaction
banking_core_utility_account  (standalone)
```

### 2.2 User Service (`banking_core_user_service` database)

#### `user` (JPA-managed, auto-DDL)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Maps to `identification_number` in core |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| `created_at` | TIMESTAMP | Audit field (from `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit field (from `AuditAware`) |

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer` (JPA-managed, auto-DDL)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | VARCHAR | Core banking transaction UUID |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment` (JPA-managed, auto-DDL)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill/reference number |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core banking transaction UUID |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (all prefixed through gateway at port 8082)

The gateway routes requests by path prefix to downstream services:
- `/user/**` → `internet-banking-user-service`
- `/fund-transfer/**` → `internet-banking-fund-transfer-service`
- `/banking-core/**` → `core-banking-service`
- `/utility-payment/**` → `internet-banking-utility-payment-service`

### 3.2 Core Banking Service (port 8092)

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Internal | Get bank account by number | – | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Internal | Get utility account by provider name | – | `UtilityAccount` |
| `POST` | `/api/v1/transaction/fund-transfer` | Internal | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Internal | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/user/{identification}` | Internal | Get user by NIC | – | `User` |
| `GET` | `/api/v1/user` | Internal | List users (paginated) | – | `List<User>` |

**Request/Response Shapes:**

```
FundTransferRequest: { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse: { message: String, transactionId: String }
UtilityPaymentRequest: { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse: { message: String, transactionId: String }
BankAccount: { id: Long, number: String, type: Enum, status: Enum, availableBalance: BigDecimal, actualBalance: BigDecimal, user: User }
User: { id: Long, firstName: String, lastName: String, email: String, identificationNumber: String }
UtilityAccount: { id: Long, number: String, providerName: String }
```

### 3.3 User Service (port 8083)

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Public | Register new banking user | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | JWT | Update user (approve/reject) | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | JWT | List users (paginated) | – | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | JWT | Get user by ID | – | `User` |

**Request/Response Shapes:**

```
User (request): { email: String, identification: String, password: String }
User (response): { id: Long, email: String, identification: String, authId: String, status: Enum, createdAt: Date, updatedAt: Date }
UserUpdateRequest: { status: Enum(PENDING|APPROVED) }
```

### 3.4 Fund Transfer Service (port 8084)

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | JWT | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | JWT | List transfers (paginated) | – | `List<FundTransfer>` |

**Request/Response Shapes:**

```
FundTransferRequest: { fromAccount: String, toAccount: String, amount: BigDecimal, authID: String }
FundTransferResponse: { message: String, transactionId: String }
FundTransfer: { id: Long, fromAccount: String, toAccount: String, amount: BigDecimal, status: Enum, transactionReference: String }
```

### 3.5 Utility Payment Service (port 8085)

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | JWT | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | JWT | List payments (paginated) | – | `List<UtilityPayment>` |

**Request/Response Shapes:**

```
UtilityPaymentRequest: { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse: { message: String, transactionId: String }
UtilityPayment: { id: Long, providerId: Long, amount: BigDecimal, referenceNumber: String, account: String, transactionId: String, status: Enum }
```

### 3.6 Infrastructure Endpoints

| Service | Path | Description |
|---|---|---|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Build/git info |
| Service Registry | `/` (port 8081) | Eureka dashboard |
| Zipkin | `/` (port 9411) | Trace viewer |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (public endpoint).
2. User Service checks Keycloak for existing email — throws `UserAlreadyRegisteredException` if found.
3. User Service calls Core Banking `GET /api/v1/user/{identification}` to verify user exists in the banking core.
4. Validates email matches core banking record — throws `InvalidEmailException` on mismatch.
5. Creates Keycloak user representation with credentials (disabled, email unverified).
6. On successful Keycloak creation (HTTP 201), saves local user entity with `status=PENDING`.
7. If identification not found in core, throws `InvalidBankingUserException`.

### 4.2 User Approval Flow

1. Admin calls `PATCH /user/api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. If status is `APPROVED`, User Service enables the Keycloak user and sets `emailVerified=true`.
3. Updates local entity status.

### 4.3 Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer`.
2. Fund Transfer Service saves a `PENDING` record in its local database.
3. Delegates to Core Banking via Feign: `POST /api/v1/transaction/fund-transfer`.
4. Core Banking validates source account balance (must be >= transfer amount, must be non-negative).
5. Core Banking performs double-entry bookkeeping:
   - Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`)
   - Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`)
   - Records two `TransactionEntity` entries (one per account) with the same `transactionId`.
6. Fund Transfer Service updates local record to `SUCCESS` with transaction reference.

**Business Rules:**
- Source account balance must be >= 0 AND >= transfer amount.
- No minimum/maximum transfer amount validation exists.
- No rate limiting or daily limits are enforced.
- Transfers are synchronous with no saga/compensation pattern.

### 4.4 Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment`.
2. Utility Payment Service saves a `PROCESSING` record locally.
3. Delegates to Core Banking via Feign: `POST /api/v1/transaction/util-payment`.
4. Core Banking validates source account balance.
5. Core Banking debits source account and records transaction.
6. Utility Payment Service updates local record to `SUCCESS`.

**Business Rules:**
- Same balance validation as fund transfers.
- No actual third-party provider integration (placeholder comment in code).
- No idempotency checks on reference numbers.

### 4.5 Balance Calculation Bug

In `TransactionService.internalFundTransfer()` and `utilPayment()`, the available balance is set incorrectly:
```java
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
```
This subtracts the amount **twice** from the available balance (once via `actualBalance`, once via `subtract(amount)`), causing available balance to diverge from actual balance.

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Version:** 23.0.7
- **Backend DB:** PostgreSQL 15
- **Realm:** Imported from `docker-compose/keycloak/` volume mount at startup
- **Client Library:** `keycloak-admin-client:24.0.4`
- **Integration Pattern:** User Service uses Keycloak Admin REST API via `KeycloakManager` component
- **Auth:** Client credentials grant (`client_credentials`)
- **Properties:**
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Realm name
  - `app.config.keycloak.clientId` — Client ID
  - `app.config.keycloak.client-secret` — Client secret
- **Singleton Pattern:** `KeycloakProperties` uses a static singleton for `Keycloak` client instance (not thread-safe, not Spring-managed lifecycle).

### 5.2 RabbitMQ Integration

- **Status:** Referenced in README as a technology component but **not implemented** in any service code.
- The README describes a Notification Service that would consume from RabbitMQ — this service is marked as "PENDING Development."

### 5.3 Zipkin Integration

- **Version:** 3 (Docker image `openzipkin/zipkin:3`)
- **Client Libraries:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` on all services
- **Configuration:** Zipkin endpoint configured via Spring Cloud Config
- **Coverage:** Traces propagated across Feign calls via `feign-micrometer`

### 5.4 Database Connections

| Service | Database | Engine | Schema Management |
|---|---|---|---|
| Core Banking | `banking_core_service` | MySQL | Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| User Service | `banking_core_user_service` | MySQL | JPA auto-DDL |
| Fund Transfer | `banking_core_fund_transfer_service` | MySQL | JPA auto-DDL |
| Utility Payment | `banking_core_utility_payment_service` | MySQL | JPA auto-DDL |
| Keycloak | `keycloak` | PostgreSQL 15 | Keycloak-managed |

- All application databases share a single MySQL instance.
- DB user: `javatodev_development` with broad privileges (`CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*`).

### 5.5 Service Registry (Eureka)

- All business services register as Eureka clients.
- Feign clients use service names (e.g., `core-banking-service`) resolved via Eureka.
- Config Server also registered with Eureka.

### 5.6 Config Server

- Serves centralized configuration to all services.
- Services use `spring-cloud-starter-bootstrap` to fetch config at startup.
- Bootstrap profiles: `default` (localhost), `docker` (container hostnames).

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service, no multi-project build)
- **Java:** 21 (source compatibility)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` for build metadata

Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is **no root-level Gradle wrapper or multi-project build**.

### 6.2 Docker

Each service has its own `Dockerfile` using `eclipse-temurin:21.0.2_13-jre-alpine`:
1. Copies the built JAR (`build/libs/<service>-0.0.1-SNAPSHOT.jar`) as `app.jar`
2. Copies `wait-for-it.sh` for startup ordering
3. Default entrypoint: `java -jar -Dspring.profiles.active=docker /app.jar`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)

**Network:** Custom bridge network `javatodev_ib_network` with static IPs (subnet `172.25.0.0/16`).

**Startup Ordering:** `wait-for-it.sh` scripts ensure:
- Services wait for Service Registry (port 8081)
- Services wait for Config Server (port 8090)
- Database-dependent services wait for MySQL (port 3306)

### 6.4 Testing

- **Framework:** JUnit 5 (`useJUnitPlatform()`)
- **Test DB:** H2 in-memory (all services include `com.h2database:h2` as test dependency)
- **Test Coverage:** Only `core-banking-service` has meaningful unit tests:
  - `AccountServiceTest` — 6 tests
  - `TransactionServiceTest` — 10 tests
  - `UserServiceTest` — (exists)
- Other services have only empty Spring Boot context-loading tests.

### 6.5 API Documentation

- **Library:** `springdoc-openapi-starter-webflux-ui:2.1.0`
- **Annotations:** Swagger `@Tag` and `@Operation` annotations on controllers in Core Banking, User, Fund Transfer, and Utility Payment services
- **Note:** The WebFlux UI dependency is used even in non-reactive (servlet-based) services, which is a mismatch

### 6.6 Test Data

Flyway seed data provides:
- 4 users with NIC identification numbers
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
