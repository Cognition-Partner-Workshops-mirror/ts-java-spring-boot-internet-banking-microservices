package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Utility payment request with bean validation annotations (Phase 8: Defensive Design).
 */
@Data
public class UtilityPaymentRequest {

    @NotNull
    private Long providerId;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String referenceNumber;

    @NotBlank
    private String account;
}
