package com.javatodev.finance.model.dto.report;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a single row in the transaction report.
 * Used by both JSP views and Tableau CSV/JSON export endpoints.
 */
@Data
@Builder
public class TransactionReportRow {

    private Long transactionId;
    private String transactionUuid;
    private String transactionType;
    private BigDecimal amount;
    private String accountNumber;
    private String referenceNumber;

}
