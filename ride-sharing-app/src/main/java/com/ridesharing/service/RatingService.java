package com.ridesharing.service;

import com.ridesharing.dto.request.RatingRequest;
import com.ridesharing.dto.response.RatingResponse;
import com.ridesharing.entity.Rating;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.enums.RideStatus;
import com.ridesharing.enums.UserRole;
import com.ridesharing.exception.BadRequestException;
import com.ridesharing.exception.ResourceNotFoundException;
import com.ridesharing.repository.RatingRepository;
import com.ridesharing.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service handling ride ratings.
 * Allows riders and drivers to rate each other after a completed ride.
 * Ratings are on a 1-5 scale with optional comments.
 */
@Service
public class RatingService {

    private static final Logger logger = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository;
    private final RideRepository rideRepository;

    public RatingService(RatingRepository ratingRepository, RideRepository rideRepository) {
        this.ratingRepository = ratingRepository;
        this.rideRepository = rideRepository;
    }

    /**
     * Submit a rating for a completed ride.
     * Riders rate drivers, and drivers rate riders.
     * Each user can only rate once per ride.
     */
    @Transactional
    public RatingResponse submitRating(User rater, RatingRequest request) {
        logger.info("Rating submitted by user ID: {} for ride ID: {}", rater.getId(), request.getRideId());

        Ride ride = rideRepository.findById(request.getRideId())
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", request.getRideId()));

        // Validate ride is completed
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Ratings can only be submitted for completed rides");
        }

        // Check if user already rated this ride
        if (ratingRepository.findByRideIdAndRaterId(request.getRideId(), rater.getId()).isPresent()) {
            throw new BadRequestException("You have already rated this ride");
        }

        // Determine the ratee (the other party in the ride)
        User ratee;
        if (rater.getRole() == UserRole.RIDER) {
            // Rider is rating the driver
            if (!ride.getRider().getId().equals(rater.getId())) {
                throw new BadRequestException("You can only rate rides you participated in");
            }
            ratee = ride.getDriver();
        } else {
            // Driver is rating the rider
            if (ride.getDriver() == null || !ride.getDriver().getId().equals(rater.getId())) {
                throw new BadRequestException("You can only rate rides you participated in");
            }
            ratee = ride.getRider();
        }

        Rating rating = Rating.builder()
                .ride(ride)
                .rater(rater)
                .ratee(ratee)
                .score(request.getScore())
                .comment(request.getComment())
                .build();

        rating = ratingRepository.save(rating);
        logger.info("Rating saved with ID: {}, score: {}", rating.getId(), rating.getScore());

        return mapToRatingResponse(rating);
    }

    /** Get all ratings received by a specific user */
    public List<RatingResponse> getUserRatings(Long userId) {
        return ratingRepository.findByRateeIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToRatingResponse)
                .toList();
    }

    /** Get the average rating for a user */
    public Double getAverageRating(Long userId) {
        Double average = ratingRepository.findAverageRatingByUserId(userId);
        return average != null ? average : 0.0;
    }

    /** Get all ratings for a specific ride */
    public List<RatingResponse> getRideRatings(Long rideId) {
        return ratingRepository.findByRideId(rideId).stream()
                .map(this::mapToRatingResponse)
                .toList();
    }

    /** Map Rating entity to RatingResponse DTO */
    private RatingResponse mapToRatingResponse(Rating rating) {
        return RatingResponse.builder()
                .id(rating.getId())
                .rideId(rating.getRide().getId())
                .raterId(rating.getRater().getId())
                .raterName(rating.getRater().getFirstName() + " " + rating.getRater().getLastName())
                .rateeId(rating.getRatee().getId())
                .rateeName(rating.getRatee().getFirstName() + " " + rating.getRatee().getLastName())
                .score(rating.getScore())
                .comment(rating.getComment())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}
