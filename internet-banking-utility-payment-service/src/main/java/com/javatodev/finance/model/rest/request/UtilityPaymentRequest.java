package com.javatodev.finance.model.rest.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Validation annotations added to enforce input constraints on utility payment requests
@Data
public class UtilityPaymentRequest {
    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    @NotBlank(message = "Account is required")
    private String account;
}
