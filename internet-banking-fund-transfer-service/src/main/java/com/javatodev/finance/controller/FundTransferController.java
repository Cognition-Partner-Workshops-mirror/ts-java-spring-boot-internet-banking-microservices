package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.service.IFundTransferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fund transfer controller with typed ResponseEntity<T> (Phase 7)
 * and @Valid bean validation (Phase 8).
 */
@Slf4j
@Tag(name = "Fund Transfer API", description = "API for processing fund transfers")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/transfer")
public class FundTransferController {

    // Depends on interface, not concrete class (DIP)
    private final IFundTransferService fundTransferService;

    @Operation(summary = "Send Fund Transfer", description = "Process a fund transfer request")
    @PostMapping
    public ResponseEntity<FundTransferResponse> sendFundTransfer(@Valid @RequestBody FundTransferRequest fundTransferRequest) {
        log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
        return ResponseEntity.ok(fundTransferService.fundTransfer(fundTransferRequest));
    }

    @Operation(summary = "Read Fund Transfers", description = "Retrieve a paginated list of fund transfers")
    @GetMapping
    public ResponseEntity<List<FundTransfer>> readFundTransfers(Pageable pageable) {
        log.info("Reading fund transfers from core");
        return ResponseEntity.ok(fundTransferService.readAllTransfers(pageable));
    }
}
