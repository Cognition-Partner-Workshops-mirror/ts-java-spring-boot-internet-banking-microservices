package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;

import java.math.BigDecimal;

/**
 * Interface for fund transfer transaction operations within core-banking (SRP split).
 */
public interface IFundTransferTransactionService {
    FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest);
    String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount);
}
