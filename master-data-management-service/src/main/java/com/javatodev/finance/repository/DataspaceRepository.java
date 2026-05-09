package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.DataspaceEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for Dataspace entity CRUD operations.
 */
public interface DataspaceRepository extends JpaRepository<DataspaceEntity, Long> {
    Optional<DataspaceEntity> findByName(String name);
}
