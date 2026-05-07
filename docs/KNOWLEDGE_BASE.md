# Application Knowledge Base

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## 1. Architecture Overview

### 1.1 Service Inventory

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| **internet-banking-api-gateway** | 8082 | Single entry point; OAuth2/JWT enforcement; request routing | Spring Cloud Gateway, Spring Security OAuth2 Resource Server, Eureka Client |
| **core-banking-service** | 8092 | System of record for accounts, users, and transaction ledger | Spring Data JPA, MySQL, Flyway, Eureka Client |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | Spring Data JPA, OpenFeign, Keycloak Admin Client, MySQL, Eureka Client |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between bank accounts | Spring Data JPA, OpenFeign, MySQL, Eureka Client |
| **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments (water, telecom, etc.) | Spring Data JPA, OpenFeign, MySQL, Eureka Client |
| **internet-banking-service-registry** | 8081 | Netflix Eureka discovery server | Spring Cloud Netflix Eureka Server |
| **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config | Spring Cloud Config Server (Git backend) |

### 1.2 Communication Patterns

```
                        [Client]
                           |
                     [API Gateway :8082]
                      OAuth2 + JWT via Keycloak
                     /       |         \
                    /        |          \
     [User Service :8083] [Fund Transfer :8084] [Utility Payment :8085]
           |                 |                    |
           |        (OpenFeign REST)       (OpenFeign REST)
           |                 |                    |
           +-------->[Core Banking Service :8092]<+
                             |
                         [MySQL :3306]
```

**All inter-service communication is synchronous REST via OpenFeign.** Services discover each other through Eureka.

| Pattern | Technology | Details |
|---|---|---|
| Client-to-Gateway | HTTP/HTTPS | JWT Bearer token required (except `/user/api/v1/bank-users/register`) |
| Gateway-to-Services | Spring Cloud Gateway | Reactive proxy with Eureka-based load balancing |
| Service-to-Service | OpenFeign | Declarative REST clients resolved via Eureka service names |
| Config Distribution | Spring Cloud Config | Bootstrap-phase config fetch from Git-backed Config Server |
| Service Discovery | Netflix Eureka | Client-side discovery; all services register on startup |
| Auth Identity Propagation | HTTP Header | Gateway extracts JWT `sub` claim and forwards as `X-Auth-Id` header |

### 1.3 Infrastructure Components

| Component | Technology | Version | Docker IP |
|---|---|---|---|
| Database (application) | MySQL | 8.4.0 | 172.25.0.9 |
| Database (Keycloak) | PostgreSQL | 15 | 172.25.0.10 |
| Identity Provider | Keycloak | 23.0.7 | 172.25.0.11 |
| Distributed Tracing | Zipkin | 3 | 172.25.0.12 |
| Message Broker | RabbitMQ | N/A | Not deployed (referenced in README, not implemented) |

### 1.4 Network Topology (Docker)

All containers run on a custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and gateway `172.25.0.1`. Each container has a static IPv4 address.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | |
| `first_name` | `VARCHAR(255)` | nullable | |
| `last_name` | `VARCHAR(255)` | nullable | |
| `email` | `VARCHAR(255)` | nullable | No unique constraint |
| `identification_number` | `VARCHAR(255)` | nullable | NIC/National ID; no unique constraint, no index |

#### `banking_core_account`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | nullable | Account number; no unique constraint, no index |
| `type` | `VARCHAR(255)` | nullable | Enum: `SAVINGS_ACCOUNT` |
| `status` | `VARCHAR(255)` | nullable | Enum: `ACTIVE` |
| `actual_balance` | `DECIMAL(19,2)` | nullable | Ledger balance |
| `available_balance` | `DECIMAL(19,2)` | nullable | Withdrawable balance |
| `user_id` | `BIGINT(20)` | FK -> `banking_core_user(id)` | |

#### `banking_core_transaction`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | |
| `amount` | `DECIMAL(19,2)` | nullable | Negative for debits |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Target account number or utility reference |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID; shared by debit/credit pair |
| `account_id` | `BIGINT(20)` | FK -> `banking_core_account(id)` | |

#### `banking_core_utility_account`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | nullable | Provider account number |
| `provider_name` | `VARCHAR(255)` | nullable | e.g., VODAFONE, AIRTEL |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK, though mapped as `@OneToOne` in JPA)
- `banking_core_utility_account` is standalone (no FK relationships)

### 2.2 User Service (MySQL: `banking_core_user_service`)

**Schema auto-generated by JPA (`ddl-auto`). No Flyway migrations.**

#### `user` (entity: `UserEntity extends AuditAware`)

| Field | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `auth_id` | `VARCHAR` | Keycloak user UUID |
| `identification` | `VARCHAR` | NIC/National ID |
| `status` | `VARCHAR` | Enum: `PENDING`, `APPROVED` |
| `created_date` | `TIMESTAMP` | Audit field |
| `created_by` | `VARCHAR` | Audit field |
| `modified_date` | `TIMESTAMP` | Audit field |
| `modified_by` | `VARCHAR` | Audit field |
| `version` | `BIGINT` | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

