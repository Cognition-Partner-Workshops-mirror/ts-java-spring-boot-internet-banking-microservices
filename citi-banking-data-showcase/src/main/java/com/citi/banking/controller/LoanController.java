package com.citi.banking.controller;

import com.citi.banking.model.Loan;
import com.citi.banking.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan portfolio management APIs")
public class LoanController {

    private final LoanService loanService;

    @GetMapping
    @Operation(summary = "Get all loans", description = "Returns paginated list of all loans")
    public ResponseEntity<Page<Loan>> getAllLoans(Pageable pageable) {
        return ResponseEntity.ok(loanService.getAllLoans(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get loan by ID")
    public ResponseEntity<Loan> getLoanById(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getLoanById(id));
    }

    @GetMapping("/number/{loanNumber}")
    @Operation(summary = "Get loan by loan number")
    public ResponseEntity<Loan> getLoanByNumber(@PathVariable String loanNumber) {
        return ResponseEntity.ok(loanService.getLoanByNumber(loanNumber));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get loans for a customer")
    public ResponseEntity<List<Loan>> getLoansByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(loanService.getLoansByCustomerId(customerId));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get loans by type", description = "Filter by PERSONAL, MORTGAGE, HOME_EQUITY, AUTO, STUDENT, SMALL_BUSINESS")
    public ResponseEntity<List<Loan>> getLoansByType(@PathVariable Loan.LoanType type) {
        return ResponseEntity.ok(loanService.getLoansByType(type));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get loans by status", description = "Filter by CURRENT, DELINQUENT, DEFAULT, PAID_OFF, IN_FORBEARANCE")
    public ResponseEntity<List<Loan>> getLoansByStatus(@PathVariable Loan.LoanStatus status) {
        return ResponseEntity.ok(loanService.getLoansByStatus(status));
    }
}
