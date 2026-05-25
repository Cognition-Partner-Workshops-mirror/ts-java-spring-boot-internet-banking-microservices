package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Handles fund transfer transactions, extracted from TransactionService (SRP - Phase 3).
 * Depends on IBankAccountService interface (ISP - Phase 4).
 * Uses BalanceValidator for shared balance checks.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FundTransferTransactionService implements IFundTransferTransactionService {

    // Depends on segregated interface, not combined IAccountService (ISP - Phase 4)
    private final IBankAccountService bankAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceValidator balanceValidator;

    @Override
    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {
        BankAccount fromBankAccount = bankAccountService.readBankAccount(fundTransferRequest.getFromAccount());
        BankAccount toBankAccount = bankAccountService.readBankAccount(fundTransferRequest.getToAccount());

        // Validate account balances using shared validator
        balanceValidator.validateBalance(fromBankAccount, fundTransferRequest.getAmount());

        String transactionId = internalFundTransfer(fromBankAccount, toBankAccount, fundTransferRequest.getAmount());
        return FundTransferResponse.builder()
            .message("Transaction successfully completed")
            .transactionId(transactionId)
            .build();
    }

    @Override
    public String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount) {
        String transactionId = UUID.randomUUID().toString();

        BankAccountEntity fromBankAccountEntity = bankAccountRepository
            .findByNumber(fromBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);
        BankAccountEntity toBankAccountEntity = bankAccountRepository
            .findByNumber(toBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);

        fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
        fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
        bankAccountRepository.save(fromBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(fromBankAccountEntity).amount(amount.negate()).build());

        toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
        toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
        bankAccountRepository.save(toBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(toBankAccountEntity).amount(amount).build());

        return transactionId;
    }
}
