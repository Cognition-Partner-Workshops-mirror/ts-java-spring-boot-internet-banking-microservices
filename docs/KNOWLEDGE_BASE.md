# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet-banking application built with a microservices architecture using Spring Cloud 2023.0.0. It simulates core banking operations including user management, fund transfers, and utility payments.

### 1.2 Services Inventory

| # | Service | Port | Description |
|---|---------|------|-------------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration server backed by a Git repository |
| 2 | **internet-banking-service-registry** | 8081 | Netflix Eureka service-discovery server |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point with OAuth2/Keycloak security |
| 4 | **internet-banking-user-service** | 8083 | User registration, approval, and profile management; integrates with Keycloak |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Processes fund transfers between bank accounts |
| 6 | **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments |
| 7 | **core-banking-service** | 8092 | Backend "banking core" — accounts, users, transactions, balance management |

> Services 1–3 are **infrastructure services**; services 4–7 are **business services**.

### 1.3 Communication Patterns

```
                        ┌───────────────┐
                        │  Config Server│  (Git-backed YAML)
                        │   :8090       │
                        └──────┬────────┘
                               │ bootstrap config
        ┌──────────────────────┼──────────────────────────┐
        │                      │                          │
┌───────▼───────┐   ┌─────────▼────────┐   ┌─────────────▼──────────────┐
│Service Registry│   │   API Gateway    │   │  Business Services (4–7)   │
│  Eureka :8081  │◄──│  :8082           │──►│  user / transfer / payment │
└───────▲───────┘   │  OAuth2+Keycloak │   │  / core-banking            │
        │           └──────────────────┘   └────────────┬───────────────┘
        │  register/discover                            │
        └───────────────────────────────────────────────┘
```

| Pattern | Technology | Details |
|---------|-----------|---------|
| Service Discovery | Netflix Eureka | All business services register with the Eureka server and discover each other by service name |
| Centralized Config | Spring Cloud Config Server | Git-backed (`internet-banking-microservices-configurations` repo); services load config via `bootstrap.yml` pointing to `:8090` |
| API Gateway | Spring Cloud Gateway | Routes prefixed `/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**` to respective services |
| Synchronous Inter-Service | OpenFeign | Fund-transfer-service → core-banking-service; Utility-payment-service → core-banking-service; User-service → core-banking-service |
| Auth Header Propagation | Gateway GlobalFilter | Extracts JWT principal name, injects `X-Auth-Id` header into downstream requests |
| Auth User Extraction | Servlet Filter (`AppAuthUserFilter`) | Downstream services read `X-Auth-Id` from the request and store in `ApiRequestContextHolder` (ThreadLocal) |
| Distributed Tracing | Micrometer Tracing + Zipkin | Brave bridge exports traces to Zipkin at `:9411` |
| Async Messaging (planned) | RabbitMQ | Mentioned in README for notification service — **not yet implemented** |

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|-----------|----------------|---------|
| MySQL 8.4.0 | Custom Dockerfile (`docker-compose/mysql`) | Persistent data store for all four business services (4 databases) |
| PostgreSQL 15 | `postgres:15` | Keycloak's backing store |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | OAuth2 / OIDC identity provider; realm imported from `keycloak/realm-export.json` |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed trace collector and UI |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL — `banking_core_service`)

Managed via **Flyway** migrations.

#### `banking_core_user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | ENUM (`SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT`) | |
| `status` | ENUM (`PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED`) | |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Many-to-one |

#### `banking_core_utility_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### `banking_core_transaction`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | ENUM (`FUND_TRANSFER`, `UTILITY_PAYMENT`) | |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID string linking debit/credit pair |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

**Relationships:**
```
banking_core_user 1──►N banking_core_account 1──►N banking_core_transaction
banking_core_utility_account (standalone)
```

### 2.2 Internet Banking User Service (MySQL — `banking_core_user_service`)

Schema auto-generated by JPA/Hibernate (no Flyway).

