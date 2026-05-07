# Gap Analysis

## Code Organization

| Gap | Severity | Effort |
|-----|----------|--------|
| No shared library — `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `AuditAware`, `BaseMapper`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are copy-pasted across 3-4 services | High | Large |
| No Gradle multi-project build — each service is completely independent with duplicated build config | Medium | Medium |
| Inconsistent package structure — core-banking uses `repository/` at top level, user-service uses `model/repository/`, utility-payment uses `repository/` at top level | Medium | Small |
| Inconsistent Feign client naming — user-service: `BankingCoreRestClient`, fund-transfer: `BankingCoreFeignClient`, utility-payment: `BankingCoreRestClient` | Low | Small |
| Mappers instantiated with `new` instead of Spring beans — e.g., `private UserMapper userMapper = new UserMapper()` in service classes | Low | Small |

## Error Handling

| Gap | Severity | Effort |
|-----|----------|--------|
| Catch-all `Exception.class` handler returns raw exception string: `"Exception occur inside API " + e` — leaks stack traces and internal details to clients | Critical | Small |
| All exceptions return HTTP 400 Bad Request regardless of type (404 for not found, 500 for server errors, etc.) | High | Small |
| No Feign error decoder in fund-transfer or utility-payment services — only user-service has `CustomFeignErrorDecoder` | High | Medium |
| `GlobalExceptionHandler` uses raw `ResponseEntity` without type parameter | Low | Small |
| No validation error handling (no `MethodArgumentNotValidException` handler) | Medium | Small |

## Testing

| Gap | Severity | Effort |
|-----|----------|--------|
| Only core-banking-service has real unit tests (3 test classes: AccountServiceTest, TransactionServiceTest, UserServiceTest). All other 6 services have only empty context-load test stubs | Critical | Large |
| Zero integration tests across the entire codebase | Critical | Large |
| Zero contract tests between services (no Spring Cloud Contract, Pact, etc.) | High | Large |
| No controller/API layer tests | High | Medium |
| No test for Keycloak integration in user-service | High | Medium |
| Core banking tests don't use `@MockBean`/`@ExtendWith(MockitoExtension.class)` — manual mock setup | Low | Small |

## Security

| Gap | Severity | Effort |
|-----|----------|--------|
| Hardcoded credentials everywhere: MySQL root password in docker-compose.yml and Dockerfile, Keycloak admin password, DB user password in privileges.sql, Keycloak client secrets in realm-export.json, test credentials in README | Critical | Medium |
| Zero input validation — no `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size` annotations on any request DTO or controller parameter. Fund transfer amount could be negative or zero. | Critical | Medium |
| Downstream services (user, fund-transfer, utility-payment, core-banking) have NO authentication/authorization — only the gateway enforces security. Any direct access to service ports bypasses auth entirely. | Critical | Medium |
| CSRF disabled in gateway without documented justification | Medium | Small |
| Actuator endpoints are publicly accessible (`/actuator/**` permitted for all) | High | Small |
| No rate limiting on any endpoint | Medium | Medium |
| No dependency vulnerability scanning (no OWASP plugin, no Snyk, no Dependabot) | Medium | Small |

## API Design

| Gap | Severity | Effort |
|-----|----------|--------|
| Controllers return raw `ResponseEntity` without generic type parameters — no compile-time type safety | Medium | Small |
| No consistent API response envelope (success responses are bare objects, errors use ErrorResponse) | Medium | Medium |
| Pagination uses Spring's default `Pageable` but no documented pagination response metadata (total pages, total elements) | Medium | Small |
| No API versioning strategy beyond `/v1/` in path — no headers, no content negotiation | Low | Small |
| OpenAPI/Swagger annotations exist (`@Tag`, `@Operation`) but `springdoc-openapi-starter-webflux-ui` is wrong dependency for WebMVC services (should be `webmvc-ui`) | High | Small |
| No filtering or sorting support on list endpoints | Low | Medium |

## Observability

| Gap | Severity | Effort |
|-----|----------|--------|
| No structured logging — uses default Spring Boot format with `@Slf4j` but no JSON formatter, no MDC correlation | Medium | Medium |
| Health check endpoints exist via Actuator but no custom health indicators for downstream dependencies | Medium | Small |
| Zipkin tracing dependencies present but no explicit configuration for sampling rate | Low | Small |
| No metrics beyond default Actuator (no custom business metrics) | Medium | Medium |
| Prometheus mentioned in README tech stack but no Prometheus dependency in any build.gradle | Medium | Small |
| Log statements include `toString()` on request objects which could log sensitive data (passwords, account numbers) | High | Small |

## Resilience

| Gap | Severity | Effort |
|-----|----------|--------|
| Zero circuit breakers — no Resilience4j, no Hystrix | Critical | Medium |
| Zero retry policies on Feign clients | High | Small |
| Zero timeout configuration on Feign clients or HTTP connections | Critical | Small |
| Zero fallback behavior — if core-banking-service is down, all business services fail with unhandled exceptions | Critical | Medium |
| No bulkhead pattern — a slow downstream call can exhaust all threads | High | Medium |
| Fund transfer is not idempotent — no idempotency key, retries could cause double transfers | Critical | Medium |
| `TransactionService.internalFundTransfer()` is not truly atomic — saves sender and receiver in separate `save()` calls; if the process crashes between them, funds are lost | Critical | Large |

## Additional Bugs Found

- `TransactionService.utilPayment()` line 64: `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(...))` — actualBalance was already subtracted on line 63, so availableBalance gets double-subtracted
- `TransactionService.internalFundTransfer()` lines 91, 100: Same double-subtraction bug for availableBalance on both sender and receiver
- `TransactionEntity` uses `@OneToOne(cascade = CascadeType.ALL)` for account relationship — should be `@ManyToOne` since one account can have many transactions. CascadeType.ALL means deleting a transaction would delete the account.
- `UtilityPaymentRepository extends JpaRepository<UtilityPaymentEntity, UtilityPayment>` — uses DTO class `UtilityPayment` as ID type instead of `Long`, which will cause runtime errors
