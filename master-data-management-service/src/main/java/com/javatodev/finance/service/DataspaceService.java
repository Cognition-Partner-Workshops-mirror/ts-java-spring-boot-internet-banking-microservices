package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.DataspaceRequest;
import com.javatodev.finance.model.dto.response.DataspaceResponse;
import com.javatodev.finance.model.entity.ColumnDefinitionEntity;
import com.javatodev.finance.model.entity.DataspaceEntity;
import com.javatodev.finance.model.entity.DatasetEntity;
import com.javatodev.finance.model.entity.MasterDataRecordEntity;
import com.javatodev.finance.model.entity.TableDefinitionEntity;
import com.javatodev.finance.model.enums.DataspaceStatus;
import com.javatodev.finance.model.enums.RecordStatus;
import com.javatodev.finance.repository.DatasetRepository;
import com.javatodev.finance.repository.DataspaceRepository;
import com.javatodev.finance.repository.MasterDataRecordRepository;
import com.javatodev.finance.repository.TableDefinitionRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling Dataspace lifecycle operations.
 * Dataspaces are isolated versioned environments for master data, similar to EBX dataspaces.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DataspaceService {

    private final DataspaceRepository dataspaceRepository;
    private final DatasetRepository datasetRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final MasterDataRecordRepository masterDataRecordRepository;

    /** Create a new dataspace with optional parent reference for branching. */
    @Transactional
    public DataspaceResponse createDataspace(DataspaceRequest request) {
        log.info("Creating dataspace: {}", request.getName());

        DataspaceEntity entity = new DataspaceEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setParentDataspaceId(request.getParentDataspaceId());
        entity.setStatus(DataspaceStatus.OPEN);

        DataspaceEntity saved = dataspaceRepository.save(entity);
        return toResponse(saved);
    }

    /** List all dataspaces with pagination. */
    public List<DataspaceResponse> listDataspaces(Pageable pageable) {
        return dataspaceRepository.findAll(pageable).getContent()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single dataspace by ID. */
    public DataspaceResponse getDataspace(Long id) {
        DataspaceEntity entity = dataspaceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + id));
        return toResponse(entity);
    }

    /** Update dataspace name and description. */
    @Transactional
    public DataspaceResponse updateDataspace(Long id, DataspaceRequest request) {
        log.info("Updating dataspace id: {}", id);
        DataspaceEntity entity = dataspaceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + id));

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        DataspaceEntity saved = dataspaceRepository.save(entity);
        return toResponse(saved);
    }

    /** Close a dataspace, preventing further modifications. */
    @Transactional
    public DataspaceResponse closeDataspace(Long id) {
        log.info("Closing dataspace id: {}", id);
        DataspaceEntity entity = dataspaceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + id));

        entity.setStatus(DataspaceStatus.CLOSED);
        DataspaceEntity saved = dataspaceRepository.save(entity);
        return toResponse(saved);
    }

    /**
     * Merge a child dataspace into its parent.
     * Copies all datasets, tables, and approved records from child into parent dataspace.
     */
    @Transactional
    public DataspaceResponse mergeDataspace(Long id) {
        log.info("Merging dataspace id: {}", id);
        DataspaceEntity child = dataspaceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + id));

        if (child.getParentDataspaceId() == null) {
            throw new IllegalStateException("Cannot merge a root dataspace — no parent defined.");
        }

        DataspaceEntity parent = dataspaceRepository.findById(child.getParentDataspaceId())
            .orElseThrow(() -> new EntityNotFoundException("Parent dataspace not found with id: " + child.getParentDataspaceId()));

        // Copy datasets, tables, and records from child to parent
        List<DatasetEntity> childDatasets = datasetRepository.findByDataspaceId(child.getId());
        for (DatasetEntity childDataset : childDatasets) {
            DatasetEntity parentDataset = new DatasetEntity();
            parentDataset.setName(childDataset.getName());
            parentDataset.setDescription(childDataset.getDescription());
            parentDataset.setDataspace(parent);
            parentDataset = datasetRepository.save(parentDataset);

            List<TableDefinitionEntity> childTables = tableDefinitionRepository.findByDatasetId(childDataset.getId());
            for (TableDefinitionEntity childTable : childTables) {
                TableDefinitionEntity parentTable = new TableDefinitionEntity();
                parentTable.setName(childTable.getName());
                parentTable.setDescription(childTable.getDescription());
                parentTable.setDataset(parentDataset);
                // Deep copy columns — must create new entities pointing to parentTable
                List<ColumnDefinitionEntity> parentColumns = new ArrayList<>();
                for (ColumnDefinitionEntity childCol : childTable.getColumns()) {
                    ColumnDefinitionEntity parentCol = new ColumnDefinitionEntity();
                    parentCol.setName(childCol.getName());
                    parentCol.setDataType(childCol.getDataType());
                    parentCol.setRequired(childCol.isRequired());
                    parentCol.setUniqueKey(childCol.isUniqueKey());
                    parentCol.setOrdinal(childCol.getOrdinal());
                    parentCol.setTableDefinition(parentTable);
                    parentColumns.add(parentCol);
                }
                parentTable.setColumns(parentColumns);
                parentTable = tableDefinitionRepository.save(parentTable);

                // Copy approved records
                List<MasterDataRecordEntity> approvedRecords =
                    masterDataRecordRepository.findByTableDefinitionIdAndStatus(childTable.getId(), RecordStatus.APPROVED);
                for (MasterDataRecordEntity record : approvedRecords) {
                    MasterDataRecordEntity parentRecord = new MasterDataRecordEntity();
                    parentRecord.setTableDefinition(parentTable);
                    parentRecord.setData(record.getData());
                    parentRecord.setStatus(RecordStatus.APPROVED);
                    masterDataRecordRepository.save(parentRecord);
                }
            }
        }

        child.setStatus(DataspaceStatus.MERGED);
        dataspaceRepository.save(child);

        return toResponse(parent);
    }

    /** Convert entity to response DTO. */
    private DataspaceResponse toResponse(DataspaceEntity entity) {
        DataspaceResponse response = new DataspaceResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setParentDataspaceId(entity.getParentDataspaceId());
        response.setStatus(entity.getStatus().name());
        response.setVersion(entity.getVersion());
        return response;
    }
}
