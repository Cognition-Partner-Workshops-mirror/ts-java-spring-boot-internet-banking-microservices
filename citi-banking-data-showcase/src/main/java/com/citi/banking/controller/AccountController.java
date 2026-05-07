package com.citi.banking.controller;

import com.citi.banking.model.Account;
import com.citi.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Bank account management APIs")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Get all accounts", description = "Returns paginated list of all bank accounts")
    public ResponseEntity<Page<Account>> getAllAccounts(Pageable pageable) {
        return ResponseEntity.ok(accountService.getAllAccounts(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<Account> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by account number")
    public ResponseEntity<Account> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get all accounts for a customer")
    public ResponseEntity<List<Account>> getAccountsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(accountService.getAccountsByCustomerId(customerId));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get accounts by type", description = "Filter by CHECKING, SAVINGS, MONEY_MARKET, CERTIFICATE_OF_DEPOSIT, etc.")
    public ResponseEntity<List<Account>> getAccountsByType(@PathVariable Account.AccountType type) {
        return ResponseEntity.ok(accountService.getAccountsByType(type));
    }

    @GetMapping("/branch/{branchCode}")
    @Operation(summary = "Get accounts by branch")
    public ResponseEntity<List<Account>> getAccountsByBranch(@PathVariable String branchCode) {
        return ResponseEntity.ok(accountService.getAccountsByBranch(branchCode));
    }
}
