package com.ridesharing.util;

/**
 * Utility class for geographic distance calculations.
 * Uses the Haversine formula to calculate great-circle distance
 * between two GPS coordinates on the Earth's surface.
 */
public final class DistanceCalculator {

    /** Earth's radius in kilometers */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private DistanceCalculator() {
        // Prevent instantiation of utility class
    }

    /**
     * Calculate distance between two GPS coordinates using the Haversine formula.
     *
     * @param lat1 latitude of the first point
     * @param lon1 longitude of the first point
     * @param lat2 latitude of the second point
     * @param lon2 longitude of the second point
     * @return distance in kilometers
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Estimate travel duration based on distance.
     * Uses an average speed assumption based on urban driving conditions.
     *
     * @param distanceKm distance in kilometers
     * @return estimated duration in minutes
     */
    public static int estimateDuration(double distanceKm) {
        // Assume average speed of 25 km/h in urban areas
        double averageSpeedKmh = 25.0;
        return (int) Math.ceil((distanceKm / averageSpeedKmh) * 60);
    }
}
