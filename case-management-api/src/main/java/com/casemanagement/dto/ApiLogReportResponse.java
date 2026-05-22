package com.casemanagement.dto;

/**
 * DTO for API usage reporting metrics.
 * Used to return aggregated log data for reporting purposes,
 * such as number of API calls per day, per endpoint, per user, etc.
 */
public class ApiLogReportResponse {

    /** The grouping key (e.g., date, endpoint, user) */
    private String groupKey;

    /** Total number of API calls for this group */
    private Long totalCalls;

    /** Average response time in milliseconds */
    private Double avgResponseTimeMs;

    /** Number of successful calls (2xx responses) */
    private Long successCount;

    /** Number of error calls (4xx/5xx responses) */
    private Long errorCount;

    // Default constructor
    public ApiLogReportResponse() {
    }

    // Parameterized constructor
    public ApiLogReportResponse(String groupKey, Long totalCalls, Double avgResponseTimeMs,
                                Long successCount, Long errorCount) {
        this.groupKey = groupKey;
        this.totalCalls = totalCalls;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.successCount = successCount;
        this.errorCount = errorCount;
    }

    // Getters and Setters
    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public Long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(Long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public Double getAvgResponseTimeMs() {
        return avgResponseTimeMs;
    }

    public void setAvgResponseTimeMs(Double avgResponseTimeMs) {
        this.avgResponseTimeMs = avgResponseTimeMs;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Long errorCount) {
        this.errorCount = errorCount;
    }
}
