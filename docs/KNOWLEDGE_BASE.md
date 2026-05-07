# Application Knowledge Base

> **Repository:** ts-java-spring-boot-internet-banking-microservices
> **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0
> **Last Updated:** 2026-05-07

---

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Port | Type | Description |
|---|---------|------|------|-------------|
| 1 | `internet-banking-config-server` | 8090 | Infrastructure | Spring Cloud Config Server; serves externalized configuration from a [Git-backed repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). |
| 2 | `internet-banking-service-registry` | 8081 | Infrastructure | Netflix Eureka Server; provides service discovery for all downstream services. |
| 3 | `internet-banking-api-gateway` | 8082 | Infrastructure | Spring Cloud Gateway; single entry point, routes requests to downstream services, enforces OAuth2/JWT security via Keycloak. |
| 4 | `core-banking-service` | 8092 | Business | Core banking engine; owns accounts, users, transactions. Processes fund transfers and utility payments at the ledger level. |
| 5 | `internet-banking-user-service` | 8083 | Business | User registration and management; integrates with Keycloak Admin API for identity provisioning and with core-banking-service for user verification. |
| 6 | `internet-banking-fund-transfer-service` | 8084 | Business | Orchestrates fund transfers; records transfer state locally, delegates to core-banking-service via Feign. |
| 7 | `internet-banking-utility-payment-service` | 8085 | Business | Orchestrates utility bill payments; records payment state locally, delegates to core-banking-service via Feign. |
| 8 | *Notification service* | — | Business | Referenced in README but **not yet implemented**. Intended to consume RabbitMQ messages and push notifications. |

### 1.2 Communication Patterns

```
                                  ┌─────────────────────┐
                                  │   Keycloak (IdP)    │
                                  │   Port 8080         │
                                  └────────┬────────────┘
                                           │ JWT validation
┌──────────┐   HTTP    ┌──────────────────────────────────────┐
│  Client   │─────────▶│   API Gateway (8082)                 │
└──────────┘           │   - OAuth2 Resource Server           │
                       │   - Route: /user/** → user-service   │
                       │   - Route: /fund-transfer/** → ft    │
                       │   - Route: /banking-core/** → core   │
                       │   - Route: /utility-payment/** → up  │
                       └──────┬──────┬──────┬──────┬──────────┘
                              │      │      │      │
          ┌───────────────────┘      │      │      └───────────────────┐
          ▼                          ▼      ▼                          ▼
  ┌───────────────┐   ┌──────────────────┐  ┌───────────────────┐  ┌────────────────────┐
  │ User Service  │   │ Fund Transfer    │  │ Core Banking      │  │ Utility Payment    │
  │ (8083)        │   │ Service (8084)   │  │ Service (8092)    │  │ Service (8085)     │
  └───────┬───────┘   └────────┬─────────┘  └───────────────────┘  └─────────┬──────────┘
          │ Feign               │ Feign             ▲  ▲                      │ Feign
          └────────────────────┼────────────────────┘  │                      │
                               └───────────────────────┘──────────────────────┘
```

- **Synchronous:** All inter-service calls use **OpenFeign** clients over HTTP, resolved through Eureka service discovery.
- **Asynchronous:** RabbitMQ is listed in the tech stack and Docker Compose infrastructure but is **not wired** into any service code yet (Notification service is pending).
- **Configuration:** All services bootstrap from the Config Server, which pulls from a remote Git repository (`internet-banking-microservices-configurations`).
- **Service Discovery:** Eureka-based. Each service registers with the Service Registry on startup.
- **Auth propagation:** The API Gateway extracts the JWT principal name and forwards it as `X-Auth-Id` header to downstream services via a `GlobalFilter`.

### 1.3 Infrastructure Components

| Component | Image / Tech | Purpose |
|-----------|-------------|---------|
| MySQL 8.4 | Custom Dockerfile (`docker-compose/mysql`) | Primary data store for all business services |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | Identity and access management (OAuth2/OIDC) |
| PostgreSQL 15 | `postgres:15` | Keycloak's backing database |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing collector |
| RabbitMQ | Referenced in stack, **not in Docker Compose** | Message broker (not yet integrated) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / SSN used for lookups |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Balance available for transactions |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Target account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping debit/credit legs |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Utility provider ID |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE, AIRTEL) |

**Schema management:** Flyway migrations in `core-banking-service/src/main/resources/db/migration/`.

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, no Flyway)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK | Internal user ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC / identification number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, no Flyway)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK | Transfer ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Core banking transaction UUID |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, no Flyway)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK | Payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Paying account number |
| `transaction_id` | VARCHAR | Core banking transaction UUID |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All external requests route through the gateway with the following path prefixes:

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### 3.2 Core Banking Service Endpoints (Port 8092)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | — | `UtilityAccount { id, number, providerName }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 User Service Endpoints (Port 8083)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 Fund Transfer Service Endpoints (Port 8084)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

### 3.5 Utility Payment Service Endpoints (Port 8085)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Config Server | `GET /{application}/{profile}` | Serve configuration properties |
| Service Registry | Eureka Dashboard at `:8081` | Service discovery UI |
| All services | `/actuator/**` | Spring Boot Actuator (health, info, metrics) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (public endpoint, no JWT required).
2. User Service checks Keycloak for existing email — throws `UserAlreadyRegisteredException` if found.
3. User Service calls Core Banking Service to verify the identification number against `banking_core_user`.
4. Validates that the provided email matches the email on file in core banking.
5. Creates a **disabled** Keycloak user with the provided password.
6. Saves a local `UserEntity` with `status=PENDING` and the Keycloak `authId`.
7. Admin must later call `PATCH /update/{id}` with `status=APPROVED` to:
   - Enable the Keycloak user and mark email as verified.
   - Update local status to `APPROVED`.

