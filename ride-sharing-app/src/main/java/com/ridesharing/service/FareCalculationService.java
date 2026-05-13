package com.ridesharing.service;

import com.ridesharing.dto.request.FareEstimateRequest;
import com.ridesharing.dto.response.FareEstimateResponse;
import com.ridesharing.enums.VehicleType;
import com.ridesharing.util.DistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for calculating ride fares.
 * Computes fare based on distance, time, vehicle type, and surge pricing.
 * Each vehicle type has a different rate multiplier.
 */
@Service
public class FareCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(FareCalculationService.class);

    @Value("${app.ride.base-fare}")
    private double baseFare;

    @Value("${app.ride.per-km-rate}")
    private double perKmRate;

    @Value("${app.ride.per-minute-rate}")
    private double perMinuteRate;

    @Value("${app.ride.surge-multiplier}")
    private double defaultSurgeMultiplier;

    /**
     * Calculate a fare estimate for a ride request.
     * Factors in distance, estimated time, vehicle type multiplier, and surge pricing.
     */
    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request) {
        // Calculate distance using Haversine formula
        double distanceKm = DistanceCalculator.calculateDistance(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDropOffLatitude(), request.getDropOffLongitude());

        // Estimate travel duration based on distance
        int estimatedDurationMinutes = DistanceCalculator.estimateDuration(distanceKm);

        // Get vehicle type multiplier (premium vehicles cost more)
        double vehicleMultiplier = getVehicleTypeMultiplier(request.getVehicleType());

        // Calculate fare components
        BigDecimal baseCharge = BigDecimal.valueOf(baseFare);
        BigDecimal distanceCharge = BigDecimal.valueOf(distanceKm * perKmRate * vehicleMultiplier);
        BigDecimal timeCharge = BigDecimal.valueOf(estimatedDurationMinutes * perMinuteRate);
        BigDecimal surgeMultiplier = BigDecimal.valueOf(defaultSurgeMultiplier);

        // Total fare = (base + distance + time) * surge multiplier
        BigDecimal totalFare = baseCharge
                .add(distanceCharge)
                .add(timeCharge)
                .multiply(surgeMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        logger.info("Fare estimate: distance={}km, duration={}min, fare={}",
                String.format("%.2f", distanceKm), estimatedDurationMinutes, totalFare);

        return FareEstimateResponse.builder()
                .estimatedFare(totalFare)
                .distanceKm(BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP))
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .baseFare(baseCharge.setScale(2, RoundingMode.HALF_UP))
                .distanceCharge(distanceCharge.setScale(2, RoundingMode.HALF_UP))
                .timeCharge(timeCharge.setScale(2, RoundingMode.HALF_UP))
                .surgeMultiplier(surgeMultiplier)
                .vehicleType(request.getVehicleType())
                .build();
    }

    /**
     * Calculate the actual fare after a ride is completed.
     * Uses actual distance and duration instead of estimates.
     */
    public BigDecimal calculateActualFare(double distanceKm, int durationMinutes,
                                           VehicleType vehicleType, double surgeMultiplier) {
        double vehicleMultiplier = getVehicleTypeMultiplier(vehicleType);

        BigDecimal baseCharge = BigDecimal.valueOf(baseFare);
        BigDecimal distanceCharge = BigDecimal.valueOf(distanceKm * perKmRate * vehicleMultiplier);
        BigDecimal timeCharge = BigDecimal.valueOf(durationMinutes * perMinuteRate);

        return baseCharge
                .add(distanceCharge)
                .add(timeCharge)
                .multiply(BigDecimal.valueOf(surgeMultiplier))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get the pricing multiplier for each vehicle type.
     * Premium vehicles have higher multipliers.
     */
    private double getVehicleTypeMultiplier(VehicleType vehicleType) {
        return switch (vehicleType) {
            case BIKE -> 0.6;      // Most affordable option
            case AUTO -> 0.8;      // Budget-friendly auto-rickshaw
            case MINI -> 1.0;      // Standard economy car
            case SEDAN -> 1.3;     // Comfortable sedan
            case SUV -> 1.6;       // Spacious SUV
            case PREMIUM -> 2.0;   // Premium/luxury vehicle
        };
    }
}
