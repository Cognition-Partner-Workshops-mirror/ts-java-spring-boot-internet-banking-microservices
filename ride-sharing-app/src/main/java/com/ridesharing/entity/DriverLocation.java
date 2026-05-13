package com.ridesharing.entity;

import com.ridesharing.enums.DriverStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks the real-time location and availability status of drivers.
 * Updated frequently as drivers move and change their availability.
 * Used by the ride matching algorithm to find nearby available drivers.
 */
@Entity
@Table(name = "driver_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The driver whose location is being tracked */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private User driver;

    /** Current latitude coordinate of the driver */
    @Column(nullable = false)
    private Double latitude;

    /** Current longitude coordinate of the driver */
    @Column(nullable = false)
    private Double longitude;

    /** Current availability status of the driver */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status;

    /** Timestamp of the last location update */
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
