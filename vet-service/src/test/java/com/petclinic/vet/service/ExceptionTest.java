package com.petclinic.vet.service;

import com.petclinic.vet.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void resourceNotFoundException_message() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Vet", 42);
        assertThat(ex.getMessage()).isEqualTo("Vet not found with id: 42");
    }
}
