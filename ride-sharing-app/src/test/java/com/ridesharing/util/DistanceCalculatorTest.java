package com.ridesharing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DistanceCalculator utility.
 * Verifies Haversine distance calculations and duration estimates.
 */
class DistanceCalculatorTest {

    @Test
    void calculateDistance_samePoint_returnsZero() {
        // Distance from a point to itself should be zero
        double distance = DistanceCalculator.calculateDistance(12.9716, 77.5946, 12.9716, 77.5946);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void calculateDistance_knownPoints_returnsCorrectDistance() {
        // Distance between Bangalore MG Road and Whitefield (~15-17km)
        double distance = DistanceCalculator.calculateDistance(
                12.9716, 77.5946,  // MG Road, Bangalore
                12.9698, 77.7500); // Whitefield, Bangalore
        assertTrue(distance > 15 && distance < 18,
                "Expected distance between 15-18km but got: " + distance);
    }

    @Test
    void calculateDistance_longDistance_returnsCorrectDistance() {
        // Distance between Bangalore and Mumbai (~840-850km)
        double distance = DistanceCalculator.calculateDistance(
                12.9716, 77.5946,  // Bangalore
                19.0760, 72.8777); // Mumbai
        assertTrue(distance > 830 && distance < 860,
                "Expected distance between 830-860km but got: " + distance);
    }

    @Test
    void estimateDuration_shortDistance_returnsMinimumMinutes() {
        // 1km at 25km/h should take about 2-3 minutes
        int duration = DistanceCalculator.estimateDuration(1.0);
        assertTrue(duration >= 2 && duration <= 3);
    }

    @Test
    void estimateDuration_mediumDistance_returnsReasonableTime() {
        // 10km at 25km/h should take about 24 minutes
        int duration = DistanceCalculator.estimateDuration(10.0);
        assertEquals(24, duration);
    }
}
