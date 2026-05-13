package com.ridesharing.dto.request;

import com.ridesharing.enums.VehicleType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for getting a fare estimate before booking a ride.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateRequest {

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLongitude;

    @NotNull(message = "Drop-off latitude is required")
    private Double dropOffLatitude;

    @NotNull(message = "Drop-off longitude is required")
    private Double dropOffLongitude;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;
}
