package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface for utility payment operations (OCP + DIP).
 * Controllers depend on this interface, not the concrete UtilityPaymentService class.
 */
public interface IUtilityPaymentService {
    UtilityPaymentResponse utilPayment(UtilityPaymentRequest paymentRequest);
    List<UtilityPayment> readPayments(Pageable pageable);
}
