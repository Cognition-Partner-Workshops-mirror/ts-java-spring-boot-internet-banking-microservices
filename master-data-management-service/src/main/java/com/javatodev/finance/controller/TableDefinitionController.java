package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.TableDefinitionRequest;
import com.javatodev.finance.service.TableDefinitionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Table Definition management.
 * Tables define the schema for master data records within a Dataset.
 */
@Slf4j
@Tag(name = "Table Definition API", description = "APIs for managing master data table definitions and their column schemas")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tables")
public class TableDefinitionController {

    private final TableDefinitionService tableDefinitionService;

    @Operation(summary = "Create Table", description = "Create a new table definition with column definitions")
    @PostMapping
    public ResponseEntity<?> createTable(@RequestBody TableDefinitionRequest request) {
        log.info("Creating table: {} in dataset: {}", request.getName(), request.getDatasetId());
        return ResponseEntity.ok(tableDefinitionService.createTableDefinition(request));
    }

    @Operation(summary = "List Tables by Dataset", description = "List all tables in a given dataset")
    @GetMapping("/dataset/{datasetId}")
    public ResponseEntity<?> listTablesByDataset(@PathVariable Long datasetId) {
        return ResponseEntity.ok(tableDefinitionService.listTablesByDataset(datasetId));
    }

    @Operation(summary = "Get Table", description = "Get a table definition with its columns")
    @GetMapping("/{id}")
    public ResponseEntity<?> getTable(@PathVariable Long id) {
        return ResponseEntity.ok(tableDefinitionService.getTableDefinition(id));
    }

    @Operation(summary = "Update Table", description = "Update table name and description")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTable(@PathVariable Long id, @RequestBody TableDefinitionRequest request) {
        return ResponseEntity.ok(tableDefinitionService.updateTableDefinition(id, request));
    }

    @Operation(summary = "Delete Table", description = "Delete a table definition and its columns")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTable(@PathVariable Long id) {
        tableDefinitionService.deleteTableDefinition(id);
        return ResponseEntity.ok().build();
    }
}
