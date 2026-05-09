package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.DatasetEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for Dataset entity CRUD operations.
 */
public interface DatasetRepository extends JpaRepository<DatasetEntity, Long> {
    List<DatasetEntity> findByDataspaceId(Long dataspaceId);
}
