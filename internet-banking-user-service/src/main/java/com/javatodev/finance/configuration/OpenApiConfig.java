package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Internet Banking User Service API")
                .description("Manages user registration, profile updates, and Keycloak identity integration for the internet banking platform.")
                .version("2.0.0")
                .contact(new Contact()
                    .name("JavaToDev")
                    .url("https://javatodev.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8083").description("Local Development"),
                new Server().url("http://internet-banking-user-service:8083").description("Docker Environment")));
    }
}
