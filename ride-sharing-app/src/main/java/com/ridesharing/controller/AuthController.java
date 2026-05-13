package com.ridesharing.controller;

import com.ridesharing.dto.request.LoginRequest;
import com.ridesharing.dto.request.RegisterRequest;
import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.AuthResponse;
import com.ridesharing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Handles user registration and login, returning JWT tokens on success.
 * All endpoints in this controller are publicly accessible.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** Register a new user (rider or driver) and return a JWT token */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new rider or driver account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authResponse));
    }

    /** Authenticate an existing user and return a JWT token */
    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }
}
