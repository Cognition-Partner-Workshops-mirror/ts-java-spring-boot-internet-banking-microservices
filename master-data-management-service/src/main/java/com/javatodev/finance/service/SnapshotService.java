package com.javatodev.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.SnapshotCompareRequest;
import com.javatodev.finance.model.dto.request.SnapshotRequest;
import com.javatodev.finance.model.dto.response.SnapshotCompareResponse;
import com.javatodev.finance.model.dto.response.SnapshotResponse;
import com.javatodev.finance.model.entity.DatasetEntity;
import com.javatodev.finance.model.entity.DataspaceEntity;
import com.javatodev.finance.model.entity.MasterDataRecordEntity;
import com.javatodev.finance.model.entity.SnapshotDataEntity;
import com.javatodev.finance.model.entity.SnapshotEntity;
import com.javatodev.finance.model.entity.TableDefinitionEntity;
import com.javatodev.finance.model.enums.RecordStatus;
import com.javatodev.finance.repository.DatasetRepository;
import com.javatodev.finance.repository.DataspaceRepository;
import com.javatodev.finance.repository.MasterDataRecordRepository;
import com.javatodev.finance.repository.SnapshotDataRepository;
import com.javatodev.finance.repository.SnapshotRepository;
import com.javatodev.finance.repository.TableDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling snapshot creation and comparison.
 * Snapshots capture a point-in-time copy of all approved records in a dataspace.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotDataRepository snapshotDataRepository;
    private final DataspaceRepository dataspaceRepository;
    private final DatasetRepository datasetRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final MasterDataRecordRepository masterDataRecordRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a snapshot of a dataspace.
     * Iterates all datasets/tables and serializes all APPROVED records.
     */
    @Transactional
    public SnapshotResponse createSnapshot(SnapshotRequest request) {
        log.info("Creating snapshot for dataspace id: {}", request.getDataspaceId());

        DataspaceEntity dataspace = dataspaceRepository.findById(request.getDataspaceId())
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + request.getDataspaceId()));

        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setName(request.getName());
        snapshot.setDescription(request.getDescription());
        snapshot.setDataspace(dataspace);
        snapshot.setCreatedBy("SYSTEM_USER");
        snapshot.setCreatedAt(Instant.now());
        snapshot = snapshotRepository.save(snapshot);

        // Iterate all datasets and tables, capture approved records
        List<DatasetEntity> datasets = datasetRepository.findByDataspaceId(dataspace.getId());
        for (DatasetEntity dataset : datasets) {
            List<TableDefinitionEntity> tables = tableDefinitionRepository.findByDatasetId(dataset.getId());
            for (TableDefinitionEntity table : tables) {
                List<MasterDataRecordEntity> approvedRecords =
                    masterDataRecordRepository.findByTableDefinitionIdAndStatus(table.getId(), RecordStatus.APPROVED);

                // Serialize all records as JSON array
                List<Map<String, Object>> recordsList = approvedRecords.stream()
                    .map(r -> {
                        try {
                            Map<String, Object> recordMap = objectMapper.readValue(r.getData(),
                                new TypeReference<Map<String, Object>>() {});
                            recordMap.put("_record_id", r.getId());
                            return recordMap;
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize record data", e);
                        }
                    }).collect(Collectors.toList());

                String recordsJson;
                try {
                    recordsJson = objectMapper.writeValueAsString(recordsList);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize snapshot data", e);
                }

                SnapshotDataEntity snapshotData = new SnapshotDataEntity();
                snapshotData.setSnapshot(snapshot);
                snapshotData.setTableDefinitionId(table.getId());
                snapshotData.setTableName(table.getName());
                snapshotData.setRecordsJson(recordsJson);
                snapshotDataRepository.save(snapshotData);
            }
        }

        log.info("Snapshot created with id: {}", snapshot.getId());
        return toResponse(snapshot);
    }

    /** List snapshots for a given dataspace. */
    public List<SnapshotResponse> listSnapshotsByDataspace(Long dataspaceId) {
        return snapshotRepository.findByDataspaceIdOrderByCreatedAtDesc(dataspaceId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single snapshot by ID. */
    public SnapshotResponse getSnapshot(Long id) {
        SnapshotEntity entity = snapshotRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Snapshot not found with id: " + id));
        return toResponse(entity);
    }

    /**
     * Compare two snapshots and return their differences.
     * Identifies records that were added, removed, or modified between the source and target.
     */
    public SnapshotCompareResponse compareSnapshots(SnapshotCompareRequest request) {
        log.info("Comparing snapshot {} vs {}", request.getSourceSnapshotId(), request.getTargetSnapshotId());

        List<SnapshotDataEntity> sourceData = snapshotDataRepository.findBySnapshotId(request.getSourceSnapshotId());
        List<SnapshotDataEntity> targetData = snapshotDataRepository.findBySnapshotId(request.getTargetSnapshotId());

        // Index target data by table name
        Map<String, List<Map<String, Object>>> targetByTable = new HashMap<>();
        for (SnapshotDataEntity sd : targetData) {
            targetByTable.put(sd.getTableName(), parseRecordsJson(sd.getRecordsJson()));
        }

        List<SnapshotCompareResponse.TableDiff> tableDiffs = new ArrayList<>();
        for (SnapshotDataEntity sd : sourceData) {
            List<Map<String, Object>> sourceRecords = parseRecordsJson(sd.getRecordsJson());
            List<Map<String, Object>> targetRecords = targetByTable.getOrDefault(sd.getTableName(), new ArrayList<>());

            SnapshotCompareResponse.TableDiff diff = computeTableDiff(sd.getTableName(), sourceRecords, targetRecords);
            tableDiffs.add(diff);
            targetByTable.remove(sd.getTableName());
        }

        // Tables only in target (i.e., removed from source)
        for (Map.Entry<String, List<Map<String, Object>>> entry : targetByTable.entrySet()) {
            SnapshotCompareResponse.TableDiff diff = new SnapshotCompareResponse.TableDiff();
            diff.setTableName(entry.getKey());
            diff.setAdded(new ArrayList<>());
            diff.setRemoved(entry.getValue());
            diff.setModified(new ArrayList<>());
            tableDiffs.add(diff);
        }

        SnapshotCompareResponse response = new SnapshotCompareResponse();
        response.setSourceSnapshotId(request.getSourceSnapshotId());
        response.setTargetSnapshotId(request.getTargetSnapshotId());
        response.setTableDiffs(tableDiffs);
        return response;
    }

    /** Compute diff between source and target records for a single table. */
    private SnapshotCompareResponse.TableDiff computeTableDiff(
            String tableName, List<Map<String, Object>> sourceRecords, List<Map<String, Object>> targetRecords) {

        // Index records by _record_id for matching
        Map<Object, Map<String, Object>> sourceById = new HashMap<>();
        for (Map<String, Object> r : sourceRecords) {
            Object id = r.get("_record_id");
            if (id != null) sourceById.put(id, r);
        }
        Map<Object, Map<String, Object>> targetById = new HashMap<>();
        for (Map<String, Object> r : targetRecords) {
            Object id = r.get("_record_id");
            if (id != null) targetById.put(id, r);
        }

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<SnapshotCompareResponse.RecordDiff> modified = new ArrayList<>();

        // Records in source but not in target = added
        for (Map.Entry<Object, Map<String, Object>> entry : sourceById.entrySet()) {
            if (!targetById.containsKey(entry.getKey())) {
                added.add(entry.getValue());
            } else {
                // Both exist — check for modifications
                Map<String, Object> sourceRec = entry.getValue();
                Map<String, Object> targetRec = targetById.get(entry.getKey());
                List<String> changedFields = new ArrayList<>();
                for (String key : sourceRec.keySet()) {
                    if ("_record_id".equals(key)) continue;
                    Object sVal = sourceRec.get(key);
                    Object tVal = targetRec.get(key);
                    if (sVal == null && tVal == null) continue;
                    if (sVal == null || !sVal.equals(tVal)) {
                        changedFields.add(key);
                    }
                }
                if (!changedFields.isEmpty()) {
                    SnapshotCompareResponse.RecordDiff rd = new SnapshotCompareResponse.RecordDiff();
                    rd.setSourceRecord(sourceRec);
                    rd.setTargetRecord(targetRec);
                    rd.setChangedFields(changedFields);
                    modified.add(rd);
                }
            }
        }

        // Records in target but not in source = removed
        for (Object id : targetById.keySet()) {
            if (!sourceById.containsKey(id)) {
                removed.add(targetById.get(id));
            }
        }

        SnapshotCompareResponse.TableDiff diff = new SnapshotCompareResponse.TableDiff();
        diff.setTableName(tableName);
        diff.setAdded(added);
        diff.setRemoved(removed);
        diff.setModified(modified);
        return diff;
    }

    /** Parse a JSON array string into a list of maps. */
    private List<Map<String, Object>> parseRecordsJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse snapshot records JSON", e);
        }
    }

    /** Convert entity to response DTO. */
    private SnapshotResponse toResponse(SnapshotEntity entity) {
        SnapshotResponse response = new SnapshotResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setDataspaceId(entity.getDataspace().getId());
        response.setDataspaceName(entity.getDataspace().getName());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
