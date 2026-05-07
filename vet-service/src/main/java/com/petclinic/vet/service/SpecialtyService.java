package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;

import java.util.List;

public interface SpecialtyService {

    List<SpecialtyResponseDto> getAllSpecialties();

    SpecialtyResponseDto getSpecialtyById(Integer id);

    SpecialtyResponseDto createSpecialty(SpecialtyRequestDto requestDto);

    SpecialtyResponseDto updateSpecialty(Integer id, SpecialtyRequestDto requestDto);

    SpecialtyResponseDto deleteSpecialty(Integer id);
}
