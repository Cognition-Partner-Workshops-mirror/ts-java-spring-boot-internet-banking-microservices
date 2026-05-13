package com.ridesharing.controller;

import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.UserResponse;
import com.ridesharing.entity.User;
import com.ridesharing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user profile operations.
 * Provides endpoints for viewing and managing user profiles.
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile management endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Get the profile of the currently authenticated user */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        UserResponse userResponse = userService.getCurrentUserProfile(currentUser);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", userResponse));
    }

    /** Get a user's profile by their ID */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Returns a user's public profile")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long userId) {
        UserResponse userResponse = userService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", userResponse));
    }
}
