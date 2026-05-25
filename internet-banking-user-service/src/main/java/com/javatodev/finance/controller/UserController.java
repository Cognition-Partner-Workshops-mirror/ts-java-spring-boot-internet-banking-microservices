package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.CreateUserRequest;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserResponse;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.service.IUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * User controller for the internet banking user service.
 * Uses typed ResponseEntity<T> (Phase 7), @Valid for bean validation (Phase 8),
 * and CreateUserRequest/UserResponse DTOs (Phase 6).
 */
@Slf4j
@Tag(name = "User Controller", description = "APIs for managing bank users")
@RestController
@RequestMapping(value = "/api/v1/bank-users")
@RequiredArgsConstructor
public class UserController {

    // Depends on interface, not concrete class (DIP)
    private final IUserService userService;

    /**
     * Accepts CreateUserRequest and returns UserResponse (Phase 6: separate DTOs).
     * Uses @Valid for bean validation (Phase 8).
     */
    @Operation(summary = "Register User", description = "Create a new user in the banking system")
    @PostMapping(value = "/register")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @Operation(summary = "Get Users", description = "Retrieve a paginated list of bank users")
    @GetMapping
    public ResponseEntity<List<User>> readUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.readUsers(pageable));
    }

    @Operation(summary = "Get User by ID", description = "Retrieve a specific user by their ID")
    @GetMapping(value = "/{id}")
    public ResponseEntity<User> readUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.readUser(id));
    }

    @Operation(summary = "Update User", description = "Update a user's information")
    @PatchMapping(value = "/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        return ResponseEntity.ok(userService.updateUser(id, userUpdateRequest));
    }
}
