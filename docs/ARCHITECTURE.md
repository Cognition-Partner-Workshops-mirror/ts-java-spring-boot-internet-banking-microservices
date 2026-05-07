# Architecture Overview

## 1. System Summary

The **Internet Banking Microservices** platform is a Java 21 / Spring Boot 3.2.4 application composed of six independently deployable services that together implement core internet banking operations: user management, fund transfers, utility payments, and account management. The system follows a typical Spring Cloud microservices architecture with centralized configuration, service discovery, and an API gateway.

---

## 2. Service Inventory

| Service | Port | Responsibility | Database |
|---|---|---|---|
| **internet-banking-config-server** | 8090 | Centralized configuration management via Spring Cloud Config (Git-backed) | None |
| **internet-banking-service-registry** | 8081 | Service discovery via Netflix Eureka Server | None |
| **internet-banking-api-gateway** | 8082 | API routing, OAuth2/JWT authentication, request enrichment (X-Auth-Id header) | None |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer processing between accounts | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | Core banking operations: accounts, transactions, balance management | MySQL (`banking_core_service`) |

### 2.1 Service Descriptions

#### Config Server
- Serves externalized configuration from a Git repository (`internet-banking-microservices-configurations`)
- All other services bootstrap by connecting to this server at startup
- Spring Cloud Config Server with Git backend

#### Service Registry (Eureka)
- Netflix Eureka Server for service registration and discovery
- All business services register as Eureka clients
- Enables service-to-service communication by logical name rather than hardcoded URLs

#### API Gateway
- Spring Cloud Gateway (reactive, WebFlux-based)
- OAuth2 Resource Server with JWT validation via Keycloak JWK endpoint
- Injects authenticated user identity as `X-Auth-Id` HTTP header to downstream services
- Permits unauthenticated access to `/user/api/v1/bank-users/register` and all `/actuator/**` endpoints

#### User Service
- Manages user registration and profile CRUD operations
- Integrates with **Keycloak** admin API for identity management (user creation, email verification, enable/disable)
- Calls **core-banking-service** via OpenFeign to validate user identification
- Exposes REST API at `/api/v1/bank-users`

#### Fund Transfer Service
- Processes fund transfers between bank accounts
- Calls **core-banking-service** via OpenFeign to execute the actual transfer
- Maintains its own transaction record with status tracking (PENDING → SUCCESS)
- Exposes REST API at `/api/v1/transfer`

#### Utility Payment Service
- Processes utility bill payments (telecom providers, etc.)
- Calls **core-banking-service** via OpenFeign to execute payments
- Maintains its own payment record with status tracking (PROCESSING → SUCCESS)
- Exposes REST API at `/api/v1/utility-payment`

#### Core Banking Service
- Acts as the central banking core, managing accounts, users, and transactions
- Handles balance validation, debit/credit operations, and transaction recording
- Uses Flyway for database schema migrations
- Exposes REST APIs at `/api/v1/account`, `/api/v1/user`, `/api/v1/transaction`

---

## 3. Technology Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| Service Discovery | Netflix Eureka | via Spring Cloud |
| API Gateway | Spring Cloud Gateway | via Spring Cloud |
| Configuration | Spring Cloud Config Server | via Spring Cloud |
| Inter-service Communication | Spring Cloud OpenFeign | via Spring Cloud |
| Identity & Auth | Keycloak | 23.0.7 |
| Security | Spring Security OAuth2 Resource Server | via Spring Boot |
| Database | MySQL | 8.x (via Docker) |
| Database (Keycloak) | PostgreSQL | 15 |
| DB Migrations | Flyway | 10.12.0 (core-banking-service only) |
| ORM | Spring Data JPA / Hibernate | via Spring Boot |
| Distributed Tracing | Zipkin + Micrometer Tracing (Brave) | Zipkin 3 |
| API Documentation | SpringDoc OpenAPI (Swagger UI) | 2.1.0 |
| Build Tool | Gradle | 8.6 |
| Containerization | Docker / Docker Compose | Compose v3.6 |
| Monitoring | Spring Boot Actuator | via Spring Boot |
| Lombok | Lombok | via Spring Boot BOM |

---

## 4. Architecture Diagram (Logical)

```
                        ┌──────────────┐
                        │   Keycloak   │
                        │  (Auth/IdP)  │
                        │   :8080      │
                        └──────┬───────┘
                               │ JWT validation
                               │
┌──────────┐            ┌──────▼───────┐
│  Client  │───────────►│  API Gateway │
│(Postman) │  HTTP/REST │    :8082     │
└──────────┘            └──────┬───────┘
                               │ X-Auth-Id header
                ┌──────────────┼──────────────┐
                │              │              │
        ┌───────▼──────┐ ┌────▼────────┐ ┌───▼───────────┐
        │ User Service │ │Fund Transfer│ │Utility Payment│
        │    :8083     │ │  Service    │ │   Service     │
        │              │ │    :8084    │ │     :8085     │
        └──────┬───────┘ └─────┬──────┘ └──────┬────────┘
               │               │               │
               │         OpenFeign calls        │
               │               │               │
               └───────────────┼───────────────┘
                               │
                        ┌──────▼───────┐
                        │Core Banking  │
                        │  Service     │
                        │    :8092     │
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │    MySQL     │
                        │    :3306     │
                        └──────────────┘

       ┌───────────────┐         ┌──────────────┐
       │ Config Server │◄────────│  Eureka      │
       │    :8090      │         │  Registry    │
       │ (Git-backed)  │         │    :8081     │
       └───────────────┘         └──────────────┘
              ▲                         ▲
              │  bootstrap config       │ register/discover
              └─────── all services ────┘

                        ┌──────────────┐
                        │   Zipkin     │
                        │    :9411     │
                        └──────────────┘
                              ▲
                              │ trace spans
                              └─── all services
```

