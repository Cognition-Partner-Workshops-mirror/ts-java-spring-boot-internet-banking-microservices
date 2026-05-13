package com.ridesharing.util;

import java.security.SecureRandom;

/**
 * Utility class for generating One-Time Passwords (OTPs).
 * Used to verify rider identity during ride pickup.
 * The rider shares the OTP with the driver to confirm the ride.
 */
public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 4;

    private OtpGenerator() {
        // Prevent instantiation of utility class
    }

    /**
     * Generate a random 4-digit numeric OTP.
     *
     * @return 4-digit OTP string (e.g., "4829")
     */
    public static String generateOtp() {
        int otp = RANDOM.nextInt((int) Math.pow(10, OTP_LENGTH));
        return String.format("%0" + OTP_LENGTH + "d", otp);
    }
}
