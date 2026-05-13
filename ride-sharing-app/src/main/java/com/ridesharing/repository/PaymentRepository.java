package com.ridesharing.repository;

import com.ridesharing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Payment entity operations.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRideId(Long rideId);

    List<Payment> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<Payment> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    Optional<Payment> findByTransactionId(String transactionId);
}
