package com.javatodev.finance.service;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.report.AccountSummaryRow;
import com.javatodev.finance.model.dto.report.TransactionReportRow;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer for generating banking reports.
 * Aggregates data from transaction and account repositories
 * and converts entities into report-specific DTOs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;

    /**
     * Retrieves transaction report rows, optionally filtered by account number
     * and/or transaction type.
     */
    public List<TransactionReportRow> getTransactionReport(String accountNumber, String transactionType) {

        List<TransactionEntity> transactions;

        // Apply filtering based on provided parameters
        boolean hasAccount = accountNumber != null && !accountNumber.isBlank();
        boolean hasType = transactionType != null && !transactionType.isBlank();

        if (hasAccount && hasType) {
            TransactionType type = TransactionType.valueOf(transactionType);
            transactions = transactionRepository.findByAccountNumberAndType(accountNumber, type);
        } else if (hasAccount) {
            transactions = transactionRepository.findByAccountNumber(accountNumber);
        } else if (hasType) {
            TransactionType type = TransactionType.valueOf(transactionType);
            transactions = transactionRepository.findByTransactionType(type);
        } else {
            transactions = transactionRepository.findAll();
        }

        log.info("Transaction report generated with {} records", transactions.size());

        return transactions.stream()
            .map(this::toTransactionReportRow)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves account summary report rows, optionally filtered by account
     * status and/or account type.
     */
    public List<AccountSummaryRow> getAccountSummaryReport(String status, String accountType) {

        List<BankAccountEntity> accounts;

        // Apply filtering based on provided parameters
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasType = accountType != null && !accountType.isBlank();

        if (hasStatus && hasType) {
            AccountStatus accountStatus = AccountStatus.valueOf(status);
            AccountType type = AccountType.valueOf(accountType);
            accounts = bankAccountRepository.findByStatusAndType(accountStatus, type);
        } else if (hasStatus) {
            AccountStatus accountStatus = AccountStatus.valueOf(status);
            accounts = bankAccountRepository.findByStatus(accountStatus);
        } else if (hasType) {
            AccountType type = AccountType.valueOf(accountType);
            accounts = bankAccountRepository.findByType(type);
        } else {
            accounts = bankAccountRepository.findAll();
        }

        log.info("Account summary report generated with {} records", accounts.size());

        return accounts.stream()
            .map(this::toAccountSummaryRow)
            .collect(Collectors.toList());
    }

    /**
     * Converts a TransactionEntity to a TransactionReportRow DTO.
     */
    private TransactionReportRow toTransactionReportRow(TransactionEntity entity) {
        return TransactionReportRow.builder()
            .transactionId(entity.getId())
            .transactionUuid(entity.getTransactionId())
            .transactionType(entity.getTransactionType() != null ? entity.getTransactionType().name() : "UNKNOWN")
            .amount(entity.getAmount())
            .accountNumber(entity.getAccount() != null ? entity.getAccount().getNumber() : "N/A")
            .referenceNumber(entity.getReferenceNumber())
            .build();
    }

    /**
     * Converts a BankAccountEntity to an AccountSummaryRow DTO,
     * including the owner's name and email when available.
     */
    private AccountSummaryRow toAccountSummaryRow(BankAccountEntity entity) {
        UserEntity user = entity.getUser();
        String ownerName = (user != null) ? user.getFirstName() + " " + user.getLastName() : "N/A";
        String ownerEmail = (user != null) ? user.getEmail() : "N/A";

        return AccountSummaryRow.builder()
            .accountId(entity.getId())
            .accountNumber(entity.getNumber())
            .accountType(entity.getType() != null ? entity.getType().name() : "UNKNOWN")
            .accountStatus(entity.getStatus() != null ? entity.getStatus().name() : "UNKNOWN")
            .availableBalance(entity.getAvailableBalance())
            .actualBalance(entity.getActualBalance())
            .ownerName(ownerName)
            .ownerEmail(ownerEmail)
            .build();
    }

}
