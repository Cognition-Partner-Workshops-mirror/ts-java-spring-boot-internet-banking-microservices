package com.casemanagement.repository;

import com.casemanagement.entity.ApiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for ApiRequestLog entity.
 * Provides CRUD operations and custom reporting queries for API request logs.
 * These queries are designed to support reporting requirements:
 *   - Daily/weekly call counts
 *   - Per-endpoint usage metrics
 *   - Per-user activity tracking
 *   - Response time analysis
 */
@Repository
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    /** Find all logs within a time range - used for daily/weekly reports */
    List<ApiRequestLog> findByRequestTimestampBetween(LocalDateTime start, LocalDateTime end);

    /** Find all logs for a specific endpoint */
    List<ApiRequestLog> findByEndpoint(String endpoint);

    /** Find all logs for a specific user */
    List<ApiRequestLog> findBySmUser(String smUser);

    /** Count total requests within a time range - for daily/weekly call counts */
    Long countByRequestTimestampBetween(LocalDateTime start, LocalDateTime end);

    /** Count requests per endpoint within a time range */
    @Query("SELECT a.endpoint, COUNT(a) FROM ApiRequestLog a " +
           "WHERE a.requestTimestamp BETWEEN :start AND :end " +
           "GROUP BY a.endpoint ORDER BY COUNT(a) DESC")
    List<Object[]> countByEndpointBetween(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** Count requests per user within a time range */
    @Query("SELECT a.smUser, COUNT(a) FROM ApiRequestLog a " +
           "WHERE a.requestTimestamp BETWEEN :start AND :end " +
           "GROUP BY a.smUser ORDER BY COUNT(a) DESC")
    List<Object[]> countBySmUserBetween(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /** Get average response time per endpoint */
    @Query("SELECT a.endpoint, AVG(a.responseTimeMs) FROM ApiRequestLog a " +
           "WHERE a.requestTimestamp BETWEEN :start AND :end " +
           "GROUP BY a.endpoint")
    List<Object[]> avgResponseTimeByEndpointBetween(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    /** Count errors (status >= 400) within a time range */
    @Query("SELECT COUNT(a) FROM ApiRequestLog a " +
           "WHERE a.requestTimestamp BETWEEN :start AND :end " +
           "AND a.responseStatus >= 400")
    Long countErrorsBetween(@Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    /** Get daily call counts grouped by date - key reporting query */
    @Query("SELECT CAST(a.requestTimestamp AS DATE), COUNT(a) FROM ApiRequestLog a " +
           "WHERE a.requestTimestamp BETWEEN :start AND :end " +
           "GROUP BY CAST(a.requestTimestamp AS DATE) " +
           "ORDER BY CAST(a.requestTimestamp AS DATE)")
    List<Object[]> dailyCallCounts(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
