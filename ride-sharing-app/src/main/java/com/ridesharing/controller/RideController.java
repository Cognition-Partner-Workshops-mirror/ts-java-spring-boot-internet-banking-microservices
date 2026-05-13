package com.ridesharing.controller;

import com.ridesharing.dto.request.FareEstimateRequest;
import com.ridesharing.dto.request.RideRequest;
import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.FareEstimateResponse;
import com.ridesharing.dto.response.RideResponse;
import com.ridesharing.entity.User;
import com.ridesharing.service.FareCalculationService;
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
 * REST controller for ride operations from the rider's perspective.
 * Handles ride booking, cancellation, fare estimation, and ride history.
 */
@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides", description = "Ride booking and management endpoints")
public class RideController {

    private final RideService rideService;
    private final FareCalculationService fareCalculationService;

    public RideController(RideService rideService, FareCalculationService fareCalculationService) {
        this.rideService = rideService;
        this.fareCalculationService = fareCalculationService;
    }

    /** Get a fare estimate before booking (publicly accessible) */
    @PostMapping("/estimate")
    @Operation(summary = "Get fare estimate", description = "Calculate estimated fare for a ride")
    public ResponseEntity<ApiResponse<FareEstimateResponse>> getFareEstimate(
            @Valid @RequestBody FareEstimateRequest request) {
        FareEstimateResponse estimate = fareCalculationService.calculateFareEstimate(request);
        return ResponseEntity.ok(ApiResponse.success("Fare estimate calculated", estimate));
    }

    /** Book a new ride (riders only) */
    @PostMapping("/book")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Book a ride", description = "Request a new ride")
    public ResponseEntity<ApiResponse<RideResponse>> bookRide(
            @AuthenticationPrincipal User rider,
            @Valid @RequestBody RideRequest request) {
        RideResponse response = rideService.requestRide(rider, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ride booked successfully. Searching for nearby drivers...", response));
    }

    /** Cancel a ride (by rider or assigned driver) */
    @PutMapping("/{rideId}/cancel")
    @Operation(summary = "Cancel ride", description = "Cancel an active ride")
    public ResponseEntity<ApiResponse<RideResponse>> cancelRide(
            @AuthenticationPrincipal User user,
            @PathVariable Long rideId,
            @RequestParam(defaultValue = "No reason provided") String reason) {
        RideResponse response = rideService.cancelRide(user, rideId, reason);
        return ResponseEntity.ok(ApiResponse.success("Ride cancelled", response));
    }

    /** Get ride details by ID */
    @GetMapping("/{rideId}")
    @Operation(summary = "Get ride details", description = "Get details of a specific ride")
    public ResponseEntity<ApiResponse<RideResponse>> getRideById(@PathVariable Long rideId) {
        RideResponse response = rideService.getRideById(rideId);
        return ResponseEntity.ok(ApiResponse.success("Ride details retrieved", response));
    }

    /** Get ride history for the authenticated rider */
    @GetMapping("/my-rides")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Get my rides", description = "Get ride history for the authenticated rider")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getMyRides(
            @AuthenticationPrincipal User rider) {
        List<RideResponse> rides = rideService.getRiderRides(rider.getId());
        return ResponseEntity.ok(ApiResponse.success("Rider rides retrieved", rides));
    }
}
