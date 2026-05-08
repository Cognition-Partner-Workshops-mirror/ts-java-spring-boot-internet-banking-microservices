package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.SnapshotCompareRequest;
import com.javatodev.finance.model.dto.request.SnapshotRequest;
import com.javatodev.finance.service.SnapshotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Snapshot management.
 * Snapshots capture a point-in-time copy of all approved records in a dataspace.
 */
@Slf4j
@Tag(name = "Snapshot API", description = "APIs for creating and comparing point-in-time snapshots of dataspaces")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/snapshots")
public class SnapshotController {

    private final SnapshotService snapshotService;

    @Operation(summary = "Create Snapshot", description = "Create a point-in-time snapshot of all approved records in a dataspace")
    @PostMapping
    public ResponseEntity<?> createSnapshot(@RequestBody SnapshotRequest request) {
        log.info("Creating snapshot for dataspace id: {}", request.getDataspaceId());
        return ResponseEntity.ok(snapshotService.createSnapshot(request));
    }

    @Operation(summary = "List Snapshots by Dataspace", description = "List all snapshots for a given dataspace")
    @GetMapping("/dataspace/{dataspaceId}")
    public ResponseEntity<?> listSnapshotsByDataspace(@PathVariable Long dataspaceId) {
        return ResponseEntity.ok(snapshotService.listSnapshotsByDataspace(dataspaceId));
    }

    @Operation(summary = "Get Snapshot", description = "Get snapshot details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getSnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(snapshotService.getSnapshot(id));
    }

    @Operation(summary = "Compare Snapshots", description = "Compare two snapshots and return their differences (added, removed, modified records)")
    @PostMapping("/compare")
    public ResponseEntity<?> compareSnapshots(@RequestBody SnapshotCompareRequest request) {
        return ResponseEntity.ok(snapshotService.compareSnapshots(request));
    }
}
