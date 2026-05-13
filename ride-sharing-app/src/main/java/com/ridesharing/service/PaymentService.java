package com.ridesharing.service;

import com.ridesharing.dto.response.PaymentResponse;
import com.ridesharing.entity.Payment;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.enums.PaymentMethod;
import com.ridesharing.enums.PaymentStatus;
import com.ridesharing.enums.RideStatus;
import com.ridesharing.exception.BadRequestException;
import com.ridesharing.exception.ResourceNotFoundException;
import com.ridesharing.repository.PaymentRepository;
import com.ridesharing.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service handling payment processing for completed rides.
 * Simulates payment processing with various payment methods.
 * In a production system, this would integrate with actual payment gateways.
 */
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RideRepository rideRepository;

    public PaymentService(PaymentRepository paymentRepository, RideRepository rideRepository) {
        this.paymentRepository = paymentRepository;
        this.rideRepository = rideRepository;
    }

    /**
     * Process payment for a completed ride.
     * Generates a unique transaction ID and simulates payment processing.
     *
     * @param rider         the rider making the payment
     * @param rideId        the ride to pay for
     * @param paymentMethod the payment method to use
     * @return payment response with transaction details
     */
    @Transactional
    public PaymentResponse processPayment(User rider, Long rideId, PaymentMethod paymentMethod) {
        logger.info("Processing payment for ride ID: {} with method: {}", rideId, paymentMethod);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        // Validate ride is completed
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Payment can only be processed for completed rides");
        }

        // Validate the rider owns this ride
        if (!ride.getRider().getId().equals(rider.getId())) {
            throw new BadRequestException("You can only pay for your own rides");
        }

        // Check if payment already exists for this ride
        if (paymentRepository.findByRideId(rideId).isPresent()) {
            throw new BadRequestException("Payment has already been processed for this ride");
        }

        // Generate unique transaction ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // Create payment record (simulated processing - always succeeds)
        Payment payment = Payment.builder()
                .ride(ride)
                .rider(rider)
                .driver(ride.getDriver())
                .amount(ride.getActualFare())
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.COMPLETED)
                .transactionId(transactionId)
                .build();

        payment = paymentRepository.save(payment);
        logger.info("Payment processed successfully. Transaction ID: {}", transactionId);

        return mapToPaymentResponse(payment);
    }

    /** Get payment details for a specific ride */
    public PaymentResponse getPaymentByRideId(Long rideId) {
        Payment payment = paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "rideId", rideId));
        return mapToPaymentResponse(payment);
    }

    /** Get payment history for a rider */
    public List<PaymentResponse> getRiderPayments(Long riderId) {
        return paymentRepository.findByRiderIdOrderByCreatedAtDesc(riderId).stream()
                .map(this::mapToPaymentResponse)
                .toList();
    }

    /** Get earnings history for a driver */
    public List<PaymentResponse> getDriverEarnings(Long driverId) {
        return paymentRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(this::mapToPaymentResponse)
                .toList();
    }

    /** Map Payment entity to PaymentResponse DTO */
    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .rideId(payment.getRide().getId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
