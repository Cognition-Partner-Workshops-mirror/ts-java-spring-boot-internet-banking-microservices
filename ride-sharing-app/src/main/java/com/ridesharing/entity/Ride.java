package com.ridesharing.entity;

import com.ridesharing.enums.RideStatus;
import com.ridesharing.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core entity representing a ride in the system.
 * Tracks the complete lifecycle of a ride from request to completion,
 * including pickup/drop-off locations, fare, distance, and timing.
 */
@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The rider who requested this ride */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private User rider;

    /** The driver assigned to this ride (null until accepted) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    // Pickup location details
    @Column(nullable = false)
    private Double pickupLatitude;

    @Column(nullable = false)
    private Double pickupLongitude;

    @Column(nullable = false)
    private String pickupAddress;

    // Drop-off location details
    @Column(nullable = false)
    private Double dropOffLatitude;

    @Column(nullable = false)
    private Double dropOffLongitude;

    @Column(nullable = false)
    private String dropOffAddress;

    /** Current status of the ride */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    /** Type of vehicle requested for this ride */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    /** Estimated fare shown to rider before booking */
    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    /** Actual fare calculated after ride completion */
    @Column(precision = 10, scale = 2)
    private BigDecimal actualFare;

    /** Distance of the ride in kilometers */
    @Column(precision = 10, scale = 2)
    private BigDecimal distanceKm;

    /** Estimated duration of the ride in minutes */
    private Integer estimatedDurationMinutes;

    /** Actual duration of the ride in minutes */
    private Integer actualDurationMinutes;

    /** Surge pricing multiplier applied to this ride */
    @Column(precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    /** OTP for ride verification at pickup */
    private String otp;

    /** Reason for cancellation (if cancelled) */
    private String cancellationReason;

    // Ride lifecycle timestamps
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        requestedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
