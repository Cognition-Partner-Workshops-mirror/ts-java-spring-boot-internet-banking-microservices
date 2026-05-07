package com.petclinic.vet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.exception.GlobalExceptionHandler;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.service.SpecialtyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpecialtyController.class)
@Import(GlobalExceptionHandler.class)
class SpecialtyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpecialtyService specialtyService;

    @Autowired
    private ObjectMapper objectMapper;

    private SpecialtyResponseDto specialtyResponse;
    private SpecialtyRequestDto specialtyRequest;

    @BeforeEach
    void setUp() {
        specialtyResponse = SpecialtyResponseDto.builder()
                .id(1)
                .name("radiology")
                .build();

        specialtyRequest = SpecialtyRequestDto.builder()
                .name("radiology")
                .build();
    }

    @Test
    void listSpecialties_shouldReturnAllSpecialties() throws Exception {
        when(specialtyService.getAllSpecialties()).thenReturn(Arrays.asList(specialtyResponse));

        mockMvc.perform(get("/api/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("radiology"));
    }

    @Test
    void getSpecialty_shouldReturnSpecialty() throws Exception {
        when(specialtyService.getSpecialtyById(1)).thenReturn(specialtyResponse);

        mockMvc.perform(get("/api/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("radiology"));
    }

    @Test
    void getSpecialty_shouldReturn404WhenNotFound() throws Exception {
        when(specialtyService.getSpecialtyById(99))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(get("/api/specialties/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Specialty not found with id: 99"));
    }

    @Test
    void addSpecialty_shouldCreateSpecialty() throws Exception {
        when(specialtyService.createSpecialty(any(SpecialtyRequestDto.class))).thenReturn(specialtyResponse);

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialtyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("radiology"));
    }

    @Test
    void addSpecialty_shouldReturn400WhenInvalid() throws Exception {
        SpecialtyRequestDto invalidRequest = SpecialtyRequestDto.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void addSpecialty_shouldReturn400WhenNameNull() throws Exception {
        SpecialtyRequestDto invalidRequest = SpecialtyRequestDto.builder()
                .name(null)
                .build();

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addSpecialty_shouldReturn400WhenNameTooLong() throws Exception {
        SpecialtyRequestDto invalidRequest = SpecialtyRequestDto.builder()
                .name("A".repeat(81))
                .build();

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSpecialty_shouldUpdateSpecialty() throws Exception {
        when(specialtyService.updateSpecialty(eq(1), any(SpecialtyRequestDto.class))).thenReturn(specialtyResponse);

        mockMvc.perform(put("/api/specialties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialtyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("radiology"));
    }

    @Test
    void updateSpecialty_shouldReturn404WhenNotFound() throws Exception {
        when(specialtyService.updateSpecialty(eq(99), any(SpecialtyRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(put("/api/specialties/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialtyRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSpecialty_shouldDeleteSpecialty() throws Exception {
        when(specialtyService.deleteSpecialty(1)).thenReturn(specialtyResponse);

        mockMvc.perform(delete("/api/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteSpecialty_shouldReturn404WhenNotFound() throws Exception {
        when(specialtyService.deleteSpecialty(99))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(delete("/api/specialties/99"))
                .andExpect(status().isNotFound());
    }
}
