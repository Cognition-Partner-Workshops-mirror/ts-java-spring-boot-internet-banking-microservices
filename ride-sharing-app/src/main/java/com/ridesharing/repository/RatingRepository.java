package com.ridesharing.repository;

import com.ridesharing.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Rating entity operations.
 * Includes a custom query for calculating average ratings.
 */
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByRateeIdOrderByCreatedAtDesc(Long rateeId);

    List<Rating> findByRideId(Long rideId);

    /** Check if a user has already rated for a specific ride */
    Optional<Rating> findByRideIdAndRaterId(Long rideId, Long raterId);

    /** Calculate the average rating for a user */
    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.ratee.id = :userId")
    Double findAverageRatingByUserId(@Param("userId") Long userId);
}
