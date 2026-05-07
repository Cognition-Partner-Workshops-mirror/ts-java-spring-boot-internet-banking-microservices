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
import static org.mockito.Mockito.*;

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
    void utilPayment_success() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        request.setReferenceNumber("REF001");
        request.setAccount("ACC001");

        UtilityPaymentEntity savedEntity = new UtilityPaymentEntity();
        savedEntity.setId(1L);
        savedEntity.setStatus(TransactionStatus.PROCESSING);
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(savedEntity);

        UtilityPaymentResponse coreResponse = UtilityPaymentResponse.builder()
            .transactionId("TXN-UTIL-1")
            .message("OK")
            .build();
        when(bankingCoreRestClient.utilityPayment(request)).thenReturn(coreResponse);

        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, null);

        assertNotNull(result);
        assertEquals("Utility Payment Successfully Processed", result.getMessage());
        assertEquals("TXN-UTIL-1", result.getTransactionId());
        verify(utilityPaymentRepository, times(2)).save(any(UtilityPaymentEntity.class));
    }

    @Test
    void utilPayment_idempotencyKey_duplicateReturnsExisting() {
        UtilityPaymentEntity existing = new UtilityPaymentEntity();
        existing.setId(1L);
        existing.setTransactionId("TXN-EXISTING");
        existing.setStatus(TransactionStatus.SUCCESS);
        when(utilityPaymentRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.of(existing));

        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        request.setReferenceNumber("REF001");
        request.setAccount("ACC001");

        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, "KEY-1");

        assertEquals("TXN-EXISTING", result.getTransactionId());
        verify(bankingCoreRestClient, never()).utilityPayment(any());
    }

    @Test
    void utilPayment_idempotencyKey_newRequestProcessesNormally() {
        when(utilityPaymentRepository.findByIdempotencyKey("KEY-NEW")).thenReturn(Optional.empty());

        UtilityPaymentEntity savedEntity = new UtilityPaymentEntity();
        savedEntity.setId(1L);
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(savedEntity);

        UtilityPaymentResponse coreResponse = UtilityPaymentResponse.builder()
            .transactionId("TXN-NEW")
            .message("OK")
            .build();
        when(bankingCoreRestClient.utilityPayment(any())).thenReturn(coreResponse);

        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(2L);
        request.setAmount(BigDecimal.valueOf(75));
        request.setReferenceNumber("REF002");
        request.setAccount("ACC002");

        UtilityPaymentResponse result = utilityPaymentService.utilPayment(request, "KEY-NEW");

        assertEquals("TXN-NEW", result.getTransactionId());
        verify(bankingCoreRestClient).utilityPayment(any());
    }

    @Test
    void utilPayment_feignFailurePropagates() {
        when(utilityPaymentRepository.save(any(UtilityPaymentEntity.class))).thenReturn(new UtilityPaymentEntity());
        when(bankingCoreRestClient.utilityPayment(any())).thenThrow(new RuntimeException("Service unavailable"));

        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        request.setReferenceNumber("REF001");
        request.setAccount("ACC001");

        assertThrows(RuntimeException.class, () -> utilityPaymentService.utilPayment(request, null));
    }
}
