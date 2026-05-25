package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.BankAccount;

/**
 * Interface segregation: only bank-account read operations (ISP).
 */
public interface IBankAccountService {
    BankAccount readBankAccount(String accountNumber);
}
