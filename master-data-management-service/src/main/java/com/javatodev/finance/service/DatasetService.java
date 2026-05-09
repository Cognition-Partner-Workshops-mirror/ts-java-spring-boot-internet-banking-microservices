package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.DatasetRequest;
import com.javatodev.finance.model.dto.response.DatasetResponse;
import com.javatodev.finance.model.entity.DatasetEntity;
import com.javatodev.finance.model.entity.DataspaceEntity;
import com.javatodev.finance.repository.DatasetRepository;
import com.javatodev.finance.repository.DataspaceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling Dataset CRUD operations.
 * Datasets are named collections of tables within a Dataspace.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DataspaceRepository dataspaceRepository;

    /** Create a new dataset within a dataspace. */
    @Transactional
    public DatasetResponse createDataset(DatasetRequest request) {
        log.info("Creating dataset: {} in dataspace: {}", request.getName(), request.getDataspaceId());

        DataspaceEntity dataspace = dataspaceRepository.findById(request.getDataspaceId())
            .orElseThrow(() -> new EntityNotFoundException("Dataspace not found with id: " + request.getDataspaceId()));

        DatasetEntity entity = new DatasetEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setDataspace(dataspace);

        DatasetEntity saved = datasetRepository.save(entity);
        return toResponse(saved);
    }

    /** List all datasets in a given dataspace. */
    public List<DatasetResponse> listDatasetsByDataspace(Long dataspaceId) {
        return datasetRepository.findByDataspaceId(dataspaceId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single dataset by ID. */
    public DatasetResponse getDataset(Long id) {
        DatasetEntity entity = datasetRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataset not found with id: " + id));
        return toResponse(entity);
    }

    /** Update dataset name and description. */
    @Transactional
    public DatasetResponse updateDataset(Long id, DatasetRequest request) {
        log.info("Updating dataset id: {}", id);
        DatasetEntity entity = datasetRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Dataset not found with id: " + id));

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        DatasetEntity saved = datasetRepository.save(entity);
        return toResponse(saved);
    }

    /** Delete a dataset by ID. */
    @Transactional
    public void deleteDataset(Long id) {
        log.info("Deleting dataset id: {}", id);
        if (!datasetRepository.existsById(id)) {
            throw new EntityNotFoundException("Dataset not found with id: " + id);
        }
        datasetRepository.deleteById(id);
    }

    /** Convert entity to response DTO. */
    private DatasetResponse toResponse(DatasetEntity entity) {
        DatasetResponse response = new DatasetResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setDataspaceId(entity.getDataspace().getId());
        response.setDataspaceName(entity.getDataspace().getName());
        response.setVersion(entity.getVersion());
        return response;
    }
}
