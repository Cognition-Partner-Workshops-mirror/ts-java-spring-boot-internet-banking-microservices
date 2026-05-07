package com.petclinic.vet.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vets/1");

    @Test
    void handleResourceNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Vet not found with id: 1");

        ProblemDetail problem = handler.handleResourceNotFound(ex, request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("Vet not found with id: 1");
        assertThat(problem.getProperties()).containsKey("timestamp");
        assertThat(problem.getProperties()).containsKey("schemaValidationErrors");
    }

    @Test
    void handleValidation_returns400() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("vetRequestDto", "firstName", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail problem = handler.handleValidation(ex, request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getProperties()).containsKey("schemaValidationErrors");
        @SuppressWarnings("unchecked")
        List<Object> errors = (List<Object>) problem.getProperties().get("schemaValidationErrors");
        assertThat(errors).hasSize(1);
    }

    @Test
    void handleGeneral_returns500() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ProblemDetail problem = handler.handleGeneral(ex, request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("RuntimeException");
        assertThat(problem.getDetail()).isEqualTo("Unexpected error");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
