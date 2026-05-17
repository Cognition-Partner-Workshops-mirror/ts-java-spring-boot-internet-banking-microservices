package com.javatodev.finance.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Validation annotations added to enforce input constraints on user registration
@Data
@EqualsAndHashCode(callSuper = false)
public class User extends AuditAware {
    private Long id;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Identification is required")
    private String identification;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String authId;

    private Status status;
}
