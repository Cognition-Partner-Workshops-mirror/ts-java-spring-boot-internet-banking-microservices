package com.javatodev.finance.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Validation annotation added to enforce status is provided on user update
@Data
public class UserUpdateRequest {
    @NotNull(message = "Status is required")
    private Status status;
}
