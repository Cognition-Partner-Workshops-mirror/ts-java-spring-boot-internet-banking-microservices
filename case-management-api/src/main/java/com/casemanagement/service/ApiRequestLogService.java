package com.casemanagement.service;

import com.casemanagement.dto.ApiLogReportResponse;
import com.casemanagement.entity.ApiRequestLog;
import com.casemanagement.repository.ApiRequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing API request logs and generating usage reports.
 * This is a core service for the reporting requirement - it provides
 * methods to log requests and query aggregated metrics.
 *
 * Key reporting capabilities:
 *   - Daily call count summaries
 *   - Weekly call count summaries
 *   - Per-endpoint usage breakdown
 *   - Per-user activity tracking
 *   - Response time analysis
 *   - Error rate monitoring
 */
@Service
public class ApiRequestLogService {

    private static final Logger logger = LoggerFactory.getLogger(ApiRequestLogService.class);

    private final ApiRequestLogRepository logRepository;

    @Autowired
    public ApiRequestLogService(ApiRequestLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Saves an API request log entry asynchronously to avoid impacting response time.
     * This method is called by the logging interceptor for every API request.
     *
     * @param logEntry the log entry to persist
     */
    @Async
    public void saveLogAsync(ApiRequestLog logEntry) {
        try {
            logRepository.save(logEntry);
            logger.debug("API request log saved: {} {} - status: {}",
                    logEntry.getHttpMethod(), logEntry.getEndpoint(), logEntry.getResponseStatus());
        } catch (Exception e) {
            // Log the error but don't let it affect the API response
            logger.error("Failed to save API request log: {}", e.getMessage(), e);
        }
    }

    /**
     * Saves an API request log entry synchronously.
     * Used when guaranteed persistence is required.
     *
     * @param logEntry the log entry to persist
     */
    public void saveLog(ApiRequestLog logEntry) {
        logRepository.save(logEntry);
    }

    /**
     * Get the total number of API calls for today.
     * Used for daily reporting dashboards.
     *
     * @return total call count for today
     */
    public Long getTodayCallCount() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return logRepository.countByRequestTimestampBetween(startOfDay, endOfDay);
    }

    /**
     * Get the total number of API calls for the current week (last 7 days).
     * Used for weekly reporting dashboards.
     *
     * @return total call count for the last 7 days
     */
    public Long getWeeklyCallCount() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return logRepository.countByRequestTimestampBetween(weekAgo, LocalDateTime.now());
    }

    /**
     * Get daily call count breakdown for a date range.
     * Returns a list of reports with date as the group key and call count.
     *
     * @param startDate start of the reporting period
     * @param endDate   end of the reporting period
     * @return list of daily call count reports
     */
    public List<ApiLogReportResponse> getDailyCallCounts(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> results = logRepository.dailyCallCounts(start, end);
        List<ApiLogReportResponse> reports = new ArrayList<>();

        for (Object[] row : results) {
            String date = row[0] != null ? row[0].toString() : "unknown";
            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            reports.add(new ApiLogReportResponse(date, count, null, null, null));
        }

        return reports;
    }

    /**
     * Get call count breakdown per endpoint for a date range.
     * Essential for understanding which endpoints are most used.
     *
     * @param startDate start of the reporting period
     * @param endDate   end of the reporting period
     * @return list of per-endpoint call count reports
     */
    public List<ApiLogReportResponse> getCallCountsByEndpoint(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> results = logRepository.countByEndpointBetween(start, end);
        List<ApiLogReportResponse> reports = new ArrayList<>();

        for (Object[] row : results) {
            String endpoint = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            reports.add(new ApiLogReportResponse(endpoint, count, null, null, null));
        }

        return reports;
    }

    /**
     * Get call count breakdown per user for a date range.
     * Useful for tracking user activity and identifying heavy API consumers.
     *
     * @param startDate start of the reporting period
     * @param endDate   end of the reporting period
     * @return list of per-user call count reports
     */
    public List<ApiLogReportResponse> getCallCountsByUser(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> results = logRepository.countBySmUserBetween(start, end);
        List<ApiLogReportResponse> reports = new ArrayList<>();

        for (Object[] row : results) {
            String user = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            reports.add(new ApiLogReportResponse(user, count, null, null, null));
        }

        return reports;
    }

    /**
     * Get all log entries within a date range.
     * Provides raw log data for detailed analysis.
     *
     * @param startDate start date
     * @param endDate   end date
     * @return list of API request log entries
     */
    public List<ApiRequestLog> getLogsByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return logRepository.findByRequestTimestampBetween(start, end);
    }

    /**
     * Get error count within a date range.
     * Used for error rate monitoring in reports.
     *
     * @param startDate start date
     * @param endDate   end date
     * @return count of error responses (HTTP status >= 400)
     */
    public Long getErrorCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return logRepository.countErrorsBetween(start, end);
    }
}
