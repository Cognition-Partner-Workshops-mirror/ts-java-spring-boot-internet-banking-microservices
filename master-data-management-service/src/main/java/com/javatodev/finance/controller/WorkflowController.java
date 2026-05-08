package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.WorkflowReviewRequest;
import com.javatodev.finance.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Workflow management (maker-checker pattern).
 * Lists pending data change requests and allows approving or rejecting them.
 */
@Slf4j
@Tag(name = "Workflow API", description = "APIs for the maker-checker approval workflow on data changes")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "List Pending Workflows", description = "Retrieve a paginated list of workflows pending approval")
    @GetMapping
    public ResponseEntity<?> listPendingWorkflows(Pageable pageable) {
        return ResponseEntity.ok(workflowService.listPendingWorkflows(pageable));
    }

    @Operation(summary = "Get Workflow", description = "Get a workflow by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.getWorkflow(id));
    }

    @Operation(summary = "Approve Workflow", description = "Approve a pending data change — applies the proposed change to the record")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveWorkflow(@PathVariable Long id, @RequestBody WorkflowReviewRequest request) {
        log.info("Approving workflow id: {}", id);
        return ResponseEntity.ok(workflowService.approveWorkflow(id, request));
    }

    @Operation(summary = "Reject Workflow", description = "Reject a pending data change — reverts the record to its previous state")
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectWorkflow(@PathVariable Long id, @RequestBody WorkflowReviewRequest request) {
        log.info("Rejecting workflow id: {}", id);
        return ResponseEntity.ok(workflowService.rejectWorkflow(id, request));
    }
}
