# Application Knowledge Base

## 1. Architecture Overview

### System Context

The Internet Banking Concept application is a Java 21 / Spring Boot 3.2.4 microservices system that simulates an internet banking platform. It follows a classic Spring Cloud architecture with centralized configuration, service discovery, and an API gateway.

### Microservices Inventory

| Service | Port | Purpose | Database |
|---------|------|---------|----------|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | Internet banking user registration & management via Keycloak | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, security, auth enforcement | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |

### Communication Patterns

```
                        ┌─────────────────────┐
                        │   Keycloak (8080)    │
                        │  OAuth2 / OIDC IdP   │
                        └──────────┬──────────┘
                                   │ JWT validation
                        ┌──────────▼──────────┐
  Clients ──────────►   │  API Gateway (8082)  │
                        │  Spring Cloud Gateway│
                        └──┬──────┬──────┬────┘
                           │      │      │
              ┌────────────▼┐  ┌──▼──────▼────────────┐
              │ User Service │  │ Fund Transfer Service │
              │   (8083)     │  │       (8084)          │
              └──────┬───────┘  └──────────┬────────────┘
                     │ Feign               │ Feign
              ┌──────▼─────────────────────▼────────────┐
              │         Core Banking Service (8092)      │
              └──────────────────┬───────────────────────┘
                                 │
                          ┌──────▼──────┐
                          │  MySQL DB   │
                          │  (3306)     │
                          └─────────────┘

  ┌────────────────────────┐       ┌──────────────┐
  │ Utility Payment Service│───►   │ Core Banking  │
  │       (8085)           │ Feign │   Service     │
  └────────────────────────┘       └──────────────┘

  All services register with ──► Eureka Service Registry (8081)
  All services pull config from ──► Config Server (8090)
  All services report traces to ──► Zipkin (9411)
```

**Inter-service communication:** Synchronous HTTP via **OpenFeign** clients. Services discover each other through **Eureka** service registry using logical service names.

**Authentication flow:** The API Gateway acts as an OAuth2 Resource Server. It validates JWT tokens issued by Keycloak, extracts the principal, and forwards the user identity as an `X-Auth-Id` HTTP header to downstream services. Downstream services extract this header via `AppAuthUserFilter` servlet filters.

**Configuration:** Externalized via **Spring Cloud Config Server**, backed by a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`). Each service connects to the config server at bootstrap time. Profile-specific bootstrap files (`bootstrap-docker.yml`, `bootstrap-dev.yml`) switch the config server URL per environment.

**Distributed tracing:** Micrometer Tracing with Brave bridge + Zipkin reporter across all services.

### Infrastructure Components

| Component | Image/Version | Purpose |
|-----------|---------------|---------|
| MySQL | 8.4.0 (custom Dockerfile) | Shared database server (4 logical databases) |
| PostgreSQL | 15 | Keycloak's backing database |
| Keycloak | 23.0.7 | Identity and Access Management (OAuth2/OIDC) |
| Zipkin | 3 | Distributed tracing UI and collector |
| Eureka | Embedded (Spring Cloud 2023.0.0) | Service discovery |

---

## 2. Data Model Documentation

### Core Banking Service

The core banking service owns the primary banking domain model via Flyway-managed migrations.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | Email address |
| `identification_number` | VARCHAR(255) | | National ID / NIC number (unique business key) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | Actual account balance |
| `available_balance` | DECIMAL(19,2) | | Available balance (may differ from actual) |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID identifying the transaction |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE, AIRTEL) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_transaction` N:1 `banking_core_account` (via `account_id` FK)
- `banking_core_utility_account` is a standalone reference table

### Internet Banking User Service

Uses JPA with `ddl-auto` for schema creation (no Flyway).

