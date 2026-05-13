package com.ridesharing.dto.response;

import com.ridesharing.enums.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for driver location and status information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationResponse {
    private Long driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private DriverStatus status;
    private LocalDateTime lastUpdated;
}
