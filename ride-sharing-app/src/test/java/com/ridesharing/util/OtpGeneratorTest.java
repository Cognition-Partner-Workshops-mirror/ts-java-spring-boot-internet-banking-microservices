package com.ridesharing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OTP generator utility.
 */
class OtpGeneratorTest {

    @Test
    void generateOtp_returnsExactlyFourDigits() {
        String otp = OtpGenerator.generateOtp();
        assertEquals(4, otp.length(), "OTP should be exactly 4 digits");
    }

    @Test
    void generateOtp_returnsOnlyNumericDigits() {
        String otp = OtpGenerator.generateOtp();
        assertTrue(otp.matches("\\d{4}"), "OTP should contain only numeric digits");
    }

    @Test
    void generateOtp_generatesUniqueValues() {
        // Generate multiple OTPs and verify they are not all the same
        String otp1 = OtpGenerator.generateOtp();
        boolean foundDifferent = false;
        for (int i = 0; i < 100; i++) {
            if (!otp1.equals(OtpGenerator.generateOtp())) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent, "OTP generator should produce varying values");
    }
}
