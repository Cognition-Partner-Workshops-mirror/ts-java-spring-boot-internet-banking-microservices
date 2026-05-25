package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Fund transfer request with bean validation annotations (Phase 8: Defensive Design).
 */
@Data
public class FundTransferRequest {
    @NotBlank
    private String fromAccount;

    @NotBlank
    private String toAccount;

    @NotNull
    @Positive
    private BigDecimal amount;
}
