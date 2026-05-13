package com.ridesharing.dto.response;

import com.ridesharing.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for fare estimates.
 * Shows the estimated cost breakdown before booking a ride.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateResponse {
    private BigDecimal estimatedFare;
    private BigDecimal distanceKm;
    private Integer estimatedDurationMinutes;
    private BigDecimal baseFare;
    private BigDecimal distanceCharge;
    private BigDecimal timeCharge;
    private BigDecimal surgeMultiplier;
    private VehicleType vehicleType;
}
