package com.ridesharing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridesharing.dto.request.RegisterRequest;
import com.ridesharing.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the AuthController endpoints.
 * Tests user registration and login flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_validRequest_returnsCreatedWithToken() throws Exception {
        // Register a new rider with valid details
        RegisterRequest request = RegisterRequest.builder()
                .email("testuser@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+91-1234567890")
                .role(UserRole.RIDER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.data.role").value("RIDER"));
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() throws Exception {
        // Attempt to register with an email that already exists (from DataInitializer)
        RegisterRequest request = RegisterRequest.builder()
                .email("rider1@example.com")
                .password("password123")
                .firstName("Duplicate")
                .lastName("User")
                .phoneNumber("+91-1111111111")
                .role(UserRole.RIDER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        // Attempt to register with an invalid email format
        RegisterRequest request = RegisterRequest.builder()
                .email("invalid-email")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+91-9999999999")
                .role(UserRole.RIDER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
