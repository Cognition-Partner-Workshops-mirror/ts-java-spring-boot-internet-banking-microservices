package com.petclinic.vet.controller;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.service.SpecialtyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/specialties")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public ResponseEntity<List<SpecialtyResponseDto>> listSpecialties(
            @RequestParam(required = false) String name) {
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(specialtyService.searchByName(name));
        }
        return ResponseEntity.ok(specialtyService.listSpecialties());
    }

    @GetMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> getSpecialty(@PathVariable Integer specialtyId) {
        return ResponseEntity.ok(specialtyService.getSpecialty(specialtyId));
    }

    @PostMapping
    public ResponseEntity<SpecialtyResponseDto> addSpecialty(
            @Valid @RequestBody SpecialtyRequestDto dto) {
        return ResponseEntity.ok(specialtyService.addSpecialty(dto));
    }

    @PutMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> updateSpecialty(
            @PathVariable Integer specialtyId,
            @Valid @RequestBody SpecialtyRequestDto dto) {
        return ResponseEntity.ok(specialtyService.updateSpecialty(specialtyId, dto));
    }

    @DeleteMapping("/{specialtyId}")
    public ResponseEntity<SpecialtyResponseDto> deleteSpecialty(@PathVariable Integer specialtyId) {
        return ResponseEntity.ok(specialtyService.deleteSpecialty(specialtyId));
    }
}
