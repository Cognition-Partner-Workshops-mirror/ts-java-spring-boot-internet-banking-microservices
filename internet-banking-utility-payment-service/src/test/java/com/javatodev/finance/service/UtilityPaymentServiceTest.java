package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UtilityPaymentService.
 * Tests cover normal flow, idempotency key deduplication, and
 * payment without idempotency key (backward compatibility).
 */
class UtilityPaymentServiceTest {

    private UtilityPaymentRepository utilityPaymentRepository;
    private BankingCoreRestClient bankingCoreRestClient;
    private UtilityPaymentService utilityPaymentService;

    @BeforeEach
    void setUp() {
        utilityPaymentRepository = mock(UtilityPaymentRepository.class);
        bankingCoreRestClient = mock(BankingCoreRestClient.class);
        utilityPaymentService = new UtilityPaymentService(utilityPaymentRepository, bankingCoreRestClient);
    }

    @Test
    void utilPayment_shouldProcessSuccessfully() {
        // Arrange: build request and mock responses
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(150));
        request.setReferenceNumber("REF-001");
        request.setAccount("ACC-001");

        UtilityPaymentEntity savedEntity = new UtilityPaymentEntity();
        savedEntity.setId(1L);
        savedEntity.setStatus(TransactionStatus.PROCESSING);
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(savedEntity);

        UtilityPaymentResponse coreResponse = UtilityPaymentResponse.builder()
                .transactionId("UTIL-TXN-001")
                .message("OK")
                .build();
        when(bankingCoreRestClient.utilityPayment(any())).thenReturn(coreResponse);

        // Act
        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, null);

        // Assert: payment processed and success message returned
        assertNotNull(result);
        assertEquals("UTIL-TXN-001", result.getTransactionId());
        assertEquals("Utility Payment Successfully Processed", result.getMessage());
        // Repository save called twice (PROCESSING then SUCCESS)
        verify(utilityPaymentRepository, times(2)).save(any(UtilityPaymentEntity.class));
        verify(bankingCoreRestClient, times(1)).utilityPayment(any());
    }

    @Test
    void utilPayment_withIdempotencyKey_shouldDeduplicateOnRetry() {
        // Arrange: simulate existing payment with same idempotency key
        UtilityPaymentEntity existingEntity = new UtilityPaymentEntity();
        existingEntity.setId(1L);
        existingEntity.setTransactionId("UTIL-TXN-EXISTING");
        existingEntity.setStatus(TransactionStatus.SUCCESS);
        when(utilityPaymentRepository.findByIdempotencyKey("util-key-123")).thenReturn(Optional.of(existingEntity));

        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(150));

        // Act
        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, "util-key-123");

        // Assert: returns cached result without calling core banking
        assertNotNull(result);
        assertEquals("UTIL-TXN-EXISTING", result.getTransactionId());
        assertTrue(result.getMessage().contains("Already Processed"));
        // Core banking should NOT be called for duplicate requests
        verify(bankingCoreRestClient, never()).utilityPayment(any());
        verify(utilityPaymentRepository, never()).save(any());
    }

    @Test
    void utilPayment_withNewIdempotencyKey_shouldProcessNormally() {
        // Arrange: no existing payment with this key
        when(utilityPaymentRepository.findByIdempotencyKey("util-key-new")).thenReturn(Optional.empty());

        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(2L);
        request.setAmount(BigDecimal.valueOf(300));
        request.setReferenceNumber("REF-NEW");
        request.setAccount("ACC-002");

        UtilityPaymentEntity savedEntity = new UtilityPaymentEntity();
        savedEntity.setId(2L);
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(savedEntity);

        UtilityPaymentResponse coreResponse = UtilityPaymentResponse.builder()
                .transactionId("UTIL-TXN-NEW")
                .message("OK")
                .build();
        when(bankingCoreRestClient.utilityPayment(any())).thenReturn(coreResponse);

        // Act
        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, "util-key-new");

        // Assert: new payment processed normally
        assertNotNull(result);
        assertEquals("UTIL-TXN-NEW", result.getTransactionId());
        assertEquals("Utility Payment Successfully Processed", result.getMessage());
        verify(bankingCoreRestClient, times(1)).utilityPayment(any());
    }

    @Test
    void utilPayment_withNullIdempotencyKey_shouldProcessWithoutDeduplication() {
        // Arrange: null key should skip deduplication entirely
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(3L);
        request.setAmount(BigDecimal.valueOf(75));
        request.setReferenceNumber("REF-003");
        request.setAccount("ACC-003");

        UtilityPaymentEntity savedEntity = new UtilityPaymentEntity();
        savedEntity.setId(3L);
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(savedEntity);

        UtilityPaymentResponse coreResponse = UtilityPaymentResponse.builder()
                .transactionId("UTIL-TXN-003")
                .message("OK")
                .build();
        when(bankingCoreRestClient.utilityPayment(any())).thenReturn(coreResponse);

        // Act
        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, null);

        // Assert: processed without checking idempotency
        assertNotNull(result);
        assertEquals("UTIL-TXN-003", result.getTransactionId());
        // findByIdempotencyKey should NOT be called when key is null
        verify(utilityPaymentRepository, never()).findByIdempotencyKey(any());
    }
}
