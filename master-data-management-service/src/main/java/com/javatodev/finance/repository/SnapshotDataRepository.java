package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.SnapshotDataEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for SnapshotData entity CRUD operations.
 */
public interface SnapshotDataRepository extends JpaRepository<SnapshotDataEntity, Long> {
    List<SnapshotDataEntity> findBySnapshotId(Long snapshotId);
}
