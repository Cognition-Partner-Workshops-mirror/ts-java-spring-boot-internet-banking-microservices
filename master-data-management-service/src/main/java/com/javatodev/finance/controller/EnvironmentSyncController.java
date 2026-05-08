package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.EnvironmentCompareRequest;
import com.javatodev.finance.model.dto.request.EnvironmentConfigRequest;
import com.javatodev.finance.service.EnvironmentSyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Environment Synchronization.
 * Register target environments, compare MDM data against them,
 * and generate SQL migration scripts to synchronize.
 */
@Slf4j
@Tag(name = "Environment Sync API", description = "APIs for comparing MDM data against target environments and generating sync scripts")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/sync")
public class EnvironmentSyncController {

    private final EnvironmentSyncService environmentSyncService;

    @Operation(summary = "Register Environment", description = "Register a target environment (DIT, SIT, UAT, etc.) with JDBC connection details")
    @PostMapping("/environments")
    public ResponseEntity<?> registerEnvironment(@RequestBody EnvironmentConfigRequest request) {
        log.info("Registering environment: {}", request.getName());
        return ResponseEntity.ok(environmentSyncService.registerEnvironment(request));
    }

    @Operation(summary = "List Environments", description = "List all registered target environments")
    @GetMapping("/environments")
    public ResponseEntity<?> listEnvironments() {
        return ResponseEntity.ok(environmentSyncService.listEnvironments());
    }

    @Operation(summary = "Compare Environment", description = "Compare an MDM snapshot against a target environment and generate a diff report with SQL sync script")
    @PostMapping("/compare")
    public ResponseEntity<?> compareEnvironment(@RequestBody EnvironmentCompareRequest request) {
        log.info("Comparing snapshot {} against environment {}", request.getSnapshotId(), request.getEnvironmentId());
        return ResponseEntity.ok(environmentSyncService.compareEnvironment(request));
    }
}
