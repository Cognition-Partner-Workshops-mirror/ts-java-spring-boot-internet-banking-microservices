package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.EnvironmentConfigEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for EnvironmentConfig entity CRUD operations.
 */
public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfigEntity, Long> {
    Optional<EnvironmentConfigEntity> findByName(String name);
}
