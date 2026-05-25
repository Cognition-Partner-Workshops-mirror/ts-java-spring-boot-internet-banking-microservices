package com.javatodev.finance.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Added @Builder for Resilience4j fallback support, @NoArgsConstructor/@AllArgsConstructor for Jackson compatibility
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundTransferResponse {
    private String message;
    private String transactionId;
}
