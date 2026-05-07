package com.petclinic.vet.service;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;

import java.util.List;

public interface VetService {

    List<VetResponseDto> listVets();

    VetResponseDto getVet(Integer id);

    VetResponseDto addVet(VetRequestDto dto);

    VetResponseDto updateVet(Integer id, VetRequestDto dto);

    VetResponseDto deleteVet(Integer id);

    List<VetResponseDto> searchByName(String name);

    List<VetResponseDto> filterBySpecialty(Integer specialtyId);
}
