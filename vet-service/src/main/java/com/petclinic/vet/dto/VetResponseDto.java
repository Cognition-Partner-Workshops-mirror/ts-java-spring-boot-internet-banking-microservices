package com.petclinic.vet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VetResponseDto {

    private Integer id;
    private String firstName;
    private String lastName;
    private List<SpecialtyResponseDto> specialties;
}
