package com.ridesharing.config;

import com.ridesharing.entity.DriverLocation;
import com.ridesharing.entity.User;
import com.ridesharing.entity.Vehicle;
import com.ridesharing.enums.DriverStatus;
import com.ridesharing.enums.UserRole;
import com.ridesharing.enums.VehicleType;
import com.ridesharing.repository.DriverLocationRepository;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Initializes the database with sample data for development and testing.
 * Creates sample riders, drivers with vehicles and locations.
 * This runs automatically on application startup with the H2 database.
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
                                       VehicleRepository vehicleRepository,
                                       DriverLocationRepository driverLocationRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            // Only initialize if database is empty
            if (userRepository.count() > 0) {
                logger.info("Database already initialized, skipping data seeding");
                return;
            }

            logger.info("Initializing sample data...");

            // Create sample riders
            User rider1 = userRepository.save(User.builder()
                    .email("rider1@example.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Rahul")
                    .lastName("Sharma")
                    .phoneNumber("+91-9876543210")
                    .role(UserRole.RIDER)
                    .active(true)
                    .build());

            User rider2 = userRepository.save(User.builder()
                    .email("rider2@example.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Priya")
                    .lastName("Patel")
                    .phoneNumber("+91-9876543211")
                    .role(UserRole.RIDER)
                    .active(true)
                    .build());

            // Create sample drivers
            User driver1 = userRepository.save(User.builder()
                    .email("driver1@example.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Amit")
                    .lastName("Kumar")
                    .phoneNumber("+91-9876543220")
                    .role(UserRole.DRIVER)
                    .active(true)
                    .build());

            User driver2 = userRepository.save(User.builder()
                    .email("driver2@example.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Suresh")
                    .lastName("Singh")
                    .phoneNumber("+91-9876543221")
                    .role(UserRole.DRIVER)
                    .active(true)
                    .build());

            User driver3 = userRepository.save(User.builder()
                    .email("driver3@example.com")
                    .password(passwordEncoder.encode("password123"))
                    .firstName("Vikram")
                    .lastName("Reddy")
                    .phoneNumber("+91-9876543222")
                    .role(UserRole.DRIVER)
                    .active(true)
                    .build());

            // Register vehicles for drivers
            vehicleRepository.save(Vehicle.builder()
                    .driver(driver1)
                    .make("Maruti Suzuki")
                    .model("Swift Dzire")
                    .year(2023)
                    .color("White")
                    .licensePlate("KA-01-AB-1234")
                    .vehicleType(VehicleType.SEDAN)
                    .capacity(4)
                    .build());

            vehicleRepository.save(Vehicle.builder()
                    .driver(driver2)
                    .make("Hyundai")
                    .model("Creta")
                    .year(2024)
                    .color("Black")
                    .licensePlate("KA-02-CD-5678")
                    .vehicleType(VehicleType.SUV)
                    .capacity(6)
                    .build());

            vehicleRepository.save(Vehicle.builder()
                    .driver(driver3)
                    .make("Bajaj")
                    .model("RE Auto")
                    .year(2022)
                    .color("Green")
                    .licensePlate("KA-03-EF-9012")
                    .vehicleType(VehicleType.AUTO)
                    .capacity(3)
                    .build());

            // Set driver locations (Bangalore area coordinates)
            driverLocationRepository.save(DriverLocation.builder()
                    .driver(driver1)
                    .latitude(12.9716)
                    .longitude(77.5946)
                    .status(DriverStatus.AVAILABLE)
                    .build());

            driverLocationRepository.save(DriverLocation.builder()
                    .driver(driver2)
                    .latitude(12.9352)
                    .longitude(77.6245)
                    .status(DriverStatus.AVAILABLE)
                    .build());

            driverLocationRepository.save(DriverLocation.builder()
                    .driver(driver3)
                    .latitude(12.9783)
                    .longitude(77.5712)
                    .status(DriverStatus.AVAILABLE)
                    .build());

            // Create an admin user
            userRepository.save(User.builder()
                    .email("admin@ridesharing.com")
                    .password(passwordEncoder.encode("admin123"))
                    .firstName("Admin")
                    .lastName("User")
                    .phoneNumber("+91-9876543200")
                    .role(UserRole.ADMIN)
                    .active(true)
                    .build());

            logger.info("Sample data initialized: 2 riders, 3 drivers, 1 admin");
        };
    }
}
