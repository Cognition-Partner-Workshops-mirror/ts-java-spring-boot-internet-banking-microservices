package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.ColumnDefinitionRequest;
import com.javatodev.finance.model.dto.request.TableDefinitionRequest;
import com.javatodev.finance.model.dto.response.ColumnDefinitionResponse;
import com.javatodev.finance.model.dto.response.TableDefinitionResponse;
import com.javatodev.finance.model.entity.ColumnDefinitionEntity;
import com.javatodev.finance.model.entity.DatasetEntity;
import com.javatodev.finance.model.entity.TableDefinitionEntity;
import com.javatodev.finance.repository.DatasetRepository;
import com.javatodev.finance.repository.TableDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling table definition CRUD operations.
 * Tables define the schema for master data records within a Dataset.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TableDefinitionService {

    /** Only allow valid SQL identifiers as table names to prevent injection. */
    private static final Pattern VALID_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final TableDefinitionRepository tableDefinitionRepository;
    private final DatasetRepository datasetRepository;

    /** Create a new table definition with column definitions atomically. */
    @Transactional
    public TableDefinitionResponse createTableDefinition(TableDefinitionRequest request) {
        log.info("Creating table: {} in dataset: {}", request.getName(), request.getDatasetId());

        // Validate table name is a safe SQL identifier
        if (request.getName() == null || !VALID_SQL_IDENTIFIER.matcher(request.getName()).matches()) {
            throw new IllegalArgumentException("Table name must be a valid SQL identifier (letters, digits, underscores): " + request.getName());
        }

        DatasetEntity dataset = datasetRepository.findById(request.getDatasetId())
            .orElseThrow(() -> new EntityNotFoundException("Dataset not found with id: " + request.getDatasetId()));

        TableDefinitionEntity entity = new TableDefinitionEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setDataset(dataset);

        // Build column definitions from request
        List<ColumnDefinitionEntity> columns = new ArrayList<>();
        if (request.getColumns() != null) {
            for (ColumnDefinitionRequest colReq : request.getColumns()) {
                ColumnDefinitionEntity col = new ColumnDefinitionEntity();
                col.setName(colReq.getName());
                col.setDataType(colReq.getDataType());
                col.setRequired(colReq.isRequired());
                col.setUniqueKey(colReq.isUniqueKey());
                col.setOrdinal(colReq.getOrdinal());
                col.setTableDefinition(entity);
                columns.add(col);
            }
        }
        entity.setColumns(columns);

        TableDefinitionEntity saved = tableDefinitionRepository.save(entity);
        return toResponse(saved);
    }

    /** List all tables in a dataset. */
    public List<TableDefinitionResponse> listTablesByDataset(Long datasetId) {
        return tableDefinitionRepository.findByDatasetId(datasetId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single table definition by ID including columns. */
    public TableDefinitionResponse getTableDefinition(Long id) {
        TableDefinitionEntity entity = tableDefinitionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Table definition not found with id: " + id));
        return toResponse(entity);
    }

    /** Update table name and description. */
    @Transactional
    public TableDefinitionResponse updateTableDefinition(Long id, TableDefinitionRequest request) {
        log.info("Updating table definition id: {}", id);
        TableDefinitionEntity entity = tableDefinitionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Table definition not found with id: " + id));

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        TableDefinitionEntity saved = tableDefinitionRepository.save(entity);
        return toResponse(saved);
    }

    /** Delete a table definition by ID. */
    @Transactional
    public void deleteTableDefinition(Long id) {
        log.info("Deleting table definition id: {}", id);
        if (!tableDefinitionRepository.existsById(id)) {
            throw new EntityNotFoundException("Table definition not found with id: " + id);
        }
        tableDefinitionRepository.deleteById(id);
    }

    /** Convert entity to response DTO. */
    private TableDefinitionResponse toResponse(TableDefinitionEntity entity) {
        TableDefinitionResponse response = new TableDefinitionResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setDatasetId(entity.getDataset().getId());
        response.setDatasetName(entity.getDataset().getName());
        response.setVersion(entity.getVersion());

        if (entity.getColumns() != null) {
            List<ColumnDefinitionResponse> colResponses = entity.getColumns().stream()
                .map(col -> {
                    ColumnDefinitionResponse colResp = new ColumnDefinitionResponse();
                    colResp.setId(col.getId());
                    colResp.setName(col.getName());
                    colResp.setDataType(col.getDataType());
                    colResp.setRequired(col.isRequired());
                    colResp.setUniqueKey(col.isUniqueKey());
                    colResp.setOrdinal(col.getOrdinal());
                    return colResp;
                }).collect(Collectors.toList());
            response.setColumns(colResponses);
        }
        return response;
    }
}
