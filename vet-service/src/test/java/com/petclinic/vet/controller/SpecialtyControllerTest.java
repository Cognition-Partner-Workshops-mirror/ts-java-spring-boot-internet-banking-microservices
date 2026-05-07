package com.petclinic.vet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.exception.GlobalExceptionHandler;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.service.SpecialtyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(SpecialtyController.class)
@Import(GlobalExceptionHandler.class)
class SpecialtyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SpecialtyService specialtyService;

    @Test
    void listSpecialties_returnsOk() throws Exception {
        SpecialtyResponseDto dto = SpecialtyResponseDto.builder().id(1).name("radiology").build();
        when(specialtyService.listSpecialties()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("radiology"));
    }

    @Test
    void getSpecialty_found() throws Exception {
        SpecialtyResponseDto dto = SpecialtyResponseDto.builder().id(1).name("radiology").build();
        when(specialtyService.getSpecialty(1)).thenReturn(dto);

        mockMvc.perform(get("/api/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("radiology"));
    }

    @Test
    void getSpecialty_notFound() throws Exception {
        when(specialtyService.getSpecialty(99)).thenThrow(new ResourceNotFoundException("Specialty", 99));

        mockMvc.perform(get("/api/specialties/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void addSpecialty_valid() throws Exception {
        SpecialtyRequestDto request = SpecialtyRequestDto.builder().name("radiology").build();
        SpecialtyResponseDto response = SpecialtyResponseDto.builder().id(1).name("radiology").build();
        when(specialtyService.createSpecialty(any())).thenReturn(response);

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void addSpecialty_invalidBlankName() throws Exception {
        SpecialtyRequestDto request = SpecialtyRequestDto.builder().name("").build();

        mockMvc.perform(post("/api/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void updateSpecialty_valid() throws Exception {
        SpecialtyRequestDto request = SpecialtyRequestDto.builder().name("surgery").build();
        SpecialtyResponseDto response = SpecialtyResponseDto.builder().id(1).name("surgery").build();
        when(specialtyService.updateSpecialty(eq(1), any())).thenReturn(response);

        mockMvc.perform(put("/api/specialties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("surgery"));
    }

    @Test
    void deleteSpecialty_found() throws Exception {
        SpecialtyResponseDto response = SpecialtyResponseDto.builder().id(1).name("radiology").build();
        when(specialtyService.deleteSpecialty(1)).thenReturn(response);

        mockMvc.perform(delete("/api/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
