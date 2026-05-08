package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TableDefinitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for TableDefinition entity CRUD operations.
 */
public interface TableDefinitionRepository extends JpaRepository<TableDefinitionEntity, Long> {
    List<TableDefinitionEntity> findByDatasetId(Long datasetId);
}
