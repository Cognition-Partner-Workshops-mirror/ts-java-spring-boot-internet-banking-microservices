package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.SnapshotEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for Snapshot entity CRUD operations.
 */
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {
    List<SnapshotEntity> findByDataspaceIdOrderByCreatedAtDesc(Long dataspaceId);
}
