package com.javatodev.finance.service;

import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.mapper.UtilityPaymentMapper;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityPaymentService {
    private final UtilityPaymentRepository utilityPaymentRepository;
    private final BankingCoreRestClient bankingCoreRestClient;

    private UtilityPaymentMapper utilityPaymentMapper = new UtilityPaymentMapper();

    /**
     * Processes a utility payment with idempotency key support and failure rollback handling.
     * If a payment with the same idempotency key already exists, returns the existing result.
     */
    @Transactional
    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest paymentRequest, String idempotencyKey) {
        log.info("Processing utility payment request");

        // Idempotency check — return existing result if already processed
        if (idempotencyKey != null) {
            Optional<UtilityPaymentEntity> existing = utilityPaymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate utility payment request detected, returning existing result");
                UtilityPaymentEntity existingEntity = existing.get();
                return UtilityPaymentResponse.builder()
                    .message("Utility Payment Already Processed")
                    .transactionId(existingEntity.getTransactionId())
                    .build();
            }
        }

        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        BeanUtils.copyProperties(paymentRequest, entity);
        entity.setStatus(TransactionStatus.PROCESSING);
        entity.setIdempotencyKey(idempotencyKey);
        UtilityPaymentEntity optUtilPayment = utilityPaymentRepository.save(entity);

        try {
            // Call core-banking-service to process the actual utility payment
            UtilityPaymentResponse utilityPaymentResponse = bankingCoreRestClient.utilityPayment(paymentRequest);
            log.info("Utility payment processed successfully");

            optUtilPayment.setStatus(TransactionStatus.SUCCESS);
            optUtilPayment.setTransactionId(utilityPaymentResponse.getTransactionId());
            utilityPaymentRepository.save(optUtilPayment);

            return UtilityPaymentResponse.builder()
                .message("Utility Payment Successfully Processed")
                .transactionId(utilityPaymentResponse.getTransactionId())
                .build();
        } catch (Exception e) {
            // Mark as FAILED on any error — core-banking @Transactional rolls back the DB changes
            log.error("Utility payment failed, rolling back", e);
            optUtilPayment.setStatus(TransactionStatus.FAILED);
            utilityPaymentRepository.save(optUtilPayment);
            throw new SimpleBankingGlobalException("Utility payment failed. No balance was deducted.");
        }
    }

    public List<UtilityPayment> readPayments(Pageable pageable) {
        Page<UtilityPaymentEntity> allUtilPayments = utilityPaymentRepository.findAll(pageable);
        return utilityPaymentMapper.convertToDtoList(allUtilPayments.getContent());
    }
}