**Schema auto-generated by JPA. No Flyway migrations.**

#### `fund_transfer` (entity: `FundTransferEntity extends AuditAware`)

| Field | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `transaction_reference` | `VARCHAR` | UUID from Core Banking |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL` | Transfer amount |
| `status` | `VARCHAR` | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

**Schema auto-generated by JPA. No Flyway migrations.**

#### `utility_payment` (entity: `UtilityPaymentEntity extends AuditAware`)

| Field | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL` | Payment amount |
| `reference_number` | `VARCHAR` | Bill reference |
| `account` | `VARCHAR` | Source bank account number |
| `transaction_id` | `VARCHAR` | UUID from Core Banking |
| `status` | `VARCHAR` | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway proxies requests to downstream services using Eureka-resolved service names. Route prefixes (configured in Spring Cloud Config, not in local files):

| Route Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Authentication:** All routes require a valid JWT Bearer token except:
- `POST /user/api/v1/bank-users/register` (permitAll)
- `/actuator/**` for all services (permitAll)

### 3.2 Core Banking Service Endpoints

**Base URL:** `http://core-banking-service:8092`

| Method | Path | Request Body | Response Body | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | — (Pageable query params) | `List<User>` | Paginated user list |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |

**DTO Shapes:**

```
FundTransferRequest:
  fromAccount: String
  toAccount: String
  amount: BigDecimal

FundTransferResponse:
  message: String
  transactionId: String

UtilityPaymentRequest:
  providerId: Long
  amount: BigDecimal
  referenceNumber: String
  account: String

UtilityPaymentResponse:
  message: String
  transactionId: String

BankAccount:
  id: Long
  number: String
  type: AccountType (SAVINGS_ACCOUNT)
  status: AccountStatus (ACTIVE)
  availableBalance: BigDecimal
  actualBalance: BigDecimal
  user: User

User (core-banking):
  id: Long
  firstName: String
  lastName: String
  email: String
  identificationNumber: String
  bankAccounts: List<BankAccount>
```

### 3.3 User Service Endpoints

**Base URL:** `http://internet-banking-user-service:8083`

| Method | Path | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | — (Pageable query params) | `List<User>` | Paginated user list |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by database ID |

**DTO Shapes:**

```
User (user-service):
  id: Long
  email: String
  identification: String
  password: String          # Write-only (for registration)
  authId: String            # Keycloak UUID
  status: Status (PENDING, APPROVED)
  version: long             # Inherited from AuditAware

UserUpdateRequest:
  status: Status
```

### 3.4 Fund Transfer Service Endpoints

**Base URL:** `http://internet-banking-fund-transfer-service:8084`

| Method | Path | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | — (Pageable query params) | `List<FundTransfer>` | Paginated transfer history |

**DTO Shapes:**

```
FundTransferRequest (fund-transfer-service):
  fromAccount: String
  toAccount: String
  amount: BigDecimal
  authID: String

FundTransfer:
  id: Long
  transactionReference: String
  status: String
  fromAccount: String
  toAccount: String
  amount: BigDecimal
```

### 3.5 Utility Payment Service Endpoints

**Base URL:** `http://internet-banking-utility-payment-service:8085`

| Method | Path | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | — (Pageable query params) | `List<UtilityPayment>` | Paginated payment history |

**DTO Shapes:**

```
UtilityPaymentRequest (utility-payment-service):
  providerId: Long
  amount: BigDecimal
  referenceNumber: String
  account: String

UtilityPaymentResponse:
  message: String
  transactionId: String
```

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `GET /` (8081) | Eureka dashboard |
| Config Server | `GET /{application}/{profile}` (8090) | Fetch configuration |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, metrics, etc.) |
| Core Banking | `GET /swagger-ui.html` | OpenAPI UI |
| User Service | `GET /swagger-ui.html` | OpenAPI UI |
| Fund Transfer | `GET /swagger-ui.html` | OpenAPI UI |
| Utility Payment | `GET /swagger-ui.html` | OpenAPI UI |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Location:** `core-banking-service/.../service/TransactionService.java`

1. **Validation:** Source account `actualBalance` must be >= transfer `amount` and >= 0.
2. **Debit:** Source account `actualBalance` and `availableBalance` are decremented.
3. **Credit:** Destination account `actualBalance` and `availableBalance` are incremented.
4. **Transaction Records:** Two `TransactionEntity` records are created per transfer — one debit (negative amount) on the source account, one credit (positive amount) on the destination account. Both share the same `transactionId` (UUID).
5. **Atomicity:** The entire operation runs within a single `@Transactional` boundary.

**Orchestration flow (Fund Transfer Service -> Core Banking):**
1. Fund Transfer Service receives request and saves a `FundTransferEntity` with status `PENDING`.
2. Calls Core Banking's `/api/v1/transaction/fund-transfer` via Feign.
3. On success, updates the entity to `SUCCESS` with the `transactionReference`.

**Known Bug:** Balance double-debit — `availableBalance` is set to `actualBalance.subtract(amount)` *after* `actualBalance` was already decremented, causing a double subtraction.

