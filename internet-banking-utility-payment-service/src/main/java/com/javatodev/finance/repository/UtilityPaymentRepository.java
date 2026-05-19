package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.UtilityPaymentEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilityPaymentRepository extends JpaRepository<UtilityPaymentEntity, Long> {

    // Find existing payment by idempotency key to prevent duplicate processing
    Optional<UtilityPaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
