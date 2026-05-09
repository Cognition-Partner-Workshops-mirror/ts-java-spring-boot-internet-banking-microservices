package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.PermissionEntity;
import com.javatodev.finance.model.enums.PermissionLevel;
import com.javatodev.finance.model.enums.ResourceType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Permission entity CRUD operations.
 */
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
    List<PermissionEntity> findByUserId(Long userId);
    Optional<PermissionEntity> findByUserIdAndResourceTypeAndResourceIdAndLevel(
        Long userId, ResourceType resourceType, Long resourceId, PermissionLevel level);
}
