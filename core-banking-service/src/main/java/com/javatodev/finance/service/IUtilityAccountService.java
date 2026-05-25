package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.UtilityAccount;

/**
 * Interface segregation: only utility-account read operations (ISP).
 */
public interface IUtilityAccountService {
    UtilityAccount readUtilityAccount(String provider);
    UtilityAccount readUtilityAccount(Long id);
}
