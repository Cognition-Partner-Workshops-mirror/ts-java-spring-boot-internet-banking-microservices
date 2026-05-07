package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.PageResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.mapper.FundTransferMapper;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

    public FundTransferResponse fundTransfer(FundTransferRequest request, String idempotencyKey) {
        log.info("Sending fund transfer request");

        if (idempotencyKey != null) {
            Optional<FundTransferEntity> existing = fundTransferRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate request detected for idempotency key {}", idempotencyKey);
                FundTransferResponse response = new FundTransferResponse();
                response.setMessage("Fund Transfer Successfully Completed");
                response.setTransactionId(existing.get().getTransactionReference());
                return response;
            }
        }

        FundTransferEntity entity = new FundTransferEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(TransactionStatus.PENDING);
        entity.setIdempotencyKey(idempotencyKey);
        FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);

        FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
        optFundTransfer.setTransactionReference(fundTransferResponse.getTransactionId());
        optFundTransfer.setStatus(TransactionStatus.SUCCESS);
        fundTransferRepository.save(optFundTransfer);

        fundTransferResponse.setMessage("Fund Transfer Successfully Completed");
        return fundTransferResponse;

    }

    public PageResponse<FundTransfer> readAllTransfers(Pageable pageable) {
        Page<FundTransferEntity> page = fundTransferRepository.findAll(pageable);
        List<FundTransfer> transfers = mapper.convertToDtoList(page.getContent());
        return PageResponse.<FundTransfer>builder()
            .content(transfers)
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .last(page.isLast())
            .build();
    }
}
