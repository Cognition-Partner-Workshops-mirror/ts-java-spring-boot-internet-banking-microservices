package com.petclinic.vet.service;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;

import java.util.List;

public interface VetService {

    List<VetResponseDto> getAllVets();

    VetResponseDto getVetById(Integer id);

    VetResponseDto createVet(VetRequestDto requestDto);

    VetResponseDto updateVet(Integer id, VetRequestDto requestDto);

    VetResponseDto deleteVet(Integer id);

    List<VetResponseDto> findByLastName(String lastName);

    List<VetResponseDto> findBySpecialtyName(String specialtyName);

    List<VetResponseDto> searchByName(String name);
}
