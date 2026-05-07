package com.petclinic.vet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VetRequestDto {

    @NotBlank
    @Size(min = 1, max = 30)
    @Pattern(regexp = "^[\\p{L}]+([ '\\-][\\p{L}]+){0,2}$")
    private String firstName;

    @NotBlank
    @Size(min = 1, max = 30)
    @Pattern(regexp = "^[\\p{L}]+([ '\\-][\\p{L}]+){0,2}\\.?$")
    private String lastName;

    private List<Integer> specialtyIds;
}
