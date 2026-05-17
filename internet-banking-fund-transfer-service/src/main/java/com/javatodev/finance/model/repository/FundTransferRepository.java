package com.javatodev.finance.model.repository;

import com.javatodev.finance.model.entity.FundTransferEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundTransferRepository extends JpaRepository<FundTransferEntity, Long> {

    // Lookup by idempotency key to prevent duplicate fund transfer processing
    Optional<FundTransferEntity> findByIdempotencyKey(String idempotencyKey);
}
