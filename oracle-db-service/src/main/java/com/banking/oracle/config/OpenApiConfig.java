package com.banking.oracle.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI/Swagger documentation.
 * Provides API metadata displayed in the Swagger UI at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Defines the OpenAPI specification metadata for the Oracle DB Service API.
     *
     * @return OpenAPI object with API title, description, version, and contact info
     */
    @Bean
    public OpenAPI oracleDbServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Oracle DB Service API")
                        .description("Spring Boot REST API connected to Oracle Database. " +
                                "Provides CRUD operations for customer management " +
                                "with Oracle DB as the persistence layer.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Banking Team")
                                .email("banking-team@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
