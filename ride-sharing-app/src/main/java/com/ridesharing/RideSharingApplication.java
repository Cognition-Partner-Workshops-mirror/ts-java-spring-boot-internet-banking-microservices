package com.ridesharing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Ride Sharing Application.
 * A ride-sharing platform similar to Ola and Uber,
 * providing ride booking, driver matching, fare calculation,
 * and payment processing capabilities.
 */
@SpringBootApplication
public class RideSharingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideSharingApplication.class, args);
    }
}
