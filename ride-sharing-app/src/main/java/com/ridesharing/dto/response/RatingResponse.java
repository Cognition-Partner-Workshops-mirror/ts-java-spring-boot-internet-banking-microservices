package com.ridesharing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for rating details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponse {
    private Long id;
    private Long rideId;
    private Long raterId;
    private String raterName;
    private Long rateeId;
    private String rateeName;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
