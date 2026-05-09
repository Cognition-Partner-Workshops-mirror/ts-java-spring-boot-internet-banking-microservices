package com.javatodev.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.MasterDataRecordRequest;
import com.javatodev.finance.model.dto.response.MasterDataRecordResponse;
import com.javatodev.finance.model.dto.response.RecordHistoryResponse;
import com.javatodev.finance.model.entity.MasterDataRecordEntity;
import com.javatodev.finance.model.entity.RecordHistoryEntity;
import com.javatodev.finance.model.entity.TableDefinitionEntity;
import com.javatodev.finance.model.entity.WorkflowEntity;
import com.javatodev.finance.model.enums.ChangeType;
import com.javatodev.finance.model.enums.RecordStatus;
import com.javatodev.finance.model.enums.WorkflowStatus;
import com.javatodev.finance.repository.MasterDataRecordRepository;
import com.javatodev.finance.repository.RecordHistoryRepository;
import com.javatodev.finance.repository.TableDefinitionRepository;
import com.javatodev.finance.repository.WorkflowRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling master data record CRUD with workflow integration.
 * Inserts/updates/deletes trigger the maker-checker workflow for approval.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MasterDataRecordService {

    private final MasterDataRecordRepository recordRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final RecordHistoryRepository recordHistoryRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    /**
     * Insert a new record. Creates the record as DRAFT and a workflow entry for approval.
     */
    @Transactional
    public MasterDataRecordResponse insertRecord(MasterDataRecordRequest request) {
        log.info("Inserting record into table id: {}", request.getTableId());

        TableDefinitionEntity table = tableDefinitionRepository.findById(request.getTableId())
            .orElseThrow(() -> new EntityNotFoundException("Table not found with id: " + request.getTableId()));

        String dataJson = serializeData(request.getData());

        // Save record as DRAFT pending approval
        MasterDataRecordEntity entity = new MasterDataRecordEntity();
        entity.setTableDefinition(table);
        entity.setData(dataJson);
        entity.setStatus(RecordStatus.DRAFT);
        MasterDataRecordEntity saved = recordRepository.save(entity);

        // Create workflow entry for the insert
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setRecordId(saved.getId());
        workflow.setTableDefinitionId(table.getId());
        workflow.setChangeType(ChangeType.INSERT);
        workflow.setProposedData(dataJson);
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setRequestedBy("SYSTEM_USER");
        workflowRepository.save(workflow);

        log.info("Record created as DRAFT with workflow pending approval, record id: {}", saved.getId());
        return toResponse(saved, table);
    }

    /** List records in a table with pagination. */
    public List<MasterDataRecordResponse> listRecordsByTable(Long tableId, Pageable pageable) {
        TableDefinitionEntity table = tableDefinitionRepository.findById(tableId)
            .orElseThrow(() -> new EntityNotFoundException("Table not found with id: " + tableId));

        return recordRepository.findByTableDefinitionId(tableId, pageable).getContent()
            .stream().map(r -> toResponse(r, table)).collect(Collectors.toList());
    }

    /** Get a single record by ID. */
    public MasterDataRecordResponse getRecord(Long id) {
        MasterDataRecordEntity entity = recordRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Record not found with id: " + id));
        return toResponse(entity, entity.getTableDefinition());
    }

    /**
     * Update a record. Saves current state to history and creates workflow for approval.
     */
    @Transactional
    public MasterDataRecordResponse updateRecord(Long id, MasterDataRecordRequest request) {
        log.info("Updating record id: {}", id);

        MasterDataRecordEntity entity = recordRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Record not found with id: " + id));

        String previousData = entity.getData();
        String newData = serializeData(request.getData());

        // Log history entry for the change
        RecordHistoryEntity history = new RecordHistoryEntity();
        history.setRecordId(entity.getId());
        history.setTableDefinitionId(entity.getTableDefinition().getId());
        history.setPreviousData(previousData);
        history.setNewData(newData);
        history.setChangeType(ChangeType.UPDATE.name());
        history.setChangedBy("SYSTEM_USER");
        history.setChangedAt(Instant.now());
        recordHistoryRepository.save(history);

        // Create workflow for the update
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setRecordId(entity.getId());
        workflow.setTableDefinitionId(entity.getTableDefinition().getId());
        workflow.setChangeType(ChangeType.UPDATE);
        workflow.setProposedData(newData);
        workflow.setPreviousData(previousData);
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setRequestedBy("SYSTEM_USER");
        workflowRepository.save(workflow);

        // Set status to pending approval
        entity.setStatus(RecordStatus.PENDING_APPROVAL);
        recordRepository.save(entity);

        return toResponse(entity, entity.getTableDefinition());
    }

    /**
     * Request deletion of a record. Creates a workflow entry; actual delete happens on approval.
     */
    @Transactional
    public void deleteRecord(Long id) {
        log.info("Requesting deletion for record id: {}", id);

        MasterDataRecordEntity entity = recordRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Record not found with id: " + id));

        // Create workflow for the delete request
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setRecordId(entity.getId());
        workflow.setTableDefinitionId(entity.getTableDefinition().getId());
        workflow.setChangeType(ChangeType.DELETE);
        workflow.setPreviousData(entity.getData());
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setRequestedBy("SYSTEM_USER");
        workflowRepository.save(workflow);

        entity.setStatus(RecordStatus.PENDING_APPROVAL);
        recordRepository.save(entity);
    }

    /** Get audit history for a specific record. */
    public List<RecordHistoryResponse> getRecordHistory(Long recordId) {
        return recordHistoryRepository.findByRecordIdOrderByChangedAtDesc(recordId)
            .stream().map(this::toHistoryResponse).collect(Collectors.toList());
    }

    /** Serialize a data map to JSON string. */
    private String serializeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize record data to JSON", e);
        }
    }

    /** Deserialize JSON string to a data map. */
    Map<String, Object> deserializeData(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize record data from JSON", e);
        }
    }

    /** Convert entity to response DTO. */
    private MasterDataRecordResponse toResponse(MasterDataRecordEntity entity, TableDefinitionEntity table) {
        MasterDataRecordResponse response = new MasterDataRecordResponse();
        response.setId(entity.getId());
        response.setTableId(table.getId());
        response.setTableName(table.getName());
        response.setData(deserializeData(entity.getData()));
        response.setStatus(entity.getStatus().name());
        response.setVersion(entity.getVersion());
        return response;
    }

    /** Convert history entity to response DTO. */
    private RecordHistoryResponse toHistoryResponse(RecordHistoryEntity entity) {
        RecordHistoryResponse response = new RecordHistoryResponse();
        response.setId(entity.getId());
        response.setRecordId(entity.getRecordId());
        response.setPreviousData(entity.getPreviousData());
        response.setNewData(entity.getNewData());
        response.setChangeType(entity.getChangeType());
        response.setChangedBy(entity.getChangedBy());
        response.setChangedAt(entity.getChangedAt());
        return response;
    }
}
