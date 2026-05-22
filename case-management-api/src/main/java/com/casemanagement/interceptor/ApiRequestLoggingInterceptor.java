package com.casemanagement.interceptor;

import com.casemanagement.entity.ApiRequestLog;
import com.casemanagement.service.ApiRequestLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * HTTP request interceptor that logs every API request for reporting purposes.
 * This is the primary mechanism for capturing API usage metrics.
 *
 * For each request, it captures:
 *   - Endpoint URL and HTTP method
 *   - SM_USER from request header (authenticated user)
 *   - Client IP address
 *   - User agent
 *   - Correlation ID (generated if not provided)
 *   - Request timestamp
 *   - Response status and response time (in afterCompletion)
 *
 * The data is persisted to API_REQUEST_LOG table and can be queried
 * for reporting via the /api/v1/reports endpoints.
 *
 * Logging can be enabled/disabled via app.logging.enabled property.
 */
@Component
public class ApiRequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiRequestLoggingInterceptor.class);

    /** Attribute key for storing the request start time */
    private static final String START_TIME_ATTR = "requestStartTime";

    /** Attribute key for storing the log entry across the request lifecycle */
    private static final String LOG_ENTRY_ATTR = "apiRequestLogEntry";

    /** SM_USER header name - the authenticated user from SiteMinder */
    private static final String SM_USER_HEADER = "SM_USER";

    /** Correlation ID header for distributed tracing */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Value("${app.logging.enabled:true}")
    private boolean loggingEnabled;

    private final ApiRequestLogService logService;

    @Autowired
    public ApiRequestLoggingInterceptor(ApiRequestLogService logService) {
        this.logService = logService;
    }

    /**
     * Pre-handle: Captures request details before the controller processes it.
     * Records start time, extracts headers, and creates the log entry.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!loggingEnabled) {
            return true;
        }

        // Record start time for response time calculation
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR, startTime);

        // Generate or extract correlation ID for request tracing
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Add correlation ID to MDC for structured logging across the request
        MDC.put("correlationId", correlationId);
        MDC.put("smUser", request.getHeader(SM_USER_HEADER) != null
                ? request.getHeader(SM_USER_HEADER) : "anonymous");

        // Create log entry with pre-request data
        ApiRequestLog logEntry = new ApiRequestLog();
        logEntry.setEndpoint(request.getRequestURI());
        logEntry.setHttpMethod(request.getMethod());
        logEntry.setSmUser(request.getHeader(SM_USER_HEADER));
        logEntry.setClientIp(getClientIpAddress(request));
        logEntry.setUserAgent(request.getHeader("User-Agent"));
        logEntry.setCorrelationId(correlationId);
        logEntry.setRequestTimestamp(LocalDateTime.now());

        // Store log entry in request attributes for afterCompletion
        request.setAttribute(LOG_ENTRY_ATTR, logEntry);

        // Set correlation ID in response header for client-side tracing
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        logger.info("API Request Started: {} {} | SM_USER: {} | IP: {} | CorrelationID: {}",
                request.getMethod(), request.getRequestURI(),
                logEntry.getSmUser(), logEntry.getClientIp(), correlationId);

        return true;
    }

    /**
     * Post-handle: Called after controller processing but before view rendering.
     * Not used in this implementation but available for future enhancement.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
        // No additional processing needed at this stage
    }

    /**
     * After completion: Captures response details and persists the complete log entry.
     * This is called after the entire request-response cycle is complete,
     * including any error handling.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        if (!loggingEnabled) {
            return;
        }

        try {
            // Calculate response time
            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            long responseTimeMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

            // Retrieve the log entry created in preHandle
            ApiRequestLog logEntry = (ApiRequestLog) request.getAttribute(LOG_ENTRY_ATTR);
            if (logEntry != null) {
                // Update with response details
                logEntry.setResponseStatus(response.getStatus());
                logEntry.setResponseTimeMs(responseTimeMs);

                // Capture exception message if an error occurred
                if (ex != null) {
                    String errorMsg = ex.getMessage();
                    logEntry.setErrorMessage(
                            errorMsg != null && errorMsg.length() > 2000
                                    ? errorMsg.substring(0, 2000) : errorMsg);
                }

                // Persist the log entry asynchronously to avoid blocking the response
                logService.saveLogAsync(logEntry);

                logger.info("API Request Completed: {} {} | Status: {} | Time: {}ms | SM_USER: {}",
                        logEntry.getHttpMethod(), logEntry.getEndpoint(),
                        logEntry.getResponseStatus(), responseTimeMs, logEntry.getSmUser());
            }
        } catch (Exception e) {
            // Ensure logging failures never affect the API response
            logger.error("Error in API request logging: {}", e.getMessage(), e);
        } finally {
            // Clean up MDC to prevent context leakage between requests
            MDC.clear();
        }
    }

    /**
     * Extracts the real client IP address, considering proxy headers.
     * Checks X-Forwarded-For and X-Real-IP headers before falling back
     * to the remote address from the request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check X-Forwarded-For header first (set by load balancers/proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP if multiple are present (original client IP)
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header (set by nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        // Fall back to the direct remote address
        return request.getRemoteAddr();
    }
}
