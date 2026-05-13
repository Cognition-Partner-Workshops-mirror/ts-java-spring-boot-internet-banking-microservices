package com.ridesharing.entity;

import com.ridesharing.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a vehicle registered by a driver in the system.
 * Each driver must register a vehicle before they can accept rides.
 * Stores vehicle details like make, model, registration number, and type.
 */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The driver who owns this vehicle */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private User driver;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(name = "manufacture_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String color;

    /** Vehicle registration/license plate number */
    @Column(nullable = false, unique = true)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    /** Maximum number of passengers the vehicle can carry */
    @Column(nullable = false)
    private Integer capacity;
}
