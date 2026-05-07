package com.petclinic.vet.controller;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.service.SpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    public ResponseEntity<List<SpecialtyResponseDto>> listSpecialties() {
        List<SpecialtyResponseDto> specialties = specialtyService.getAllSpecialties();
        return ResponseEntity.ok(specialties);
    }

    @GetMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> getSpecialty(@PathVariable Integer specialtyId) {
        SpecialtyResponseDto specialty = specialtyService.getSpecialtyById(specialtyId);
        return ResponseEntity.ok(specialty);
    }

    @PostMapping
    public ResponseEntity<SpecialtyResponseDto> addSpecialty(@Valid @RequestBody SpecialtyRequestDto requestDto) {
        SpecialtyResponseDto created = specialtyService.createSpecialty(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> updateSpecialty(@PathVariable Integer specialtyId,
                                                                @Valid @RequestBody SpecialtyRequestDto requestDto) {
        SpecialtyResponseDto updated = specialtyService.updateSpecialty(specialtyId, requestDto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> deleteSpecialty(@PathVariable Integer specialtyId) {
        SpecialtyResponseDto deleted = specialtyService.deleteSpecialty(specialtyId);
        return ResponseEntity.ok(deleted);
    }
}
