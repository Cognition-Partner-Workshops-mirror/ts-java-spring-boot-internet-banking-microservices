package com.javatodev.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.EnvironmentCompareRequest;
import com.javatodev.finance.model.dto.request.EnvironmentConfigRequest;
import com.javatodev.finance.model.dto.response.EnvironmentCompareResponse;
import com.javatodev.finance.model.dto.response.EnvironmentConfigResponse;
import com.javatodev.finance.model.entity.EnvironmentConfigEntity;
import com.javatodev.finance.model.entity.SnapshotDataEntity;
import com.javatodev.finance.model.enums.EnvironmentType;
import com.javatodev.finance.repository.EnvironmentConfigRepository;
import com.javatodev.finance.repository.SnapshotDataRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling environment registration, comparison, and SQL script generation.
 * Enables comparing MDM snapshot data against a target environment (e.g., DIT)
 * and generating migration scripts to synchronize them.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EnvironmentSyncService {

    private final EnvironmentConfigRepository environmentConfigRepository;
    private final SnapshotDataRepository snapshotDataRepository;
    private final ObjectMapper objectMapper;

    /** Register a new target environment. */
    @Transactional
    public EnvironmentConfigResponse registerEnvironment(EnvironmentConfigRequest request) {
        log.info("Registering environment: {}", request.getName());

        EnvironmentConfigEntity entity = new EnvironmentConfigEntity();
        entity.setName(request.getName());
        entity.setType(EnvironmentType.valueOf(request.getType()));
        entity.setDbUrl(request.getDbUrl());
        entity.setDbUsername(request.getDbUsername());
        entity.setDbPassword(request.getDbPassword());

        EnvironmentConfigEntity saved = environmentConfigRepository.save(entity);
        return toResponse(saved);
    }

    /** List all registered environments. */
    public List<EnvironmentConfigResponse> listEnvironments() {
        return environmentConfigRepository.findAll()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Compare MDM snapshot data against a target environment's database.
     * Connects to the target DB via JDBC, reads table data, and computes diff.
     * Also generates SQL INSERT/UPDATE/DELETE scripts to synchronize.
     */
    public EnvironmentCompareResponse compareEnvironment(EnvironmentCompareRequest request) {
        log.info("Comparing snapshot {} against environment {}", request.getSnapshotId(), request.getEnvironmentId());

        EnvironmentConfigEntity envConfig = environmentConfigRepository.findById(request.getEnvironmentId())
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with id: " + request.getEnvironmentId()));

        List<SnapshotDataEntity> snapshotDataList = snapshotDataRepository.findBySnapshotId(request.getSnapshotId());

        List<EnvironmentCompareResponse.TableSyncDiff> tableDiffs = new ArrayList<>();
        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("-- SQL Sync Script: MDM Snapshot -> ").append(envConfig.getName()).append("\n");
        scriptBuilder.append("-- Generated at: ").append(java.time.Instant.now()).append("\n\n");

        for (SnapshotDataEntity snapshotData : snapshotDataList) {
            // Filter by specific table if requested
            if (request.getTableName() != null && !request.getTableName().equals(snapshotData.getTableName())) {
                continue;
            }

            List<Map<String, Object>> mdmRecords = parseRecordsJson(snapshotData.getRecordsJson());
            List<Map<String, Object>> targetRecords = readTargetTableData(envConfig, snapshotData.getTableName());

            EnvironmentCompareResponse.TableSyncDiff diff = computeSyncDiff(
                snapshotData.getTableName(), mdmRecords, targetRecords);
            tableDiffs.add(diff);

            // Generate SQL for this table
            generateTableSyncScript(scriptBuilder, snapshotData.getTableName(), diff);
        }

        EnvironmentCompareResponse response = new EnvironmentCompareResponse();
        response.setSnapshotId(request.getSnapshotId());
        response.setEnvironmentId(request.getEnvironmentId());
        response.setEnvironmentName(envConfig.getName());
        response.setTableDiffs(tableDiffs);
        response.setGeneratedScript(scriptBuilder.toString());
        return response;
    }

    /**
     * Read data from a table in the target environment via JDBC.
     * Returns empty list if the table doesn't exist in the target.
     */
    private List<Map<String, Object>> readTargetTableData(EnvironmentConfigEntity envConfig, String tableName) {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(envConfig.getDbUrl(), envConfig.getDbUsername(), envConfig.getDbPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                records.add(row);
            }
        } catch (Exception e) {
            log.warn("Could not read table {} from target environment: {}", tableName, e.getMessage());
        }
        return records;
    }

    /** Compute sync diff between MDM records and target environment records. */
    private EnvironmentCompareResponse.TableSyncDiff computeSyncDiff(
            String tableName, List<Map<String, Object>> mdmRecords, List<Map<String, Object>> targetRecords) {

        // Index by _record_id for matching
        Map<Object, Map<String, Object>> mdmById = new HashMap<>();
        for (Map<String, Object> r : mdmRecords) {
            Object id = r.get("_record_id");
            if (id != null) mdmById.put(id, r);
        }
        Map<Object, Map<String, Object>> targetById = new HashMap<>();
        for (Map<String, Object> r : targetRecords) {
            Object id = r.get("_record_id");
            if (id != null) targetById.put(id, r);
        }

        List<Map<String, Object>> inserts = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> deletes = new ArrayList<>();

        // Records in MDM but not in target -> INSERT
        for (Map.Entry<Object, Map<String, Object>> entry : mdmById.entrySet()) {
            if (!targetById.containsKey(entry.getKey())) {
                inserts.add(entry.getValue());
            } else {
                // Check for differences -> UPDATE
                Map<String, Object> mdmRec = entry.getValue();
                Map<String, Object> targetRec = targetById.get(entry.getKey());
                boolean different = false;
                for (String key : mdmRec.keySet()) {
                    if ("_record_id".equals(key)) continue;
                    Object mVal = mdmRec.get(key);
                    Object tVal = targetRec.get(key);
                    if (mVal == null && tVal == null) continue;
                    if (mVal == null || !mVal.equals(tVal)) {
                        different = true;
                        break;
                    }
                }
                if (different) {
                    updates.add(mdmRec);
                }
            }
        }

        // Records in target but not in MDM -> DELETE
        for (Object id : targetById.keySet()) {
            if (!mdmById.containsKey(id)) {
                deletes.add(targetById.get(id));
            }
        }

        EnvironmentCompareResponse.TableSyncDiff diff = new EnvironmentCompareResponse.TableSyncDiff();
        diff.setTableName(tableName);
        diff.setInsertCount(inserts.size());
        diff.setUpdateCount(updates.size());
        diff.setDeleteCount(deletes.size());
        diff.setInserts(inserts);
        diff.setUpdates(updates);
        diff.setDeletes(deletes);
        return diff;
    }

    /** Generate SQL INSERT/UPDATE/DELETE statements for a table diff. */
    private void generateTableSyncScript(StringBuilder sb, String tableName,
            EnvironmentCompareResponse.TableSyncDiff diff) {
        sb.append("-- Table: ").append(tableName).append("\n");

        // INSERT statements
        for (Map<String, Object> record : diff.getInserts()) {
            Set<String> columns = new HashSet<>(record.keySet());
            columns.remove("_record_id");
            if (columns.isEmpty()) continue;

            sb.append("INSERT INTO ").append(tableName).append(" (");
            sb.append(String.join(", ", columns));
            sb.append(") VALUES (");
            sb.append(columns.stream()
                .map(col -> formatSqlValue(record.get(col)))
                .collect(Collectors.joining(", ")));
            sb.append(");\n");
        }

        // UPDATE statements
        for (Map<String, Object> record : diff.getUpdates()) {
            Object recordId = record.get("_record_id");
            Set<String> columns = new HashSet<>(record.keySet());
            columns.remove("_record_id");
            if (columns.isEmpty()) continue;

            sb.append("UPDATE ").append(tableName).append(" SET ");
            sb.append(columns.stream()
                .map(col -> col + " = " + formatSqlValue(record.get(col)))
                .collect(Collectors.joining(", ")));
            sb.append(" WHERE _record_id = ").append(formatSqlValue(recordId)).append(";\n");
        }

        // DELETE statements
        for (Map<String, Object> record : diff.getDeletes()) {
            Object recordId = record.get("_record_id");
            sb.append("DELETE FROM ").append(tableName);
            sb.append(" WHERE _record_id = ").append(formatSqlValue(recordId)).append(";\n");
        }

        sb.append("\n");
    }

    /** Format a value for SQL output (handles strings, nulls, numbers). */
    private String formatSqlValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /** Parse a JSON array string into a list of maps. */
    private List<Map<String, Object>> parseRecordsJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse snapshot records JSON", e);
        }
    }

    /** Convert entity to response DTO. Password is masked. */
    private EnvironmentConfigResponse toResponse(EnvironmentConfigEntity entity) {
        EnvironmentConfigResponse response = new EnvironmentConfigResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setType(entity.getType().name());
        response.setDbUrl(entity.getDbUrl());
        response.setDbUsername(entity.getDbUsername());
        return response;
    }
}
