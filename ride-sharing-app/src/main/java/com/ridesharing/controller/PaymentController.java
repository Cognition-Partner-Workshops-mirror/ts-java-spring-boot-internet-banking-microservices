package com.ridesharing.controller;

import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.PaymentResponse;
import com.ridesharing.entity.User;
import com.ridesharing.enums.PaymentMethod;
import com.ridesharing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for payment operations.
 * Handles payment processing, payment history, and driver earnings.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment processing and history endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Process payment for a completed ride (riders only) */
    @PostMapping("/pay")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Process payment", description = "Pay for a completed ride")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal User rider,
            @RequestParam Long rideId,
            @RequestParam PaymentMethod paymentMethod) {
        PaymentResponse response = paymentService.processPayment(rider, rideId, paymentMethod);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", response));
    }

    /** Get payment details for a specific ride */
    @GetMapping("/ride/{rideId}")
    @Operation(summary = "Get ride payment", description = "Get payment details for a ride")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByRide(@PathVariable Long rideId) {
        PaymentResponse response = paymentService.getPaymentByRideId(rideId);
        return ResponseEntity.ok(ApiResponse.success("Payment details retrieved", response));
    }

    /** Get payment history for the authenticated rider */
    @GetMapping("/my-payments")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Get my payments", description = "Get payment history for the rider")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal User rider) {
        List<PaymentResponse> payments = paymentService.getRiderPayments(rider.getId());
        return ResponseEntity.ok(ApiResponse.success("Payment history retrieved", payments));
    }

    /** Get earnings history for the authenticated driver */
    @GetMapping("/my-earnings")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get my earnings", description = "Get earnings history for the driver")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyEarnings(
            @AuthenticationPrincipal User driver) {
        List<PaymentResponse> earnings = paymentService.getDriverEarnings(driver.getId());
        return ResponseEntity.ok(ApiResponse.success("Earnings history retrieved", earnings));
    }
}
