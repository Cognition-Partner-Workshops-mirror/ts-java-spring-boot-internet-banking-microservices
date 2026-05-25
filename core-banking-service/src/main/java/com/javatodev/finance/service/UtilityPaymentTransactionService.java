package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Handles utility payment transactions, extracted from TransactionService (SRP - Phase 3).
 * Depends on IBankAccountService and IUtilityAccountService interfaces (ISP - Phase 4).
 * Uses BalanceValidator for shared balance checks.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UtilityPaymentTransactionService implements IUtilityPaymentTransactionService {

    // Depends on segregated interfaces, not combined IAccountService (ISP - Phase 4)
    private final IBankAccountService bankAccountService;
    private final IUtilityAccountService utilityAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceValidator balanceValidator;

    @Override
    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest) {
        String transactionId = UUID.randomUUID().toString();

        BankAccount fromBankAccount = bankAccountService.readBankAccount(utilityPaymentRequest.getAccount());

        // Validate account balances using shared validator
        balanceValidator.validateBalance(fromBankAccount, utilityPaymentRequest.getAmount());

        UtilityAccount utilityAccount = utilityAccountService.readUtilityAccount(utilityPaymentRequest.getProviderId());

        BankAccountEntity fromAccount = bankAccountRepository.findByNumber(fromBankAccount.getNumber()).get();

        // Third party API call to process utility payment from payment provider would go here

        fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));

        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.UTILITY_PAYMENT)
            .account(fromAccount)
            .transactionId(transactionId)
            .referenceNumber(utilityPaymentRequest.getReferenceNumber())
            .amount(utilityPaymentRequest.getAmount().negate()).build());

        return UtilityPaymentResponse.builder()
            .message("Utility payment successfully completed")
            .transactionId(transactionId)
            .build();
    }
}