#### `user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Links to core-banking `identification_number` |
| `status` | ENUM (`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | |
| `created_date` | INSTANT | Audit — `@CreatedDate` |
| `created_by` | VARCHAR | Audit — `@CreatedBy` |
| `modified_date` | INSTANT | Audit — `@LastModifiedDate` |
| `modified_by` | VARCHAR | Audit — `@LastModifiedBy` |
| `version` | BIGINT | Optimistic locking — `@Version` |

### 2.3 Internet Banking Fund Transfer Service (MySQL — `banking_core_fund_transfer_service`)

Schema auto-generated by JPA/Hibernate (no Flyway).

#### `fund_transfer`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core-banking response |
| `status` | ENUM (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| Audit fields | (inherited from `AuditAware`) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Internet Banking Utility Payment Service (MySQL — `banking_core_utility_payment_service`)

Schema auto-generated by JPA/Hibernate (no Flyway).

#### `utility_payment`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | References utility provider |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking response |
| `status` | ENUM (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| Audit fields | (inherited from `AuditAware`) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

All public-facing traffic enters through the **API Gateway** (`:8082`), which routes by path prefix. Direct service ports are listed for internal reference.

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Retrieve bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Retrieve utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Retrieve user by NIC / identification |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated user listing |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` { fromAccount, toAccount, amount } | `FundTransferResponse` { message, transactionId } | Execute a fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } | Execute a utility payment |

### 3.2 Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` { email, identification, password } | `User` | Register a new internet-banking user (creates Keycloak user + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` { status } | `User` | Update user status (e.g., approve); enables Keycloak account when `APPROVED` |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated listing of internet-banking users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Retrieve user by internal ID |

### 3.3 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` { fromAccount, toAccount, amount } | `FundTransferResponse` { message, transactionId } | Initiate a fund transfer (delegates to core-banking-service via Feign) |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | Paginated listing of fund transfers |

### 3.4 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } | Process a utility payment (delegates to core-banking-service via Feign) |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | Paginated listing of utility payments |

### 3.5 API Gateway Routes (`:8082`)

