package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.entity.UtilityAccountEntity;
import com.javatodev.finance.model.mapper.UtilityAccountMapper;
import com.javatodev.finance.repository.UtilityAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UtilityAccountServiceImpl (Phase 12: split from AccountServiceTest).
 * Tests the segregated IUtilityAccountService implementation.
 */
class UtilityAccountServiceTest {

    private UtilityAccountRepository utilityAccountRepository;
    private UtilityAccountServiceImpl utilityAccountService;

    @BeforeEach
    void setUp() {
        utilityAccountRepository = mock(UtilityAccountRepository.class);
        UtilityAccountMapper utilityAccountMapper = new UtilityAccountMapper();
        utilityAccountService = new UtilityAccountServiceImpl(utilityAccountRepository, utilityAccountMapper);
    }

    @Test
    void readUtilityAccount_byProvider_found() {
        UtilityAccountEntity entity = new UtilityAccountEntity();
        entity.setProviderName("ProviderA");
        when(utilityAccountRepository.findByProviderName("ProviderA")).thenReturn(Optional.of(entity));

        UtilityAccount dto = utilityAccountService.readUtilityAccount("ProviderA");
        assertNotNull(dto);
    }

    @Test
    void readUtilityAccount_byProvider_notFound() {
        when(utilityAccountRepository.findByProviderName("ProviderA")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> utilityAccountService.readUtilityAccount("ProviderA"));
    }

    @Test
    void readUtilityAccount_byId_found() {
        UtilityAccountEntity entity = new UtilityAccountEntity();
        entity.setId(1L);
        when(utilityAccountRepository.findById(1L)).thenReturn(Optional.of(entity));

        UtilityAccount dto = utilityAccountService.readUtilityAccount(1L);
        assertNotNull(dto);
    }

    @Test
    void readUtilityAccount_byId_notFound() {
        when(utilityAccountRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> utilityAccountService.readUtilityAccount(1L));
    }
}
