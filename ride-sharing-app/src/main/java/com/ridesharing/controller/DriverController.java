package com.ridesharing.controller;

import com.ridesharing.dto.request.LocationUpdateRequest;
import com.ridesharing.dto.request.VehicleRequest;
import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.DriverLocationResponse;
import com.ridesharing.dto.response.RideResponse;
import com.ridesharing.entity.User;
import com.ridesharing.entity.Vehicle;
import com.ridesharing.enums.RideStatus;
import com.ridesharing.service.DriverService;
import com.ridesharing.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for driver-specific operations.
 * Handles vehicle registration, location updates, availability management,
 * and ride acceptance/management for drivers.
 * All endpoints require DRIVER role unless otherwise specified.
 */
@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Drivers", description = "Driver management and ride operations")
public class DriverController {

    private final DriverService driverService;
    private final RideService rideService;

    public DriverController(DriverService driverService, RideService rideService) {
        this.driverService = driverService;
        this.rideService = rideService;
    }

    /** Register a new vehicle for the authenticated driver */
    @PostMapping("/vehicle")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Register vehicle", description = "Register a vehicle for the driver")
    public ResponseEntity<ApiResponse<Vehicle>> registerVehicle(
            @AuthenticationPrincipal User driver,
            @Valid @RequestBody VehicleRequest request) {
        Vehicle vehicle = driverService.registerVehicle(driver, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle registered successfully", vehicle));
    }

    /** Update the driver's current GPS location */
    @PutMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Update location", description = "Update driver's current GPS coordinates")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> updateLocation(
            @AuthenticationPrincipal User driver,
            @Valid @RequestBody LocationUpdateRequest request) {
        DriverLocationResponse response = driverService.updateLocation(driver, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", response));
    }

    /** Go online - set driver status to AVAILABLE */
    @PutMapping("/go-online")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Go online", description = "Set driver status to available for rides")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> goOnline(
            @AuthenticationPrincipal User driver) {
        DriverLocationResponse response = driverService.toggleAvailability(driver, true);
        return ResponseEntity.ok(ApiResponse.success("You are now online and available for rides", response));
    }

    /** Go offline - set driver status to OFFLINE */
    @PutMapping("/go-offline")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Go offline", description = "Set driver status to offline")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> goOffline(
            @AuthenticationPrincipal User driver) {
        DriverLocationResponse response = driverService.toggleAvailability(driver, false);
        return ResponseEntity.ok(ApiResponse.success("You are now offline", response));
    }

    /** Get all currently available ride requests that the driver can accept */
    @GetMapping("/available-rides")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get available rides", description = "List all rides waiting for a driver")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getAvailableRides() {
        List<RideResponse> rides = rideService.getRidesByStatus(RideStatus.REQUESTED);
        return ResponseEntity.ok(ApiResponse.success("Available rides retrieved", rides));
    }

    /** Accept a ride request */
    @PutMapping("/rides/{rideId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Accept ride", description = "Accept a pending ride request")
    public ResponseEntity<ApiResponse<RideResponse>> acceptRide(
            @AuthenticationPrincipal User driver,
            @PathVariable Long rideId) {
        RideResponse response = rideService.acceptRide(driver, rideId);
        return ResponseEntity.ok(ApiResponse.success("Ride accepted", response));
    }

    /** Notify that driver is en route to pickup location */
    @PutMapping("/rides/{rideId}/en-route")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Start en route", description = "Mark driver as en route to pickup")
    public ResponseEntity<ApiResponse<RideResponse>> startEnRoute(
            @AuthenticationPrincipal User driver,
            @PathVariable Long rideId) {
        RideResponse response = rideService.startDriverEnRoute(driver, rideId);
        return ResponseEntity.ok(ApiResponse.success("Driver en route to pickup", response));
    }

    /** Mark that driver has arrived at the pickup location */
    @PutMapping("/rides/{rideId}/arrived")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Mark arrived", description = "Mark driver as arrived at pickup location")
    public ResponseEntity<ApiResponse<RideResponse>> markArrived(
            @AuthenticationPrincipal User driver,
            @PathVariable Long rideId) {
        RideResponse response = rideService.markDriverArrived(driver, rideId);
        return ResponseEntity.ok(ApiResponse.success("Driver arrived at pickup location", response));
    }

    /** Start the ride after OTP verification */
    @PutMapping("/rides/{rideId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Start ride", description = "Start the ride after OTP verification")
    public ResponseEntity<ApiResponse<RideResponse>> startRide(
            @AuthenticationPrincipal User driver,
            @PathVariable Long rideId,
            @RequestParam String otp) {
        RideResponse response = rideService.startRide(driver, rideId, otp);
        return ResponseEntity.ok(ApiResponse.success("Ride started", response));
    }

    /** Complete the ride */
    @PutMapping("/rides/{rideId}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Complete ride", description = "Mark the ride as completed")
    public ResponseEntity<ApiResponse<RideResponse>> completeRide(
            @AuthenticationPrincipal User driver,
            @PathVariable Long rideId) {
        RideResponse response = rideService.completeRide(driver, rideId);
        return ResponseEntity.ok(ApiResponse.success("Ride completed", response));
    }

    /** Get the driver's ride history */
    @GetMapping("/rides")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get driver rides", description = "Get ride history for the authenticated driver")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getDriverRides(
            @AuthenticationPrincipal User driver) {
        List<RideResponse> rides = rideService.getDriverRides(driver.getId());
        return ResponseEntity.ok(ApiResponse.success("Driver rides retrieved", rides));
    }

    /** Get nearby available drivers (accessible to all authenticated users) */
    @GetMapping("/nearby")
    @Operation(summary = "Find nearby drivers", description = "Find available drivers near a location")
    public ResponseEntity<ApiResponse<List<DriverLocationResponse>>> getNearbyDrivers(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5.0") Double radiusKm) {
        List<DriverLocationResponse> drivers = driverService.findNearbyDrivers(latitude, longitude, radiusKm);
        return ResponseEntity.ok(ApiResponse.success("Nearby drivers found", drivers));
    }
}
