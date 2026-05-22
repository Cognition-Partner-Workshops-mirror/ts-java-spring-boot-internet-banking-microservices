package com.casemanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Case Management API.
 * Provides Swagger UI at /swagger-ui.html for interactive API testing.
 *
 * The SM_USER header is automatically added as a global parameter
 * to all API endpoints in the Swagger UI for convenient testing.
 *
 * Access Swagger UI at: http://localhost:8080/swagger-ui.html
 * Access OpenAPI spec at: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configure OpenAPI metadata for the Swagger UI.
     * Describes the API purpose, version, and contact information.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("Case Management API")
                        .version("1.0.0")
                        .description(
                                "REST API for case data management with Oracle database backend. " +
                                "Provides two sets of endpoints for receiving case data " +
                                "(case number, version number, agent name, party identifier) " +
                                "and comprehensive API request logging for reporting.\n\n" +
                                "**Authentication:** All endpoints require the `SM_USER` header.\n\n" +
                                "**Reporting:** Every API request is logged and available via " +
                                "the /api/v1/reports endpoints for usage metrics.")
                        .contact(new Contact()
                                .name("Case Management Team")));
    }

    /**
     * Global operation customizer that adds the SM_USER header parameter
     * to all API operations in the Swagger UI.
     * This ensures testers always see the SM_USER field when testing endpoints.
     */
    @Bean
    public OperationCustomizer globalHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            // Check if SM_USER parameter is already defined on the operation
            boolean hasSmUser = operation.getParameters() != null &&
                    operation.getParameters().stream()
                            .anyMatch(p -> "SM_USER".equals(p.getName()));

            // If not already present, add it as a required header parameter
            if (!hasSmUser) {
                Parameter smUserParam = new Parameter()
                        .in("header")
                        .name("SM_USER")
                        .description("Authenticated user identifier from SiteMinder")
                        .required(true)
                        .schema(new StringSchema().example("john.doe"));
                operation.addParametersItem(smUserParam);
            }

            return operation;
        };
    }
}
