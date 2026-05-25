package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.mapper.BankAccountMapper;
import com.javatodev.finance.repository.BankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BankAccountServiceImpl (Phase 12: split from AccountServiceTest).
 * Tests the segregated IBankAccountService implementation.
 */
class BankAccountServiceTest {

    private BankAccountRepository bankAccountRepository;
    private BankAccountServiceImpl bankAccountService;

    @BeforeEach
    void setUp() {
        bankAccountRepository = mock(BankAccountRepository.class);
        BankAccountMapper bankAccountMapper = new BankAccountMapper();
        bankAccountService = new BankAccountServiceImpl(bankAccountRepository, bankAccountMapper);
    }

    @Test
    void readBankAccount_found() {
        BankAccountEntity entity = new BankAccountEntity();
        entity.setNumber("123");
        when(bankAccountRepository.findByNumber("123")).thenReturn(Optional.of(entity));

        BankAccount dto = bankAccountService.readBankAccount("123");
        assertNotNull(dto);
    }

    @Test
    void readBankAccount_notFound() {
        when(bankAccountRepository.findByNumber("123")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.readBankAccount("123"));
    }
}
