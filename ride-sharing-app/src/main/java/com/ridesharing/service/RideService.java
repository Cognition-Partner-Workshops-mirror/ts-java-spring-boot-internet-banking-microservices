package com.ridesharing.service;

import com.ridesharing.dto.request.FareEstimateRequest;
import com.ridesharing.dto.request.RideRequest;
import com.ridesharing.dto.response.FareEstimateResponse;
import com.ridesharing.dto.response.RideResponse;
import com.ridesharing.entity.DriverLocation;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.enums.DriverStatus;
import com.ridesharing.enums.RideStatus;
import com.ridesharing.enums.UserRole;
import com.ridesharing.exception.BadRequestException;
import com.ridesharing.exception.ResourceNotFoundException;
import com.ridesharing.exception.UnauthorizedException;
import com.ridesharing.repository.DriverLocationRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.util.DistanceCalculator;
import com.ridesharing.util.OtpGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Core service handling the complete ride lifecycle.
 * Manages ride booking, driver matching, status transitions,
 * fare calculation, and ride completion.
 */
@Service
public class RideService {

    private static final Logger logger = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final FareCalculationService fareCalculationService;

    @Value("${app.ride.search-radius-km}")
    private double searchRadiusKm;

    @Value("${app.ride.surge-multiplier}")
    private double defaultSurgeMultiplier;

