package com.ridesharing.controller;

import com.ridesharing.dto.request.RatingRequest;
import com.ridesharing.dto.response.ApiResponse;
import com.ridesharing.dto.response.RatingResponse;
import com.ridesharing.entity.User;
import com.ridesharing.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for rating operations.
 * Handles rating submission and retrieval for rides.
 * Both riders and drivers can rate each other after a completed ride.
 */
@RestController
@RequestMapping("/api/ratings")
@Tag(name = "Ratings", description = "Ride rating endpoints")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /** Submit a rating for a completed ride */
    @PostMapping
    @Operation(summary = "Submit rating", description = "Rate the other party after a completed ride")
    public ResponseEntity<ApiResponse<RatingResponse>> submitRating(
            @AuthenticationPrincipal User rater,
            @Valid @RequestBody RatingRequest request) {
        RatingResponse response = ratingService.submitRating(rater, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rating submitted successfully", response));
    }

    /** Get all ratings for a specific user */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user ratings", description = "Get all ratings received by a user")
    public ResponseEntity<ApiResponse<List<RatingResponse>>> getUserRatings(@PathVariable Long userId) {
        List<RatingResponse> ratings = ratingService.getUserRatings(userId);
        return ResponseEntity.ok(ApiResponse.success("User ratings retrieved", ratings));
    }

    /** Get average rating for a user */
    @GetMapping("/user/{userId}/average")
    @Operation(summary = "Get average rating", description = "Get the average rating for a user")
    public ResponseEntity<ApiResponse<Double>> getAverageRating(@PathVariable Long userId) {
        Double average = ratingService.getAverageRating(userId);
        return ResponseEntity.ok(ApiResponse.success("Average rating retrieved", average));
    }

    /** Get all ratings for a specific ride */
    @GetMapping("/ride/{rideId}")
    @Operation(summary = "Get ride ratings", description = "Get all ratings for a specific ride")
    public ResponseEntity<ApiResponse<List<RatingResponse>>> getRideRatings(@PathVariable Long rideId) {
        List<RatingResponse> ratings = ratingService.getRideRatings(rideId);
        return ResponseEntity.ok(ApiResponse.success("Ride ratings retrieved", ratings));
    }
}
