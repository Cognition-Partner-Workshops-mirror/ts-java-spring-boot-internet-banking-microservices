# Gap Analysis — Internet Banking Microservices

## Code Organization

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| CO-1 | No shared library for common code | High | Large | Exception classes (SimpleBankingGlobalException, GlobalExceptionHandler, ErrorResponse, AuditAware, GlobalErrorCode) are copy-pasted across 4 services. Should extract to a shared module. |
| CO-2 | No multi-project Gradle build | Medium | Medium | Each service has independent build.gradle with duplicated plugin/dependency declarations. Should use a root settings.gradle with shared conventions. |
| CO-3 | Inconsistent package structure for Feign clients | Low | Small | Fund transfer uses `service.rest.client.BankingCoreFeignClient`, utility payment uses `service.rest.BankingCoreRestClient`, user service uses `service.rest.BankingCoreRestClient`. Naming and package paths differ. |
| CO-4 | Mapper classes instantiated with `new` instead of Spring beans | Low | Small | `AccountService`, `FundTransferService`, `UtilityPaymentService` all do `private XMapper mapper = new XMapper()` instead of injecting via Spring. |

## Error Handling

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| EH-1 | All exceptions return HTTP 400 | Critical | Small | GlobalExceptionHandler catches `Exception.class` and returns `badRequest()`. EntityNotFoundException should return 404, server errors should return 500. |
| EH-2 | Generic catch-all returns raw string | High | Small | The `handleException(Exception e)` method returns a plain string `"Exception occur inside API " + e` — leaks stack traces and is not a structured ErrorResponse. |
| EH-3 | No error handling for Feign failures in fund-transfer and utility-payment services | Critical | Medium | FundTransferService and UtilityPaymentService have no try-catch around Feign calls. If core-banking-service is down, unhandled exceptions propagate. Only user-service has a CustomFeignErrorDecoder. |
| EH-4 | Fund transfer service missing GlobalErrorCode | Medium | Small | Fund transfer service has no GlobalErrorCode class (only core-banking and user-service have one). |

## Testing

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| T-1 | Only core-banking-service has unit tests | Critical | Large | Only 3 test classes with actual tests exist (AccountServiceTest, TransactionServiceTest, UserServiceTest) — all in core-banking-service. Other services have only empty ApplicationTests. |
| T-2 | No integration tests | High | Large | No tests that verify Feign client contracts, database integration, or end-to-end flows. |
| T-3 | No contract tests between services | High | Large | No Spring Cloud Contract or Pact tests to verify Feign client compatibility with provider APIs. |
| T-4 | No controller/API layer tests | High | Medium | No MockMvc or WebTestClient tests for any controller. |

## Security

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| S-1 | Zero input validation | Critical | Medium | No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, or `@Pattern` annotations anywhere. Fund transfer amounts, account numbers, user registration data are all unvalidated. |
| S-2 | Hardcoded secrets in docker-compose.yml | Critical | Small | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), Keycloak DB password (`password`) are in plaintext. |
| S-3 | No authentication on internal services | High | Medium | Only the API Gateway enforces OAuth2. Internal services (core-banking, user, fund-transfer, utility-payment) have no security — anyone with network access can call them directly. |
| S-4 | Config server fetches from public GitHub repo | High | Small | Sensitive configuration (DB credentials, Keycloak secrets) is stored in a public GitHub repository. |
| S-5 | Sensitive data logged | Medium | Small | Controllers log full request objects including potentially sensitive data (e.g., `fundTransferRequest.toString()`, `user.toString()`). |

## API Design

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| A-1 | Raw ResponseEntity without type parameters | Medium | Small | All controllers use `ResponseEntity` without generics (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`), losing type safety and OpenAPI schema generation. |
| A-2 | No API versioning strategy | Low | Medium | All endpoints use `/api/v1/` but there's no mechanism for version negotiation or multiple versions. |
| A-3 | Inconsistent REST conventions | Medium | Small | User update uses PATCH `/update/{id}` (verb in URL). Fund transfer uses POST on collection root. Utility account lookup uses provider name in path instead of query param. |
| A-4 | No filtering or sorting on list endpoints | Low | Medium | Pageable is accepted but no filtering/sorting parameters are exposed. |
| A-5 | OpenAPI/Swagger dependency mismatch | Medium | Small | Services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC (not WebFlux) applications. Should use `springdoc-openapi-starter-webmvc-ui`. |

## Observability

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| O-1 | No structured logging | Medium | Medium | Uses default Spring Boot logging with `@Slf4j`. No JSON log format, no correlation ID propagation in logs, no MDC usage. |
| O-2 | No custom health checks | Low | Small | Actuator is included but no custom health indicators for database connectivity, Keycloak availability, etc. |
| O-3 | No metrics endpoints beyond defaults | Low | Medium | No custom Micrometer metrics for business operations (transfer counts, payment amounts, etc.). |
| O-4 | Tracing configuration externalized | Low | Small | Zipkin/tracing config is in the external config repo, not visible in the codebase. Sampling rate and configuration are not documented. |

## Resilience

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| R-1 | No circuit breakers | Critical | Medium | Zero resilience4j or Spring Cloud Circuit Breaker usage. If core-banking-service goes down, all dependent services will cascade-fail. |
| R-2 | No retry policies | High | Small | No `@Retryable` or Feign retry configuration. Transient network failures cause immediate failure. |
| R-3 | No timeout configuration | High | Small | No Feign timeouts, no connection/read timeouts configured. Slow downstream services will block threads indefinitely. |
| R-4 | No fallback behavior | High | Medium | No fallback methods for Feign clients. No graceful degradation. |
| R-5 | No idempotency on fund transfers | Critical | Medium | If a fund transfer Feign call succeeds but the response is lost (network issue), retrying would create a duplicate transfer. No idempotency keys. |

## Data Integrity Bugs

| # | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| D-1 | Double-deduction bug in balance calculation | Critical | Small | In TransactionService.internalFundTransfer() (line 90-91) and utilPayment() (line 63-64): `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` was already subtracted, causing availableBalance to be deducted twice. |
| D-2 | TransactionEntity uses @OneToOne instead of @ManyToOne | High | Small | TransactionEntity has `@OneToOne(cascade = CascadeType.ALL)` to BankAccountEntity. Multiple transactions reference the same account, so this should be `@ManyToOne`. The `CascadeType.ALL` could cascade-delete accounts. |
| D-3 | No transactional consistency across services | Critical | Large | Fund transfer service saves PENDING locally, then calls core-banking via Feign. If the Feign call fails after core-banking commits, the local record stays PENDING forever. No saga pattern or compensation. |
