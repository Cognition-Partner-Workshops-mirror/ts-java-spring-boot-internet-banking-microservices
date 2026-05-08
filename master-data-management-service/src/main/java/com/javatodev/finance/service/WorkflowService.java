package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.WorkflowReviewRequest;
import com.javatodev.finance.model.dto.response.WorkflowResponse;
import com.javatodev.finance.model.entity.MasterDataRecordEntity;
import com.javatodev.finance.model.entity.RecordHistoryEntity;
import com.javatodev.finance.model.entity.WorkflowEntity;
import com.javatodev.finance.model.enums.ChangeType;
import com.javatodev.finance.model.enums.RecordStatus;
import com.javatodev.finance.model.enums.WorkflowStatus;
import com.javatodev.finance.repository.MasterDataRecordRepository;
import com.javatodev.finance.repository.RecordHistoryRepository;
import com.javatodev.finance.repository.WorkflowRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling the maker-checker workflow for data changes.
 * Supports approve and reject operations on pending workflow items.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final MasterDataRecordRepository recordRepository;
    private final RecordHistoryRepository recordHistoryRepository;

    /** List pending workflows with pagination. */
    public List<WorkflowResponse> listPendingWorkflows(Pageable pageable) {
        return workflowRepository.findByStatus(WorkflowStatus.PENDING, pageable).getContent()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single workflow by ID. */
    public WorkflowResponse getWorkflow(Long id) {
        WorkflowEntity entity = workflowRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found with id: " + id));
        return toResponse(entity);
    }

    /**
     * Approve a workflow request.
     * Applies the proposed data change and updates the record status to APPROVED.
     */
    @Transactional
    public WorkflowResponse approveWorkflow(Long id, WorkflowReviewRequest request) {
        log.info("Approving workflow id: {}", id);

        WorkflowEntity workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found with id: " + id));

        if (workflow.getStatus() != WorkflowStatus.PENDING) {
            throw new IllegalStateException("Workflow is not in PENDING status, current: " + workflow.getStatus());
        }

        workflow.setStatus(WorkflowStatus.APPROVED);
        workflow.setReviewedBy(request.getReviewedBy());
        workflow.setReviewComment(request.getReviewComment());
        workflow.setReviewedAt(Instant.now());
        workflowRepository.save(workflow);

        // Apply the change to the record
        if (workflow.getChangeType() == ChangeType.INSERT || workflow.getChangeType() == ChangeType.UPDATE) {
            MasterDataRecordEntity record = recordRepository.findById(workflow.getRecordId())
                .orElseThrow(() -> new EntityNotFoundException("Record not found with id: " + workflow.getRecordId()));
            record.setData(workflow.getProposedData());
            record.setStatus(RecordStatus.APPROVED);
            recordRepository.save(record);

            // Log history for the approval
            RecordHistoryEntity history = new RecordHistoryEntity();
            history.setRecordId(record.getId());
            history.setTableDefinitionId(workflow.getTableDefinitionId());
            history.setPreviousData(workflow.getPreviousData());
            history.setNewData(workflow.getProposedData());
            history.setChangeType(workflow.getChangeType().name());
            history.setChangedBy(request.getReviewedBy());
            history.setChangedAt(Instant.now());
            recordHistoryRepository.save(history);

        } else if (workflow.getChangeType() == ChangeType.DELETE) {
            // Delete the record upon approval
            recordRepository.deleteById(workflow.getRecordId());

            // Log deletion history
            RecordHistoryEntity history = new RecordHistoryEntity();
            history.setRecordId(workflow.getRecordId());
            history.setTableDefinitionId(workflow.getTableDefinitionId());
            history.setPreviousData(workflow.getPreviousData());
            history.setChangeType(ChangeType.DELETE.name());
            history.setChangedBy(request.getReviewedBy());
            history.setChangedAt(Instant.now());
            recordHistoryRepository.save(history);
        }

        return toResponse(workflow);
    }

    /**
     * Reject a workflow request.
     * Reverts the record to its previous state or deletes it if it was a new insert.
     */
    @Transactional
    public WorkflowResponse rejectWorkflow(Long id, WorkflowReviewRequest request) {
        log.info("Rejecting workflow id: {}", id);

        WorkflowEntity workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found with id: " + id));

        if (workflow.getStatus() != WorkflowStatus.PENDING) {
            throw new IllegalStateException("Workflow is not in PENDING status, current: " + workflow.getStatus());
        }

        workflow.setStatus(WorkflowStatus.REJECTED);
        workflow.setReviewedBy(request.getReviewedBy());
        workflow.setReviewComment(request.getReviewComment());
        workflow.setReviewedAt(Instant.now());
        workflowRepository.save(workflow);

        // Revert the record
        if (workflow.getChangeType() == ChangeType.INSERT) {
            // Delete the draft record since the insert was rejected
            recordRepository.deleteById(workflow.getRecordId());
        } else if (workflow.getChangeType() == ChangeType.UPDATE || workflow.getChangeType() == ChangeType.DELETE) {
            // Restore record to APPROVED status with previous data
            MasterDataRecordEntity record = recordRepository.findById(workflow.getRecordId())
                .orElseThrow(() -> new EntityNotFoundException("Record not found with id: " + workflow.getRecordId()));
            if (workflow.getPreviousData() != null) {
                record.setData(workflow.getPreviousData());
            }
            record.setStatus(RecordStatus.APPROVED);
            recordRepository.save(record);
        }

        return toResponse(workflow);
    }

    /** Convert entity to response DTO. */
    private WorkflowResponse toResponse(WorkflowEntity entity) {
        WorkflowResponse response = new WorkflowResponse();
        response.setId(entity.getId());
        response.setRecordId(entity.getRecordId());
        response.setTableDefinitionId(entity.getTableDefinitionId());
        response.setChangeType(entity.getChangeType().name());
        response.setProposedData(entity.getProposedData());
        response.setPreviousData(entity.getPreviousData());
        response.setStatus(entity.getStatus().name());
        response.setRequestedBy(entity.getRequestedBy());
        response.setReviewedBy(entity.getReviewedBy());
        response.setReviewComment(entity.getReviewComment());
        response.setReviewedAt(entity.getReviewedAt());
        return response;
    }
}
