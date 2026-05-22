# Case Management API

A Java 21 Spring Boot REST API application for case data management with Oracle database backend and comprehensive API request logging for reporting.

## Overview

This application provides two sets of REST endpoints for receiving and storing case data (case number, version number, agent name, party identifier). Every API request is authenticated via the `SM_USER` header and logged for reporting purposes.

## Architecture

```
Client → [SM_USER Header] → Spring Boot API → [Logging Interceptor] → Controller → Service → Oracle Package/Stored Procedure → Oracle DB
                                                      ↓
                                              API_REQUEST_LOG table
                                                      ↓
                                              Reporting Endpoints
```

## Tech Stack

- **Java 21** (OpenJDK)
- **Spring Boot 3.3.5** (Web, Data JPA, Validation, Actuator)
- **Oracle Database** (via ojdbc11 driver, PL/SQL packages)
- **H2 Database** (for local development/testing)
- **Logback** (structured logging with correlation IDs)

## Endpoints

### Case Data Set 1
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/case-data/set1` | Submit case data to Set 1 |
| GET | `/api/v1/case-data/set1` | Retrieve all Set 1 data |
| GET | `/api/v1/case-data/set1/search?caseNumber=X` | Search Set 1 by case number |

### Case Data Set 2
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/case-data/set2` | Submit case data to Set 2 |
| GET | `/api/v1/case-data/set2` | Retrieve all Set 2 data |
| GET | `/api/v1/case-data/set2/search?caseNumber=X` | Search Set 2 by case number |

### Reporting
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/reports/summary` | Today's + weekly call count summary |
| GET | `/api/v1/reports/daily?startDate=X&endDate=Y` | Daily call count breakdown |
| GET | `/api/v1/reports/by-endpoint?startDate=X&endDate=Y` | Calls grouped by endpoint |
| GET | `/api/v1/reports/by-user?startDate=X&endDate=Y` | Calls grouped by SM_USER |
| GET | `/api/v1/reports/logs?startDate=X&endDate=Y` | Raw log entries |

## Request Headers

All endpoints require the `SM_USER` header:
```
SM_USER: john.doe
```

## Request Body (POST endpoints)

```json
{
    "caseNumber": "CASE-001",
    "versionNumber": "1.0",
    "agentName": "Agent Smith",
    "partyIdentifier": "PARTY-12345"
}
```

## Running Locally (H2 Database)

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean package -DskipTests
java -jar target/case-management-api-1.0.0-SNAPSHOT.jar
```

Access H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:casemanagement`)

## Running with Oracle

```bash
export ORACLE_DB_USERNAME=case_mgmt_user
export ORACLE_DB_PASSWORD=case_mgmt_pass
java -jar target/case-management-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=oracle
```

Before running, execute the SQL scripts:
1. `src/main/resources/db/oracle_schema.sql` - Creates tables, sequences, and indexes
2. `src/main/resources/db/oracle_package.sql` - Creates the CASE_DATA_PKG PL/SQL package

## Logging & Reporting

Every API request is automatically logged via the `ApiRequestLoggingInterceptor` which captures:
- Endpoint URL and HTTP method
- SM_USER (authenticated user)
- Client IP address
- Response status and response time
- Correlation ID for distributed tracing
- Error messages (if any)

Logs are written to:
- **Console** (stdout) - with correlation ID and SM_USER in the pattern
- **File** (`logs/case-management-api.log`) - daily rotation, 30-day retention
- **JSON File** (`logs/case-management-api-json.log`) - structured format for log analysis tools
- **Database** (`API_REQUEST_LOG` table) - queryable via reporting endpoints

## Database Schema

### Tables
- `CASE_DATA_SET1` - Stores case data from Set 1 endpoints
- `CASE_DATA_SET2` - Stores case data from Set 2 endpoints
- `API_REQUEST_LOG` - Logs all API requests for reporting

### Oracle Package
- `CASE_DATA_PKG.INSERT_CASE_DATA_SET1` - Inserts data into Set 1
- `CASE_DATA_PKG.INSERT_CASE_DATA_SET2` - Inserts data into Set 2
