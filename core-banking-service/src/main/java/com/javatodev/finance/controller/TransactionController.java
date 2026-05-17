package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Transaction Controller", description = "APIs for managing transactions")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    // Added @Valid for input validation, sanitized log to avoid logging full request
    @Operation(summary = "Fund Transfer", description = "Process a fund transfer request")
    @PostMapping("/fund-transfer")
    public ResponseEntity fundTransfer(@Valid @RequestBody FundTransferRequest fundTransferRequest) {
        log.info("Fund transfer initiated in core bank");
        return ResponseEntity.ok(transactionService.fundTransfer(fundTransferRequest));
    }

    // Added @Valid for input validation, sanitized log to avoid logging full request
    @Operation(summary = "Utility Payment", description = "Process a utility payment request")
    @PostMapping("/util-payment")
    public ResponseEntity utilPayment(@Valid @RequestBody UtilityPaymentRequest utilityPaymentRequest) {
        log.info("Utility payment initiated in core bank");
        return ResponseEntity.ok(transactionService.utilPayment(utilityPaymentRequest));
    }

}
