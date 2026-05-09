package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.MdmUserRequest;
import com.javatodev.finance.service.MdmUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for MDM User management.
 * Handles CRUD operations for MDM platform users with role-based access.
 */
@Slf4j
@Tag(name = "MDM User API", description = "APIs for managing MDM platform users (ADMIN, DATA_STEWARD, VIEWER roles)")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/mdm-users")
public class MdmUserController {

    private final MdmUserService mdmUserService;

    @Operation(summary = "Create MDM User", description = "Create a new MDM user with username, email, password, and role")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody MdmUserRequest request) {
        log.info("Creating MDM user: {}", request.getUsername());
        return ResponseEntity.ok(mdmUserService.createUser(request));
    }

    @Operation(summary = "List MDM Users", description = "Retrieve a paginated list of MDM users")
    @GetMapping
    public ResponseEntity<?> listUsers(Pageable pageable) {
        return ResponseEntity.ok(mdmUserService.listUsers(pageable));
    }

    @Operation(summary = "Get MDM User", description = "Get an MDM user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(mdmUserService.getUser(id));
    }

    @Operation(summary = "Update MDM User", description = "Update MDM user details")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody MdmUserRequest request) {
        return ResponseEntity.ok(mdmUserService.updateUser(id, request));
    }

    @Operation(summary = "Deactivate MDM User", description = "Deactivate an MDM user (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(mdmUserService.deactivateUser(id));
    }
}
