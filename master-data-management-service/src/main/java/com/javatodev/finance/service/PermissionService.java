package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.PermissionRequest;
import com.javatodev.finance.model.dto.response.PermissionResponse;
import com.javatodev.finance.model.entity.MdmUserEntity;
import com.javatodev.finance.model.entity.PermissionEntity;
import com.javatodev.finance.model.enums.PermissionLevel;
import com.javatodev.finance.model.enums.ResourceType;
import com.javatodev.finance.repository.MdmUserRepository;
import com.javatodev.finance.repository.PermissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling permission management for MDM resources.
 * Controls which users can access which dataspaces, datasets, and tables.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final MdmUserRepository mdmUserRepository;

    /** Grant a permission to a user on a resource. */
    @Transactional
    public PermissionResponse grantPermission(PermissionRequest request) {
        log.info("Granting {} permission to user {} on {} {}", request.getLevel(),
            request.getUserId(), request.getResourceType(), request.getResourceId());

        MdmUserEntity user = mdmUserRepository.findById(request.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("MDM User not found with id: " + request.getUserId()));

        PermissionEntity entity = new PermissionEntity();
        entity.setUser(user);
        entity.setResourceType(ResourceType.valueOf(request.getResourceType()));
        entity.setResourceId(request.getResourceId());
        entity.setLevel(PermissionLevel.valueOf(request.getLevel()));

        PermissionEntity saved = permissionRepository.save(entity);
        return toResponse(saved);
    }

    /** Get all permissions for a given user. */
    public List<PermissionResponse> getUserPermissions(Long userId) {
        return permissionRepository.findByUserId(userId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Revoke a permission by ID. */
    @Transactional
    public void revokePermission(Long id) {
        log.info("Revoking permission id: {}", id);
        if (!permissionRepository.existsById(id)) {
            throw new EntityNotFoundException("Permission not found with id: " + id);
        }
        permissionRepository.deleteById(id);
    }

    /** Check if a user has a specific permission on a resource. */
    public boolean hasPermission(Long userId, String resourceType, Long resourceId, String level) {
        return permissionRepository.findByUserIdAndResourceTypeAndResourceIdAndLevel(
            userId,
            ResourceType.valueOf(resourceType),
            resourceId,
            PermissionLevel.valueOf(level)
        ).isPresent();
    }

    /** Convert entity to response DTO. */
    private PermissionResponse toResponse(PermissionEntity entity) {
        PermissionResponse response = new PermissionResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUser().getId());
        response.setUsername(entity.getUser().getUsername());
        response.setResourceType(entity.getResourceType().name());
        response.setResourceId(entity.getResourceId());
        response.setLevel(entity.getLevel().name());
        return response;
    }
}
