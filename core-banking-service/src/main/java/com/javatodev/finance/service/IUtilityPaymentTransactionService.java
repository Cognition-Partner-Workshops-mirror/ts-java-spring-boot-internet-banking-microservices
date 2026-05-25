package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;

/**
 * Interface for utility payment transaction operations within core-banking (SRP split).
 */
public interface IUtilityPaymentTransactionService {
    UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest);
}
