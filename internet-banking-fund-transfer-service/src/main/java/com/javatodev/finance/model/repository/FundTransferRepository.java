package com.javatodev.finance.model.repository;

import com.javatodev.finance.model.entity.FundTransferEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundTransferRepository extends JpaRepository<FundTransferEntity, Long> {
    Optional<FundTransferEntity> findByIdempotencyKey(String idempotencyKey);
}
