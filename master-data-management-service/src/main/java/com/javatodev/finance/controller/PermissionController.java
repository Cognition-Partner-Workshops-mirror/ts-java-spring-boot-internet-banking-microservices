package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.PermissionRequest;
import com.javatodev.finance.service.PermissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Permission management.
 * Controls which users can access which dataspaces, datasets, and tables.
 */
@Slf4j
@Tag(name = "Permission API", description = "APIs for managing role-based access control on MDM resources")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Grant Permission", description = "Grant a permission to a user on a resource (DATASPACE, DATASET, or TABLE)")
    @PostMapping
    public ResponseEntity<?> grantPermission(@RequestBody PermissionRequest request) {
        log.info("Granting {} on {} {} to user {}", request.getLevel(),
            request.getResourceType(), request.getResourceId(), request.getUserId());
        return ResponseEntity.ok(permissionService.grantPermission(request));
    }

    @Operation(summary = "Get User Permissions", description = "Get all permissions for a given user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getUserPermissions(userId));
    }

    @Operation(summary = "Revoke Permission", description = "Revoke a permission by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> revokePermission(@PathVariable Long id) {
        permissionService.revokePermission(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Check Permission", description = "Check if a user has a specific permission on a resource")
    @GetMapping("/check")
    public ResponseEntity<?> checkPermission(
            @RequestParam Long userId,
            @RequestParam String resourceType,
            @RequestParam Long resourceId,
            @RequestParam String level) {
        boolean hasPermission = permissionService.hasPermission(userId, resourceType, resourceId, level);
        return ResponseEntity.ok(java.util.Map.of("hasPermission", hasPermission));
    }
}
