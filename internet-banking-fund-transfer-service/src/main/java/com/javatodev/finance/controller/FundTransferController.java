package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.service.FundTransferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Fund Transfer API", description = "API for processing fund transfers")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/transfer")
public class FundTransferController {

    private final FundTransferService fundTransferService;

    /**
     * Process a fund transfer. Accepts optional X-Idempotency-Key header to prevent
     * duplicate transfers on retries. If a transfer with the same key already exists,
     * the original response is returned instead of processing a new transfer.
     */
    @Operation(summary = "Send Fund Transfer", description = "Process a fund transfer request with optional idempotency key")
    @PostMapping
    public ResponseEntity sendFundTransfer(
            @RequestBody FundTransferRequest fundTransferRequest,
            @Parameter(description = "Unique idempotency key to prevent duplicate transfers")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
        return ResponseEntity.ok(fundTransferService.fundTransfer(fundTransferRequest, idempotencyKey));
    }

    @Operation(summary = "Read Fund Transfers", description = "Retrieve a paginated list of fund transfers")
    @GetMapping
    public ResponseEntity readFundTransfers(Pageable pageable) {
        log.info("Reading fund transfers from core");
        return ResponseEntity.ok(fundTransferService.readAllTransfers(pageable));
    }
}
