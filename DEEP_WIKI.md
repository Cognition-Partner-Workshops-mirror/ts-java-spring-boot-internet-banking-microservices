# Deep Wiki: Internet Banking Microservices

> A comprehensive technical reference for the Java Spring Boot Internet Banking Microservices platform.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
   - [High-Level Architecture](#21-high-level-architecture)
   - [Service Communication](#22-service-communication)
   - [Network Topology](#23-network-topology)
3. [Technology Stack](#3-technology-stack)
4. [Microservices Deep Dive](#4-microservices-deep-dive)
   - [Service Registry](#41-service-registry-internet-banking-service-registry)
   - [Config Server](#42-config-server-internet-banking-config-server)
   - [API Gateway](#43-api-gateway-internet-banking-api-gateway)
   - [Core Banking Service](#44-core-banking-service)
   - [User Service](#45-user-service-internet-banking-user-service)
   - [Fund Transfer Service](#46-fund-transfer-service-internet-banking-fund-transfer-service)
   - [Utility Payment Service](#47-utility-payment-service-internet-banking-utility-payment-service)
5. [Data Model](#5-data-model)
   - [Core Banking Database](#51-core-banking-database-banking_core_service)
   - [User Service Database](#52-user-service-database-banking_core_user_service)
   - [Fund Transfer Database](#53-fund-transfer-database-banking_core_fund_transfer_service)
   - [Utility Payment Database](#54-utility-payment-database-banking_core_utility_payment_service)
   - [Entity Relationship Diagram](#55-entity-relationship-diagram)
6. [Security](#6-security)
   - [Keycloak Integration](#61-keycloak-integration)
   - [OAuth2 / JWT Flow](#62-oauth2--jwt-flow)
   - [Gateway Security Configuration](#63-gateway-security-configuration)
   - [Auth User Propagation](#64-auth-user-propagation)
7. [Inter-Service Communication](#7-inter-service-communication)
   - [OpenFeign Clients](#71-openfeign-clients)
   - [Custom Feign Configuration](#72-custom-feign-configuration)
8. [Infrastructure](#8-infrastructure)
   - [Docker Compose](#81-docker-compose)
   - [Docker Images & Dockerfiles](#82-docker-images--dockerfiles)
   - [MySQL Initialization](#83-mysql-initialization)
   - [Keycloak Realm Configuration](#84-keycloak-realm-configuration)
   - [Distributed Tracing (Zipkin)](#85-distributed-tracing-zipkin)
9. [Configuration Management](#9-configuration-management)
   - [Spring Cloud Config](#91-spring-cloud-config)
   - [Bootstrap Profiles](#92-bootstrap-profiles)
10. [Database Migrations (Flyway)](#10-database-migrations-flyway)
11. [API Reference](#11-api-reference)
    - [Authentication](#111-authentication)
    - [Core Banking Service APIs](#112-core-banking-service-apis)
    - [User Service APIs](#113-user-service-apis)
    - [Fund Transfer Service APIs](#114-fund-transfer-service-apis)
    - [Utility Payment Service APIs](#115-utility-payment-service-apis)
    - [Health Check Endpoints](#116-health-check-endpoints)
12. [Exception Handling](#12-exception-handling)
13. [Audit Trail](#13-audit-trail)
14. [Getting Started](#14-getting-started)
    - [Prerequisites](#141-prerequisites)
    - [Running with Docker Compose](#142-running-with-docker-compose)
    - [Testing with Postman](#143-testing-with-postman)
    - [Test Data](#144-test-data)
15. [Project Structure](#15-project-structure)

---

## 1. Project Overview

This is a microservices-based internet banking platform built with **Java 21** and **Spring Boot 3.2.4**. It demonstrates a production-style architecture for managing user registration, account operations, fund transfers, and utility bill payments.

The system is composed of **7 independently deployable microservices** that communicate via REST (OpenFeign) and are orchestrated through Docker Compose with a fixed-IP bridge network. Security is enforced at the API Gateway layer using Keycloak as the OAuth2/OIDC identity provider.

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Service Discovery | Netflix Eureka | Dynamic location resolution for microservices |
| Configuration | Spring Cloud Config (Git-backed) | Centralized, version-controlled config |
| API Gateway | Spring Cloud Gateway | Reactive, non-blocking request routing |
| Security | Keycloak + OAuth2 Resource Server | Enterprise-grade IAM with JWT validation |
| Inter-service calls | Spring Cloud OpenFeign | Declarative HTTP clients with Eureka integration |
| Tracing | Zipkin + Micrometer Brave | Distributed request tracking across service boundaries |
| Database | MySQL 8.4 (app data), PostgreSQL 15 (Keycloak) | Separate concerns for app and IAM data |
| Migrations | Flyway | Version-controlled schema evolution |
| Build Tool | Gradle | Multi-project build with Spring dependency management |

---

## 2. Architecture

### 2.1 High-Level Architecture

```
                                    ┌──────────────────────┐
                                    │    Keycloak (IAM)     │
                                    │    :8080 / 172.25.0.11│
                                    └──────────┬───────────┘
                                               │ JWT Validation
                                               │
┌──────────┐    HTTP     ┌─────────────────────┴──────────────────────┐
│  Client   │───────────>│          API Gateway (:8082)               │
│ (Browser/ │            │          172.25.0.6                        │
│  Postman) │            │  - OAuth2 Resource Server                  │
└──────────┘            │  - Route prefixes to downstream services   │
                         │  - Injects X-Auth-Id header                │
                         └────────┬────────┬────────┬────────────────┘
                                  │        │        │
                    ┌─────────────┘        │        └─────────────┐
                    │                      │                      │
           ┌────────▼──────┐    ┌──────────▼────────┐   ┌────────▼──────────┐
           │  User Service  │    │  Fund Transfer     │   │  Utility Payment   │
           │  :8083         │    │  Service :8084      │   │  Service :8085     │
           │  172.25.0.5    │    │  172.25.0.4         │   │  172.25.0.3        │
           └───────┬────────┘    └──────────┬──────────┘   └────────┬───────────┘
                   │  Feign                 │  Feign                │  Feign
                   │                        │                       │
                   └────────────┬───────────┘───────────────────────┘
                                │
                      ┌─────────▼──────────┐
                      │  Core Banking       │
                      │  Service :8092      │
                      │  172.25.0.2         │
                      └─────────┬───────────┘
                                │
                      ┌─────────▼──────────┐
                      │  MySQL 8.4          │
                      │  :3306 / 172.25.0.9 │
                      │  4 databases         │
                      └────────────────────┘

    ┌────────────────────────┐     ┌────────────────────────┐
    │  Service Registry       │     │  Config Server          │
    │  (Eureka) :8081         │     │  :8090 / 172.25.0.8     │
    │  172.25.0.7             │     │  Git-backed configs      │
    └────────────────────────┘     └────────────────────────┘

    ┌────────────────────────┐
    │  Zipkin :9411           │
    │  172.25.0.12            │
    │  Distributed Tracing    │
    └────────────────────────┘
```

### 2.2 Service Communication

| From | To | Method | Purpose |
|------|----|--------|---------|
| API Gateway | All services | HTTP (via Eureka) | Route proxying |
| User Service | Core Banking Service | OpenFeign | Validate user identity during registration |
| Fund Transfer Service | Core Banking Service | OpenFeign | Execute fund transfers at the ledger level |
| Utility Payment Service | Core Banking Service | OpenFeign | Process utility payments at the ledger level |
| All services | Config Server | HTTP (bootstrap) | Fetch externalized configuration |
| All services | Eureka | HTTP | Service registration and discovery |
| All services | Zipkin | HTTP | Trace span reporting |

### 2.3 Network Topology

All services run on a custom Docker bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and gateway `172.25.0.1`.

| Container | IP Address | Port |
|-----------|-----------|------|
| `core-banking-service` | 172.25.0.2 | 8092 |
| `internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| `internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| `internet-banking-user-service` | 172.25.0.5 | 8083 |
| `internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| `internet-banking-service-registry` | 172.25.0.7 | 8081 |
| `internet-banking-config-server` | 172.25.0.8 | 8090 |
| `mysql_javatodev_app` | 172.25.0.9 | 3306 |
| `keycloak_postgre_db` | 172.25.0.10 | 5432 (closed) |
| `keycloak_web` | 172.25.0.11 | 8080 |
| `openzipkin_server` | 172.25.0.12 | 9411 |

---

## 3. Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| Service Discovery | Netflix Eureka Server/Client | (managed by Spring Cloud BOM) |
| API Gateway | Spring Cloud Gateway | (managed by Spring Cloud BOM) |
| Config | Spring Cloud Config Server | (managed by Spring Cloud BOM) |
| Inter-service | Spring Cloud OpenFeign | (managed by Spring Cloud BOM) |
| Security | Keycloak | 23.0.7 |
| Security | Spring Security OAuth2 Resource Server | (managed by Spring Boot BOM) |
| Tracing | Zipkin | 3 |
| Tracing | Micrometer Tracing (Brave bridge) | (managed by Spring Boot BOM) |
| Database | MySQL | 8.4.0 |
| Database | PostgreSQL (Keycloak only) | 15 |
| Migrations | Flyway | 10.12.0 |
| ORM | Spring Data JPA / Hibernate | (managed by Spring Boot BOM) |
| HTTP Client | OkHttp (Feign) | 13.2.1 |
| API Docs | SpringDoc OpenAPI (Swagger) | 2.1.0 |
| Build | Gradle | 8.6 |
| Containerization | Docker / Docker Compose | 3.6 |
| Code Generation | Lombok | (managed by Spring Boot BOM) |
| Keycloak Admin | keycloak-admin-client | 24.0.4 |

---

## 4. Microservices Deep Dive

### 4.1 Service Registry (`internet-banking-service-registry`)

**Purpose:** Netflix Eureka Server providing service registration and discovery.

**Port:** 8081

**Key Annotations:**
- `@EnableEurekaServer` - Activates the Eureka Server
- `@SpringBootApplication`

**Configuration (`application.yml`):**
```yaml
server:
  port: 8081
eureka:
  client:
    service-url:
      defaultZone: http://localhost:${server.port}/eureka
    register-with-eureka: false   # Does not register itself
    fetch-registry: false         # Does not fetch from other registries
  instance:
    prefer-ip-address: true
    hostname: localhost
```

**Dependencies:**
- `spring-cloud-starter-netflix-eureka-server`
- `spring-boot-starter-actuator`
- `spring-boot-starter-web`

**Role in Architecture:** All other services register themselves here using `spring-cloud-starter-netflix-eureka-client`. OpenFeign clients resolve service names (e.g., `core-banking-service`) through Eureka rather than hard-coded URLs.

---

### 4.2 Config Server (`internet-banking-config-server`)

**Purpose:** Centralized configuration management backed by a Git repository.

**Port:** 8090

**Key Annotations:**
- `@EnableConfigServer` - Activates Spring Cloud Config Server
- `@SpringBootApplication`

**Configuration (`application.yml`):**
```yaml
server:
  port: 8090
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git
          search-paths: configuration
          default-label: main
```

**Dependencies:**
- `spring-cloud-config-server`
- `spring-boot-starter-actuator`

**How It Works:**
1. On startup, the Config Server clones the Git repository specified in `spring.cloud.config.server.git.uri`.
2. Client services include `spring-cloud-starter-config` and `spring-cloud-starter-bootstrap`.
3. During the bootstrap phase (before `ApplicationContext` is created), each service fetches its configuration from `http://<config-server>:8090/<service-name>/<profile>`.
4. The `search-paths: configuration` directive tells the server to look in the `configuration/` directory of the Git repo.
5. The `default-label: main` specifies the Git branch to use.

---

### 4.3 API Gateway (`internet-banking-api-gateway`)

**Purpose:** Single entry point for all client traffic. Handles routing, security (OAuth2/JWT), and user identity propagation.

**Port:** 8082

**Main Application:** `InternetBankingApiGatewayApplication.java` (`@SpringBootApplication`)

#### Security Configuration (`SecurityConfiguration.java`)

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Public endpoints (no auth required):
        //   - /user/api/v1/bank-users/register
        //   - /actuator/** and service-specific actuator paths

        // All other exchanges require authentication

        // CSRF disabled
        // JWT validation via JWK Set URI from Keycloak
    }
}
```

**Public Endpoints (no authentication):**
- `POST /user/api/v1/bank-users/register` - User registration
- `/actuator/**` - All actuator health endpoints
- `/user/actuator/**`, `/fund-transfer/actuator/**`, `/banking-core/actuator/**`, `/utility-payment/actuator/**`

**All other endpoints require a valid JWT Bearer token.**

#### Gateway Global Filter (`GatewayConfiguration.java`)

The gateway injects an `X-Auth-Id` header into every proxied request:

```java
@Bean
public GlobalFilter customGlobalFilter() {
    return (exchange, chain) -> exchange.getPrincipal()
        .map(Principal::getName)
        .defaultIfEmpty("SYSTEM USER")
        .map(principal -> {
            exchange.getRequest().mutate()
                .header("X-Auth-Id", principal)
                .build();
            return exchange;
        }).flatMap(chain::filter);
}
```

This extracts the authenticated user's principal name (from the JWT `sub` or `preferred_username` claim) and forwards it as `X-Auth-Id` to downstream services.

**Route Configuration:**
Routes are configured via Spring Cloud Config (externalized). The gateway uses Eureka-aware routing, where route predicates map URL prefixes to registered service names:

| Route Prefix | Target Service |
|-------------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

**Dependencies:**
- `spring-cloud-starter-gateway`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-security`
- `spring-boot-starter-webflux`
- Spring Cloud Config + Bootstrap
- Micrometer Tracing (Brave + Zipkin)

---

### 4.4 Core Banking Service

**Purpose:** The system of record for bank accounts, users, transactions, and utility accounts. Acts as the ledger for all financial operations.

**Port:** 8092

**Main Application:** `CoreBankingServiceApplication.java` (`@SpringBootApplication`)

#### Controllers

| Controller | Base Path | Endpoints |
|-----------|-----------|-----------|
| `AccountController` | `/api/v1/account` | `GET /bank-account/{account_number}` - Read bank account<br>`GET /util-account/{account_name}` - Read utility account |
| `TransactionController` | `/api/v1/transaction` | `POST /fund-transfer` - Process fund transfer<br>`POST /util-payment` - Process utility payment |
| `UserController` | `/api/v1/user` | `GET /{identification}` - Read user by ID number<br>`GET /` - Read all users (paginated) |

#### Services

**`AccountService`**
- `readBankAccount(accountNumber)` - Fetches a bank account by its number. Throws `EntityNotFoundException` if not found.
- `readUtilityAccount(provider)` - Fetches a utility account by provider name.
- `readUtilityAccount(id)` - Fetches a utility account by its database ID.

**`TransactionService`** (`@Transactional`)
- `fundTransfer(request)` - Validates source account balance, then debits the source and credits the destination. Records two `TransactionEntity` rows (debit and credit) with the same `transactionId` (UUID).
- `utilPayment(request)` - Validates source account balance, debits the account, and records a single `TransactionEntity` with type `UTILITY_PAYMENT`.
- `validateBalance(bankAccount, amount)` - Throws `InsufficientFundsException` if the account's actual balance is negative or less than the requested amount.
- `internalFundTransfer(from, to, amount)` - The core transfer logic that updates both account balances and creates transaction records.

**`UserService`**
- `readUser(identification)` - Fetches a user by their national identification number.
- `readUsers(pageable)` - Returns a paginated list of all users.

#### Entities

| Entity | Table | Fields |
|--------|-------|--------|
| `BankAccountEntity` | `banking_core_account` | `id`, `number`, `type` (SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT), `status` (PENDING, ACTIVE, DORMANT, BLOCKED), `availableBalance`, `actualBalance`, `user` (FK) |
| `UserEntity` | `banking_core_user` | `id`, `firstName`, `lastName`, `email`, `identificationNumber`, `accounts` (OneToMany) |
| `TransactionEntity` | `banking_core_transaction` | `id`, `amount`, `transactionType` (FUND_TRANSFER, UTILITY_PAYMENT), `referenceNumber`, `transactionId`, `account` (FK) |
| `UtilityAccountEntity` | `banking_core_utility_account` | `id`, `number`, `providerName` |

#### Enums

| Enum | Values |
|------|--------|
| `AccountStatus` | `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `AccountType` | `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `TransactionType` | `FUND_TRANSFER`, `UTILITY_PAYMENT` |

**Dependencies:**
- Spring Boot Web, Data JPA
- Eureka Client
- Flyway (MySQL)
- MySQL Connector
- SpringDoc OpenAPI (Swagger)
- Spring Cloud Config + Bootstrap
- Micrometer Tracing (Brave + Zipkin)
- Lombok
- H2 (test only)

---

### 4.5 User Service (`internet-banking-user-service`)

**Purpose:** Manages internet banking user registration and profile management. Integrates with Keycloak for identity management and with Core Banking Service for user validation.

**Port:** 8083

**Main Application:** `InternetBankingUserServiceApplication.java` (`@EnableFeignClients`, `@SpringBootApplication`)

#### Controller (`UserController`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g., approve) |
| `GET` | `/api/v1/bank-users` | List all users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | Read a single user by database ID |

#### Service (`UserService`)

**`createUser(user)` - Registration Flow:**

```
1. Check if email already exists in Keycloak
   └─ If exists → throw UserAlreadyRegisteredException

2. Call Core Banking Service via Feign to validate identification number
   └─ Fetch user by identification from /api/v1/user/{identification}

3. Validate the provided email matches the core banking record
   └─ If mismatch → throw InvalidEmailException

4. Create user in Keycloak:
   - Set email, username, firstName, lastName
   - Set emailVerified=false, enabled=false
   - Set password credentials

5. If Keycloak returns HTTP 201:
   - Fetch the Keycloak user to get their authId (UUID)
   - Save to local database with status=PENDING

6. If core banking user not found → throw InvalidBankingUserException
```

**`updateUser(id, request)` - Approval Flow:**
```
1. Find user in local database
2. If new status is APPROVED:
   - Fetch user from Keycloak by authId
   - Set enabled=true, emailVerified=true
   - Update in Keycloak
3. Update status in local database
```

**`readUsers(pageable)`:**
- Fetches all local users, then enriches each with Keycloak data (email).

#### Keycloak Integration

**`KeycloakProperties`** - Configurable via `app.config.keycloak.*`:
- `server-url` - Keycloak base URL
- `realm` - Target realm name
- `clientId` - OAuth2 client ID
- `client-secret` - OAuth2 client secret
- Uses `client_credentials` grant type
- Implements a singleton Keycloak admin client instance

**`KeycloakManager`** - Provides `getKeyCloakInstanceWithRealm()` which returns a `RealmResource` for the configured realm.

**`KeycloakUserService`** - CRUD operations on Keycloak users:
- `createUser(userRepresentation)` - Creates a user in Keycloak
- `updateUser(userRepresentation)` - Updates an existing Keycloak user
- `readUserByEmail(email)` - Searches users by email
- `readUser(authId)` - Fetches a user by Keycloak ID

#### Feign Client

```java
@FeignClient(name = "core-banking-service")
public interface BankingCoreRestClient {
    @GetMapping("/api/v1/user/{identification}")
    UserResponse readUser(@PathVariable("identification") String identification);
}
```

#### Entity

| Entity | Table | Fields |
|--------|-------|--------|
| `UserEntity` | `user` | `id`, `authId` (Keycloak UUID), `identification` (NIC), `status` (PENDING, APPROVED, DISABLED, BLACKLIST), audit fields (from `AuditAware`) |

**Dependencies:** Same as Core Banking + OpenFeign, OkHttp, Keycloak Admin Client 24.0.4

---

### 4.6 Fund Transfer Service (`internet-banking-fund-transfer-service`)

**Purpose:** Handles fund transfer requests between bank accounts. Maintains its own transaction log and delegates the actual balance operations to Core Banking Service.

**Port:** 8084

**Main Application:** `InternetBankingFundTransferServiceApplication.java` (`@EnableFeignClients`, `@SpringBootApplication`)

#### Controller (`FundTransferController`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | List all fund transfers (paginated) |

#### Service (`FundTransferService`)

**`fundTransfer(request)` Flow:**
```
1. Create a FundTransferEntity with status=PENDING
2. Save to local database
3. Call Core Banking Service via Feign: POST /api/v1/transaction/fund-transfer
4. Update entity with:
   - transactionReference from response
   - status=SUCCESS
5. Save updated entity
6. Return response with "Fund Transfer Successfully Completed"
```

#### Feign Client

```java
@FeignClient(value = "core-banking-service", configuration = CustomFeignClientConfiguration.class)
public interface BankingCoreFeignClient {
    @RequestMapping(path = "/api/v1/account/bank-account/{account_number}", method = GET)
    AccountResponse readAccount(@PathVariable("account_number") String accountNumber);

    @RequestMapping(path = "/api/v1/transaction/fund-transfer", method = POST)
    FundTransferResponse fundTransfer(@RequestBody FundTransferRequest request);
}
```

#### Entity

| Entity | Table | Fields |
|--------|-------|--------|
| `FundTransferEntity` | `fund_transfer` | `id`, `transactionReference`, `fromAccount`, `toAccount`, `amount`, `status` (PENDING, PROCESSING, SUCCESS, FAILED), audit fields |

---

### 4.7 Utility Payment Service (`internet-banking-utility-payment-service`)

**Purpose:** Processes utility bill payments (e.g., telecom, water, electricity). Maintains its own payment log and delegates balance deduction to Core Banking Service.

**Port:** 8085

**Main Application:** `InternetBankingUtilityPaymentServiceApplication.java` (`@EnableFeignClients`, `@SpringBootApplication`)

#### Controller (`UtilityPaymentController`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/utility-payment` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | List all utility payments (paginated) |

#### Service (`UtilityPaymentService`)

**`utilPayment(request)` Flow:**
```
1. Create a UtilityPaymentEntity with status=PROCESSING
2. Save to local database
3. Call Core Banking Service via Feign: POST /api/v1/transaction/util-payment
4. Update entity with:
   - transactionId from response
   - status=SUCCESS
5. Save updated entity
6. Return response with "Utility Payment Successfully Processed"
```

#### Feign Client

```java
@FeignClient(name = "core-banking-service", configuration = CustomFeignClientConfiguration.class)
public interface BankingCoreRestClient {
    @RequestMapping(path = "/api/v1/account/bank-account/{account_number}", method = GET)
    AccountResponse readAccount(@PathVariable("account_number") String accountNumber);

    @RequestMapping(path = "/api/v1/transaction/util-payment", method = POST)
    UtilityPaymentResponse utilityPayment(@RequestBody UtilityPaymentRequest request);
}
```

#### Entity

| Entity | Table | Fields |
|--------|-------|--------|
| `UtilityPaymentEntity` | `utility_payment` | `id`, `providerId`, `amount`, `referenceNumber`, `account`, `transactionId`, `status` (PENDING, PROCESSING, SUCCESS, FAILED), audit fields |

---

## 5. Data Model

### 5.1 Core Banking Database (`banking_core_service`)

```sql
CREATE TABLE banking_core_user (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    email                 VARCHAR(255),
    first_name            VARCHAR(255),
    identification_number VARCHAR(255),
    last_name             VARCHAR(255)
);

CREATE TABLE banking_core_account (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    actual_balance    DECIMAL(19,2),
    available_balance DECIMAL(19,2),
    number            VARCHAR(255),
    status            VARCHAR(255),   -- PENDING, ACTIVE, DORMANT, BLOCKED
    type              VARCHAR(255),   -- SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT
    user_id           BIGINT,
    FOREIGN KEY (user_id) REFERENCES banking_core_user(id)
);

CREATE TABLE banking_core_utility_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    number        VARCHAR(255),
    provider_name VARCHAR(255)
);

CREATE TABLE banking_core_transaction (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount           DECIMAL(19,2),
    transaction_type VARCHAR(30),    -- FUND_TRANSFER, UTILITY_PAYMENT
    reference_number VARCHAR(50),
    transaction_id   VARCHAR(50),
    account_id       BIGINT,
    FOREIGN KEY (account_id) REFERENCES banking_core_account(id)
);
```

### 5.2 User Service Database (`banking_core_user_service`)

Managed by Hibernate auto-DDL (no Flyway migrations). Contains:

| Table | Columns |
|-------|---------|
| `user` | `id` (PK), `auth_id` (Keycloak UUID), `identification` (NIC), `status` (enum), `created_by`, `created_on`, `updated_by`, `updated_on` |

### 5.3 Fund Transfer Database (`banking_core_fund_transfer_service`)

Managed by Hibernate auto-DDL. Contains:

| Table | Columns |
|-------|---------|
| `fund_transfer` | `id` (PK), `transaction_reference`, `from_account`, `to_account`, `amount`, `status` (enum), `created_by`, `created_on`, `updated_by`, `updated_on` |

### 5.4 Utility Payment Database (`banking_core_utility_payment_service`)

Managed by Hibernate auto-DDL. Contains:

| Table | Columns |
|-------|---------|
| `utility_payment` | `id` (PK), `provider_id`, `amount`, `reference_number`, `account`, `transaction_id`, `status` (enum), `created_by`, `created_on`, `updated_by`, `updated_on` |

### 5.5 Entity Relationship Diagram

```
banking_core_service
┌──────────────────────┐       ┌──────────────────────────┐
│  banking_core_user    │       │  banking_core_account     │
├──────────────────────┤       ├──────────────────────────┤
│  id (PK)              │◄──┐  │  id (PK)                  │
│  first_name           │   └──│  user_id (FK)             │
│  last_name            │      │  number                    │
│  email                │      │  type                      │
│  identification_number│      │  status                    │
└──────────────────────┘      │  actual_balance            │
                               │  available_balance         │
                               └────────────┬──────────────┘
                                            │
                               ┌────────────▼──────────────┐
                               │  banking_core_transaction  │
                               ├───────────────────────────┤
                               │  id (PK)                   │
                               │  account_id (FK)           │
                               │  amount                    │
                               │  transaction_type          │
                               │  reference_number          │
                               │  transaction_id            │
                               └───────────────────────────┘

┌───────────────────────────┐
│  banking_core_utility_acct │
├───────────────────────────┤
│  id (PK)                   │
│  number                    │
│  provider_name             │
└───────────────────────────┘
```

---

## 6. Security

### 6.1 Keycloak Integration

**Keycloak Version:** 23.0.7

**Realm:** `javatodev-internet-banking`

**Client:** `internet-banking-core-client`
- Client Secret: Configured in Postman environment (`0efd3e37-258e-4488-96ae-1dfe34679c9d`)
- Grant Types: `client_credentials` (admin operations), `password_credentials` (user login)

**Deployment:**
- Keycloak runs in `start-dev` mode with realm auto-import
- Backed by PostgreSQL 15 (`keycloak` database)
- Realm configuration is mounted from `docker-compose/keycloak/realm-export.json`

**Test Credentials:**
```
Email:    ib_admin@javatodev.com
Password: 5V7huE3G86uB
```

### 6.2 OAuth2 / JWT Flow

```
1. Client sends credentials to Keycloak:
   POST http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/token
   Body: grant_type=password&client_id=internet-banking-core-client
         &client_secret=...&username=...&password=...

2. Keycloak returns a JWT access token

3. Client includes token in requests to API Gateway:
   Authorization: Bearer <jwt_token>

4. API Gateway validates the JWT using Keycloak's JWK Set URI:
   spring.security.oauth2.resourceserver.jwt.jwk-set-uri

5. If valid, the gateway extracts the principal and injects X-Auth-Id header

6. Downstream services receive the X-Auth-Id header
```

### 6.3 Gateway Security Configuration

The API Gateway uses **WebFlux Security** (`@EnableWebFluxSecurity`) as it's built on Spring Cloud Gateway (reactive).

**Permit All:**
- `/user/api/v1/bank-users/register` - User self-registration
- All `/actuator/**` endpoints across services

**Require Authentication:**
- All other routes

**CSRF:** Disabled (stateless REST API)

**JWT Validation:** Uses `oauth2ResourceServer` with JWK Set URI from Keycloak.

### 6.4 Auth User Propagation

The `X-Auth-Id` header flow:

```
API Gateway                          Downstream Service
┌─────────────┐                     ┌─────────────────────┐
│ JWT validated│                     │ AppAuthUserFilter    │
│ Principal    │──X-Auth-Id header──>│ extracts X-Auth-Id   │
│ extracted    │                     │ sets in              │
│              │                     │ ApiRequestContextHolder│
└─────────────┘                     └─────────────────────┘
```

**`AppAuthUserFilter`** (Servlet Filter) in User, Fund Transfer, and Utility Payment services:
1. Reads `X-Auth-Id` from the incoming HTTP request header
2. Stores it in a thread-local `ApiRequestContextHolder`
3. Used by audit configuration to track who performed each operation
4. Context is cleared after the request completes

---

## 7. Inter-Service Communication

### 7.1 OpenFeign Clients

All downstream services use **Spring Cloud OpenFeign** to communicate with `core-banking-service`. Feign resolves the service name through Eureka.

| Service | Feign Client | Target Endpoints |
|---------|-------------|------------------|
| User Service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`<br>`POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`<br>`POST /api/v1/transaction/util-payment` |

### 7.2 Custom Feign Configuration

Fund Transfer and Utility Payment services use a custom Feign configuration:

```java
@Configuration
public class CustomFeignClientConfiguration extends FeignClientProperties.FeignClientConfiguration {
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }
}
```

This provides custom error handling for Feign responses, translating HTTP error responses from Core Banking into appropriate exceptions in the calling service.

The User Service also has a `CustomFeignClientConfiguration` and `CustomFeignErrorDecoder` with the same pattern.

**Feign HTTP Client:** The User Service uses `feign-okhttp:13.2.1` as the underlying HTTP client for better connection pooling and performance.

---

## 8. Infrastructure

### 8.1 Docker Compose

Two compose files are provided:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |

**Startup Order (enforced by `wait-for-it.sh`):**
```
1. MySQL, PostgreSQL, Zipkin (no dependencies)
2. Keycloak (depends on PostgreSQL)
3. Config Server (no service dependencies)
4. Service Registry (no service dependencies)
5. API Gateway (waits for: Service Registry, Config Server)
6. User Service (waits for: Service Registry, Config Server, MySQL)
7. Fund Transfer Service (waits for: Service Registry, Config Server, MySQL)
8. Utility Payment Service (waits for: Service Registry, Config Server, MySQL)
9. Core Banking Service (waits for: Service Registry, Config Server, MySQL)
```

### 8.2 Docker Images & Dockerfiles

All application services use the same Dockerfile pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
LABEL maintainer="chinthaka@javatodev.com"
VOLUME /main-app
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash          # Required for wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

The `docker` profile activates `bootstrap-docker.yml` which points the Config Server URI to the container hostname instead of `localhost`.

### 8.3 MySQL Initialization

Custom MySQL image built from `docker-compose/mysql/Dockerfile`:

```dockerfile
FROM mysql:8.4.0
ENV MYSQL_ROOT_PASSWORD woVERANKliGharym
COPY ./privileges.sql /docker-entrypoint-initdb.d/
```

**`privileges.sql`** runs on first container start:
```sql
-- Create application user
CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt';
GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON *.* TO 'javatodev_development'@'%';

-- Create databases for each service
CREATE DATABASE IF NOT EXISTS banking_core_service;
CREATE DATABASE IF NOT EXISTS banking_core_fund_transfer_service;
CREATE DATABASE IF NOT EXISTS banking_core_user_service;
CREATE DATABASE IF NOT EXISTS banking_core_utility_payment_service;
```

### 8.4 Keycloak Realm Configuration

The Keycloak instance is pre-configured with realm data imported from `docker-compose/keycloak/realm-export.json`. This includes:
- Realm: `javatodev-internet-banking`
- Client: `internet-banking-core-client` with client secret
- Pre-configured users and roles
- Realm settings matching the application's security requirements

The realm is imported via the `--import-realm` command flag and volume mount:
```yaml
volumes:
  - ./keycloak:/opt/keycloak/data/import
command: ["start-dev", "--import-realm"]
```

### 8.5 Distributed Tracing (Zipkin)

**Image:** `openzipkin/zipkin:3`
**Port:** 9411
**Dashboard:** http://localhost:9411

All application services include tracing dependencies:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
implementation 'io.github.openfeign:feign-micrometer'
```

This enables:
- Automatic trace context propagation across HTTP calls
- Feign client call tracing
- Trace reporting to Zipkin collector
- End-to-end request visualization in the Zipkin dashboard

---

## 9. Configuration Management

### 9.1 Spring Cloud Config

All application services (except Service Registry) fetch their configuration from the Config Server at startup.

**Config Repository:** https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git

**Config Resolution Pattern:**
```
http://config-server:8090/{application-name}/{profile}
```

For example, `core-banking-service` with `docker` profile fetches:
```
http://config-server:8090/core-banking-service/docker
```

The externalized configs include:
- Database connection details (URL, username, password)
- Eureka client configuration
- Keycloak settings (server URL, realm, client ID, client secret)
- Gateway route definitions
- Zipkin/tracing configuration
- Server ports
- JPA/Hibernate settings

### 9.2 Bootstrap Profiles

Each service has three bootstrap configuration files:

| File | Config Server URI | Use Case |
|------|-------------------|----------|
| `bootstrap.yml` | `http://localhost:8090` | Local development (default) |
| `bootstrap-dev.yml` | `http://192.168.1.5:8090` | Development network |
| `bootstrap-docker.yml` | `http://internet-banking-config-server:8090` | Docker Compose deployment |

The active profile is set via:
- Docker: `-Dspring.profiles.active=docker` in the `ENTRYPOINT`
- Local: Default (no profile)

---

## 10. Database Migrations (Flyway)

Only the **Core Banking Service** uses Flyway for schema management. Other services use Hibernate auto-DDL.

**Migration Files** (`core-banking-service/src/main/resources/db/migration/`):

| Version | Filename | Description |
|---------|----------|-------------|
| V1.0.20210427174638 | `create_base_table_structure.sql` | Creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables |
| V1.0.20210427174721 | `temp_data.sql` | Seeds test data: 4 users, 14 bank accounts, 6 utility providers |
| V1.0.20210429210839 | `create_transaction_table.sql` | Creates `banking_core_transaction` table |

**Flyway Dependencies:**
```gradle
implementation 'org.flywaydb:flyway-core:10.12.0'
implementation 'org.flywaydb:flyway-mysql:10.12.0'
```

---

## 11. API Reference

### 11.1 Authentication

**Obtain Access Token:**
```
POST http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=internet-banking-core-client
&client_secret=0efd3e37-258e-4488-96ae-1dfe34679c9d
&username=ib_admin@javatodev.com
&password=5V7huE3G86uB
```

All subsequent API calls (except registration and health) require:
```
Authorization: Bearer <access_token>
```

### 11.2 Core Banking Service APIs

**Base URL:** `http://localhost:8082/core`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|-------------|
| `GET` | `/api/v1/user` | List all users (paginated) | - |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | - |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account | - |
| `GET` | `/api/v1/account/util-account/{provider_name}` | Get utility account | - |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{"fromAccount": "...", "toAccount": "...", "amount": 2000.00}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{"providerId": 2, "amount": 250, "referenceNumber": "...", "account": "..."}` |

### 11.3 User Service APIs

**Base URL:** `http://localhost:8082/user`

| Method | Endpoint | Description | Auth Required | Request Body |
|--------|----------|-------------|--------------|-------------|
| `POST` | `/api/v1/bank-users/register` | Register new user | No | `{"password": "123", "email": "guru@gmail.com", "identification": "901830556V"}` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | Yes | `{"status": "APPROVED"}` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | Yes | - |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | Yes | - |

### 11.4 Fund Transfer Service APIs

**Base URL:** `http://localhost:8082/fund-transfer`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|-------------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{"fromAccount": "100015003001", "toAccount": "100015003000", "amount": 1250.34}` |
| `GET` | `/api/v1/transfer` | List all transfers (paginated) | - |

### 11.5 Utility Payment Service APIs

**Base URL:** `http://localhost:8082/payment`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|-------------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{"providerId": 2, "amount": 2000, "referenceNumber": "0712402547", "account": "100015003000"}` |
| `GET` | `/api/v1/utility-payment` | List all payments (paginated) | - |

### 11.6 Health Check Endpoints

All services expose Spring Boot Actuator health endpoints:

| Service | Health Endpoint (via Gateway) |
|---------|-------------------------------|
| User Service | `GET /user/actuator/health` |
| Fund Transfer Service | `GET /fund-transfer/actuator/health` |
| Core Banking Service | `GET /banking-core/actuator/health` |
| Utility Payment Service | `GET /utility-payment/actuator/health` |
| Service Registry | Direct: `GET http://localhost:8081/actuator/health` |
| Config Server | Direct: `GET http://localhost:8090/actuator/health` |

---

## 12. Exception Handling

All services implement a layered exception handling strategy using `@ControllerAdvice`.

### Exception Hierarchy

```
SimpleBankingGlobalException (abstract base)
├── EntityNotFoundException
├── InsufficientFundsException        (Core Banking only)
├── UserAlreadyRegisteredException    (User Service only)
├── InvalidEmailException             (User Service only)
└── InvalidBankingUserException       (User Service only)
```

### Global Exception Handler

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity handleGlobalException(...) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                .code(exception.getCode())
                .message(exception.getMessage())
                .build());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity handleException(...) {
        return ResponseEntity.badRequest()
            .body("Exception occur inside API " + e);
    }
}
```

### Error Response Format

```json
{
    "code": "ERROR_CODE_STRING",
    "message": "Human-readable error message"
}
```

### Error Codes (`GlobalErrorCode`)

| Code | Meaning |
|------|---------|
| `INSUFFICIENT_FUNDS` | Account balance too low for the operation |
| `ERROR_EMAIL_REGISTERED` | Email already exists in Keycloak |
| `ERROR_INVALID_EMAIL` | Email doesn't match core banking record |
| `ERROR_USER_NOT_FOUND_UNDER_NIC` | No user found with given identification number |

---

## 13. Audit Trail

The User Service, Fund Transfer Service, and Utility Payment Service implement JPA auditing.

### AuditAware Base Class

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditAware {
    @CreatedBy   private String createdBy;
    @CreatedDate private Date createdOn;
    @LastModifiedBy   private String updatedBy;
    @LastModifiedDate private Date updatedOn;
}
```

### Auditor Resolution

The `AuditorAwareConfig` implements `AuditorAware<String>` and resolves the current auditor from `ApiRequestContextHolder`, which stores the `X-Auth-Id` header value set by the API Gateway.

```
Request Flow:
JWT → Gateway extracts principal → X-Auth-Id header → AppAuthUserFilter
→ ApiRequestContextHolder → AuditorAwareConfig → @CreatedBy / @LastModifiedBy
```

---

## 14. Getting Started

### 14.1 Prerequisites

- **Docker** and **Docker Compose** installed
- **Java 21** (if building locally)
- **Gradle 8.6** (if building locally)
- **Postman** (for API testing)

### 14.2 Running with Docker Compose

```bash
# Clone the repository
git clone <repository-url>

# Navigate to docker-compose directory
cd internet-banking-concept-microservices/docker-compose

# Start all services
docker-compose up -d
```

**Startup takes several minutes** due to sequential dependency resolution via `wait-for-it.sh`. Monitor with:
```bash
docker-compose logs -f
```

**Service availability order:**
1. MySQL, PostgreSQL, Zipkin (~10s)
2. Keycloak (~30s)
3. Config Server, Service Registry (~20s)
4. API Gateway (~30s after Config Server + Registry)
5. Application services (~30-60s after all infrastructure)

### 14.3 Testing with Postman

1. Import `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
2. Import `postman_collection/BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
3. Select the `BANKING_CORE_MICROSERVICES_PROJECT` environment
4. Start with the **AUTHENTICATION** request to obtain a JWT token
5. The token is used via OAuth2 collection-level authentication

**Postman Environment Variables:**

| Variable | Value |
|----------|-------|
| `api_gateway_host` | `http://localhost:8082` |
| `keycloack_host` | `http://localhost:8080` |
| `keycloack_client_id` | `internet-banking-core-client` |
| `keycloack_client_secret` | `0efd3e37-258e-4488-96ae-1dfe34679c9d` |
| `keycloack_realm` | `javatodev-internet-banking` |

### 14.4 Test Data

The system comes pre-loaded with test data (via Flyway migrations):

**Users:**

| ID | Name | Email | Identification |
|----|------|-------|---------------|
| 1 | Sam Silva | sam@gmail.com | 808829932V |
| 2 | Guru Darmaraj | guru@gmail.com | 901830556V |
| 3 | Ragu Sivaraj | ragu@gmail.com | 348829932V |
| 4 | Randor Manoon | randor@gmail.com | 842829932V |

**Bank Accounts (sample):**

| Account Number | User | Balance | Type | Status |
|---------------|------|---------|------|--------|
| 100015003000 | Sam Silva | 100,000.00 | SAVINGS | ACTIVE |
| 100015003001 | Sam Silva | 100,000.00 | SAVINGS | ACTIVE |
| 100015003002 | Guru Darmaraj | 100,000.00 | SAVINGS | ACTIVE |
| 100015003003 | Guru Darmaraj | 12,000.00 | SAVINGS | ACTIVE |
| 100015003010 | Randor Manoon | 365,023.00 | SAVINGS | ACTIVE |
| 100015003013 | Randor Manoon | 889,000.33 | SAVINGS | ACTIVE |

**Utility Providers:**

| ID | Provider | Account Number |
|----|----------|---------------|
| 1 | VODAFONE | 8203232565 |
| 2 | VERIZON | 5464546545 |
| 3 | SINGTEL | 6546456464 |
| 4 | HUTCH | 7889987999 |
| 5 | AIRTEL | 2132123132 |
| 6 | GIO | 61645564646 |

---

## 15. Project Structure

```
internet-banking-concept-microservices/
├── core-banking-service/                        # Ledger and account management
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/javatodev/finance/
│       ├── CoreBankingServiceApplication.java
│       ├── controller/
│       │   ├── AccountController.java           # GET /api/v1/account/**
│       │   ├── TransactionController.java       # POST /api/v1/transaction/**
│       │   └── UserController.java              # GET /api/v1/user/**
│       ├── service/
│       │   ├── AccountService.java
│       │   ├── TransactionService.java          # @Transactional fund/util operations
│       │   └── UserService.java
│       ├── model/
│       │   ├── AccountStatus.java               # enum
│       │   ├── AccountType.java                 # enum
│       │   ├── TransactionType.java             # enum
│       │   ├── dto/
│       │   │   ├── BankAccount.java
│       │   │   ├── Transaction.java
│       │   │   ├── User.java
│       │   │   ├── UtilityAccount.java
│       │   │   ├── request/
│       │   │   │   ├── FundTransferRequest.java
│       │   │   │   └── UtilityPaymentRequest.java
│       │   │   └── response/
│       │   │       ├── FundTransferResponse.java
│       │   │       └── UtilityPaymentResponse.java
│       │   ├── entity/
│       │   │   ├── BankAccountEntity.java
│       │   │   ├── TransactionEntity.java
│       │   │   ├── UserEntity.java
│       │   │   └── UtilityAccountEntity.java
│       │   └── mapper/
│       │       ├── BaseMapper.java
│       │       ├── BankAccountMapper.java
│       │       ├── UserMapper.java
│       │       └── UtilityAccountMapper.java
│       ├── repository/
│       │   ├── BankAccountRepository.java
│       │   ├── TransactionRepository.java
│       │   ├── UserRepository.java
│       │   └── UtilityAccountRepository.java
│       └── exception/
│           ├── EntityNotFoundException.java
│           ├── ErrorResponse.java
│           ├── GlobalErrorCode.java
│           ├── GlobalExceptionHandler.java
│           ├── InsufficientFundsException.java
│           └── SimpleBankingGlobalException.java
│
├── internet-banking-user-service/               # User registration & Keycloak integration
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/javatodev/finance/
│       ├── InternetBankingUserServiceApplication.java  # @EnableFeignClients
│       ├── controller/
│       │   └── UserController.java              # /api/v1/bank-users/**
│       ├── service/
│       │   ├── UserService.java                 # Registration, approval workflows
│       │   ├── KeycloakUserService.java         # Keycloak admin operations
│       │   └── rest/
│       │       └── BankingCoreRestClient.java   # Feign client
│       ├── configuration/
│       │   ├── keycloak/
│       │   │   ├── KeycloakManager.java
│       │   │   └── KeycloakProperties.java
│       │   ├── feign/
│       │   │   ├── CustomFeignClientConfiguration.java
│       │   │   └── CustomFeignErrorDecoder.java
│       │   ├── filter/
│       │   │   ├── AppAuthUserFilter.java       # X-Auth-Id extraction
│       │   │   ├── ApiRequestContext.java
│       │   │   └── ApiRequestContextHolder.java
│       │   └── audit/
│       │       ├── AuditConfig.java
│       │       └── AuditorAwareConfig.java
│       ├── model/
│       │   ├── dto/
│       │   │   ├── AuditAware.java
│       │   │   ├── Status.java                  # enum
│       │   │   ├── User.java
│       │   │   └── UserUpdateRequest.java
│       │   ├── entity/
│       │   │   └── UserEntity.java
│       │   ├── mapper/
│       │   │   ├── BaseMapper.java
│       │   │   └── UserMapper.java
│       │   ├── repository/
│       │   │   └── UserRepository.java
│       │   └── rest/response/
│       │       ├── AccountResponse.java
│       │       └── UserResponse.java
│       └── exception/
│           ├── EntityNotFoundException.java
│           ├── ErrorResponse.java
│           ├── GlobalErrorCode.java
│           ├── GlobalExceptionHandler.java
│           ├── InvalidBankingUserException.java
│           ├── InvalidEmailException.java
│           ├── SimpleBankingGlobalException.java
│           └── UserAlreadyRegisteredException.java
│
├── internet-banking-fund-transfer-service/      # Fund transfer processing
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/javatodev/finance/
│       ├── InternetBankingFundTransferServiceApplication.java  # @EnableFeignClients
│       ├── controller/
│       │   └── FundTransferController.java      # /api/v1/transfer
│       ├── service/
│       │   ├── FundTransferService.java
│       │   └── rest/client/
│       │       └── BankingCoreFeignClient.java  # Feign client
│       ├── configuration/                       # Same pattern as User Service
│       ├── model/
│       │   ├── TransactionStatus.java           # enum
│       │   ├── dto/
│       │   │   ├── AuditAware.java
│       │   │   ├── FundTransfer.java
│       │   │   ├── request/
│       │   │   └── response/
│       │   ├── entity/
│       │   │   └── FundTransferEntity.java
│       │   ├── mapper/
│       │   └── repository/
│       └── exception/
│
├── internet-banking-utility-payment-service/    # Utility bill payments
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/javatodev/finance/
│       ├── InternetBankingUtilityPaymentServiceApplication.java  # @EnableFeignClients
│       ├── controller/
│       │   └── UtilityPaymentController.java    # /api/v1/utility-payment
│       ├── service/
│       │   ├── UtilityPaymentService.java
│       │   └── rest/
│       │       └── BankingCoreRestClient.java   # Feign client
│       ├── configuration/                       # Same pattern as User Service
│       ├── model/
│       │   ├── TransactionStatus.java           # enum
│       │   ├── dto/
│       │   ├── entity/
│       │   │   └── UtilityPaymentEntity.java
│       │   └── rest/
│       │       ├── request/
│       │       └── response/
│       ├── repository/
│       └── exception/
│
├── internet-banking-api-gateway/                # API Gateway + Security
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/javatodev/finance/
│       ├── InternetBankingApiGatewayApplication.java
│       └── configuration/
│           ├── GatewayConfiguration.java        # X-Auth-Id GlobalFilter
│           └── security/
│               └── SecurityConfiguration.java   # OAuth2 + JWT config
│
├── internet-banking-service-registry/           # Eureka Server
│   ├── build.gradle
│   └── src/main/java/com/javatodev/finance/
│       └── InternetBankingServiceRegistryApplication.java  # @EnableEurekaServer
│
├── internet-banking-config-server/              # Spring Cloud Config Server
│   ├── build.gradle
│   └── src/main/java/com/javatodev/finance/
│       └── InternetBankingConfigServerApplication.java  # @EnableConfigServer
│
├── docker-compose/
│   ├── docker-compose.yml                       # Full stack
│   ├── docker-compose-support-apps.yml          # Infrastructure only
│   ├── keycloak/
│   │   ├── Dockerfile
│   │   └── realm-export.json                    # Pre-configured realm
│   └── mysql/
│       ├── Dockerfile
│       └── privileges.sql                       # DB init: users + databases
│
├── postman_collection/
│   ├── JAVA_TO_DEV_MICROSERVICES.postman_collection.json
│   └── BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json
│
├── draw_io/
│   ├── centralized_configuration.drawio
│   └── centralized_configuration.jpg
│
├── README.md
├── LICENSE
└── .gitignore
```
