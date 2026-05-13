package com.ridesharing.repository;

import com.ridesharing.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Vehicle entity operations.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByDriverId(Long driverId);

    Boolean existsByLicensePlate(String licensePlate);

    Boolean existsByDriverId(Long driverId);
}
