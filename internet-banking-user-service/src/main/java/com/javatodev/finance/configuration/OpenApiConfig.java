package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Internet Banking User Service API")
                        .description("User registration, authentication, and management for internet banking")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("JavaToDev")
                                .url("https://javatodev.com")));
    }
}
