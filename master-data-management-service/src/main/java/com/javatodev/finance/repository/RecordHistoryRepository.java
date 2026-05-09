package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.RecordHistoryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for RecordHistory entity CRUD operations.
 */
public interface RecordHistoryRepository extends JpaRepository<RecordHistoryEntity, Long> {
    List<RecordHistoryEntity> findByRecordIdOrderByChangedAtDesc(Long recordId);
}
