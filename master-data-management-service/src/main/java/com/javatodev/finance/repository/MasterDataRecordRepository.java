package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.MasterDataRecordEntity;
import com.javatodev.finance.model.enums.RecordStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for MasterDataRecord entity CRUD operations.
 */
public interface MasterDataRecordRepository extends JpaRepository<MasterDataRecordEntity, Long> {
    Page<MasterDataRecordEntity> findByTableDefinitionId(Long tableDefinitionId, Pageable pageable);
    List<MasterDataRecordEntity> findByTableDefinitionIdAndStatus(Long tableDefinitionId, RecordStatus status);
}
