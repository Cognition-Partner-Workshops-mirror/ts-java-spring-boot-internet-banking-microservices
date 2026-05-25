package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.mapper.BankAccountMapper;
import com.javatodev.finance.repository.BankAccountRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Bank account operations extracted from AccountService (ISP split).
 * Consumers that only need bank accounts depend on IBankAccountService.
 */
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements IBankAccountService {

    private final BankAccountRepository bankAccountRepository;
    // Injected as Spring bean (DIP - Phase 5)
    private final BankAccountMapper bankAccountMapper;

    @Override
    public BankAccount readBankAccount(String accountNumber) {
        return bankAccountMapper.convertToDto(
            bankAccountRepository.findByNumber(accountNumber)
                .orElseThrow(EntityNotFoundException::new));
    }
}