---

## 5. Communication Patterns

### 5.1 Synchronous (REST/HTTP)
All inter-service communication is **synchronous** via REST:

| Caller | Callee | Protocol | Client Library |
|---|---|---|---|
| API Gateway | All downstream services | HTTP | Spring Cloud Gateway (reactive proxy) |
| User Service | Core Banking Service | HTTP | OpenFeign (`BankingCoreRestClient`) |
| Fund Transfer Service | Core Banking Service | HTTP | OpenFeign (`BankingCoreFeignClient`) |
| Utility Payment Service | Core Banking Service | HTTP | OpenFeign (`BankingCoreRestClient`) |
| User Service | Keycloak | HTTP | Keycloak Admin Client SDK |

### 5.2 Asynchronous (Message Queue)
The README references **RabbitMQ** for notification messaging from Fund Transfer and Utility Payment services. However, **RabbitMQ is not yet implemented** in the current codebase — no RabbitMQ dependency exists in any `build.gradle`, and the Notification Service is marked as "PENDING Development."

---

## 6. Data Architecture

### 6.1 Database per Service
Each service owns its own MySQL database (created via `privileges.sql`):

| Database | Owner Service |
|---|---|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

### 6.2 Schema Management
- **core-banking-service**: Uses Flyway migrations (`db/migration/V1.0.*`)
  - `V1.0.20210427174638__create_base_table_structure.sql` — Users, accounts, utility accounts
  - `V1.0.20210427174721__temp_data.sql` — Seed data (test users, accounts, utility providers)
  - `V1.0.20210429210839__create_transaction_table.sql` — Transaction table
- **Other services**: Rely on JPA/Hibernate auto-DDL (no explicit migrations)

### 6.3 Core Data Model
```
banking_core_user (id, email, first_name, last_name, identification_number)
       │
       │ 1:N
       ▼
banking_core_account (id, number, type, status, actual_balance, available_balance, user_id)
       │
       │ 1:N
       ▼
banking_core_transaction (id, amount, transaction_type, reference_number, transaction_id, account_id)

banking_core_utility_account (id, number, provider_name)
```

---

## 7. Integration Points

### 7.1 Keycloak (Identity Provider)
- **Version**: 23.0.7
- **Connection**: User Service → Keycloak Admin REST API
- **Authentication flow**: Client credentials grant (`client_credentials`)
- **Configuration**: Externalized via Spring Cloud Config (`app.config.keycloak.*`)
- **Realm data**: Pre-imported via `keycloak/realm-export.json`
- **Keycloak DB**: PostgreSQL 15 (separate from application MySQL)

### 7.2 RabbitMQ (Message Broker) — Planned, Not Implemented
- Referenced in README as the messaging layer for the Notification Service
- No RabbitMQ dependency or configuration exists in the current codebase
- Notification Service is listed as "PENDING Development"

### 7.3 Zipkin (Distributed Tracing)
- **Version**: Zipkin 3 (Docker image: `openzipkin/zipkin:3`)
- **Integration**: Via Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`)
- **Reporter**: `zipkin-reporter-brave`
- **Feign instrumentation**: `feign-micrometer` for tracing Feign client calls
- **All services** (except Config Server and Service Registry) include tracing dependencies

### 7.4 Database Connections
- **MySQL** (single instance, multiple databases): `mysql_javatodev_app:3306`
  - Root password hardcoded in Docker Compose
  - Application user: `javatodev_development` / `oPItyPticIAt`
- **PostgreSQL** (Keycloak only): `keycloakdb:5432`
  - User: `keycloak` / `password`

---

## 8. Build and Deployment Pipeline

### 8.1 Build System (Gradle)
Each service is an independent Gradle project (no multi-project build):
- **Plugin**: `org.springframework.boot` 3.2.4, `io.spring.dependency-management` 1.1.4
- **Git info**: `com.gorylenko.gradle-git-properties` generates `git.properties` (most services)
- **Java**: Source compatibility Java 21
- **Testing**: JUnit 5 (`useJUnitPlatform()`)
- **No shared/common library**: DTOs, exceptions, mappers are duplicated across services

### 8.2 Docker Images
Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```
- Base image: Eclipse Temurin 21 JRE Alpine
- Uses `wait-for-it.sh` for service dependency ordering
- Docker profile activated via `-Dspring.profiles.active=docker`

### 8.3 Docker Compose
Two compose files in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: infrastructure + all application services |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak, MySQL, Config Server, Service Registry |

- Custom bridge network: `javatodev_ib_network` (subnet `172.25.0.0/16`)
- Static IP addresses assigned to each container
- Service startup ordering via `wait-for-it.sh` (waits for Config Server, Service Registry, MySQL)
- Persistent volumes: `postgres_data` (Keycloak DB), `mysqldata` (application DB)

### 8.4 Configuration Management
- **Spring Cloud Config Server** fetches configuration from Git:
  - Repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
  - Branch: `main`, Path: `configuration/`
- **Profile-based bootstrap**:
  - `bootstrap.yml` — Default (localhost)
  - `bootstrap-dev.yml` — Development (IP: `192.168.1.5`)
  - `bootstrap-docker.yml` — Docker (service name: `internet-banking-config-server`)

### 8.5 CI/CD
- No CI/CD pipeline configuration exists in the repository (no GitHub Actions, Jenkins, or equivalent)
- Build is manual: `./gradlew build` per service, then `docker-compose up`
