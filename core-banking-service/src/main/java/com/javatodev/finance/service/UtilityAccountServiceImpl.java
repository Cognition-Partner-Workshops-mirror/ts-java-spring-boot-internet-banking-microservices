package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.mapper.UtilityAccountMapper;
import com.javatodev.finance.repository.UtilityAccountRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Utility account operations extracted from AccountService (ISP split).
 * Consumers that only need utility accounts depend on IUtilityAccountService.
 */
@Service
@RequiredArgsConstructor
public class UtilityAccountServiceImpl implements IUtilityAccountService {

    private final UtilityAccountRepository utilityAccountRepository;
    // Injected as Spring bean (DIP - Phase 5)
    private final UtilityAccountMapper utilityAccountMapper;

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
