package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.DataspaceRequest;
import com.javatodev.finance.service.DataspaceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Dataspace management.
 * Dataspaces are isolated versioned environments for master data (similar to EBX dataspaces).
 */
@Slf4j
@Tag(name = "Dataspace API", description = "APIs for managing dataspaces — isolated versioned environments for master data")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/dataspaces")
public class DataspaceController {

    private final DataspaceService dataspaceService;

    @Operation(summary = "Create Dataspace", description = "Create a new dataspace with optional parent for branching")
    @PostMapping
    public ResponseEntity<?> createDataspace(@RequestBody DataspaceRequest request) {
        log.info("Creating dataspace: {}", request.getName());
        return ResponseEntity.ok(dataspaceService.createDataspace(request));
    }

    @Operation(summary = "List Dataspaces", description = "Retrieve a paginated list of all dataspaces")
    @GetMapping
    public ResponseEntity<?> listDataspaces(Pageable pageable) {
        return ResponseEntity.ok(dataspaceService.listDataspaces(pageable));
    }

    @Operation(summary = "Get Dataspace", description = "Get a dataspace by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getDataspace(@PathVariable Long id) {
        return ResponseEntity.ok(dataspaceService.getDataspace(id));
    }

    @Operation(summary = "Update Dataspace", description = "Update dataspace name and description")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDataspace(@PathVariable Long id, @RequestBody DataspaceRequest request) {
        return ResponseEntity.ok(dataspaceService.updateDataspace(id, request));
    }

    @Operation(summary = "Close Dataspace", description = "Close a dataspace, preventing further modifications")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> closeDataspace(@PathVariable Long id) {
        return ResponseEntity.ok(dataspaceService.closeDataspace(id));
    }

    @Operation(summary = "Merge Dataspace", description = "Merge a child dataspace into its parent, copying all datasets, tables, and approved records")
    @PostMapping("/{id}/merge")
    public ResponseEntity<?> mergeDataspace(@PathVariable Long id) {
        return ResponseEntity.ok(dataspaceService.mergeDataspace(id));
    }
}
