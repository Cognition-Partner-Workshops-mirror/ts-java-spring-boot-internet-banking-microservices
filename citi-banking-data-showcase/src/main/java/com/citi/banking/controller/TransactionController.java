package com.citi.banking.controller;

import com.citi.banking.model.Transaction;
import com.citi.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction history and management APIs")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "Get all transactions", description = "Returns paginated list of all transactions")
    public ResponseEntity<Page<Transaction>> getAllTransactions(Pageable pageable) {
        return ResponseEntity.ok(transactionService.getAllTransactions(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @GetMapping("/ref/{transactionId}")
    @Operation(summary = "Get transaction by transaction reference ID")
    public ResponseEntity<Transaction> getTransactionByTransactionId(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.getTransactionByTransactionId(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get transactions for an account", description = "Returns paginated transaction history for a given account number")
    public ResponseEntity<Page<Transaction>> getTransactionsByAccount(@PathVariable String accountNumber, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountNumber, pageable));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get transactions by type", description = "Filter by DEPOSIT, WITHDRAWAL, TRANSFER_IN, WIRE_TRANSFER, ACH_CREDIT, etc.")
    public ResponseEntity<List<Transaction>> getTransactionsByType(@PathVariable Transaction.TransactionType type) {
        return ResponseEntity.ok(transactionService.getTransactionsByType(type));
    }
}
