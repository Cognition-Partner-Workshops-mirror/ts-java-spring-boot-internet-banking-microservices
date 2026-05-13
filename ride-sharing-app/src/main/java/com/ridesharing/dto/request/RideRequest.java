package com.ridesharing.dto.request;

import com.ridesharing.enums.PaymentMethod;
import com.ridesharing.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for booking a new ride.
 * Contains pickup/drop-off coordinates, addresses, and preferences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequest {

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLongitude;

    @NotBlank(message = "Pickup address is required")
    private String pickupAddress;

    @NotNull(message = "Drop-off latitude is required")
    private Double dropOffLatitude;

    @NotNull(message = "Drop-off longitude is required")
    private Double dropOffLongitude;

    @NotBlank(message = "Drop-off address is required")
    private String dropOffAddress;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
}
