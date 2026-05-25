package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;

/**
 * Interface for account-related operations (OCP + DIP).
 * Controllers and other consumers depend on this interface, not the concrete class.
 */
public interface IAccountService {
    BankAccount readBankAccount(String accountNumber);
    UtilityAccount readUtilityAccount(String provider);
    UtilityAccount readUtilityAccount(Long id);
}
