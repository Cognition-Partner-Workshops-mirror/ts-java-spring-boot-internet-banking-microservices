package com.javatodev.finance.model.rest.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UtilityPaymentRequest {
    @NotNull(message = "Provider ID is required")
    private Long providerId;

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    @NotBlank(message = "Source account number is required")
    private String account;
}