### 4.2 Utility Payment Processing

**Location:** `core-banking-service/.../service/TransactionService.java`

1. **Validation:** Source account balance check (same as fund transfer).
2. **Provider Lookup:** Utility provider resolved by `providerId`.
3. **Debit:** Source account balance decremented.
4. **Transaction Record:** Single debit transaction with `UTILITY_PAYMENT` type.

**Orchestration flow (Utility Payment Service -> Core Banking):**
1. Utility Payment Service saves a `UtilityPaymentEntity` with status `PROCESSING`.
2. Calls Core Banking's `/api/v1/transaction/util-payment` via Feign.
3. On success, updates entity to `SUCCESS` with the `transactionId`.

### 4.3 User Registration & Management

**Location:** `internet-banking-user-service/.../service/UserService.java`

**Registration Flow:**
1. Check if email already exists in Keycloak (reject if duplicate).
2. Verify the user exists in Core Banking by `identification` number (NIC).
3. Validate that the email matches the Core Banking record.
4. Create user in Keycloak (disabled, email not verified).
5. Save user locally with `status = PENDING` and `authId` from Keycloak.

**Approval Flow:**
1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `status: APPROVED`.
2. User Service enables the Keycloak account and marks email as verified.
3. Updates local entity status to `APPROVED`.

### 4.4 Authentication Flow

1. Client authenticates directly with Keycloak (OAuth2 token endpoint) to obtain a JWT.
2. All subsequent API requests include the JWT as a `Bearer` token in the `Authorization` header.
3. API Gateway validates the JWT against Keycloak's JWK Set URI.
4. Gateway extracts the authenticated principal name and forwards it as `X-Auth-Id` header.
5. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in a `ThreadLocal` (`ApiRequestContextHolder`) for audit purposes.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Details |
|---|---|
| Version | 23.0.7 |
| Realm | Imported via `realm-export.json` at startup |
| Admin API | Used by User Service via `keycloak-admin-client:24.0.4` |
| Authentication | `client_credentials` grant type |
| JWT Validation | API Gateway validates tokens via JWK Set URI |
| Configuration | `app.config.keycloak.server-url`, `.realm`, `.clientId`, `.client-secret` (from Spring Cloud Config) |

### 5.2 MySQL

| Aspect | Details |
|---|---|
| Version | 8.4.0 |
| Databases | `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| App User | `javatodev_development` (created via `privileges.sql` init script) |
| Migrations | Flyway (Core Banking only); JPA `ddl-auto` for other services |
| Driver | `mysql-connector-j:8.4.0` |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Details |
|---|---|
| Version | 3 (latest) |
| Bridge | `micrometer-tracing-bridge-brave` |
| Reporter | `zipkin-reporter-brave` |
| Feign Integration | `feign-micrometer` for trace propagation across Feign calls |
| Port | 9411 |

### 5.4 RabbitMQ (Not Implemented)

Referenced in README for notification service but **not deployed or used** in any service. No `spring-boot-starter-amqp` dependency exists in any `build.gradle`.

### 5.5 Spring Cloud Config (External Configuration)

| Aspect | Details |
|---|---|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch | `main` |
| Search Path | `configuration` |
| Bootstrap | Services use `bootstrap.yml` / `bootstrap-{profile}.yml` to connect to Config Server |
| Profiles | `default` (localhost), `dev` (192.168.1.5), `docker` (container hostname) |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service, no root multi-module project)
- **Gradle Wrapper:** Each service has its own `gradlew` / `gradle/wrapper`
- **Java Version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot Plugin:** 3.2.4
- **Dependency Management:** Spring Cloud BOM `2023.0.0`
- **Additional Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (generates `git.properties` at build time)

### 6.2 Docker Build

Each service has a `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build steps (per service):**
1. `./gradlew build` — Produces fat JAR in `build/libs/`
2. `docker build -t javatodev/{service-name} .` — Builds Docker image

### 6.3 Docker Compose Deployment

**Files:**
- `docker-compose/docker-compose.yml` — Full stack (all services + infrastructure)
- `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup Order (enforced by `wait-for-it.sh`):**
1. MySQL, PostgreSQL, Zipkin, Keycloak (no dependencies)
2. Config Server (no dependencies)
3. Service Registry (no dependencies)
4. API Gateway (waits for: Service Registry, Config Server)
5. Business services (wait for: Service Registry, Config Server, MySQL)

### 6.4 CI/CD

**No CI/CD pipeline exists.** The `.github/` directory contains only `FUNDING.yml`. There are no GitHub Actions workflows, Jenkinsfiles, or other automation.

### 6.5 Seed Data

Core Banking is bootstrapped with Flyway migration `V1.0.20210427174721__temp_data.sql`:
- 4 test users (Sam, Guru, Ragu, Randor)
- 14 bank accounts with pre-loaded balances
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak is bootstrapped with `realm-export.json` containing a pre-configured realm, client, and user data.

**Test credentials:** `ib_admin@javatodev.com / 5V7huE3G86uB`
