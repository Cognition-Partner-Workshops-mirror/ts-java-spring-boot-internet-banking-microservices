package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UtilityPaymentTransactionService (Phase 12: split from TransactionServiceTest).
 * Mocks segregated interfaces IBankAccountService and IUtilityAccountService.
 */
class UtilityPaymentTransactionServiceTest {
    private IBankAccountService bankAccountService;
    private IUtilityAccountService utilityAccountService;
    private BankAccountRepository bankAccountRepository;
    private TransactionRepository transactionRepository;
    private BalanceValidator balanceValidator;
    private UtilityPaymentTransactionService service;

    @BeforeEach
    void setUp() {
        bankAccountService = mock(IBankAccountService.class);
        utilityAccountService = mock(IUtilityAccountService.class);
        bankAccountRepository = mock(BankAccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        balanceValidator = new BalanceValidator();
        service = new UtilityPaymentTransactionService(bankAccountService, utilityAccountService, bankAccountRepository, transactionRepository, balanceValidator);
    }

    @Test
    void utilPayment_success() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        request.setReferenceNumber("REF123");
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(100));
        UtilityAccount utility = new UtilityAccount();
        utility.setId(1L);
        when(bankAccountService.readBankAccount("A1")).thenReturn(from);
        when(utilityAccountService.readUtilityAccount(1L)).thenReturn(utility);
        BankAccountEntity fromEntity = new BankAccountEntity();
        fromEntity.setNumber("A1");
        fromEntity.setActualBalance(BigDecimal.valueOf(100));
        fromEntity.setAvailableBalance(BigDecimal.valueOf(100));
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(fromEntity));
        UtilityPaymentResponse response = service.utilPayment(request);
        assertNotNull(response.getTransactionId());
        assertEquals("Utility payment successfully completed", response.getMessage());
    }

    @Test
    void utilPayment_insufficientFunds() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(150));
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(100));
        when(bankAccountService.readBankAccount("A1")).thenReturn(from);
        assertThrows(InsufficientFundsException.class, () -> service.utilPayment(request));
    }

    @Test
    void utilPayment_accountNotFound() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        when(bankAccountService.readBankAccount("A1")).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class, () -> service.utilPayment(request));
    }
}