#### `user` table (JPA-managed)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC linking to core banking user |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR | Audit: creating user |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR | Audit: modifying user |
| `version` | BIGINT | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer` table (JPA-managed)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `transaction_reference` | VARCHAR | UUID from core banking transaction |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### Internet Banking Utility Payment Service

#### `utility_payment` table (JPA-managed)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Utility reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | | Same as fund transfer |

---

## 3. API Surface Map

### Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Retrieve bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Retrieve utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute a fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process a utility bill payment |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated list of all users |

### Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User` | Register a new internet banking user (creates Keycloak account + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (e.g., APPROVED enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated list of internet banking users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by internal ID |

### Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` | Initiate a fund transfer (delegates to core banking) |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | Paginated list of fund transfer records |

### Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process a utility payment (delegates to core banking) |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | Paginated list of utility payment records |

### API Gateway Routes (port 8082)

The gateway routes are configured via Spring Cloud Config (external Git repo). Based on the codebase structure, the gateway proxies:

| Route Prefix | Target Service | Notes |
|-------------|----------------|-------|
| `/user/**` | `internet-banking-user-service` | `/user/api/v1/bank-users/register` is publicly accessible |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | Requires JWT authentication |
| `/banking-core/**` | `core-banking-service` | Requires JWT authentication |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | Requires JWT authentication |

**Public endpoints** (no auth required):
- `POST /user/api/v1/bank-users/register`
- All `/actuator/**` endpoints (per service prefix)

### Authentication Endpoint (via Keycloak)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `http://{keycloak}:8080/realms/javatodev-internet-banking/protocol/openid-connect/token` | Obtain JWT token |

**Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`
2. User Service checks Keycloak for existing email — throws `UserAlreadyRegisteredException` if exists
3. User Service calls Core Banking via Feign to verify identification number matches a known banking customer
4. Validates that the provided email matches the core banking user's email — throws `InvalidEmailException` if mismatch
5. Creates a Keycloak user (disabled, email not verified) with the provided password
6. Saves a local `UserEntity` with `status=PENDING` and the Keycloak `authId`
7. An admin must later call `PATCH /update/{id}` with `status=APPROVED` to enable the Keycloak account

### Fund Transfer Flow

1. Client calls `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`
2. Fund Transfer Service creates a `FundTransferEntity` with `status=PENDING` and persists it
3. Delegates to Core Banking Service via Feign (`POST /api/v1/transaction/fund-transfer`)
4. Core Banking validates both accounts exist, checks sufficient balance in source account
5. Core Banking deducts from source, credits to destination, creates two `TransactionEntity` records (debit + credit)
6. Returns `transactionId` (UUID) to Fund Transfer Service
7. Fund Transfer Service updates its record with `transactionReference` and `status=SUCCESS`

**Balance validation rule:** `actualBalance >= 0 AND actualBalance >= transferAmount`

### Utility Payment Flow

1. Client calls `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`
2. Utility Payment Service creates entity with `status=PROCESSING`, persists it
3. Delegates to Core Banking via Feign (`POST /api/v1/transaction/util-payment`)
4. Core Banking validates source account balance, looks up utility provider by ID
5. Deducts amount from source account (both `actualBalance` and `availableBalance`)
6. Creates a `TransactionEntity` record, returns `transactionId`
7. Utility Payment Service updates its record with `transactionId` and `status=SUCCESS`

### User Status Management

| Status | Meaning |
|--------|---------|
| `PENDING` | Newly registered, Keycloak account disabled |
| `APPROVED` | Admin-approved, Keycloak account enabled + email verified |
| `DISABLED` | Manually disabled |
| `BLACKLIST` | Blacklisted user |

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Realm:** `javatodev-internet-banking`
- **Integration type:** Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`) for user management, OAuth2 Resource Server for JWT validation
- **Client:** `internet-banking-api-client` (client_credentials grant for admin operations)
- **Configuration:** `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Used by:** `internet-banking-user-service` (user CRUD), `internet-banking-api-gateway` (JWT validation via JWK Set URI)

### MySQL Database

- **Version:** 8.4.0 (custom Docker image)
- **Connection:** Single MySQL server, multiple logical databases
- **User:** `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql` init script)
- **Databases:** `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **Migration:** Core Banking uses Flyway; other services use JPA `ddl-auto`

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services
- **Feign tracing:** `feign-micrometer` for automatic span propagation across Feign calls

### RabbitMQ (Message Broker)

- **Status:** Referenced in README as planned for notification service, but **not currently implemented** in the codebase
- **Planned use:** Fund transfer and utility payment services would publish notification messages

### Spring Cloud Config Server

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Bootstrap:** Each service connects at startup via `spring.cloud.config.uri` in `bootstrap.yml`

### Netflix Eureka Service Registry

- **Port:** 8081
- **Self-registration:** Disabled (`register-with-eureka: false`, `fetch-registry: false`)
- **Client services:** All business services register and discover via Eureka
- **Feign resolution:** `@FeignClient(value = "core-banking-service")` resolves via Eureka

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build tool:** Gradle (per-service `build.gradle`, no multi-module root build)
- **Java version:** 21 (Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties` for Git metadata in Actuator info endpoint

### Docker

Each service has an identical Dockerfile pattern:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
EXPOSE {port}
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry)

**Custom network:** `javatodev_ib_network` (172.25.0.0/16) with static IPs per container.

**Service startup ordering:** `wait-for-it.sh` scripts ensure services wait for Config Server (8090), Service Registry (8081), and MySQL (3306) before starting.

### Profiles

| Profile | Purpose | Config Server URL |
|---------|---------|-------------------|
| (default) | Local development | `http://localhost:8090` |
| `dev` | Dev environment | `http://192.168.1.5:8090` |
| `docker` | Docker Compose | `http://internet-banking-config-server:8090` |

### Test Configuration

- **Test framework:** JUnit 5 (JUnit Platform)
- **Test database:** H2 in-memory (`jdbc:h2:mem:{db_name}`)
- **Flyway:** Disabled in test profiles
- **Eureka:** Disabled in test profiles for user, fund-transfer, and utility-payment services
- **Unit tests exist for:** Core Banking Service only (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`)
- **Other services:** Only have empty Spring Boot context-load tests
