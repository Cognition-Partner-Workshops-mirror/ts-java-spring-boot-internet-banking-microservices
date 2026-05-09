package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.ColumnDefinitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for ColumnDefinition entity CRUD operations.
 */
public interface ColumnDefinitionRepository extends JpaRepository<ColumnDefinitionEntity, Long> {
}
