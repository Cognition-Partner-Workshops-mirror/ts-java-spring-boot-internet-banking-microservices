package com.ridesharing.dto.response;

import com.ridesharing.enums.RideStatus;
import com.ridesharing.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for ride details.
 * Contains full ride information including locations, fare, status, and participants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {
    private Long id;
    private Long riderId;
    private String riderName;
    private Long driverId;
    private String driverName;
    private String driverPhone;

    // Pickup details
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;

    // Drop-off details
    private Double dropOffLatitude;
    private Double dropOffLongitude;
    private String dropOffAddress;

    private RideStatus status;
    private VehicleType vehicleType;
    private BigDecimal estimatedFare;
    private BigDecimal actualFare;
    private BigDecimal distanceKm;
    private Integer estimatedDurationMinutes;
    private Integer actualDurationMinutes;
    private BigDecimal surgeMultiplier;
    private String otp;

    // Timestamps
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
