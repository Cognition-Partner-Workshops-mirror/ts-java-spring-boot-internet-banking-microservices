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
import static org.mockito.Mockito.*;

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
    void fundTransfer_success() {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(100));

        FundTransferEntity savedEntity = new FundTransferEntity();
        savedEntity.setId(1L);
        savedEntity.setStatus(TransactionStatus.PENDING);
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(savedEntity);

        FundTransferResponse coreResponse = new FundTransferResponse();
        coreResponse.setTransactionId("TXN-123");
        coreResponse.setMessage("OK");
        when(bankingCoreFeignClient.fundTransfer(request)).thenReturn(coreResponse);

        FundTransferResponse result = fundTransferService.fundTransfer(request, null);

        assertNotNull(result);
        assertEquals("Fund Transfer Successfully Completed", result.getMessage());
        assertEquals("TXN-123", result.getTransactionId());
        verify(fundTransferRepository, times(2)).save(any(FundTransferEntity.class));
    }

    @Test
    void fundTransfer_idempotencyKey_duplicateReturnsExisting() {
        FundTransferEntity existing = new FundTransferEntity();
        existing.setId(1L);
        existing.setTransactionReference("TXN-EXISTING");
        existing.setStatus(TransactionStatus.SUCCESS);
        when(fundTransferRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.of(existing));

        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(100));

        FundTransferResponse result = fundTransferService.fundTransfer(request, "KEY-1");

        assertEquals("TXN-EXISTING", result.getTransactionId());
        verify(bankingCoreFeignClient, never()).fundTransfer(any());
    }

    @Test
    void fundTransfer_idempotencyKey_newRequestProcessesNormally() {
        when(fundTransferRepository.findByIdempotencyKey("KEY-NEW")).thenReturn(Optional.empty());

        FundTransferEntity savedEntity = new FundTransferEntity();
        savedEntity.setId(1L);
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(savedEntity);

        FundTransferResponse coreResponse = new FundTransferResponse();
        coreResponse.setTransactionId("TXN-NEW");
        when(bankingCoreFeignClient.fundTransfer(any())).thenReturn(coreResponse);

        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(50));

        FundTransferResponse result = fundTransferService.fundTransfer(request, "KEY-NEW");

        assertEquals("TXN-NEW", result.getTransactionId());
        verify(bankingCoreFeignClient).fundTransfer(any());
    }

    @Test
    void fundTransfer_feignFailurePropagates() {
        when(fundTransferRepository.save(any(FundTransferEntity.class))).thenReturn(new FundTransferEntity());
        when(bankingCoreFeignClient.fundTransfer(any())).thenThrow(new RuntimeException("Service unavailable"));

        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("ACC001");
        request.setToAccount("ACC002");
        request.setAmount(BigDecimal.valueOf(100));

        assertThrows(RuntimeException.class, () -> fundTransferService.fundTransfer(request, null));
    }
}
