package com.ridesharing.enums;

/**
 * Represents the lifecycle states of a ride.
 * REQUESTED - Ride has been requested by a rider, awaiting driver acceptance.
 * ACCEPTED - A driver has accepted the ride request.
 * DRIVER_EN_ROUTE - Driver is on the way to pickup location.
 * ARRIVED - Driver has arrived at pickup location.
 * IN_PROGRESS - Ride is currently in progress (rider picked up).
 * COMPLETED - Ride has been completed successfully.
 * CANCELLED - Ride was cancelled by either rider or driver.
 */
public enum RideStatus {
    REQUESTED,
    ACCEPTED,
    DRIVER_EN_ROUTE,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
