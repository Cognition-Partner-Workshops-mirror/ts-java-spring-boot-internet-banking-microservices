package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.mapper.BankAccountMapper;
import com.javatodev.finance.model.mapper.UtilityAccountMapper;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.UtilityAccountRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Combined AccountService implementing IAccountService.
 * Kept for backward compatibility with AccountController.
 * Prefer the segregated BankAccountServiceImpl / UtilityAccountServiceImpl for new consumers (ISP).
 */
@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    // Injected as Spring beans (DIP - Phase 5)
    private final BankAccountMapper bankAccountMapper;
    private final UtilityAccountMapper utilityAccountMapper;

    private final BankAccountRepository bankAccountRepository;
    private final UtilityAccountRepository utilityAccountRepository;

    @Override
    public BankAccount readBankAccount(String accountNumber) {
        return bankAccountMapper.convertToDto(
            bankAccountRepository.findByNumber(accountNumber)
                .orElseThrow(EntityNotFoundException::new));
    }

    @Override
    public UtilityAccount readUtilityAccount(String provider) {
        return utilityAccountMapper.convertToDto(
            utilityAccountRepository.findByProviderName(provider)
                .orElseThrow(EntityNotFoundException::new));
    }

    @Override
    public UtilityAccount readUtilityAccount(Long id) {
        return utilityAccountMapper.convertToDto(
            utilityAccountRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new));
    }
}
