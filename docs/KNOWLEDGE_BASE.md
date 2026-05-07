# Knowledge Base: Internet Banking Microservices

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** microservices banking application composed of **6 microservices** plus supporting infrastructure containers. The system implements an internet banking concept with user management, fund transfers, and utility payments built on top of a core banking engine.

### 1.2 Microservices Inventory

| Service | Port | Responsibility | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: user lookup, account management, fund transfers, utility payments | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests via core banking service | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests via core banking service | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway, OAuth2/JWT security, route management | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |

### 1.3 Infrastructure Components

| Component | Technology | Port | Purpose |
|---|---|---|---|
| Database | MySQL 8 | 3306 | Persistent storage for all business services |
| Identity Provider | Keycloak 23.0.7 | 8080 | OAuth2/OIDC authentication & user management |
| Keycloak DB | PostgreSQL 15 | 5432 (internal) | Keycloak persistence |
| Distributed Tracing | Zipkin 3 | 9411 | Request tracing across services |

### 1.4 Communication Patterns

```
Client --> [API Gateway :8082] --> [Service Registry :8081] --> Target Service
                  |                                                    |
                  |--- JWT Validation (Keycloak)                       |--- OpenFeign (service-to-service)
                  |--- X-Auth-Id Header Injection                      |--- Eureka Client Discovery
```

- **External traffic** enters through the **API Gateway**, which enforces OAuth2/JWT authentication via Keycloak.
- **Service-to-service** communication uses **Spring Cloud OpenFeign** with Eureka-based service discovery (no hardcoded URLs).
- **Configuration** is centralized via **Spring Cloud Config Server** backed by a Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **User identity propagation**: The API Gateway extracts the authenticated principal name and injects it as an `X-Auth-Id` HTTP header, which downstream `AppAuthUserFilter` filters read and store in a thread-local `ApiRequestContextHolder`.

### 1.5 Service Startup Order

Docker Compose uses `wait-for-it.sh` to enforce:
1. **Config Server** + **Service Registry** start first
2. **API Gateway** waits for both
3. **Business services** (user, fund-transfer, utility-payment, core-banking) wait for Config Server, Service Registry, and MySQL

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT FK | References `banking_core_user.id` |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | e.g. VODAFONE, VERIZON |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID-based |
| `account_id` | BIGINT FK | References `banking_core_account.id` |

**Relationships:**
- `User` 1:N `BankAccount` (one user has many accounts)
- `BankAccount` 1:1 `Transaction` (via `@OneToOne` with `CascadeType.ALL` -- note: this is likely a modeling issue, should be 1:N)

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (via JPA auto-DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID reference |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_by` | VARCHAR | Audit field (from `AuditAware`) |
| `created_date` | TIMESTAMP | Audit field |
| `last_modified_by` | VARCHAR | Audit field |
| `last_modified_date` | TIMESTAMP | Audit field |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (via JPA auto-DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| Audit fields | | Inherited from `AuditAware` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (via JPA auto-DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| Audit fields | | Inherited from `AuditAware` |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` DTO |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` DTO |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | - | `UtilityAccount` DTO |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

### 3.2 Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` DTO | `User` DTO |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g. approve) | `UserUpdateRequest` | `User` DTO |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` DTO |

### 3.3 Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.5 API Gateway Routes (`:8082`)

Routes are configured via Spring Cloud Config (external Git repo). Based on security configuration, the gateway exposes:

| Route Prefix | Target Service | Auth Required |
|---|---|---|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` | Various | No (all actuator endpoints open) |

### 3.6 Key Request/Response Shapes

**FundTransferRequest:**
```json
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }
```

**FundTransferResponse:**
```json
{ "message": "string", "transactionId": "UUID" }
```

**UtilityPaymentRequest:**
```json
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }
```

**User (registration):**
```json
{ "email": "string", "identification": "string", "password": "string" }
```

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow
1. Client calls `POST /user/api/v1/bank-users/register` (no auth required)
2. User Service checks if email already exists in Keycloak
3. Fetches user from Core Banking Service by identification number (Feign)
4. Validates email matches core banking record
5. Creates Keycloak user (disabled, email unverified, with provided password)
6. Saves local user record with `PENDING` status
7. Admin approves via `PATCH /user/api/v1/bank-users/update/{id}` setting status to `APPROVED`, which enables the Keycloak account