### 4.2 Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer`.
2. Fund Transfer Service saves a `FundTransferEntity` with `status=PENDING`.
3. Delegates to Core Banking Service via Feign (`POST /api/v1/transaction/fund-transfer`).
4. Core Banking Service:
   a. Looks up both source and destination `BankAccountEntity` records.
   b. Validates the source account has sufficient balance (`actualBalance >= amount`).
   c. Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   d. Creates a debit `TransactionEntity` (negative amount).
   e. Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   f. Creates a credit `TransactionEntity` (positive amount).
   g. Returns `{ message, transactionId }`.
5. Fund Transfer Service updates local record with `transactionReference` and `status=SUCCESS`.

**Note:** The entire core banking operation runs within a single `@Transactional` boundary.

### 4.3 Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment`.
2. Utility Payment Service saves a `UtilityPaymentEntity` with `status=PROCESSING`.
3. Delegates to Core Banking Service via Feign (`POST /api/v1/transaction/util-payment`).
4. Core Banking Service:
   a. Looks up the source `BankAccountEntity`.
   b. Validates sufficient balance.
   c. Looks up the `UtilityAccountEntity` by provider ID.
   d. Debits the source account.
   e. Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
   f. Returns `{ message, transactionId }`.
5. Utility Payment Service updates local record with `transactionId` and `status=SUCCESS`.

### 4.4 Authentication & Authorization

- **Keycloak realm:** `javatodev-microservices` (imported via JSON realm export at container startup).
- **OAuth2 client:** `javatodev-microservices-client` with `client_credentials` and `password` grant types.
- **Gateway security:** All endpoints require a valid JWT except `/user/api/v1/bank-users/register` and `/actuator/**`.
- **Auth propagation:** The API Gateway extracts `Principal.getName()` from the JWT and passes it downstream as `X-Auth-Id` header.
- **Downstream filters:** Each business service reads `X-Auth-Id` via `AppAuthUserFilter` and stores it in a `ThreadLocal` (`ApiRequestContextHolder`) for JPA auditing.

---

## 5. Integration Points

### 5.1 Keycloak

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **Protocol** | OIDC / OAuth2 |
| **Gateway integration** | JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| **User Service integration** | Keycloak Admin Client 24.0.4 (`keycloak-admin-client`) for user CRUD |
| **Configuration** | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (externalized via Config Server) |
| **Realm import** | `docker-compose/keycloak/javatodev-microservices-realm.json` auto-imported on startup |

### 5.2 RabbitMQ

- Listed in the technology stack but **no dependency declared** in any `build.gradle`.
- No `spring-boot-starter-amqp` present. No message producers or consumers implemented.
- The Notification Service that would consume messages is marked as **PENDING**.

### 5.3 Zipkin / Distributed Tracing

| Aspect | Detail |
|--------|--------|
| **Collector** | Zipkin 3 at port 9411 |
| **Client libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave` |
| **Feign tracing** | `feign-micrometer` for propagating trace context across Feign calls |
| **Services instrumented** | User Service, Fund Transfer Service, Utility Payment Service, API Gateway |
| **Not instrumented** | Core Banking Service (missing tracing dependencies in `build.gradle`) |

### 5.4 Database Connections

| Service | Database Name | Driver |
|---------|--------------|--------|
| core-banking-service | `banking_core_service` | MySQL 8.4 (`com.mysql:mysql-connector-j:8.4.0`) |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4 |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4 |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4 |

- Connection details are externalized via Config Server (datasource URL, username, password).
- Test profiles use H2 in-memory databases (user, fund-transfer, utility-payment services).
- Core Banking Service uses Flyway for schema migrations; other services rely on Hibernate DDL auto.

### 5.5 Feign Client Connections

| Source Service | Target Service | Feign Client Interface | Endpoints Called |
|---------------|---------------|----------------------|-----------------|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `POST /api/v1/transaction/fund-transfer`, `GET /api/v1/account/bank-account/{account_number}` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `POST /api/v1/transaction/util-payment`, `GET /api/v1/account/bank-account/{account_number}` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (each service has its own `build.gradle`; no multi-project root build).
- **Java version:** 21 (source compatibility set in each `build.gradle`).
- **Spring Boot plugin:** 3.2.4 with Spring Dependency Management 1.1.4.
- **Git properties plugin:** `com.gorylenko.gradle-git-properties:2.4.2` in all business services + config server.
- **No shared library module.** Common code (mappers, audit, filters, exceptions) is duplicated across services.

### 6.2 Docker

Each service has a `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering (waits for config-server, service-registry, MySQL).
- Spring profile `docker` is activated, which loads `bootstrap-docker.yml` pointing to the Docker-internal config server URL.

### 6.3 Docker Compose

Two compose files:
1. **`docker-compose.yml`** — Full stack (all services + infrastructure).
2. **`docker-compose-support-apps.yml`** — Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry) for local development.

Custom Docker network: `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments.

### 6.4 CI/CD

- **No CI/CD pipeline files** found (no GitHub Actions workflows, Jenkinsfile, or equivalent).
- The `.github/FUNDING.yml` file exists for sponsorship only.
- No Kubernetes manifests despite Kubernetes being listed in the tech stack.

### 6.5 Configuration Profiles

| Profile | Config Server URI | Purpose |
|---------|------------------|---------|
| `default` (local) | `http://localhost:8090` | Local development |
| `dev` | `http://192.168.1.5:8090` | Dev environment |
| `docker` | `http://internet-banking-config-server:8090` | Docker Compose |

### 6.6 Test Data

Pre-loaded via Flyway migration `V1.0.20210427174721__temp_data.sql`:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

**Test credentials:** `ib_admin@javatodev.com / 5V7huE3G86uB` (Keycloak realm user).
