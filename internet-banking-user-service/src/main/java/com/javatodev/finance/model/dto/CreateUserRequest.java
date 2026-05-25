package com.javatodev.finance.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for user creation (SRP - Phase 6).
 * Contains only the fields needed for creating a new user, with bean validation annotations (Phase 8).
 */
@Data
public class CreateUserRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String identification;

    @NotBlank
    private String password;
}
