package com.javatodev.finance.controller;

import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.service.UtilityPaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Utility Payment API", description = "API for processing utility payments")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/utility-payment")
public class UtilityPaymentController {

    private final UtilityPaymentService utilityPaymentService;

    @Operation(summary = "Read Utility Payments", description = "Retrieve a paginated list of utility payments")
    @GetMapping
    public ResponseEntity readPayments(Pageable pageable) {
        return ResponseEntity.ok(utilityPaymentService.readPayments(pageable));
    }

    // Added @Valid for input validation, X-Idempotency-Key header to prevent duplicate processing
    @Operation(summary = "Process Utility Payment", description = "Process a utility payment request")
    @PostMapping
    public ResponseEntity processPayment(
            @Valid @RequestBody UtilityPaymentRequest paymentRequest,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Received utility payment request");
        return ResponseEntity.ok(utilityPaymentService.utilPayment(paymentRequest, idempotencyKey));
    }

}
