package com.javatodev.finance.service;

import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.dto.BankAccount;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Shared balance validation component extracted from TransactionService (SRP).
 * Injected into both FundTransferTransactionService and UtilityPaymentTransactionService.
 */
@Component
public class BalanceValidator {

    /**
     * Validates that the bank account has sufficient funds for the requested amount.
     * @throws InsufficientFundsException if balance is insufficient
     */
    public void validateBalance(BankAccount bankAccount, BigDecimal amount) {
        if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0
            || bankAccount.getActualBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds in the account " + bankAccount.getNumber(),
                GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
    }
}
