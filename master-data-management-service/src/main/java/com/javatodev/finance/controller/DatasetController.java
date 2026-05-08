package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.DatasetRequest;
import com.javatodev.finance.service.DatasetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Dataset management.
 * Datasets are named collections of tables within a Dataspace.
 */
@Slf4j
@Tag(name = "Dataset API", description = "APIs for managing datasets — named collections of tables within a dataspace")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    @Operation(summary = "Create Dataset", description = "Create a new dataset within a dataspace")
    @PostMapping
    public ResponseEntity<?> createDataset(@RequestBody DatasetRequest request) {
        log.info("Creating dataset: {} in dataspace: {}", request.getName(), request.getDataspaceId());
        return ResponseEntity.ok(datasetService.createDataset(request));
    }

    @Operation(summary = "List Datasets by Dataspace", description = "List all datasets in a given dataspace")
    @GetMapping("/dataspace/{dataspaceId}")
    public ResponseEntity<?> listDatasetsByDataspace(@PathVariable Long dataspaceId) {
        return ResponseEntity.ok(datasetService.listDatasetsByDataspace(dataspaceId));
    }

    @Operation(summary = "Get Dataset", description = "Get a dataset by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getDataset(@PathVariable Long id) {
        return ResponseEntity.ok(datasetService.getDataset(id));
    }

    @Operation(summary = "Update Dataset", description = "Update dataset name and description")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDataset(@PathVariable Long id, @RequestBody DatasetRequest request) {
        return ResponseEntity.ok(datasetService.updateDataset(id, request));
    }

    @Operation(summary = "Delete Dataset", description = "Delete a dataset by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDataset(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return ResponseEntity.ok().build();
    }
}
