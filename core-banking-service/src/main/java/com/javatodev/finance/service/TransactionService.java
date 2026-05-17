package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    private final AccountService accountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {

        BankAccount fromBankAccount = accountService.readBankAccount(fundTransferRequest.getFromAccount());
        BankAccount toBankAccount = accountService.readBankAccount(fundTransferRequest.getToAccount());

        //validating account balances
        validateBalance(fromBankAccount, fundTransferRequest.getAmount());

        String transactionId = internalFundTransfer(fromBankAccount, toBankAccount, fundTransferRequest.getAmount());
        return FundTransferResponse.builder().message("Transaction successfully completed").transactionId(transactionId).build();

    }

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest) {

        String transactionId = UUID.randomUUID().toString();

        BankAccount fromBankAccount = accountService.readBankAccount(utilityPaymentRequest.getAccount());

        //validating account balances
        validateBalance(fromBankAccount, utilityPaymentRequest.getAmount());

        UtilityAccount utilityAccount = accountService.readUtilityAccount(utilityPaymentRequest.getProviderId());

        // Use pessimistic locking to prevent concurrent balance modifications
        BankAccountEntity fromAccount = bankAccountRepository.findByNumberForUpdate(fromBankAccount.getNumber()).get();

        //we can call third party API to process UTIL payment from payment provider from here.

        // Fix: compute new balance once to avoid double-subtraction bug
        BigDecimal newBalance = fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount());
        fromAccount.setActualBalance(newBalance);
        fromAccount.setAvailableBalance(newBalance);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.UTILITY_PAYMENT)
            .account(fromAccount)
            .transactionId(transactionId)
            .referenceNumber(utilityPaymentRequest.getReferenceNumber())
            .amount(utilityPaymentRequest.getAmount().negate()).build());

        return UtilityPaymentResponse.builder().message("Utility payment successfully completed")
            .transactionId(transactionId).build();

    }

    private void validateBalance(BankAccount bankAccount, BigDecimal amount) {
        if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0 || bankAccount.getActualBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the account " + bankAccount.getNumber(), GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
    }

    public String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount) {

        String transactionId = UUID.randomUUID().toString();

        // Acquire pessimistic locks in consistent lexicographic order to prevent deadlocks
        // when concurrent opposite-direction transfers (A->B and B->A) run simultaneously
        String fromNumber = fromBankAccount.getNumber();
        String toNumber = toBankAccount.getNumber();
        BankAccountEntity firstLocked, secondLocked;
        if (fromNumber.compareTo(toNumber) <= 0) {
            firstLocked = bankAccountRepository.findByNumberForUpdate(fromNumber).orElseThrow(EntityNotFoundException::new);
            secondLocked = bankAccountRepository.findByNumberForUpdate(toNumber).orElseThrow(EntityNotFoundException::new);
        } else {
            secondLocked = bankAccountRepository.findByNumberForUpdate(toNumber).orElseThrow(EntityNotFoundException::new);
            firstLocked = bankAccountRepository.findByNumberForUpdate(fromNumber).orElseThrow(EntityNotFoundException::new);
        }
        // Map back to from/to regardless of lock acquisition order
        BankAccountEntity fromBankAccountEntity = firstLocked.getNumber().equals(fromNumber) ? firstLocked : secondLocked;
        BankAccountEntity toBankAccountEntity = firstLocked.getNumber().equals(toNumber) ? firstLocked : secondLocked;

        // Fix: compute new balance once to avoid double-subtraction bug
        BigDecimal newFromBalance = fromBankAccountEntity.getActualBalance().subtract(amount);
        fromBankAccountEntity.setActualBalance(newFromBalance);
        fromBankAccountEntity.setAvailableBalance(newFromBalance);
        bankAccountRepository.save(fromBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(fromBankAccountEntity).amount(amount.negate()).build());

        // Fix: compute new balance once to avoid double-addition bug
        BigDecimal newToBalance = toBankAccountEntity.getActualBalance().add(amount);
        toBankAccountEntity.setActualBalance(newToBalance);
        toBankAccountEntity.setAvailableBalance(newToBalance);
        bankAccountRepository.save(toBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(toBankAccountEntity).amount(amount).build());

        return transactionId;

    }

}
