package com.javatodev.finance.model.dto.report;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a single row in the account summary report.
 * Used by both JSP views and Tableau CSV/JSON export endpoints.
 */
@Data
@Builder
public class AccountSummaryRow {

    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal availableBalance;
    private BigDecimal actualBalance;
    private String ownerName;
    private String ownerEmail;

}