### 4.2 Fund Transfer Flow
1. Client calls `POST /fund-transfer/api/v1/transfer`
2. Fund Transfer Service saves a `PENDING` record
3. Calls Core Banking Service `POST /api/v1/transaction/fund-transfer` via Feign
4. Core Banking validates balances, debits source, credits destination, creates transaction records
5. Fund Transfer Service updates local record to `SUCCESS`

### 4.3 Utility Payment Flow
1. Client calls `POST /utility-payment/api/v1/utility-payment`
2. Utility Payment Service saves a `PROCESSING` record
3. Calls Core Banking Service `POST /api/v1/transaction/util-payment` via Feign
4. Core Banking validates balance, debits source account, creates transaction record
5. Utility Payment Service updates local record to `SUCCESS`

### 4.4 Balance Validation
- Applied in Core Banking `TransactionService.validateBalance()`
- Checks: `actualBalance >= 0` AND `actualBalance >= requestedAmount`
- Throws `InsufficientFundsException` on failure

### 4.5 Audit Trail
- `AuditAware` base class with `@MappedSuperclass` provides `createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate`
- `AuditorAwareConfig` reads the authenticated user from `ApiRequestContextHolder` (populated by the `AppAuthUserFilter` from `X-Auth-Id` header)

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)
- **Version**: 23.0.7
- **Connection**: User Service communicates via `keycloak-admin-client:24.0.4`
- **Configuration**: `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Singleton pattern**: `KeycloakProperties` uses a static `Keycloak` instance (not thread-safe, not refreshable)
- **Import on startup**: Docker Compose mounts `./keycloak` data for realm import

### 5.2 MySQL Database
- **Version**: MySQL 8 (custom Docker image with `privileges.sql` init script)
- **Connection**: All 4 business services connect to the same MySQL instance, each using a separate database
- **Credentials**: User `javatodev_development` / password `oPItyPticIAt` (hardcoded in `privileges.sql`)
- **Schema management**: Core Banking uses Flyway; other services rely on JPA auto-DDL

### 5.3 Spring Cloud Config Server
- **Backend**: Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`, search path: `configuration`
- **Bootstrap**: Each service has `bootstrap.yml` (localhost) and `bootstrap-docker.yml` (Docker hostname) pointing to config server at port 8090

### 5.4 Eureka Service Registry
- **Self-registration**: Disabled (`register-with-eureka: false`, `fetch-registry: false`)
- **All business services** register as Eureka clients
- **Feign clients** use Eureka service names (e.g., `@FeignClient(name = "core-banking-service")`)

### 5.5 Zipkin (Distributed Tracing)
- **Version**: Zipkin 3
- **Integration**: Via Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`)
- **Port**: 9411

### 5.6 OpenAPI / Swagger
- **Library**: `springdoc-openapi-starter-webflux-ui:2.1.0`
- **Annotations**: `@Tag` and `@Operation` present on all controllers

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System
- **Build tool**: Gradle with Spring Boot plugin 3.2.4, Dependency Management plugin 1.1.4
- **Java**: Source compatibility 21
- **Additional plugins**: `com.gorylenko.gradle-git-properties:2.4.2` (generates `git.properties` for info endpoint)
- **No multi-module build**: Each service is an independent Gradle project (no shared parent `settings.gradle`)

### 6.2 Docker
- **Base image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Each service has an identical Dockerfile structure: copy JAR, copy `wait-for-it.sh`, install bash, set entrypoint
- **Profiles**: Docker profile activated via `-Dspring.profiles.active=docker`

### 6.3 Docker Compose
- **Network**: Custom bridge network `javatodev_ib_network` with static IPs (`172.25.0.0/16`)
- **Two compose files**:
  - `docker-compose.yml` -- full stack (all services + infrastructure)
  - `docker-compose-support-apps.yml` -- infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

### 6.4 CI/CD
- **No CI/CD pipeline** is currently configured. The `.github` directory contains only a `FUNDING.yml` file. No GitHub Actions workflows, no Jenkinsfile, no GitLab CI.

### 6.5 Test Data
- Flyway migrations seed 4 test users and 14 bank accounts with balances
- 6 utility providers pre-loaded (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Keycloak realm data imported on startup
- Default test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
