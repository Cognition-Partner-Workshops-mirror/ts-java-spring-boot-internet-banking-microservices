package com.petclinic.vet.service;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.mapper.VetMapper;
import com.petclinic.vet.repository.SpecialtyRepository;
import com.petclinic.vet.repository.VetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VetServiceImpl implements VetService {

    private final VetRepository vetRepository;
    private final SpecialtyRepository specialtyRepository;
    private final VetMapper vetMapper;

    @Override
    public List<VetResponseDto> listVets() {
        return vetRepository.findAll().stream()
                .map(vetMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public VetResponseDto getVet(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet", id));
        return vetMapper.toResponseDto(vet);
    }

    @Override
    @Transactional
    public VetResponseDto createVet(VetRequestDto dto) {
        Set<Specialty> specialties = resolveSpecialties(dto.getSpecialtyIds());
        Vet vet = Vet.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .specialties(specialties)
                .build();
        Vet saved = vetRepository.save(vet);
        return vetMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public VetResponseDto updateVet(Integer id, VetRequestDto dto) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet", id));
        vet.setFirstName(dto.getFirstName());
        vet.setLastName(dto.getLastName());
        vet.setSpecialties(resolveSpecialties(dto.getSpecialtyIds()));
        Vet saved = vetRepository.save(vet);
        return vetMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public VetResponseDto deleteVet(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet", id));
        vetRepository.delete(vet);
        return vetMapper.toResponseDto(vet);
    }

    @Override
    public List<VetResponseDto> findBySpecialty(Integer specialtyId) {
        return vetRepository.findBySpecialtyId(specialtyId).stream()
                .map(vetMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<VetResponseDto> searchByName(String name) {
        return vetRepository.findByNameContaining(name).stream()
                .map(vetMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    private Set<Specialty> resolveSpecialties(List<Integer> specialtyIds) {
        if (specialtyIds == null || specialtyIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Specialty> found = specialtyRepository.findAllById(specialtyIds);
        if (found.size() != specialtyIds.size()) {
            Set<Integer> foundIds = found.stream().map(Specialty::getId).collect(Collectors.toSet());
            Integer missingId = specialtyIds.stream()
                    .filter(sid -> !foundIds.contains(sid))
                    .findFirst()
                    .orElse(null);
            throw new ResourceNotFoundException("Specialty", missingId);
        }
        return new HashSet<>(found);
    }
}
