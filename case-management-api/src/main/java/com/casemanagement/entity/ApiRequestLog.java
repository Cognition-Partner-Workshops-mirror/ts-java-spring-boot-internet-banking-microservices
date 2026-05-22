package com.casemanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import java.time.LocalDateTime;

/**
 * Entity representing an API request log entry.
 * Every API request is logged here for reporting purposes.
 * This table is the foundation for measuring API usage metrics such as:
 *   - Number of calls per day/week/month
 *   - Calls per endpoint
 *   - Calls per user (sm_user)
 *   - Response time tracking
 *   - Error rate monitoring
 *
 * Indexes are defined on key columns to optimize reporting queries.
 */
@Entity
@Table(name = "API_REQUEST_LOG", indexes = {
    @Index(name = "IDX_REQ_LOG_TIMESTAMP", columnList = "REQUEST_TIMESTAMP"),
    @Index(name = "IDX_REQ_LOG_ENDPOINT", columnList = "ENDPOINT"),
    @Index(name = "IDX_REQ_LOG_SM_USER", columnList = "SM_USER"),
    @Index(name = "IDX_REQ_LOG_HTTP_METHOD", columnList = "HTTP_METHOD"),
    @Index(name = "IDX_REQ_LOG_RESPONSE_STATUS", columnList = "RESPONSE_STATUS")
})
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "api_log_seq")
    @SequenceGenerator(name = "api_log_seq", sequenceName = "API_LOG_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /** The API endpoint that was called (e.g., /api/v1/case-data/set1) */
    @Column(name = "ENDPOINT", nullable = false, length = 500)
    private String endpoint;

    /** HTTP method used (GET, POST, PUT, DELETE, etc.) */
    @Column(name = "HTTP_METHOD", nullable = false, length = 10)
    private String httpMethod;

    /** The authenticated user from SM_USER header */
    @Column(name = "SM_USER", length = 100)
    private String smUser;

    /** IP address of the client making the request */
    @Column(name = "CLIENT_IP", length = 50)
    private String clientIp;

    /** Request body payload (stored for audit purposes, truncated if too large) */
    @Column(name = "REQUEST_BODY", length = 4000)
    private String requestBody;

    /** HTTP response status code returned */
    @Column(name = "RESPONSE_STATUS")
    private Integer responseStatus;

    /** Time taken to process the request in milliseconds */
    @Column(name = "RESPONSE_TIME_MS")
    private Long responseTimeMs;

    /** Error message if the request resulted in an error */
    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    /** Timestamp when the request was received */
    @Column(name = "REQUEST_TIMESTAMP", nullable = false)
    private LocalDateTime requestTimestamp;

    /** User agent string from the request headers */
    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    /** Correlation ID for tracing requests across services */
    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    // Default constructor required by JPA
    public ApiRequestLog() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getSmUser() {
        return smUser;
    }

    public void setSmUser(String smUser) {
        this.smUser = smUser;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(LocalDateTime requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public String toString() {
        return "ApiRequestLog{" +
                "id=" + id +
                ", endpoint='" + endpoint + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", smUser='" + smUser + '\'' +
                ", responseStatus=" + responseStatus +
                ", responseTimeMs=" + responseTimeMs +
                ", requestTimestamp=" + requestTimestamp +
                '}';
    }
}