    public RideService(RideRepository rideRepository,
                       UserRepository userRepository,
                       DriverLocationRepository driverLocationRepository,
                       FareCalculationService fareCalculationService) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.fareCalculationService = fareCalculationService;
    }

    /**
     * Book a new ride request.
     * Calculates fare estimate, generates OTP, and sets the ride to REQUESTED status.
     * The ride will be available for nearby drivers to accept.
     */
    @Transactional
    public RideResponse requestRide(User rider, RideRequest request) {
        logger.info("Ride requested by rider ID: {}", rider.getId());

        // Validate user is a rider
        if (rider.getRole() != UserRole.RIDER) {
            throw new BadRequestException("Only riders can request rides");
        }

        // Check if rider has any active rides already
        List<Ride> activeRides = rideRepository.findByRiderIdAndStatus(rider.getId(), RideStatus.REQUESTED);
        activeRides.addAll(rideRepository.findByRiderIdAndStatus(rider.getId(), RideStatus.ACCEPTED));
        activeRides.addAll(rideRepository.findByRiderIdAndStatus(rider.getId(), RideStatus.IN_PROGRESS));
        if (!activeRides.isEmpty()) {
            throw new BadRequestException("You already have an active ride. Please complete or cancel it first.");
        }

        // Calculate fare estimate
        FareEstimateResponse fareEstimate = fareCalculationService.calculateFareEstimate(
                FareEstimateRequest.builder()
                        .pickupLatitude(request.getPickupLatitude())
                        .pickupLongitude(request.getPickupLongitude())
                        .dropOffLatitude(request.getDropOffLatitude())
                        .dropOffLongitude(request.getDropOffLongitude())
                        .vehicleType(request.getVehicleType())
                        .build());

        // Generate OTP for ride verification at pickup
        String otp = OtpGenerator.generateOtp();

        // Create the ride entity
        Ride ride = Ride.builder()
                .rider(rider)
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .pickupAddress(request.getPickupAddress())
                .dropOffLatitude(request.getDropOffLatitude())
                .dropOffLongitude(request.getDropOffLongitude())
                .dropOffAddress(request.getDropOffAddress())
                .status(RideStatus.REQUESTED)
                .vehicleType(request.getVehicleType())
                .estimatedFare(fareEstimate.getEstimatedFare())
                .distanceKm(fareEstimate.getDistanceKm())
                .estimatedDurationMinutes(fareEstimate.getEstimatedDurationMinutes())
                .surgeMultiplier(BigDecimal.valueOf(defaultSurgeMultiplier))
                .otp(otp)
                .build();

        ride = rideRepository.save(ride);
        logger.info("Ride created with ID: {}, estimated fare: {}", ride.getId(), fareEstimate.getEstimatedFare());

        return mapToRideResponse(ride);
    }

    /**
     * Driver accepts a ride request.
     * Validates the driver is available and updates ride status to ACCEPTED.
     * Also marks the driver as BUSY to prevent accepting multiple rides.
     */
    @Transactional
    public RideResponse acceptRide(User driver, Long rideId) {
        logger.info("Driver ID: {} accepting ride ID: {}", driver.getId(), rideId);

        // Validate user is a driver
        if (driver.getRole() != UserRole.DRIVER) {
            throw new BadRequestException("Only drivers can accept rides");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        // Verify ride is in REQUESTED status
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new BadRequestException("This ride is no longer available for acceptance");
        }

        // Verify driver is available
        DriverLocation driverLocation = driverLocationRepository.findByDriverId(driver.getId())
                .orElseThrow(() -> new BadRequestException("Please update your location first"));

        if (driverLocation.getStatus() != DriverStatus.AVAILABLE) {
            throw new BadRequestException("You must be available to accept rides");
        }

        // Assign driver to ride and update statuses
        ride.setDriver(driver);
        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(LocalDateTime.now());

        // Mark driver as busy
        driverLocation.setStatus(DriverStatus.BUSY);
        driverLocationRepository.save(driverLocation);

        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} accepted by driver ID: {}", rideId, driver.getId());

        return mapToRideResponse(ride);
    }

    /** Update ride status to DRIVER_EN_ROUTE (driver is heading to pickup) */
    @Transactional
    public RideResponse startDriverEnRoute(User driver, Long rideId) {
        Ride ride = getAndValidateDriverRide(driver, rideId, RideStatus.ACCEPTED);
        ride.setStatus(RideStatus.DRIVER_EN_ROUTE);
        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} - driver en route to pickup", rideId);
        return mapToRideResponse(ride);
    }

    /** Update ride status to ARRIVED (driver arrived at pickup location) */
    @Transactional
    public RideResponse markDriverArrived(User driver, Long rideId) {
        Ride ride = getAndValidateDriverRide(driver, rideId, RideStatus.DRIVER_EN_ROUTE);
        ride.setStatus(RideStatus.ARRIVED);
        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} - driver arrived at pickup", rideId);
        return mapToRideResponse(ride);
    }

    /**
     * Start the ride (rider has been picked up).
     * Requires OTP verification to prevent fraud.
     */
    @Transactional
    public RideResponse startRide(User driver, Long rideId, String otp) {
        Ride ride = getAndValidateDriverRide(driver, rideId, RideStatus.ARRIVED);

        // Verify OTP for ride security
        if (!ride.getOtp().equals(otp)) {
            throw new BadRequestException("Invalid OTP. Please verify with the rider.");
        }

        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(LocalDateTime.now());
        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} started", rideId);

        return mapToRideResponse(ride);
    }

    /**
     * Complete a ride.
     * Calculates actual fare based on ride duration and distance,
     * and marks the driver as available again.
     */
    @Transactional
    public RideResponse completeRide(User driver, Long rideId) {
        Ride ride = getAndValidateDriverRide(driver, rideId, RideStatus.IN_PROGRESS);

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        // Calculate actual duration
        int actualDurationMinutes = (int) ChronoUnit.MINUTES.between(ride.getStartedAt(), ride.getCompletedAt());
        ride.setActualDurationMinutes(Math.max(actualDurationMinutes, 1)); // Minimum 1 minute

        // Calculate actual fare using the fare calculation service
        BigDecimal actualFare = fareCalculationService.calculateActualFare(
                ride.getDistanceKm().doubleValue(),
                ride.getActualDurationMinutes(),
                ride.getVehicleType(),
                ride.getSurgeMultiplier().doubleValue());
        ride.setActualFare(actualFare);

        // Mark driver as available again
        DriverLocation driverLocation = driverLocationRepository.findByDriverId(driver.getId())
                .orElse(null);
        if (driverLocation != null) {
            driverLocation.setStatus(DriverStatus.AVAILABLE);
            driverLocationRepository.save(driverLocation);
        }

        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} completed. Actual fare: {}", rideId, actualFare);

        return mapToRideResponse(ride);
    }

    /**
     * Cancel a ride. Can be cancelled by either the rider or the assigned driver.
     * Only rides in REQUESTED, ACCEPTED, DRIVER_EN_ROUTE, or ARRIVED status can be cancelled.
     */
    @Transactional
    public RideResponse cancelRide(User user, Long rideId, String reason) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        // Verify the user is either the rider or the assigned driver
        boolean isRider = ride.getRider().getId().equals(user.getId());
        boolean isDriver = ride.getDriver() != null && ride.getDriver().getId().equals(user.getId());

        if (!isRider && !isDriver) {
            throw new UnauthorizedException("You are not authorized to cancel this ride");
        }

        // Verify ride can be cancelled (not already completed or in progress)
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new BadRequestException("This ride cannot be cancelled");
        }
        if (ride.getStatus() == RideStatus.IN_PROGRESS) {
            throw new BadRequestException("Cannot cancel a ride that is already in progress");
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(LocalDateTime.now());
        ride.setCancellationReason(reason);

        // Free up the driver if one was assigned
        if (ride.getDriver() != null) {
            DriverLocation driverLocation = driverLocationRepository.findByDriverId(
                    ride.getDriver().getId()).orElse(null);
            if (driverLocation != null) {
                driverLocation.setStatus(DriverStatus.AVAILABLE);
                driverLocationRepository.save(driverLocation);
            }
        }

        ride = rideRepository.save(ride);
        logger.info("Ride ID: {} cancelled by user ID: {}. Reason: {}", rideId, user.getId(), reason);

        return mapToRideResponse(ride);
    }

    /** Get ride details by ID */
    public RideResponse getRideById(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        return mapToRideResponse(ride);
    }

    /** Get all rides for a specific rider */
    public List<RideResponse> getRiderRides(Long riderId) {
        return rideRepository.findByRiderIdOrderByCreatedAtDesc(riderId).stream()
                .map(this::mapToRideResponse)
                .toList();
    }

    /** Get all rides for a specific driver */
    public List<RideResponse> getDriverRides(Long driverId) {
        return rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(this::mapToRideResponse)
                .toList();
    }

    /** Get all rides with a specific status (e.g., all REQUESTED rides for driver matching) */
    public List<RideResponse> getRidesByStatus(RideStatus status) {
        return rideRepository.findByStatus(status).stream()
                .map(this::mapToRideResponse)
                .toList();
    }

    /**
     * Helper method to validate that the driver owns the ride and it's in the expected status.
     * Used by ride status transition methods to ensure valid state transitions.
     */
    private Ride getAndValidateDriverRide(User driver, Long rideId, RideStatus expectedStatus) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        // Verify the driver is assigned to this ride
        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new UnauthorizedException("You are not the assigned driver for this ride");
        }

        // Verify ride is in the expected status
        if (ride.getStatus() != expectedStatus) {
            throw new BadRequestException(
                    String.format("Ride must be in %s status. Current status: %s",
                            expectedStatus, ride.getStatus()));
        }

        return ride;
    }

    /** Map Ride entity to RideResponse DTO */
    private RideResponse mapToRideResponse(Ride ride) {
        RideResponse.RideResponseBuilder builder = RideResponse.builder()
                .id(ride.getId())
                .riderId(ride.getRider().getId())
                .riderName(ride.getRider().getFirstName() + " " + ride.getRider().getLastName())
                .pickupLatitude(ride.getPickupLatitude())
                .pickupLongitude(ride.getPickupLongitude())
                .pickupAddress(ride.getPickupAddress())
                .dropOffLatitude(ride.getDropOffLatitude())
                .dropOffLongitude(ride.getDropOffLongitude())
                .dropOffAddress(ride.getDropOffAddress())
                .status(ride.getStatus())
                .vehicleType(ride.getVehicleType())
                .estimatedFare(ride.getEstimatedFare())
                .actualFare(ride.getActualFare())
                .distanceKm(ride.getDistanceKm())
                .estimatedDurationMinutes(ride.getEstimatedDurationMinutes())
                .actualDurationMinutes(ride.getActualDurationMinutes())
                .surgeMultiplier(ride.getSurgeMultiplier())
                .otp(ride.getOtp())
                .requestedAt(ride.getRequestedAt())
                .acceptedAt(ride.getAcceptedAt())
                .startedAt(ride.getStartedAt())
                .completedAt(ride.getCompletedAt());

        // Include driver info if a driver is assigned
        if (ride.getDriver() != null) {
            builder.driverId(ride.getDriver().getId())
                    .driverName(ride.getDriver().getFirstName() + " " + ride.getDriver().getLastName())
                    .driverPhone(ride.getDriver().getPhoneNumber());
        }

        return builder.build();
    }
}
