package com.petclinic.vet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.exception.GlobalExceptionHandler;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.service.VetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VetController.class)
@Import(GlobalExceptionHandler.class)
class VetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VetService vetService;

    @Autowired
    private ObjectMapper objectMapper;

    private VetResponseDto vetResponse;
    private VetRequestDto vetRequest;

    @BeforeEach
    void setUp() {
        SpecialtyResponseDto specialtyDto = SpecialtyResponseDto.builder()
                .id(1)
                .name("radiology")
                .build();

        vetResponse = VetResponseDto.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(specialtyDto))
                .build();

        vetRequest = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(specialtyDto))
                .build();
    }

    @Test
    void listVets_shouldReturnAllVets() throws Exception {
        when(vetService.getAllVets()).thenReturn(Arrays.asList(vetResponse));

        mockMvc.perform(get("/api/vets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("James"))
                .andExpect(jsonPath("$[0].lastName").value("Carter"))
                .andExpect(jsonPath("$[0].specialties[0].name").value("radiology"));
    }

    @Test
    void listVets_withLastNameFilter_shouldReturnFilteredVets() throws Exception {
        when(vetService.findByLastName("Carter")).thenReturn(Arrays.asList(vetResponse));

        mockMvc.perform(get("/api/vets").param("lastName", "Carter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Carter"));
    }

    @Test
    void listVets_withSpecialtyFilter_shouldReturnFilteredVets() throws Exception {
        when(vetService.findBySpecialtyName("radiology")).thenReturn(Arrays.asList(vetResponse));

        mockMvc.perform(get("/api/vets").param("specialty", "radiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].specialties[0].name").value("radiology"));
    }

    @Test
    void listVets_withNameSearch_shouldReturnFilteredVets() throws Exception {
        when(vetService.searchByName("James")).thenReturn(Arrays.asList(vetResponse));

        mockMvc.perform(get("/api/vets").param("name", "James"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("James"));
    }

    @Test
    void getVet_shouldReturnVet() throws Exception {
        when(vetService.getVetById(1)).thenReturn(vetResponse);

        mockMvc.perform(get("/api/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("James"))
                .andExpect(jsonPath("$.lastName").value("Carter"));
    }

    @Test
    void getVet_shouldReturn404WhenNotFound() throws Exception {
        when(vetService.getVetById(99)).thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(get("/api/vets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Vet not found with id: 99"));
    }

    @Test
    void addVet_shouldCreateVet() throws Exception {
        when(vetService.createVet(any(VetRequestDto.class))).thenReturn(vetResponse);

        mockMvc.perform(post("/api/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vetRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("James"));
    }

    @Test
    void addVet_shouldReturn400WhenInvalid() throws Exception {
        VetRequestDto invalidRequest = VetRequestDto.builder()
                .firstName("")
                .lastName("")
                .specialties(null)
                .build();

        mockMvc.perform(post("/api/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void addVet_shouldReturn400WhenFirstNameTooLong() throws Exception {
        VetRequestDto invalidRequest = VetRequestDto.builder()
                .firstName("A".repeat(31))
                .lastName("Carter")
                .specialties(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateVet_shouldUpdateVet() throws Exception {
        when(vetService.updateVet(eq(1), any(VetRequestDto.class))).thenReturn(vetResponse);

        mockMvc.perform(put("/api/vets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("James"));
    }

    @Test
    void updateVet_shouldReturn404WhenNotFound() throws Exception {
        when(vetService.updateVet(eq(99), any(VetRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(put("/api/vets/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vetRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteVet_shouldDeleteVet() throws Exception {
        when(vetService.deleteVet(1)).thenReturn(vetResponse);

        mockMvc.perform(delete("/api/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteVet_shouldReturn404WhenNotFound() throws Exception {
        when(vetService.deleteVet(99)).thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(delete("/api/vets/99"))
                .andExpect(status().isNotFound());
    }
}
