package com.ridesharing.repository;

import com.ridesharing.entity.DriverLocation;
import com.ridesharing.enums.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DriverLocation entity operations.
 * Includes a custom query for finding nearby available drivers using the Haversine formula.
 */
@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

    Optional<DriverLocation> findByDriverId(Long driverId);

    List<DriverLocation> findByStatus(DriverStatus status);

    /**
     * Find available drivers within a given radius using Haversine distance formula.
     * Calculates great-circle distance between two GPS coordinates.
     *
     * @param latitude  center point latitude
     * @param longitude center point longitude
     * @param radiusKm  search radius in kilometers
     * @param status    driver availability status filter
     * @return list of nearby available drivers sorted by distance
     */
    @Query("SELECT dl FROM DriverLocation dl WHERE dl.status = :status AND " +
            "(6371 * acos(cos(radians(:latitude)) * cos(radians(dl.latitude)) * " +
            "cos(radians(dl.longitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(dl.latitude)))) <= :radiusKm " +
            "ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(dl.latitude)) * " +
            "cos(radians(dl.longitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(dl.latitude))))")
    List<DriverLocation> findNearbyAvailableDrivers(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusKm") Double radiusKm,
            @Param("status") DriverStatus status);
}
