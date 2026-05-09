package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.WorkflowEntity;
import com.javatodev.finance.model.enums.WorkflowStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for Workflow entity CRUD operations.
 */
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, Long> {
    Page<WorkflowEntity> findByStatus(WorkflowStatus status, Pageable pageable);
}
