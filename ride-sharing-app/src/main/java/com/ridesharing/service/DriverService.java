package com.ridesharing.service;

import com.ridesharing.dto.request.LocationUpdateRequest;
import com.ridesharing.dto.request.VehicleRequest;
import com.ridesharing.dto.response.DriverLocationResponse;
import com.ridesharing.entity.DriverLocation;
import com.ridesharing.entity.User;
import com.ridesharing.entity.Vehicle;
import com.ridesharing.enums.DriverStatus;
import com.ridesharing.enums.UserRole;
import com.ridesharing.exception.BadRequestException;
import com.ridesharing.exception.ResourceNotFoundException;
import com.ridesharing.repository.DriverLocationRepository;
import com.ridesharing.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service handling driver-specific operations including vehicle registration,
 * location updates, availability management, and nearby driver search.
 */
@Service
public class DriverService {

    private static final Logger logger = LoggerFactory.getLogger(DriverService.class);

    private final VehicleRepository vehicleRepository;
    private final DriverLocationRepository driverLocationRepository;

    public DriverService(VehicleRepository vehicleRepository,
                         DriverLocationRepository driverLocationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.driverLocationRepository = driverLocationRepository;
    }

    /**
     * Register a vehicle for a driver.
     * Each driver can only register one vehicle.
     */
    @Transactional
    public Vehicle registerVehicle(User driver, VehicleRequest request) {
        logger.info("Registering vehicle for driver ID: {}", driver.getId());

        // Validate user is a driver
        if (driver.getRole() != UserRole.DRIVER) {
            throw new BadRequestException("Only drivers can register vehicles");
        }

        // Check if driver already has a vehicle registered
        if (vehicleRepository.existsByDriverId(driver.getId())) {
            throw new BadRequestException("Driver already has a vehicle registered");
        }

        // Check for duplicate license plate
        if (vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new BadRequestException("License plate is already registered");
        }

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .make(request.getMake())
                .model(request.getModel())
                .year(request.getYear())
                .color(request.getColor())
                .licensePlate(request.getLicensePlate())
                .vehicleType(request.getVehicleType())
                .capacity(request.getCapacity())
                .build();

        vehicle = vehicleRepository.save(vehicle);
        logger.info("Vehicle registered with ID: {} for driver: {}", vehicle.getId(), driver.getId());
        return vehicle;
    }

    /** Get the vehicle registered by a specific driver */
    public Vehicle getDriverVehicle(Long driverId) {
        return vehicleRepository.findByDriverId(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "driverId", driverId));
    }

    /**
     * Update the driver's current GPS location.
     * Creates a new location entry if one doesn't exist, otherwise updates it.
     */
    @Transactional
    public DriverLocationResponse updateLocation(User driver, LocationUpdateRequest request) {
        // Validate user is a driver
        if (driver.getRole() != UserRole.DRIVER) {
            throw new BadRequestException("Only drivers can update location");
        }

        // Find existing location or create new one
        DriverLocation location = driverLocationRepository.findByDriverId(driver.getId())
                .orElse(DriverLocation.builder()
                        .driver(driver)
                        .status(DriverStatus.OFFLINE)
                        .build());

        // Update coordinates
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());

        location = driverLocationRepository.save(location);
        logger.debug("Location updated for driver ID: {} to ({}, {})",
                driver.getId(), request.getLatitude(), request.getLongitude());

        return mapToDriverLocationResponse(location, driver);
    }

    /**
     * Toggle driver availability status.
     * Drivers must be online (AVAILABLE) to receive ride requests.
     */
    @Transactional
    public DriverLocationResponse toggleAvailability(User driver, boolean available) {
        if (driver.getRole() != UserRole.DRIVER) {
            throw new BadRequestException("Only drivers can toggle availability");
        }

        DriverLocation location = driverLocationRepository.findByDriverId(driver.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Please update your location before going online"));

        // Update availability status
        DriverStatus newStatus = available ? DriverStatus.AVAILABLE : DriverStatus.OFFLINE;
        location.setStatus(newStatus);

        location = driverLocationRepository.save(location);
        logger.info("Driver ID: {} availability set to: {}", driver.getId(), newStatus);

        return mapToDriverLocationResponse(location, driver);
    }

    /** Find all available drivers near a given location within the search radius */
    public List<DriverLocationResponse> findNearbyDrivers(Double latitude, Double longitude, Double radiusKm) {
        List<DriverLocation> nearbyDrivers = driverLocationRepository.findNearbyAvailableDrivers(
                latitude, longitude, radiusKm, DriverStatus.AVAILABLE);

        return nearbyDrivers.stream()
                .map(dl -> mapToDriverLocationResponse(dl, dl.getDriver()))
                .toList();
    }

    /** Get all currently available drivers in the system */
    public List<DriverLocationResponse> getAvailableDrivers() {
        return driverLocationRepository.findByStatus(DriverStatus.AVAILABLE).stream()
                .map(dl -> mapToDriverLocationResponse(dl, dl.getDriver()))
                .toList();
    }

    /** Map DriverLocation entity to response DTO */
    private DriverLocationResponse mapToDriverLocationResponse(DriverLocation location, User driver) {
        return DriverLocationResponse.builder()
                .driverId(driver.getId())
                .driverName(driver.getFirstName() + " " + driver.getLastName())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .status(location.getStatus())
                .lastUpdated(location.getLastUpdated())
                .build();
    }
}
