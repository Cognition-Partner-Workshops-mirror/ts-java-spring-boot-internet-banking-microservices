package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// Validation annotations added to enforce input constraints on fund transfer requests
@Data
public class FundTransferRequest {
    @NotBlank(message = "From account is required")
    @Pattern(regexp = "^[0-9]{10,20}$", message = "Invalid account number format")
    private String fromAccount;

    @NotBlank(message = "To account is required")
    @Pattern(regexp = "^[0-9]{10,20}$", message = "Invalid account number format")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String authID;
}
