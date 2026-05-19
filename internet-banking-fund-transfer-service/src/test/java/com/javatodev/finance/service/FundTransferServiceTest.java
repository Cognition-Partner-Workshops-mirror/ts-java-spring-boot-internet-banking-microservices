package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FundTransferService.
 * Tests cover normal flow, idempotency key deduplication, and
 * transfer without idempotency key (backward compatibility).
 */
class FundTransferServiceTest {

    private FundTransferRepository fundTransferRepository;
    private BankingCoreFeignClient bankingCoreFeignClient;
    private FundTransferService fundTransferService;

    @BeforeEach
    void setUp() {
        fundTransferRepository = mock(FundTransferRepository.class);
        bankingCoreFeignClient = mock(BankingCoreFeignClient.class);
        fundTransferService = new FundTransferService(fundTransferRepository, bankingCoreFeignClient);
    }

    @Test
    void fundTransfer_shouldProcessSuccessfully() {
        // Arrange: build request and mock responses
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(500));

        FundTransferEntity savedEntity = new FundTransferEntity();
        savedEntity.setId(1L);
        savedEntity.setStatus(TransactionStatus.PENDING);
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(savedEntity);

        FundTransferResponse coreResponse = new FundTransferResponse();
        coreResponse.setTransactionId("TXN-001");
        when(bankingCoreFeignClient.fundTransfer(any())).thenReturn(coreResponse);

        // Act
        FundTransferResponse result = fundTransferService.fundTransfer(request, null);

        // Assert: transfer processed and success message returned
        assertNotNull(result);
        assertEquals("TXN-001", result.getTransactionId());
        assertEquals("Fund Transfer Successfully Completed", result.getMessage());
        // Repository save called twice (PENDING then SUCCESS)
        verify(fundTransferRepository, times(2)).save(any(FundTransferEntity.class));
        verify(bankingCoreFeignClient, times(1)).fundTransfer(any());
    }

    @Test
    void fundTransfer_withIdempotencyKey_shouldDeduplicateOnRetry() {
        // Arrange: simulate existing transfer with same idempotency key
        FundTransferEntity existingEntity = new FundTransferEntity();
        existingEntity.setId(1L);
        existingEntity.setTransactionReference("TXN-EXISTING");
        existingEntity.setStatus(TransactionStatus.SUCCESS);
        when(fundTransferRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existingEntity));

        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(500));

        // Act
        FundTransferResponse result = fundTransferService.fundTransfer(request, "key-123");

        // Assert: returns cached result without calling core banking
        assertNotNull(result);
        assertEquals("TXN-EXISTING", result.getTransactionId());
        assertTrue(result.getMessage().contains("Already Processed"));
        // Core banking should NOT be called for duplicate requests
        verify(bankingCoreFeignClient, never()).fundTransfer(any());
        verify(fundTransferRepository, never()).save(any());
    }

    @Test
    void fundTransfer_withNewIdempotencyKey_shouldProcessNormally() {
        // Arrange: no existing transfer with this key
        when(fundTransferRepository.findByIdempotencyKey("key-new")).thenReturn(Optional.empty());

        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(1000));

        FundTransferEntity savedEntity = new FundTransferEntity();
        savedEntity.setId(2L);
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(savedEntity);

        FundTransferResponse coreResponse = new FundTransferResponse();
        coreResponse.setTransactionId("TXN-NEW");
        when(bankingCoreFeignClient.fundTransfer(any())).thenReturn(coreResponse);

        // Act
        FundTransferResponse result = fundTransferService.fundTransfer(request, "key-new");

        // Assert: new transfer processed normally
        assertNotNull(result);
        assertEquals("TXN-NEW", result.getTransactionId());
        assertEquals("Fund Transfer Successfully Completed", result.getMessage());
        verify(bankingCoreFeignClient, times(1)).fundTransfer(any());
    }

    @Test
    void fundTransfer_withNullIdempotencyKey_shouldProcessWithoutDeduplication() {
        // Arrange: null key should skip deduplication entirely
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(200));

        FundTransferEntity savedEntity = new FundTransferEntity();
        savedEntity.setId(3L);
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(savedEntity);

        FundTransferResponse coreResponse = new FundTransferResponse();
        coreResponse.setTransactionId("TXN-003");
        when(bankingCoreFeignClient.fundTransfer(any())).thenReturn(coreResponse);

        // Act
        FundTransferResponse result = fundTransferService.fundTransfer(request, null);

        // Assert: processed without checking idempotency
        assertNotNull(result);
        assertEquals("TXN-003", result.getTransactionId());
        // findByIdempotencyKey should NOT be called when key is null
        verify(fundTransferRepository, never()).findByIdempotencyKey(any());
    }
}
