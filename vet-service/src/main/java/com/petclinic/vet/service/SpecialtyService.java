package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;

import java.util.List;

public interface SpecialtyService {

    List<SpecialtyResponseDto> listSpecialties();

    SpecialtyResponseDto getSpecialty(Integer id);

    SpecialtyResponseDto addSpecialty(SpecialtyRequestDto dto);

    SpecialtyResponseDto updateSpecialty(Integer id, SpecialtyRequestDto dto);

    SpecialtyResponseDto deleteSpecialty(Integer id);

    List<SpecialtyResponseDto> searchByName(String name);
}
