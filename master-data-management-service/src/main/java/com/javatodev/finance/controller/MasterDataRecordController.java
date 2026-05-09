package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.MasterDataRecordRequest;
import com.javatodev.finance.service.MasterDataRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Master Data Record management.
 * All insert/update/delete operations trigger the maker-checker workflow for approval.
 */
@Slf4j
@Tag(name = "Master Data Record API", description = "APIs for managing master data records with workflow-based approval")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/records")
public class MasterDataRecordController {

    private final MasterDataRecordService recordService;

    @Operation(summary = "Insert Record", description = "Insert a new master data record (triggers workflow for approval)")
    @PostMapping
    public ResponseEntity<?> insertRecord(@RequestBody MasterDataRecordRequest request) {
        log.info("Inserting record into table id: {}", request.getTableId());
        return ResponseEntity.ok(recordService.insertRecord(request));
    }

    @Operation(summary = "List Records by Table", description = "Retrieve a paginated list of records in a table")
    @GetMapping("/table/{tableId}")
    public ResponseEntity<?> listRecordsByTable(@PathVariable Long tableId, Pageable pageable) {
        return ResponseEntity.ok(recordService.listRecordsByTable(tableId, pageable));
    }

    @Operation(summary = "Get Record", description = "Get a record by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getRecord(id));
    }

    @Operation(summary = "Update Record", description = "Update a master data record (triggers workflow for approval)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRecord(@PathVariable Long id, @RequestBody MasterDataRecordRequest request) {
        return ResponseEntity.ok(recordService.updateRecord(id, request));
    }

    @Operation(summary = "Delete Record", description = "Request deletion of a record (triggers workflow for approval)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
        recordService.deleteRecord(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get Record History", description = "Get the audit trail history for a specific record")
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getRecordHistory(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getRecordHistory(id));
    }
}
