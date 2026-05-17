package com.javatodev.finance.repository;

import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilityPaymentRepository extends JpaRepository<UtilityPaymentEntity, UtilityPayment> {

    // Lookup by idempotency key to prevent duplicate utility payment processing
    Optional<UtilityPaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