| Route Prefix | Target Service | Auth Required |
|-------------|---------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` | (self + downstream) | No |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Git properties / build info |
| Config Server | `/actuator/**` | Standard actuator |
| Service Registry | `:8081` (Eureka dashboard) | Service discovery UI |
| Zipkin | `:9411` | Trace search and visualization |
| Keycloak | `:8080` | IAM admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException` if duplicate
2. Call core-banking-service via Feign (`/api/v1/user/{identification}`) to verify the user exists in the banking core
3. Validate that the email in the request matches the core-banking user's email → throw `InvalidEmailException` if mismatch
4. Create Keycloak user (disabled, email unverified) with provided password
5. If Keycloak returns HTTP 201, read back the created user to get `authId`
6. Save local `UserEntity` with status `PENDING`
7. If banking-core user not found → throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Service:** `internet-banking-user-service` → `UserService.updateUser()`

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`
2. If status is `APPROVED`:
   - Fetch Keycloak user representation by `authId`
   - Enable the user and mark email as verified in Keycloak
3. Update local user entity status
4. Possible statuses: `PENDING` → `APPROVED` / `DISABLED` / `BLACKLIST`

### 4.3 Fund Transfer Flow

**Services:** `internet-banking-fund-transfer-service` → `core-banking-service`

1. **Fund Transfer Service** (`FundTransferService.fundTransfer()`):
   - Persist a `FundTransferEntity` with status `PENDING`
   - Call core-banking-service Feign client at `POST /api/v1/transaction/fund-transfer`
   - On success: update entity with transaction reference and status `SUCCESS`

2. **Core Banking Service** (`TransactionService.fundTransfer()`):
   - Look up both source and destination accounts
   - **Balance validation:** `actualBalance >= 0 AND actualBalance >= transferAmount` → throw `InsufficientFundsException` if failed
   - Call `internalFundTransfer()`:
     - Debit source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`)
     - Record debit transaction (negative amount)
     - Credit destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`)
     - Record credit transaction (positive amount)
   - Both transactions share the same UUID `transactionId`
   - Entire operation is `@Transactional`

### 4.4 Utility Payment Flow

**Services:** `internet-banking-utility-payment-service` → `core-banking-service`

1. **Utility Payment Service** (`UtilityPaymentService.utilPayment()`):
   - Persist a `UtilityPaymentEntity` with status `PROCESSING`
   - Call core-banking-service Feign client at `POST /api/v1/transaction/util-payment`
   - On success: update entity with transaction ID and status `SUCCESS`

2. **Core Banking Service** (`TransactionService.utilPayment()`):
   - Look up source bank account
   - **Balance validation:** same rules as fund transfer
   - Look up utility provider account by `providerId`
   - Debit source account
   - Record transaction with type `UTILITY_PAYMENT`
   - Note: no credit to utility provider account in the current implementation

### 4.5 Business Rules Summary

| Rule | Location | Behavior |
|------|----------|----------|
| Insufficient funds | `TransactionService.validateBalance()` | Throws `InsufficientFundsException` if `actualBalance < 0` or `actualBalance < amount` |
| Duplicate email | `UserService.createUser()` | Throws `UserAlreadyRegisteredException` if Keycloak has the email |
| Email mismatch | `UserService.createUser()` | Throws `InvalidEmailException` if request email ≠ core-banking email |
| Unknown banking user | `UserService.createUser()` | Throws `InvalidBankingUserException` if NIC not in core-banking |
| Entity not found | Multiple services | Throws `EntityNotFoundException` (custom per service) |

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Protocol:** Admin REST API via `keycloak-admin-client:24.0.4`
- **Connection:** Configured in externalized properties (`app.config.keycloak.*`)
  - `server-url`, `realm`, `clientId`, `client-secret`
  - Uses `client_credentials` grant type
- **Realm:** Pre-configured via `realm-export.json` import on container start
- **JWT Validation:** API Gateway validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **User Lifecycle:** Create user → disable by default → enable on admin approval
- **Singleton Pattern:** `KeycloakProperties` uses a static singleton for the `Keycloak` client instance (not thread-safe)

### 5.2 RabbitMQ (Async Messaging)

- **Status:** Referenced in README but **not implemented** in the codebase
- **Intended Use:** Notification service would consume messages from RabbitMQ for fund-transfer and utility-payment events
- **No dependencies** for RabbitMQ exist in any `build.gradle`

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker image `openzipkin/zipkin:3`)
- **Libraries:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- **Port:** 9411
- **Coverage:** All 7 services include tracing dependencies; Feign calls are traced via `feign-micrometer`
- **Configuration:** Externalized in the config-server Git repo

### 5.4 Database Connections

| Service | Database | Schema | DDL Strategy |
|---------|----------|--------|-------------|
| core-banking-service | MySQL 8.4 | `banking_core_service` | **Flyway** migrations (3 scripts) |
| internet-banking-user-service | MySQL 8.4 | `banking_core_user_service` | JPA/Hibernate auto-DDL |
| internet-banking-fund-transfer-service | MySQL 8.4 | `banking_core_fund_transfer_service` | JPA/Hibernate auto-DDL |
| internet-banking-utility-payment-service | MySQL 8.4 | `banking_core_utility_payment_service` | JPA/Hibernate auto-DDL |
| Keycloak | PostgreSQL 15 | `keycloak` | Internal Keycloak DDL |

- All services share a single MySQL container (`mysql_javatodev_app`)
- Connection details (URL, credentials) are externalized in the config server's Git repo
- MySQL root password: hardcoded in `docker-compose.yml` and `Dockerfile`
- Application DB user: `javatodev_development` created via `privileges.sql`

### 5.5 Spring Cloud Config Server

- **Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search path:** `configuration/`
- **Default label:** `main`
- **Consumer bootstrap:** Each service has `bootstrap.yml` (local) and `bootstrap-docker.yml` (Docker) pointing to the config server URI
- **Profile activation:** Docker Compose entrypoints pass `-Dspring.profiles.active=docker`

### 5.6 Netflix Eureka (Service Discovery)

- **Server:** `internet-banking-service-registry` on port 8081
- **Clients:** All business services register as Eureka clients
- **Feign Resolution:** `@FeignClient(name = "core-banking-service")` — resolved via Eureka service name
- **Gateway:** Uses service discovery for route targets

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`; no multi-project root `settings.gradle`)
- **Java Version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Gradle Plugins:**
  - `org.springframework.boot` 3.2.4
  - `io.spring.dependency-management` 1.1.4
  - `com.gorylenko.gradle-git-properties` 2.4.2 (all except service-registry)

### 6.2 Docker Compose Deployment

Two Compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack — all infrastructure + all 7 application services |
| `docker-compose-support-apps.yml` | Infrastructure only — Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry |

**Startup Orchestration:**
- Services use `wait-for-it.sh` entrypoint scripts to ensure dependencies are ready
- Dependency chain: Config Server + Service Registry + MySQL → Application services
- Static IP assignments on a custom bridge network (`172.25.0.0/16`)

### 6.3 Container Images

- Pre-built images published to Docker Hub under `javatodev/*`
- No CI/CD pipeline defined in the repository (no Jenkinsfile, GitHub Actions, or Gradle Docker plugin)
- Each service has a `Dockerfile` (implied by image naming convention) but Dockerfiles are not present in the repository

### 6.4 Testing

- **Test Framework:** JUnit 5 (Jupiter) via `spring-boot-starter-test`
- **In-memory DB:** H2 for test profiles in business services
- **Existing Tests (core-banking-service only):**
  - `AccountServiceTest` — 6 unit tests (mock-based)
  - `TransactionServiceTest` — 10 unit tests (mock-based)
  - `UserServiceTest` — 3 unit tests (mock-based)
- **Other services:** Only default Spring Boot context-load tests (`*ApplicationTests`)

### 6.5 API Documentation

- **Library:** `springdoc-openapi-starter-webflux-ui:2.1.0` (included in 4 business services)
- **Annotations:** `@Tag` and `@Operation` on all controllers
- **Postman Collection:** Exported collection and environment in `postman_collection/`

### 6.6 Dependency Summary

| Dependency | Used In |
|-----------|---------|
| Spring Boot Starter Web | core-banking, user, fund-transfer, utility-payment, service-registry |
| Spring Boot Starter Data JPA | core-banking, user, fund-transfer, utility-payment |
| Spring Cloud Starter Netflix Eureka Client | core-banking, user, fund-transfer, utility-payment, api-gateway |
| Spring Cloud Starter Netflix Eureka Server | service-registry |
| Spring Cloud Starter Gateway | api-gateway |
| Spring Cloud Starter OpenFeign | user, fund-transfer, utility-payment |
| Spring Cloud Starter Config | core-banking, user, fund-transfer, utility-payment, api-gateway |
| Spring Boot Starter Actuator | All services |
| Spring Boot Starter OAuth2 Client/Resource Server | api-gateway |
| Spring Boot Starter Security | api-gateway |
| Keycloak Admin Client 24.0.4 | user |
| Flyway Core + MySQL 10.12.0 | core-banking |
| MySQL Connector/J 8.4.0 | core-banking, user, fund-transfer, utility-payment |
| Lombok | core-banking, user, fund-transfer, utility-payment |
| Micrometer Tracing (Brave) + Zipkin Reporter | All business services |
| SpringDoc OpenAPI 2.1.0 | core-banking, user, fund-transfer, utility-payment |
| H2 Database (test) | core-banking, user, fund-transfer, utility-payment |
