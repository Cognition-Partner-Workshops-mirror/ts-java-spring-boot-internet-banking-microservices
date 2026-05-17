package com.javatodev.finance.service;

import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.mapper.FundTransferMapper;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class FundTransferService {

    private final FundTransferRepository fundTransferRepository;
    private final BankingCoreFeignClient bankingCoreFeignClient;

    private FundTransferMapper mapper = new FundTransferMapper();

    /**
     * Processes a fund transfer with idempotency key support and failure rollback handling.
     * If a transfer with the same idempotency key already exists, returns the existing result.
     */
    @Transactional
    public FundTransferResponse fundTransfer(FundTransferRequest request, String idempotencyKey) {
        log.info("Processing fund transfer request");

        // Idempotency check — return existing result if already processed
        if (idempotencyKey != null) {
            Optional<FundTransferEntity> existing = fundTransferRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate fund transfer request detected, returning existing result");
                FundTransferEntity existingEntity = existing.get();
                return FundTransferResponse.builder()
                    .message("Fund Transfer Already Processed")
                    .transactionId(existingEntity.getTransactionReference())
                    .build();
            }
        }

        FundTransferEntity entity = new FundTransferEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(TransactionStatus.PENDING);
        entity.setIdempotencyKey(idempotencyKey);
        FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);

        try {
            // Call core-banking-service to process the actual fund transfer
            FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
            optFundTransfer.setTransactionReference(fundTransferResponse.getTransactionId());
            optFundTransfer.setStatus(TransactionStatus.SUCCESS);
            fundTransferRepository.save(optFundTransfer);

            fundTransferResponse.setMessage("Fund Transfer Successfully Completed");
            return fundTransferResponse;
        } catch (Exception e) {
            // Mark as FAILED on any error — core-banking @Transactional rolls back the DB changes
            log.error("Fund transfer failed, rolling back", e);
            optFundTransfer.setStatus(TransactionStatus.FAILED);
            fundTransferRepository.save(optFundTransfer);
            throw new SimpleBankingGlobalException("Fund transfer failed. No balance was deducted.");
        }
    }

    public List<FundTransfer> readAllTransfers(Pageable pageable) {
        return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
    }
}
