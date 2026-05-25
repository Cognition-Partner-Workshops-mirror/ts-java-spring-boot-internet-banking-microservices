package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;

import java.math.BigDecimal;

/**
 * Interface for transaction-related operations (OCP + DIP).
 * Controllers depend on this interface, not the concrete class.
 */
public interface ITransactionService {
    FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest);
    UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest);
    String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount);
}
