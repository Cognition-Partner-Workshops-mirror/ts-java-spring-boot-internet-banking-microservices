package com.citi.banking.service;

import com.citi.banking.model.Account;
import com.citi.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Page<Account> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable);
    }

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }

    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }

    public List<Account> getAccountsByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    public List<Account> getAccountsByType(Account.AccountType type) {
        return accountRepository.findByAccountType(type);
    }

    public List<Account> getAccountsByBranch(String branchCode) {
        return accountRepository.findByBranchCode(branchCode);
    }

    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }
}
