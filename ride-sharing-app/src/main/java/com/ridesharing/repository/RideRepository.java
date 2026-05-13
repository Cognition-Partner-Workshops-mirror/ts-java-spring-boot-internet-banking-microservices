package com.ridesharing.repository;

import com.ridesharing.entity.Ride;
import com.ridesharing.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Ride entity operations.
 * Provides queries for finding rides by rider, driver, and status.
 */
@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    List<Ride> findByRiderIdAndStatus(Long riderId, RideStatus status);

    List<Ride> findByDriverIdAndStatus(Long driverId, RideStatus status);

    List<Ride> findByStatus(RideStatus status);

    /** Count completed rides for a given rider */
    Long countByRiderIdAndStatus(Long riderId, RideStatus status);

    /** Count completed rides for a given driver */
    Long countByDriverIdAndStatus(Long driverId, RideStatus status);
}
