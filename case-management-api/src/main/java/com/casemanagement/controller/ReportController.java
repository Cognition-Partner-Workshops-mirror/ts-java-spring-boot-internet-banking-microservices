package com.casemanagement.controller;

import com.casemanagement.dto.ApiLogReportResponse;
import com.casemanagement.entity.ApiRequestLog;
import com.casemanagement.service.ApiRequestLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for API usage reporting endpoints.
 * Provides aggregated metrics from the API request log for reporting purposes.
 *
 * This controller is key for measuring API usage and will be extended
 * as more endpoints are added to the application.
 *
 * Available reports:
 *   GET /api/v1/reports/summary       - Overall summary (today + weekly counts)
 *   GET /api/v1/reports/daily         - Daily call count breakdown
 *   GET /api/v1/reports/by-endpoint   - Call counts grouped by endpoint
 *   GET /api/v1/reports/by-user       - Call counts grouped by SM_USER
 *   GET /api/v1/reports/logs          - Raw log entries for a date range
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "API usage reporting and analytics endpoints")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ApiRequestLogService logService;

    @Autowired
    public ReportController(ApiRequestLogService logService) {
        this.logService = logService;
    }

    /**
     * Get an overall summary of API usage.
     * Returns today's call count, weekly call count, and error count.
     * This is the primary endpoint for dashboard reporting.
     *
     * @param smUser the authenticated user from SM_USER header
     * @return summary map with key metrics
     */
    @Operation(summary = "Get API usage summary", description = "Returns today's and weekly call counts, error count, and report metadata")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(value = "SM_USER", required = true) String smUser) {

        logger.info("GET /api/v1/reports/summary - SM_USER: {}", smUser);

        Map<String, Object> summary = new HashMap<>();
        summary.put("todayCallCount", logService.getTodayCallCount());
        summary.put("weeklyCallCount", logService.getWeeklyCallCount());
        summary.put("errorCountToday", logService.getErrorCount(LocalDate.now(), LocalDate.now()));
        summary.put("reportGeneratedAt", java.time.LocalDateTime.now().toString());
        summary.put("reportGeneratedBy", smUser);

        return ResponseEntity.ok(summary);
    }

    /**
     * Get daily call count breakdown for a date range.
     * Useful for trending and capacity planning reports.
     *
     * @param smUser    the authenticated user from SM_USER header
     * @param startDate start date of the reporting period (yyyy-MM-dd)
     * @param endDate   end date of the reporting period (yyyy-MM-dd)
     * @return list of daily call count reports
     */
    @Operation(summary = "Get daily call counts", description = "Returns call count breakdown by day for the given date range")
    @GetMapping("/daily")
    public ResponseEntity<List<ApiLogReportResponse>> getDailyReport(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        logger.info("GET /api/v1/reports/daily - SM_USER: {}, range: {} to {}", smUser, startDate, endDate);

        List<ApiLogReportResponse> report = logService.getDailyCallCounts(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get call counts grouped by endpoint.
     * Shows which API endpoints are most frequently used.
     *
     * @param smUser    the authenticated user from SM_USER header
     * @param startDate start date of the reporting period (yyyy-MM-dd)
     * @param endDate   end date of the reporting period (yyyy-MM-dd)
     * @return list of per-endpoint call count reports
     */
    @Operation(summary = "Get calls by endpoint", description = "Returns call count breakdown grouped by endpoint for the given date range")
    @GetMapping("/by-endpoint")
    public ResponseEntity<List<ApiLogReportResponse>> getByEndpointReport(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        logger.info("GET /api/v1/reports/by-endpoint - SM_USER: {}, range: {} to {}", smUser, startDate, endDate);

        List<ApiLogReportResponse> report = logService.getCallCountsByEndpoint(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get call counts grouped by SM_USER.
     * Shows which users are making the most API calls.
     *
     * @param smUser    the authenticated user from SM_USER header
     * @param startDate start date of the reporting period (yyyy-MM-dd)
     * @param endDate   end date of the reporting period (yyyy-MM-dd)
     * @return list of per-user call count reports
     */
    @Operation(summary = "Get calls by user", description = "Returns call count breakdown grouped by SM_USER for the given date range")
    @GetMapping("/by-user")
    public ResponseEntity<List<ApiLogReportResponse>> getByUserReport(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        logger.info("GET /api/v1/reports/by-user - SM_USER: {}, range: {} to {}", smUser, startDate, endDate);

        List<ApiLogReportResponse> report = logService.getCallCountsByUser(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Get raw log entries for a date range.
     * Provides detailed log data for in-depth analysis and debugging.
     *
     * @param smUser    the authenticated user from SM_USER header
     * @param startDate start date (yyyy-MM-dd)
     * @param endDate   end date (yyyy-MM-dd)
     * @return list of raw API request log entries
     */
    @Operation(summary = "Get raw log entries", description = "Returns detailed API request log entries for the given date range")
    @GetMapping("/logs")
    public ResponseEntity<List<ApiRequestLog>> getRawLogs(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        logger.info("GET /api/v1/reports/logs - SM_USER: {}, range: {} to {}", smUser, startDate, endDate);

        List<ApiRequestLog> logs = logService.getLogsByDateRange(startDate, endDate);
        return ResponseEntity.ok(logs);
    }
}
